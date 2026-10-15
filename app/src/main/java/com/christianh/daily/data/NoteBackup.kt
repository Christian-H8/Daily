package com.christianh.daily.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.christianh.daily.domain.NoteMerge
import com.christianh.daily.domain.model.Note
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * The notes' off-device copy: `daily-notes.json` in the folder chosen in Settings
 * (SRS [R8.1]–[R8.5], D-028).
 *
 * **Why it exists.** `notes.json` lives in app-private storage, which no sync client
 * can read, so the D-020 claim that "the store is the export" was true of the format
 * and false of the location. Until D-027 the only copy anywhere else was Google Auto
 * Backup, which is now excluded. Without this file a lost phone, a broken phone, or a
 * signing-key change (which forces an uninstall) deletes every note.
 *
 * **It does not touch the network, and D-003 still holds.** It writes one file into a
 * folder on the phone. Whether that file goes anywhere is the sync app's business,
 * and the same folder already carries the corpus in the other direction. It survives
 * an uninstall either way, because the folder is in shared storage rather than the
 * app's own.
 *
 * **Off the main thread, latest wins.** Each change enqueues a snapshot of the whole
 * map. If one is already queued it is replaced rather than joined, so a burst of
 * debounced saves costs one write. The worker is a single daemon thread; nothing
 * waits on it.
 *
 * **The first write to a folder is guarded.** A folder that already holds a copy, say
 * after a reinstall, may have notes this phone has never seen. Overwriting it with an
 * empty or shorter set is the one way this feature could destroy what it exists to
 * protect, so until this install has written to *this* folder once, a write that
 * [NoteMerge.wouldLose] anything is refused and Settings says "Restore first". After
 * that first write the phone is the truth and deletions propagate.
 */
object NoteBackup {

    const val FILE_NAME = NoteBackupFile.NAME

    private const val PREFS = "daily"
    private const val KEY_SAVED_AT = "notes_backup_at"
    private const val KEY_SAVED_COUNT = "notes_backup_count"
    /** The tree URI this install last wrote to: the guard's "written here before". */
    private const val KEY_WRITTEN_TREE = "notes_backup_tree"

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "notes-backup").apply { isDaemon = true }
    }
    private val pending = AtomicReference<Map<LocalDate, Note>?>(null)

    /** The last outcome that was not a success, cleared by the next success. */
    @Volatile
    private var problem: BackupStatus? = null

    /** Queue a copy of [notes]. A no-op without a folder. */
    fun enqueue(context: Context, notes: Map<LocalDate, Note>) {
        if (CorpusSource.treeUri(context) == null) return
        val app = context.applicationContext
        if (pending.getAndSet(notes) == null) {
            worker.execute {
                val latest = pending.getAndSet(null) ?: return@execute
                val outcome = try {
                    backUp(app, latest)
                } catch (t: Throwable) {
                    BackupStatus.Failed(t.message ?: t.javaClass.simpleName)
                }
                problem = if (outcome is BackupStatus.Saved) null else outcome
            }
        }
    }

    /** The folder's copy as text, or null if there is no folder or no copy. */
    fun read(context: Context): String? = CorpusSource.siblingJson(context, FILE_NAME)

    fun status(context: Context): BackupStatus {
        val tree = CorpusSource.treeUri(context) ?: return BackupStatus.NoFolder
        if (!CorpusSource.canWrite(context, tree)) return BackupStatus.ReadOnly
        problem?.let { return it }
        val p = prefs(context)
        val at = p.getLong(KEY_SAVED_AT, 0L)
        if (at == 0L || p.getString(KEY_WRITTEN_TREE, null) != tree.toString()) {
            return BackupStatus.Pending
        }
        return BackupStatus.Saved(at, p.getInt(KEY_SAVED_COUNT, 0))
    }

    private fun backUp(context: Context, notes: Map<LocalDate, Note>): BackupStatus {
        val tree = CorpusSource.treeUri(context) ?: return BackupStatus.NoFolder
        val p = prefs(context)

        if (p.getString(KEY_WRITTEN_TREE, null) != tree.toString()) {
            // An unreadable copy does not block: it protects nothing, and refusing to
            // replace it would leave the folder without a usable copy indefinitely.
            val existing = read(context)?.let { runCatching { NoteStore.decode(it) }.getOrNull() }
            if (existing != null) {
                val lost = NoteMerge.wouldLose(notes, existing)
                if (lost > 0) return BackupStatus.Blocked(lost)
            }
        }

        writeDocument(context, tree, NoteStore.encode(notes))

        val now = System.currentTimeMillis()
        p.edit()
            .putLong(KEY_SAVED_AT, now)
            .putInt(KEY_SAVED_COUNT, notes.size)
            .putString(KEY_WRITTEN_TREE, tree.toString())
            .apply()
        return BackupStatus.Saved(now, notes.size)
    }

    private fun writeDocument(context: Context, tree: Uri, json: String) {
        val resolver = context.contentResolver
        val doc = CorpusSource.child(context, tree, FILE_NAME)
            ?: DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree)
                ),
                "application/json",
                FILE_NAME
            )
            ?: throw IOException("could not create $FILE_NAME")
        // "wt", not "w": some providers do not truncate on "w", which would leave the
        // tail of a longer old copy after a shorter new one and make it invalid JSON.
        // Not atomic. A torn copy is recoverable, because notes.json is untouched
        // and the next save rewrites this file whole.
        val out = resolver.openOutputStream(doc, "wt")
            ?: throw IOException("could not open $FILE_NAME for writing")
        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

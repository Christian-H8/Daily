package com.christianh.daily.data

import android.content.Context
import com.christianh.daily.domain.model.Note
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Notes on disk, as one small JSON file in app-private storage.
 *
 * **This deviates from SDS §4 / ARCHITECTURE §2, which specify Room.** The reasoning
 * for Room was that notes are non-disposable and need a real migration path — which
 * is true, and is satisfied here by an explicit [VERSION] and a migration function,
 * in about twenty lines rather than an annotation processor.
 *
 * What Room would buy: typed queries, Flow observability, indexed lookup. All three
 * are for a scale this will never reach — one note per day is ~365 rows a year, read
 * entirely into memory in a single pass.
 *
 * What Room would cost, against [NFR5]: the KSP plugin, a version that must stay
 * aligned with every Kotlin release, a committed schema JSON, and annotation
 * processing in every build. That is exactly the recurring upgrade obligation the
 * longevity pillar exists to refuse.
 *
 * The format is the plain, human-readable one [R8.1] asks for. The location is not:
 * app-private storage is invisible to any sync client, so this file alone was never
 * the export D-020 took it for. [NoteBackup] writes the same JSON into the chosen
 * folder (D-028).
 *
 * `NoteRepository` is the boundary, so swapping this for Room later touches one
 * file. That is the ports-and-adapters argument actually being cashed in.
 */
object NoteStore {

    const val FILE_NAME = "notes.json"
    const val VERSION = 1

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun read(context: Context): Map<LocalDate, Note> {
        val f = file(context)
        if (!f.exists()) return emptyMap()
        return runCatching { decode(f.readText()) }.getOrElse { emptyMap() }
    }

    /**
     * Atomic: write a temp file and rename over the original. A half-written notes
     * file is the one failure in this app that loses something unrecoverable.
     */
    fun write(context: Context, notes: Map<LocalDate, Note>) {
        val target = file(context)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeText(encode(notes))
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    // --- pure, so both directions are testable off-device ---

    fun encode(notes: Map<LocalDate, Note>): String {
        val arr = JSONArray()
        notes.values.sortedBy { it.date }.forEach { n ->
            arr.put(
                JSONObject()
                    .put("date", n.date.toString())
                    .put("entryId", n.entryId)
                    .put("text", n.text)
                    .put("createdAt", n.createdAt)
                    .put("updatedAt", n.updatedAt)
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("notes", arr)
            .toString(2)
    }

    fun decode(json: String): Map<LocalDate, Note> {
        val root = JSONObject(json)
        val migrated = migrate(root)
        val arr = migrated.optJSONArray("notes") ?: return emptyMap()
        val out = LinkedHashMap<LocalDate, Note>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: continue
            out[date] = Note(
                date = date,
                entryId = o.optString("entryId", ""),
                text = o.optString("text", ""),
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", o.optLong("createdAt", 0L))
            )
        }
        return out
    }

    /**
     * The migration path [R10.5] requires. Each version bump adds one branch that
     * moves the document forward a step; nothing older is ever dropped silently.
     * Unknown future versions are read as-is rather than discarded — a newer file
     * from a newer build must not destroy the user's writing.
     */
    private fun migrate(root: JSONObject): JSONObject {
        @Suppress("UNUSED_VARIABLE")
        val from = root.optInt("version", 1)
        // v1 is current. Add: if (from < 2) { ...transform...; root.put("version", 2) }
        return root
    }
}

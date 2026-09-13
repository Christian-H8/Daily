package com.christianh.daily.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Where corpus.json comes from.
 *
 * Preferred: a folder the user picked once, which Autosync keeps in step with the
 * desktop vault. That is what makes adding an entry a sync rather than a rebuild —
 * and a rebuild means a reinstall, which wipes the battery exemption (D-012).
 *
 * Fallback: the copy bundled in assets, so the app works on first run and survives
 * a revoked permission, a missing file, or a half-written sync.
 *
 * Deliberately no androidx.documentfile dependency — the same thing in ~20 lines
 * of DocumentsContract, and this module stays buildable without touching Gradle.
 */
object CorpusSource {

    private const val PREFS = "daily"
    private const val KEY_TREE = "corpus_tree_uri"
    const val FILE_NAME = "corpus.json"

    fun saveTree(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE, null)
            ?.let { Uri.parse(it) }

    /** The folder's display name, for the status line. Null if none chosen. */
    fun treeLabel(context: Context): String? =
        treeUri(context)?.let { Uri.decode(it.lastPathSegment ?: it.toString()) }

    /** corpus.json from the chosen folder, or null. Never throws — 8am is not the time. */
    fun syncedJson(context: Context): String? = runCatching {
        val tree = treeUri(context) ?: return null
        val doc = findChild(context, tree, FILE_NAME) ?: return null
        context.contentResolver.openInputStream(doc)
            ?.bufferedReader()
            ?.use { it.readText() }
    }.getOrNull()

    fun assetJson(context: Context): String =
        context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }

    private fun findChild(context: Context, tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree)
        )
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return null
    }
}

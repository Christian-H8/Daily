package com.christianh.daily.data

import android.content.Context
import com.christianh.daily.domain.model.Block
import com.christianh.daily.domain.model.Entry
import com.christianh.daily.domain.model.Section
import org.json.JSONArray
import org.json.JSONObject

/** What was loaded, and from where — the status line needs all three. */
data class LoadedCorpus(
    val entries: List<Entry>,
    val version: String,
    val fromSyncedFolder: Boolean
)

object CorpusLoader {

    /**
     * The synced folder wins; a bad or missing file there falls back to assets
     * rather than failing. A corpus that half-parses is treated as no corpus —
     * publish.py is the gate, and anything that gets past it reaching the device
     * broken means the sync was mid-write, not that the entry was bad.
     */
    fun load(context: Context): LoadedCorpus {
        val synced = CorpusSource.syncedJson(context)
        if (synced != null) {
            val parsed = runCatching {
                LoadedCorpus(parse(synced), versionOf(synced), true)
            }.getOrNull()
            if (parsed != null && parsed.entries.isNotEmpty()) return parsed
        }
        val bundled = CorpusSource.assetJson(context)
        return LoadedCorpus(parse(bundled), versionOf(bundled), false)
    }

    fun versionOf(json: String): String = JSONObject(json).optString("corpusVersion", "?")

    /** Pure — no Android types — so it can be unit-tested off-device. */
    fun parse(json: String): List<Entry> {
        val arr = JSONObject(json).getJSONArray("entries")
        return (0 until arr.length()).map { readEntry(arr.getJSONObject(it)) }
    }

    private fun readEntry(o: JSONObject) = Entry(
        id = o.getString("id"),
        type = o.getString("type"),
        title = o.getString("title"),
        tagline = o.optString("tagline", ""),
        tags = strings(o.optJSONArray("tags")),
        related = strings(o.optJSONArray("related")),
        words = o.optInt("words", 0),
        minutes = o.optInt("minutes", 1),
        sections = objects(o.optJSONArray("sections")).map(::readSection)
    )

    private fun readSection(o: JSONObject) = Section(
        heading = o.optString("heading", ""),
        thinkers = strings(o.optJSONObject("meta")?.optJSONArray("thinkers")),
        blocks = objects(o.optJSONArray("blocks")).mapNotNull(::readBlock)
    )

    // An unknown block kind is skipped, not fatal. The corpus ships ahead of the
    // client, so a new kind must degrade to "missing" rather than to a crash.
    private fun readBlock(o: JSONObject): Block? = when (o.optString("kind")) {
        "p" -> Block.Paragraph(o.optString("text"))
        "quote" -> Block.Quote(
            text = o.optString("text"),
            attribution = o.optString("attribution").ifEmpty { null }
        )
        else -> null
    }

    private fun objects(a: JSONArray?): List<JSONObject> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
}

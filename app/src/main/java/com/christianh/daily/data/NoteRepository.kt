package com.christianh.daily.data

import android.content.Context
import com.christianh.daily.Domain.model.Note
import java.time.LocalDate

/**
 * The boundary around note storage (SDS §3.6). Everything above this sees a map;
 * everything below it is [NoteStore]'s problem, which is what makes swapping the
 * backing store a one-file change.
 *
 * Loads once, keeps the map in memory, writes through on every change. At one note
 * per day the whole file is kilobytes — there is nothing here worth a cache policy.
 */
object NoteRepository {

    private var loaded = false
    private var notes: Map<LocalDate, Note> = emptyMap()

    fun all(context: Context): Map<LocalDate, Note> {
        if (!loaded) {
            notes = NoteStore.read(context)
            loaded = true
        }
        return notes
    }

    fun forDate(context: Context, date: LocalDate): Note? = all(context)[date]

    /**
     * Blank text deletes the note rather than storing an empty one — an empty note
     * is indistinguishable from no note to the reader, and keeping it would put a
     * meaningless row under a timeline day.
     */
    fun put(context: Context, date: LocalDate, entryId: String, text: String) {
        val current = all(context)
        val now = System.currentTimeMillis()
        val next = current.toMutableMap()

        if (text.isBlank()) {
            if (next.remove(date) == null) return
        } else {
            val existing = current[date]
            if (existing != null && existing.text == text) return
            next[date] = Note(
                date = date,
                entryId = entryId,
                text = text,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        }

        notes = next
        NoteStore.write(context, next)
    }
}

package com.christianh.daily.domain

import com.christianh.daily.domain.model.Note
import java.time.LocalDate

/**
 * Combining two sets of notes without losing either (SRS [R8.5], D-028).
 *
 * Used in both directions of the off-device copy: restoring merges the folder's copy
 * into the phone's, and the backup's first write to a folder asks [wouldLose] before
 * it overwrites a copy that might hold notes this phone has never seen.
 *
 * **The rule is per day, newest edit wins, nothing is ever deleted.** A note the
 * phone lacks is added; a note both sides have is replaced only if the incoming one
 * was edited later; a note only the phone has is kept. The cost of never deleting is
 * that a note deleted on the phone can come back from an older copy. That is the
 * right side to err on: a note that returns can be deleted again, and one that was
 * merged away cannot be written again.
 *
 * Pure, so every case runs on the JVM.
 */
object NoteMerge {

    data class Result(
        val notes: Map<LocalDate, Note>,
        /** Days the incoming set had and [local] did not. */
        val added: Int,
        /** Days both had, where the incoming note was edited later. */
        val updated: Int
    )

    fun merge(local: Map<LocalDate, Note>, incoming: Map<LocalDate, Note>): Result {
        val out = LinkedHashMap<LocalDate, Note>(local)
        var added = 0
        var updated = 0
        for ((date, note) in incoming) {
            // A blank note is "no note" everywhere else in the app (NoteRepository.put).
            if (note.text.isBlank()) continue
            val mine = local[date]
            when {
                mine == null -> { out[date] = note; added++ }
                note.updatedAt > mine.updatedAt -> { out[date] = note; updated++ }
            }
        }
        return Result(out, added, updated)
    }

    /**
     * How many notes in [incoming] would be destroyed by writing [local] over it:
     * exactly the ones [merge] would bring in. Zero means overwriting is safe.
     */
    fun wouldLose(local: Map<LocalDate, Note>, incoming: Map<LocalDate, Note>): Int =
        merge(local, incoming).let { it.added + it.updated }
}

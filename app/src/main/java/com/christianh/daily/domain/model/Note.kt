package com.christianh.daily.domain.model

import java.time.LocalDate

/**
 * The user's own writing. The only non-disposable data in the app.
 *
 * Carries **both** [date] and [entryId] on purpose (SRS [R10.1], [R5.8]):
 * the date is what the note attaches to, the entry id is what pins the timeline
 * row against drift. Because the permutation derives from corpus size, publishing
 * new entries changes what a past date computes to — a note is the only record of
 * what was actually there.
 *
 * One note per day. A day is the unit, not an entry: the same entry seen again in
 * 27 days is a different day and gets its own note, which is the whole point of
 * the rotation (D-015).
 */
data class Note(
    val date: LocalDate,
    val entryId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long
)

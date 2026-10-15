package com.christianh.daily.domain

import com.christianh.daily.domain.model.Entry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The day the index counts from. Fixed forever — changing it shifts every future day. */
val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)

object DailySelector {

    /**
     * The one fixed permutation. Freshness comes from the corpus growing, not from
     * reshuffling — see DECISIONS D-007 for why the reshuffle was dropped.
     *
     * Exposed so callers that need many days at once (the timeline) sort once
     * instead of once per day.
     */
    fun orderOf(corpus: List<Entry>): List<Entry> =
        corpus.sortedWith(compareBy({ StableHash.of(it.id) }, { it.id }))

    /** Position in [orderOf] for [date]. Pure: no clock, no I/O, no corpus. */
    fun indexFor(date: LocalDate, size: Int, epoch: LocalDate = EPOCH): Int =
        Math.floorMod(ChronoUnit.DAYS.between(epoch, date).toInt(), size)

    /** The entry for [date], or null if the corpus is empty. */
    fun entryFor(date: LocalDate, corpus: List<Entry>, epoch: LocalDate = EPOCH): Entry? {
        if (corpus.isEmpty()) return null
        return orderOf(corpus)[indexFor(date, corpus.size, epoch)]
    }
}

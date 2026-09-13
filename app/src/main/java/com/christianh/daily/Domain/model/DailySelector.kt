package com.christianh.daily.Domain

import com.christianh.daily.Domain.model.Entry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The day the index counts from. Fixed forever — changing it shifts every future day. */
val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)

object DailySelector {

    /** The entry for [date], or null if the corpus is empty. Pure: no clock, no I/O. */
    fun entryFor(date: LocalDate, corpus: List<Entry>, epoch: LocalDate = EPOCH): Entry? {
        if (corpus.isEmpty()) return null
        val dayIndex = ChronoUnit.DAYS.between(epoch, date).toInt()
        return corpus
            .sortedWith(compareBy({ StableHash.of(it.id) }, { it.id }))[
            Math.floorMod(dayIndex, corpus.size)
        ]
    }
}
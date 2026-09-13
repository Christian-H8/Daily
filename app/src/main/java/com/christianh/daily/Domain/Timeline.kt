package com.christianh.daily.Domain

import com.christianh.daily.Domain.model.Entry
import java.time.LocalDate

data class Day(val date: LocalDate, val entry: Entry)

object Timeline {

    /**
     * Every day from [newest] back to [oldest], newest first.
     *
     * This costs no stored state. Selection is a pure function of the date
     * (D-005/D-007), so running it backwards reconstructs the whole history —
     * an unplanned payoff of the statelessness chosen for picking tomorrow.
     *
     * Caveat inherited from D-007: the permutation derives from corpus size, so
     * publishing new entries changes what past dates compute to. A row for
     * 3 September may show a different entry than was actually seen that day.
     * Notes pin the days that matter; days scrolled past and forgotten drift.
     */
    fun days(newest: LocalDate, oldest: LocalDate, corpus: List<Entry>): List<Day> {
        if (corpus.isEmpty() || oldest.isAfter(newest)) return emptyList()
        val order = DailySelector.orderOf(corpus)
        val out = ArrayList<Day>()
        var d = newest
        while (!d.isBefore(oldest)) {
            out += Day(d, order[DailySelector.indexFor(d, order.size)])
            d = d.minusDays(1)
        }
        return out
    }

    /** Every tag in the corpus, for the filter row. */
    fun tagsOf(corpus: List<Entry>): List<String> =
        corpus.flatMap { it.tags }.distinct().sorted()
}

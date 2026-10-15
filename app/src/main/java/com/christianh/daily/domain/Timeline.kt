package com.christianh.daily.domain

import com.christianh.daily.domain.model.Entry
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

    /**
     * What the library actually shows.
     *
     * **One full pass, plus every older day you wrote on.**
     *
     * Running [days] back to EPOCH produces 255 rows over a 27-entry corpus, so
     * every title appears about nine times — the correct output of the rotation,
     * and it reads as a bug. A window of exactly *n* days is one complete pass:
     * every entry once, in the order the rotation actually delivers them, with no
     * repetition at all.
     *
     * Older days survive only if they carry a note. That is not a compromise — it
     * follows from D-007 and [R5.8]: the permutation derives from corpus size, so
     * publishing changes what a past date computes to. An unnoted day from six
     * weeks ago is both a duplicate *and* probably wrong. A noted day is pinned by
     * what you wrote against it, and is the only kind worth keeping.
     */
    fun view(today: LocalDate, corpus: List<Entry>, noted: Set<LocalDate>): List<Day> {
        if (corpus.isEmpty()) return emptyList()
        val order = DailySelector.orderOf(corpus)
        val start = today.minusDays((corpus.size - 1).toLong())

        val dates = LinkedHashSet<LocalDate>()
        var d = today
        while (!d.isBefore(start)) {
            dates += d
            d = d.minusDays(1)
        }
        noted.filter { it.isBefore(start) }.sortedDescending().forEach { dates += it }

        return dates.map { Day(it, order[DailySelector.indexFor(it, order.size)]) }
    }

    /** Every tag in the corpus, for the filter row. */
    fun tagsOf(corpus: List<Entry>): List<String> =
        corpus.flatMap { it.tags }.distinct().sorted()
}

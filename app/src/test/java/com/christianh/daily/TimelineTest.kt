package com.christianh.daily

import com.christianh.daily.domain.DailySelector
import com.christianh.daily.domain.EPOCH
import com.christianh.daily.domain.Timeline
import com.christianh.daily.domain.model.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimelineTest {

    private fun entry(id: String, vararg tags: String) = Entry(
        id = id,
        type = "principle",
        title = id,
        tagline = "",
        tags = tags.toList(),
        related = emptyList(),
        words = 0,
        minutes = 1,
        sections = emptyList()
    )

    private val corpus = listOf(
        entry("alpha", "ethics", "time"),
        entry("bravo", "time"),
        entry("charlie", "ethics"),
        entry("delta", "attention"),
        entry("echo")
    )

    /**
     * The timeline sorts the permutation once and indexes it, while the daily
     * screen calls entryFor. If those two ever disagree, the history shown would
     * contradict the entry actually delivered — pin them together.
     */
    @Test fun `timeline agrees with the daily selector on every day`() {
        val newest = LocalDate.of(2026, 9, 12)
        val oldest = EPOCH
        for (day in Timeline.days(newest, oldest, corpus)) {
            assertEquals(
                DailySelector.entryFor(day.date, corpus)!!.id,
                day.entry.id
            )
        }
    }

    @Test fun `newest first, and every day in the range is present`() {
        val newest = LocalDate.of(2026, 9, 12)
        val oldest = LocalDate.of(2026, 9, 1)
        val days = Timeline.days(newest, oldest, corpus)

        assertEquals(12, days.size)                       // inclusive of both ends
        assertEquals(newest, days.first().date)
        assertEquals(oldest, days.last().date)
        for (i in 1 until days.size) {
            assertTrue(days[i].date.isBefore(days[i - 1].date))
        }
    }

    @Test fun `a single day is one row`() {
        val d = LocalDate.of(2026, 9, 12)
        assertEquals(1, Timeline.days(d, d, corpus).size)
    }

    @Test fun `an inverted range is empty, not a crash`() {
        val days = Timeline.days(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 12), corpus)
        assertTrue(days.isEmpty())
    }

    @Test fun `empty corpus gives an empty timeline`() {
        val days = Timeline.days(LocalDate.of(2026, 9, 12), EPOCH, emptyList())
        assertTrue(days.isEmpty())
    }

    // --- view(): the range the library actually shows -------------------------

    /** The bug this fixes: 255 rows over 27 entries meant every title nine times. */
    @Test fun `one pass shows every entry exactly once, with no repeats`() {
        val today = LocalDate.of(2026, 9, 13)
        val rows = Timeline.view(today, corpus, emptySet())

        assertEquals(corpus.size, rows.size)
        assertEquals(corpus.size, rows.map { it.entry.id }.toSet().size)
    }

    @Test fun `the window is newest first and ends one pass back`() {
        val today = LocalDate.of(2026, 9, 13)
        val rows = Timeline.view(today, corpus, emptySet())

        assertEquals(today, rows.first().date)
        assertEquals(today.minusDays((corpus.size - 1).toLong()), rows.last().date)
        for (i in 1 until rows.size) {
            assertTrue(rows[i].date.isBefore(rows[i - 1].date))
        }
    }

    /** Older days survive only if something was written against them. */
    @Test fun `an older day is kept when it carries a note`() {
        val today = LocalDate.of(2026, 9, 13)
        val old = LocalDate.of(2026, 6, 1)
        val rows = Timeline.view(today, corpus, setOf(old))

        assertEquals(corpus.size + 1, rows.size)
        assertEquals(old, rows.last().date)
    }

    @Test fun `a noted day already inside the window is not duplicated`() {
        val today = LocalDate.of(2026, 9, 13)
        val inside = today.minusDays(2)
        val rows = Timeline.view(today, corpus, setOf(inside))

        assertEquals(corpus.size, rows.size)
        assertEquals(1, rows.count { it.date == inside })
    }

    @Test fun `older noted days are newest first too`() {
        val today = LocalDate.of(2026, 9, 13)
        val notes = setOf(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 5, 9)
        )
        val tail = Timeline.view(today, corpus, notes).takeLast(3).map { it.date }
        assertEquals(
            listOf(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 9)),
            tail
        )
    }

    /** The library and the daily screen must never disagree about a date. */
    @Test fun `view agrees with the daily selector on every row`() {
        val today = LocalDate.of(2026, 9, 13)
        val notes = setOf(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 4))
        for (day in Timeline.view(today, corpus, notes)) {
            assertEquals(DailySelector.entryFor(day.date, corpus)!!.id, day.entry.id)
        }
    }

    @Test fun `an empty corpus gives an empty view`() {
        assertTrue(
            Timeline.view(LocalDate.of(2026, 9, 13), emptyList(), setOf(LocalDate.of(2026, 6, 1)))
                .isEmpty()
        )
    }

    @Test fun `a single-entry corpus is a single row`() {
        val one = listOf(entry("only"))
        assertEquals(1, Timeline.view(LocalDate.of(2026, 9, 13), one, emptySet()).size)
    }

    @Test fun `tags are deduplicated and sorted`() {
        assertEquals(listOf("attention", "ethics", "time"), Timeline.tagsOf(corpus))
    }

    @Test fun `filtering by tag keeps only days whose entry carries it`() {
        val days = Timeline.days(LocalDate.of(2026, 9, 12), EPOCH, corpus)
        val filtered = days.filter { "ethics" in it.entry.tags }
        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.entry.id == "alpha" || it.entry.id == "charlie" })
    }
}

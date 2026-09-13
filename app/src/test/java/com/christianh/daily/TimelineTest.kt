package com.christianh.daily

import com.christianh.daily.Domain.DailySelector
import com.christianh.daily.Domain.EPOCH
import com.christianh.daily.Domain.Timeline
import com.christianh.daily.Domain.model.Entry
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

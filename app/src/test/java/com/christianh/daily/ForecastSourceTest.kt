package com.christianh.daily

import com.christianh.daily.domain.model.Condition
import com.christianh.daily.data.ForecastSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ForecastSourceTest {

    private val json = """
        {
          "hours": [
            { "time": "2026-09-14T08:00", "condition": "clear", "temp": 79 },
            { "time": "2026-09-14T15:00", "condition": "storm", "temp": 86 },
            { "time": "2026-09-14T20:00", "condition": "rain",  "temp": 81 },
            { "time": "not-a-time",       "condition": "rain",  "temp": 70 }
          ],
          "days": [
            { "date": "2026-09-14", "condition": "rain",  "high": 87, "low": 76 },
            { "date": "2026-09-18", "condition": "cloud", "high": 88, "low": 77 },
            { "date": "2026-09-19", "condition": "wat",   "high": null, "low": null }
          ]
        }
    """.trimIndent()

    private val f = ForecastSource.parse(json)

    @Test fun `parses both blocks and skips a malformed row`() {
        assertEquals(3, f.hours.size)      // the bad timestamp is dropped, not fatal
        assertEquals(3, f.days.size)
    }

    /** The reason hourly exists: the sky should move through the day. */
    @Test fun `the hour wins over the day`() {
        assertEquals(
            Condition.STORM,
            f.conditionAt(LocalDateTime.parse("2026-09-14T15:00"))
        )
        assertEquals(
            Condition.RAIN,
            f.conditionAt(LocalDateTime.parse("2026-09-14T20:00"))
        )
    }

    @Test fun `minutes are truncated to the hour`() {
        assertEquals(
            Condition.STORM,
            f.conditionAt(LocalDateTime.parse("2026-09-14T15:47:39"))
        )
    }

    /** Tier two: a stale file whose hourly window has run out still has daily rows. */
    @Test fun `falls back to the day when the hour is missing`() {
        assertEquals(
            Condition.CLOUD,
            f.conditionAt(LocalDateTime.parse("2026-09-18T13:00"))
        )
    }

    /** Tier three: nothing, so the ambient layer degrades to time-of-day. */
    @Test fun `returns null when neither the hour nor the day is covered`() {
        assertNull(f.conditionAt(LocalDateTime.parse("2026-10-01T09:00")))
    }

    @Test fun `an unrecognised condition reads as clear, matching the publisher`() {
        assertEquals(
            Condition.CLEAR,
            f.conditionAt(LocalDateTime.parse("2026-09-19T09:00"))
        )
    }

    @Test fun `null temperatures survive`() {
        val d = f.days[LocalDate.parse("2026-09-19")]!!
        assertNull(d.high)
        assertNull(d.low)
    }

    @Test fun `a daily-only file still works`() {
        val onlyDays = ForecastSource.parse(
            """{"days":[{"date":"2026-09-14","condition":"fog"}]}"""
        )
        assertTrue(onlyDays.hours.isEmpty())
        assertEquals(
            Condition.FOG,
            onlyDays.conditionAt(LocalDateTime.parse("2026-09-14T11:00"))
        )
    }

    @Test fun `an empty document yields nothing rather than throwing`() {
        val empty = ForecastSource.parse("{}")
        assertTrue(empty.hours.isEmpty() && empty.days.isEmpty())
        assertNull(empty.conditionAt(LocalDateTime.parse("2026-09-14T11:00")))
    }
}

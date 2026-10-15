package com.christianh.daily

import com.christianh.daily.domain.DailySelector
import com.christianh.daily.domain.Derived
import com.christianh.daily.domain.EPOCH
import com.christianh.daily.domain.model.Condition
import com.christianh.daily.domain.model.DayForecast
import com.christianh.daily.domain.model.Forecast
import com.christianh.daily.domain.model.HourForecast
import com.christianh.daily.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The values Home prints and Settings diagnoses. All of this used to live inline in
 * `MainActivity`, where the only way to check it was to install the APK and read the
 * screen — which is exactly how "no weather" stayed three problems wearing one face
 * for as long as it did.
 */
class DerivedTest {

    private val today = LocalDate.of(2026, 9, 14)

    // --- passDay -----------------------------------------------------------------

    @Test fun `an empty corpus is on no day of no pass`() {
        assertEquals(0, Derived.passDay(0, today))
    }

    @Test fun `passDay counts from one, not zero`() {
        assertEquals(1, Derived.passDay(27, EPOCH))
    }

    @Test fun `passDay agrees with the selector it is printing about`() {
        for (size in 1..40) {
            assertEquals(
                DailySelector.indexFor(today, size) + 1,
                Derived.passDay(size, today)
            )
        }
    }

    @Test fun `passDay stays inside the pass for every day of a full turn`() {
        val size = 27
        var d = EPOCH
        repeat(size * 2) {
            val day = Derived.passDay(size, d)
            assertTrue("day $day out of range on $d", day in 1..size)
            d = d.plusDays(1)
        }
    }

    @Test fun `a one-entry corpus is always day one`() {
        assertEquals(1, Derived.passDay(1, today))
        assertEquals(1, Derived.passDay(1, today.plusDays(9)))
    }

    // --- cycleFraction -----------------------------------------------------------

    @Test fun `a corpus too small to travel pins the hand at the start`() {
        assertEquals(0f, Derived.cycleFraction(0, today), 0f)
        assertEquals(0f, Derived.cycleFraction(1, today), 0f)
    }

    @Test fun `the hand runs from zero to one across a pass`() {
        val size = 10
        val first = EPOCH
        assertEquals(0f, Derived.cycleFraction(size, first), 1e-6f)
        assertEquals(1f, Derived.cycleFraction(size, first.plusDays((size - 1).toLong())), 1e-6f)
    }

    @Test fun `the hand never leaves the dial`() {
        val size = 27
        var d = EPOCH
        repeat(size * 3) {
            val f = Derived.cycleFraction(size, d)
            assertTrue("fraction $f out of range on $d", f in 0f..1f)
            d = d.plusDays(1)
        }
    }

    // --- lastNote ----------------------------------------------------------------

    private fun note(date: LocalDate, writtenAt: Long) =
        Note(date = date, entryId = "e", text = "t", createdAt = writtenAt, updatedAt = writtenAt)

    @Test fun `no notes means nothing to show`() {
        assertNull(Derived.lastNote(emptyMap()))
    }

    /**
     * By the day written *about*, not the day written *on*. Editing an old note does
     * not make it the most recent thing you wrote — the timeline is a record of days,
     * and Home echoing a two-month-old entry because it was touched this morning
     * would misreport what you have been reading.
     */
    @Test fun `the latest note is the latest date, not the latest edit`() {
        val older = LocalDate.of(2026, 7, 1)
        val newer = LocalDate.of(2026, 9, 1)
        val notes = mapOf(
            older to note(older, writtenAt = 9_000L),   // edited most recently
            newer to note(newer, writtenAt = 1_000L)
        )
        assertEquals(newer, Derived.lastNote(notes)!!.date)
    }

    @Test fun `a single note is the latest note`() {
        val d = LocalDate.of(2026, 8, 3)
        assertEquals(d, Derived.lastNote(mapOf(d to note(d, 1L)))!!.date)
    }

    // --- forecastLine ------------------------------------------------------------

    private val at: LocalDateTime = LocalDateTime.of(2026, 9, 14, 15, 0)

    private fun forecastCovering(hour: LocalDateTime) = Forecast(
        hours = mapOf(hour to HourForecast(hour, Condition.RAIN, 80)),
        days = mapOf(hour.toLocalDate() to DayForecast(hour.toLocalDate(), Condition.CLOUD, 89, 76))
    )

    @Test fun `no folder is named as no folder`() {
        val line = Derived.forecastLine(false, null, forecastCovering(at), at)
        assertTrue(line, line.contains("no folder chosen"))
    }

    /** A folder chosen but no file in it is a different problem, and says so. */
    @Test fun `a folder with no forecast file names the file and the folder`() {
        val line = Derived.forecastLine(true, "Notes", Forecast.EMPTY, at)
        assertTrue(line, line.contains("forecast.json not found"))
        assertTrue(line, line.contains("Notes"))
    }

    @Test fun `a file that covers nothing is called stale, not missing`() {
        val elsewhere = at.minusDays(30)
        val line = Derived.forecastLine(true, "Notes", forecastCovering(elsewhere), at)
        assertTrue(line, line.contains("stale"))
        assertTrue(line, !line.contains("not found"))
    }

    @Test fun `a working forecast reports counts and the current condition`() {
        val line = Derived.forecastLine(true, "Notes", forecastCovering(at), at)
        assertTrue(line, line.contains("1h / 1d"))
        assertTrue(line, line.contains("rain"))
    }

    /** The order of the branches is the order the chain actually breaks. */
    @Test fun `no folder wins over an empty forecast`() {
        val line = Derived.forecastLine(false, null, Forecast.EMPTY, at)
        assertTrue(line, line.contains("no folder chosen"))
    }
}

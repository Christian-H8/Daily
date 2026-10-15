package com.christianh.daily

import android.net.Uri
import com.christianh.daily.domain.Day
import com.christianh.daily.domain.model.Condition
import com.christianh.daily.domain.model.Entry
import com.christianh.daily.domain.model.Forecast
import com.christianh.daily.domain.model.HourForecast
import com.christianh.daily.domain.model.Note
import com.christianh.daily.domain.model.PartOfDay
import com.christianh.daily.data.DailyEnv
import com.christianh.daily.data.LoadedCorpus
import com.christianh.daily.data.RestoreResult
import com.christianh.daily.data.line
import com.christianh.daily.ui.DailyState
import com.christianh.daily.ui.SECTIONS
import com.christianh.daily.ui.Screen
import com.christianh.daily.ui.sectionIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The composition layer's state holder.
 *
 * **Why this file exists at all.** SDS §14.2 extracted [DailyState] out of
 * `MainActivity` specifically to create a test seam, and then nothing was put
 * through it. On 2026-09-14 that empty seam let a `DailyState.kt` that did not
 * compile — a platform declaration clash between `var forecastOverride`'s generated
 * setter and a `fun setForecastOverride` — sit on disk unnoticed, while
 * DECISIONS D-022 recorded 59 passing tests that could not have run. Any test in
 * this file would have turned that into a red bar the same afternoon, because it
 * puts the class on the test classpath. That is most of the value here; the
 * assertions are the rest.
 *
 * Everything below runs off-device. [FakeEnv] is the whole point of [DailyEnv]
 * existing as an interface.
 */
class DailyStateTest {

    private val day = LocalDate.of(2026, 9, 14)
    private val noon = LocalDateTime.of(2026, 9, 14, 12, 0)

    // --- fixtures ----------------------------------------------------------------

    private fun entry(id: String) = Entry(
        id = id,
        type = "principle",
        title = "Title of $id",
        tagline = "",
        tags = emptyList(),
        related = emptyList(),
        words = 800,
        minutes = 4,
        sections = emptyList()
    )

    private fun note(on: LocalDate, text: String) = Note(
        date = on,
        entryId = "whatever",
        text = text,
        createdAt = 1_000L,
        updatedAt = 2_000L
    )

    private fun forecastOf(at: LocalDateTime, condition: Condition) = Forecast(
        hours = mapOf(
            at.withMinute(0).withSecond(0).withNano(0) to HourForecast(at, condition, 24)
        ),
        days = emptyMap()
    )

    /**
     * Records what it was told rather than acting on it.
     *
     * [putNote] deliberately does **not** apply the write to [storedNotes]. That is what
     * lets the write-through test below distinguish "the screen re-read the store"
     * from "the screen patched its own copy and looked right by accident".
     */
    private class FakeEnv(
        var storedCorpus: LoadedCorpus = LoadedCorpus(emptyList(), "v-test", false),
        var storedForecast: Forecast = Forecast.EMPTY,
        var storedFolder: String? = null,
        var folderChosen: Boolean = false,
        val storedNotes: MutableMap<LocalDate, Note> = linkedMapOf(),
        var storedHour: Int = 8,
        var storedMinute: Int = 0,
        var storedOverride: Condition? = null,
        var storedFlick: Boolean = false
    ) : DailyEnv {

        val putNotes = mutableListOf<Triple<LocalDate, String, String>>()
        val timesSet = mutableListOf<Pair<Int, Int>>()
        val overridesSet = mutableListOf<Condition?>()
        val flicksSet = mutableListOf<Boolean>()
        var corpusLoads = 0
        var forecastLoads = 0

        override fun loadCorpus(): LoadedCorpus {
            corpusLoads++
            return storedCorpus
        }

        override fun reloadForecast(): Forecast {
            forecastLoads++
            return storedForecast
        }

        override fun folderLabel(): String? = storedFolder
        override fun hasFolder(): Boolean = folderChosen

        // Never called here. android.net.Uri is a stub off-device, so touching it
        // would throw rather than fail in a way that means anything.
        override fun saveTree(uri: Uri) = Unit

        override fun notes(): Map<LocalDate, Note> = LinkedHashMap(storedNotes)

        override fun putNote(date: LocalDate, entryId: String, text: String) {
            putNotes += Triple(date, entryId, text)
        }

        var storedBackupLine: String = "No folder chosen."
        var restoreResult: RestoreResult = RestoreResult.NoCopy
        /** What a restore "brings in": applied to [storedNotes] when it runs. */
        val notesInCopy: MutableMap<LocalDate, Note> = linkedMapOf()
        var restores = 0

        override fun backupLine(): String = storedBackupLine

        override fun restoreNotes(): RestoreResult {
            restores++
            storedNotes.putAll(notesInCopy)
            return restoreResult
        }

        override fun hour(): Int = storedHour
        override fun minute(): Int = storedMinute

        override fun setTime(hour: Int, minute: Int) {
            storedHour = hour
            storedMinute = minute
            timesSet += hour to minute
        }

        override fun forecastOverride(): Condition? = storedOverride

        override fun setForecastOverride(condition: Condition?) {
            storedOverride = condition
            overridesSet += condition
        }

        override fun flickNavigation(): Boolean = storedFlick

        override fun setFlickNavigation(enabled: Boolean) {
            storedFlick = enabled
            flicksSet += enabled
        }
    }

    // --- construction ------------------------------------------------------------

    @Test fun `reads everything it needs once, at construction`() {
        val env = FakeEnv(storedHour = 6, storedMinute = 45, storedFolder = "Synced")
        val state = DailyState(env)

        assertEquals(6, state.hour)
        assertEquals(45, state.minute)
        assertEquals("Synced", state.folder)
        assertEquals(Screen.Today, state.screen)
        // Once each. A fresh read per recomposition would hit SAF every frame.
        assertEquals(1, env.corpusLoads)
        assertEquals(1, env.forecastLoads)
    }

    // --- notes -------------------------------------------------------------------

    /**
     * SDS §3.6 and the comment on `writeNote`: written first, re-read second, so a
     * write that silently failed cannot leave the screen showing a note that no
     * longer exists anywhere. The fake's `putNote` drops the write on the floor —
     * if the screen patched its own map, this test would see the caller's text.
     */
    @Test fun `writeNote takes the map back from the store instead of patching it`() {
        val env = FakeEnv()
        env.storedNotes[day] = note(day, "what the store actually holds")
        val state = DailyState(env)

        state.writeNote(day, "entry-1", "what the caller passed")

        assertEquals("what the store actually holds", state.noteText(day))
        assertEquals(listOf(Triple(day, "entry-1", "what the caller passed")), env.putNotes)
    }

    @Test fun `a day with no note reads as empty text, not null`() {
        val state = DailyState(FakeEnv())
        assertEquals("", state.noteText(day))
    }

    @Test fun `the timeline gets text, and only for days that have some`() {
        val env = FakeEnv()
        env.storedNotes[day] = note(day, "written")
        env.storedNotes[day.minusDays(3)] = note(day.minusDays(3), "also written")
        val state = DailyState(env)

        assertEquals(setOf(day, day.minusDays(3)), state.noteTexts.keys)
        assertEquals("written", state.noteTexts[day])
    }

    // --- the forecast override ---------------------------------------------------

    /**
     * Named `overrideForecast`, not `setForecastOverride`. The second spelling has
     * the same JVM signature as the generated setter for the `forecastOverride`
     * property and does not compile — see D-023. This test is the thing that would
     * have caught that, simply by referring to the class.
     */
    @Test fun `an override is held on the screen and pushed to preferences`() {
        val env = FakeEnv()
        val state = DailyState(env)

        state.overrideForecast(Condition.STORM)

        assertEquals(Condition.STORM, state.forecastOverride)
        assertEquals(listOf<Condition?>(Condition.STORM), env.overridesSet)
    }

    @Test fun `null is Auto, and reaches preferences as null`() {
        val env = FakeEnv(storedOverride = Condition.RAIN)
        val state = DailyState(env)
        assertEquals(Condition.RAIN, state.forecastOverride)

        state.overrideForecast(null)

        assertNull(state.forecastOverride)
        assertEquals(listOf<Condition?>(null), env.overridesSet)
    }

    @Test fun `an override beats the forecast file, and Auto gives it back`() {
        val env = FakeEnv(storedForecast = forecastOf(noon, Condition.CLEAR))
        val state = DailyState(env)

        assertEquals(Condition.CLEAR, state.ambientAt(noon).condition)

        state.overrideForecast(Condition.STORM)
        assertEquals(Condition.STORM, state.ambientAt(noon).condition)

        state.overrideForecast(null)
        assertEquals(Condition.CLEAR, state.ambientAt(noon).condition)
    }

    /** Part of day comes from the clock, never from the file — no network, no permission. */
    @Test fun `part of day is read from the time it is handed, with no forecast at all`() {
        val state = DailyState(FakeEnv())
        assertEquals(PartOfDay.DAY, state.ambientAt(noon).partOfDay)
        assertNull(state.ambientAt(noon).condition)
    }

    // --- flick navigation --------------------------------------------------------

    @Test fun `flick navigation is off unless preferences say it is on`() {
        assertFalse(DailyState(FakeEnv()).flickNavigation)
        assertTrue(DailyState(FakeEnv(storedFlick = true)).flickNavigation)
    }

    /**
     * `useFlickNavigation`, not `setFlickNavigation` — the third method on this
     * class that cannot use the obvious name, because the property's `private set`
     * already owns that JVM signature (D-023).
     */
    @Test fun `switching the gesture on is held and written through`() {
        val env = FakeEnv()
        val state = DailyState(env)

        state.useFlickNavigation(true)
        assertTrue(state.flickNavigation)

        state.useFlickNavigation(false)
        assertFalse(state.flickNavigation)

        assertEquals(listOf(true, false), env.flicksSet)
    }

    // --- the notification time ---------------------------------------------------

    /**
     * The env call is not bookkeeping: `AndroidEnv.setTime` is what reschedules the
     * alarm, so a screen that updated its own fields and skipped it would show the
     * new time and keep firing at the old one.
     */
    @Test fun `setting the time updates the screen and tells the environment`() {
        val env = FakeEnv()
        val state = DailyState(env)

        state.setTime(21, 30)

        assertEquals(21, state.hour)
        assertEquals(30, state.minute)
        assertEquals(listOf(21 to 30), env.timesSet)
    }

    // --- navigation --------------------------------------------------------------

    @Test fun `back from a section goes to Today`() {
        val state = DailyState(FakeEnv())
        state.go(Screen.Settings)
        state.back()
        assertEquals(Screen.Today, state.screen)
    }

    @Test fun `back from the reader goes to the timeline it was opened from`() {
        val state = DailyState(FakeEnv())
        state.go(Screen.Reader(Day(day.minusDays(4), entry("e1"))))
        state.back()
        assertEquals(Screen.Timeline, state.screen)
    }

    @Test fun `the reader leaves the hand where the timeline left it`() {
        assertEquals(Screen.Timeline.sectionIndex(), Screen.Reader(Day(day, entry("e1"))).sectionIndex())
    }

    @Test fun `a swipe past either end of the arc stays on the arc`() {
        val state = DailyState(FakeEnv())

        state.settleTo(-3)
        assertEquals(SECTIONS.first(), state.screen)

        state.settleTo(SECTIONS.size + 5)
        assertEquals(SECTIONS.last(), state.screen)
    }

    @Test fun `every section on the arc has a distinct index`() {
        assertEquals(SECTIONS.size, SECTIONS.map { it.sectionIndex() }.distinct().size)
        assertEquals(SECTIONS.indices.toList(), SECTIONS.map { it.sectionIndex() })
    }

    // --- the diagnostic lines ----------------------------------------------------

    @Test fun `the source line names the folder only when the corpus came from it`() {
        val bundled = DailyState(
            FakeEnv(storedCorpus = LoadedCorpus(listOf(entry("a")), "v1", false), storedFolder = "Synced")
        )
        assertEquals("bundled copy", bundled.sourceLine)

        val synced = DailyState(
            FakeEnv(storedCorpus = LoadedCorpus(listOf(entry("a")), "v1", true), storedFolder = "Synced")
        )
        assertTrue(synced.sourceLine.contains("Synced"))
    }

    @Test fun `the forecast diagnostic distinguishes no folder from no file`() {
        val noFolder = DailyState(FakeEnv(folderChosen = false)).forecastLineAt(noon)
        assertTrue(noFolder, noFolder.contains("no folder"))

        val noFile = DailyState(FakeEnv(folderChosen = true, storedFolder = "Synced")).forecastLineAt(noon)
        assertTrue(noFile, noFile.contains("not found"))
    }

    // --- reloads -----------------------------------------------------------------

    @Test fun `reloading the corpus asks the environment again`() {
        val env = FakeEnv()
        val state = DailyState(env)
        env.storedCorpus = LoadedCorpus(listOf(entry("a"), entry("b")), "v2", true)

        state.reloadCorpus()

        assertEquals(2, state.entries.size)
        assertEquals(2, state.passLength)
        assertEquals(2, env.corpusLoads)
    }

    @Test fun `refreshing the forecast asks the environment again`() {
        val env = FakeEnv()
        val state = DailyState(env)
        env.storedForecast = forecastOf(noon, Condition.FOG)

        state.refreshForecast()

        assertEquals(Condition.FOG, state.ambientAt(noon).condition)
        assertEquals(2, env.forecastLoads)
    }

    // --- notes backup (D-028) -----------------------------------------------------

    @Test fun `no restore line until a restore has run`() {
        assertNull(DailyState(FakeEnv()).restoreLine)
    }

    @Test fun `restore reports what it did and re-reads the store`() {
        val env = FakeEnv()
        val state = DailyState(env)
        env.notesInCopy[day] = note(day, "from the copy")
        env.restoreResult = RestoreResult.Merged(added = 1, updated = 0, inCopy = 1)

        state.restoreNotes()

        assertEquals(1, env.restores)
        assertEquals(RestoreResult.Merged(1, 0, 1).line(), state.restoreLine)
        // Re-read from the environment, not patched in place — the same write-through
        // rule as writeNote, for the same reason.
        assertEquals("from the copy", state.noteText(day))
    }

    @Test fun `the backup line is read fresh, because the write is asynchronous`() {
        val env = FakeEnv()
        val state = DailyState(env)

        env.storedBackupLine = "first"
        assertEquals("first", state.backupLine)
        env.storedBackupLine = "second"
        assertEquals("second", state.backupLine)
    }
}

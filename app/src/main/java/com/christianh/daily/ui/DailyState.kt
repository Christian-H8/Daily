package com.christianh.daily.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.christianh.daily.domain.DailySelector
import com.christianh.daily.domain.Day
import com.christianh.daily.domain.Derived
import com.christianh.daily.domain.model.AmbientState
import com.christianh.daily.domain.model.Condition
import com.christianh.daily.domain.model.Entry
import com.christianh.daily.domain.model.Forecast
import com.christianh.daily.domain.model.Note
import com.christianh.daily.domain.model.partOfDay
import com.christianh.daily.data.DailyEnv
import com.christianh.daily.data.LoadedCorpus
import com.christianh.daily.data.RestoreResult
import com.christianh.daily.data.line
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Where you are. [Reader] is not a section on the dial — it is reached from the
 * timeline and leaves the hand where it was.
 */
sealed interface Screen {
    data object Home : Screen
    data object Today : Screen
    data object Timeline : Screen
    data object Settings : Screen
    data class Reader(val day: Day) : Screen
}

/**
 * The sections on the dial's arc, in order.
 *
 * Adding a section is an entry here and a branch in the `when`. The arc subdivides
 * itself: four sections sit at 0, ⅓, ⅔ and 1 across the visible half.
 */
val SECTIONS: List<Screen> = listOf(Screen.Home, Screen.Today, Screen.Timeline, Screen.Settings)

fun Screen.sectionIndex(): Int = when (this) {
    Screen.Home -> 0
    Screen.Today -> 1
    Screen.Timeline -> 2
    Screen.Settings -> 3
    is Screen.Reader -> 2
}

fun Int.asFraction(): Float =
    if (SECTIONS.size <= 1) 0f else this.toFloat() / (SECTIONS.size - 1).toFloat()

/**
 * Everything the four screens read and everything they change, in one object.
 *
 * **Why this exists.** `MainActivity` had grown to 424 lines and was doing four
 * jobs at once: Android lifecycle, navigation, data loading and the derived values
 * the screens print. That made it the one class in the app with no test at all —
 * not because testing it was hard in principle, but because every piece of state
 * lived in a `remember` inside `setContent`, where nothing outside a running phone
 * could reach it. SDS §14.2 records the finding; this is the fix.
 *
 * Nothing here is new behaviour. Every field was already a `var ... by remember`
 * and every method was already a lambda; they have only stopped being anonymous.
 *
 * **What it deliberately does not own.** The clock. Time-of-day is passed in to
 * [ambientAt] and [forecastLineAt] rather than read here, so the sky still follows
 * recomposition exactly as it did before and the class stays testable without
 * anyone having to fake a clock.
 */
@Stable
class DailyState(private val env: DailyEnv) {

    var screen: Screen by mutableStateOf(Screen.Today)
        private set

    var corpus: LoadedCorpus by mutableStateOf(env.loadCorpus())
        private set

    var folder: String? by mutableStateOf(env.folderLabel())
        private set

    var notes: Map<LocalDate, Note> by mutableStateOf(env.notes())
        private set

    var hour: Int by mutableStateOf(env.hour())
        private set

    var minute: Int by mutableStateOf(env.minute())
        private set

    /** null means "believe the forecast file" (SRS [R9.2d]). */
    var forecastOverride: Condition? by mutableStateOf(env.forecastOverride())
        private set

    var forecast: Forecast by mutableStateOf(env.reloadForecast())
        private set

    /** Whether a wrist flick navigates. Off until switched on in Settings. */
    var flickNavigation: Boolean by mutableStateOf(env.flickNavigation())
        private set

    /** What the last restore did, shown under its button. Null until one has run. */
    var restoreLine: String? by mutableStateOf(null)
        private set

    // --- navigation -----------------------------------------------------------

    fun go(to: Screen) {
        screen = to
    }

    /**
     * Back goes to Today from a section, and to the timeline from a reader — the
     * reader was opened from a list, so returning to that list is what "back" means
     * there, and only there.
     */
    fun back() {
        screen = if (screen is Screen.Reader) Screen.Timeline else Screen.Today
    }

    /** Where a horizontal swipe lands, clamped to the ends of the arc. */
    fun settleTo(index: Int) {
        screen = SECTIONS[index.coerceIn(0, SECTIONS.size - 1)]
    }

    // --- derived --------------------------------------------------------------

    val entries: List<Entry> get() = corpus.entries

    fun entryFor(date: LocalDate): Entry? = DailySelector.entryFor(date, corpus.entries)

    fun passDay(date: LocalDate): Int = Derived.passDay(corpus.entries.size, date)

    val passLength: Int get() = corpus.entries.size

    fun cycleFraction(date: LocalDate): Float = Derived.cycleFraction(corpus.entries.size, date)

    val lastNote: Note? get() = Derived.lastNote(notes)

    fun noteText(date: LocalDate): String = notes[date]?.text.orEmpty()

    /** The timeline wants text, not [Note] — the rest of a note is storage bookkeeping. */
    val noteTexts: Map<LocalDate, String> get() = notes.mapValues { it.value.text }

    fun ambientAt(at: LocalDateTime): AmbientState = AmbientState(
        partOfDay = partOfDay(at.toLocalTime()),
        condition = forecastOverride ?: forecast.conditionAt(at)
    )

    fun forecastLineAt(at: LocalDateTime): String =
        Derived.forecastLine(env.hasFolder(), folder, forecast, at)

    val corpusLine: String get() = "${corpus.entries.size} entries  ·  ${corpus.version}"

    val sourceLine: String
        get() = if (corpus.fromSyncedFolder) "from ${folder ?: "synced folder"}" else "bundled copy"

    /**
     * Read fresh on every call rather than held as state: the backup writes on a
     * background thread, so any copy held here would be stale by the time Settings
     * showed it. Settings composes anew each time it is opened, which is often enough.
     */
    val backupLine: String get() = env.backupLine()

    // --- changes --------------------------------------------------------------

    /**
     * Written first, re-read second. The store is the truth about what is on disk,
     * so the map comes back from it rather than being patched in memory — a write
     * that silently failed would otherwise leave the screen showing a note that no
     * longer exists anywhere.
     */
    fun writeNote(date: LocalDate, entryId: String, text: String) {
        env.putNote(date, entryId, text)
        notes = env.notes()
    }

    fun setTime(hour: Int, minute: Int) {
        this.hour = hour
        this.minute = minute
        env.setTime(hour, minute)
    }

    /**
     * Not `setFlickNavigation`, for exactly the reason [overrideForecast] is not
     * `setForecastOverride`: a `var` with a `private set` already owns that JVM
     * signature, and the collision is a compile error rather than a warning (D-023).
     */
    fun useFlickNavigation(enabled: Boolean) {
        flickNavigation = enabled
        env.setFlickNavigation(enabled)
    }

    /**
     * Not `setForecastOverride`. A `var` with a `private set` still emits a JVM
     * setter under the standard name, so that spelling collided with the property's
     * own accessor and the file would not compile — which is also how it came to
     * light that this version had never been built. `setTime` escapes the same trap
     * only because it is named for neither `hour` nor `minute`.
     *
     * null means "believe the forecast file" (SRS [R9.2d]), so this is the Auto
     * branch as well as the override.
     */
    fun overrideForecast(condition: Condition?) {
        forecastOverride = condition
        env.setForecastOverride(condition)
    }

    fun reloadCorpus() {
        corpus = env.loadCorpus()
    }

    /** Called when the hour turns, so the sky moves through the day. */
    fun refreshForecast() {
        forecast = env.reloadForecast()
    }

    /**
     * A new folder changes all three at once — the label, the corpus and the
     * forecast all come out of it — so they are re-read together rather than
     * waiting for whichever screen happens to recompose first.
     */
    fun chooseFolder(uri: Uri) {
        env.saveTree(uri)
        folder = env.folderLabel()
        corpus = env.loadCorpus()
        forecast = env.reloadForecast()
        // A folder that already holds a copy of the notes, after a reinstall say, is
        // merged in before anything can write over it (D-028). Silent when the folder
        // holds no copy: that is the normal first choice, not news.
        val restored = env.restoreNotes()
        restoreLine = if (restored == RestoreResult.NoCopy) null else restored.line()
        notes = env.notes()
    }

    /** Merge the folder's copy back in. Additive: a note is never deleted by this. */
    fun restoreNotes() {
        restoreLine = env.restoreNotes().line()
        notes = env.notes()
    }
}

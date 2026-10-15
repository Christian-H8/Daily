package com.christianh.daily.data

import android.content.Context
import android.net.Uri
import com.christianh.daily.domain.model.Condition
import com.christianh.daily.domain.model.Forecast
import com.christianh.daily.domain.model.Note
import java.time.LocalDate

/**
 * Everything the screens need from outside themselves, in one place.
 *
 * Before this existed, `MainActivity` reached straight into five singletons —
 * [CorpusLoader], [CorpusSource], [ForecastSource], [NoteRepository] and [Prefs] —
 * from inside a Compose lambda. That is the reason nothing above `domain` had a
 * test: there was no seam to put a fake into, and every call needed a live Context.
 *
 * The interface is the seam. [AndroidEnv] is the only implementation that ships;
 * a test writes its own, which is what turns [com.christianh.daily.ui.DailyState]
 * from something you verify by installing the APK into something you verify by
 * running `gradlew test`.
 *
 * Deliberately not a general-purpose repository: it is exactly the calls the
 * composition root makes, and nothing else. A method here that no screen calls is
 * a method to delete.
 */
interface DailyEnv {

    fun loadCorpus(): LoadedCorpus
    fun reloadForecast(): Forecast

    /** The display name of the synced folder, or null if none has been chosen. */
    fun folderLabel(): String?

    /**
     * Whether a folder has been chosen at all — a different question from whether
     * the forecast parsed, and the diagnostic in Settings depends on telling the
     * two apart.
     */
    fun hasFolder(): Boolean

    fun saveTree(uri: Uri)

    fun notes(): Map<LocalDate, Note>
    fun putNote(date: LocalDate, entryId: String, text: String)

    /** Where the notes' off-device copy stands, as one line for Settings (D-028). */
    fun backupLine(): String

    /**
     * Merges the chosen folder's copy of the notes into this phone's, then refreshes
     * the copy. Never deletes a note.
     */
    fun restoreNotes(): RestoreResult

    fun hour(): Int
    fun minute(): Int
    fun setTime(hour: Int, minute: Int)

    fun forecastOverride(): Condition?
    fun setForecastOverride(condition: Condition?)

    fun flickNavigation(): Boolean
    fun setFlickNavigation(enabled: Boolean)
}

/**
 * The shipping implementation: a thin adapter over the objects that were being
 * called directly before. It holds the Context so nothing above it has to.
 *
 * [onTimeChanged] is the alarm reschedule. It arrives as a lambda rather than a
 * direct call to `scheduleDailyQuote` so that `data` keeps pointing downwards —
 * the scheduler lives in the app package, and a data class importing upwards from
 * it is the first crack in a layering rule that has otherwise held.
 */
class AndroidEnv(
    private val context: Context,
    private val onTimeChanged: () -> Unit
) : DailyEnv {

    override fun loadCorpus(): LoadedCorpus = CorpusLoader.load(context)

    override fun reloadForecast(): Forecast = ForecastSource.load(context)

    override fun folderLabel(): String? = CorpusSource.treeLabel(context)

    override fun hasFolder(): Boolean = CorpusSource.treeUri(context) != null

    override fun saveTree(uri: Uri) {
        CorpusSource.saveTree(context, uri)
    }

    override fun notes(): Map<LocalDate, Note> = NoteRepository.all(context)

    override fun putNote(date: LocalDate, entryId: String, text: String) {
        NoteRepository.put(context, date, entryId, text)
    }

    override fun backupLine(): String = NoteBackup.status(context).line()

    override fun restoreNotes(): RestoreResult {
        if (!hasFolder()) return RestoreResult.NoFolder
        val result = NoteRepository.restore(context, NoteBackup.read(context))
        // Bring the copy up to the merged set, which also clears the first-write guard.
        // Not after an unreadable copy: it may be a sync still in flight, and
        // overwriting it would race whatever is about to land there.
        if (result != RestoreResult.Unreadable) NoteRepository.backUpNow(context)
        return result
    }

    override fun hour(): Int = Prefs.hour(context)

    override fun minute(): Int = Prefs.minute(context)

    override fun setTime(hour: Int, minute: Int) {
        Prefs.setTime(context, hour, minute)
        onTimeChanged()
    }

    override fun forecastOverride(): Condition? = Prefs.forecastOverride(context)

    override fun setForecastOverride(condition: Condition?) {
        Prefs.setForecastOverride(context, condition)
    }

    override fun flickNavigation(): Boolean = Prefs.flickNavigation(context)

    override fun setFlickNavigation(enabled: Boolean) {
        Prefs.setFlickNavigation(context, enabled)
    }
}

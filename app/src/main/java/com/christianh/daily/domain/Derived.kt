package com.christianh.daily.domain

import com.christianh.daily.domain.model.Forecast
import com.christianh.daily.domain.model.Note
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The small calculations the screens used to do inline.
 *
 * None of this was ever complicated; all of it was untestable, because it lived in
 * a lambda inside `setContent`. Every function here is pure — no clock, no Context,
 * no Compose — so the values Home prints and Settings diagnoses can be pinned by a
 * JVM test instead of by launching the app and squinting at it.
 *
 * SDS §14.2 explains why this file exists: the derived layer was the part
 * of the composition root that was carrying real logic, so it was the part worth
 * pulling out first.
 */
object Derived {

    /**
     * Where today sits in the current pass, counting from one — "DAY 12 OF 27".
     *
     * Zero for an empty corpus, because there is no pass to be on day one of, and
     * [DailySelector.indexFor] would divide by zero anyway.
     */
    fun passDay(corpusSize: Int, date: LocalDate): Int =
        if (corpusSize <= 0) 0 else DailySelector.indexFor(date, corpusSize) + 1

    /**
     * The same position as a 0..1 fraction, for the dial's slow hand.
     *
     * A one-entry corpus pins the hand at the start rather than dividing by zero:
     * with nothing to travel through, no position is more honest than any other.
     */
    fun cycleFraction(corpusSize: Int, date: LocalDate): Float =
        if (corpusSize <= 1) 0f
        else DailySelector.indexFor(date, corpusSize).toFloat() / (corpusSize - 1).toFloat()

    /** The most recent day written on, by the date the note hangs off — not by clock time. */
    fun lastNote(notes: Map<LocalDate, Note>): Note? = notes.values.maxByOrNull { it.date }

    /**
     * The Settings diagnostic line.
     *
     * "No weather" was three different problems wearing one face — no folder chosen,
     * no `forecast.json` in the folder, and a file too stale to cover the hour — and
     * the phone showed the same blank sky for all three. Each branch names which one
     * it is, in the order the chain actually breaks.
     */
    fun forecastLine(
        hasFolder: Boolean,
        folderLabel: String?,
        forecast: Forecast,
        at: LocalDateTime
    ): String {
        val condition = forecast.conditionAt(at)
        return when {
            !hasFolder ->
                "no folder chosen — the forecast cannot be read"
            forecast.hours.isEmpty() && forecast.days.isEmpty() ->
                "forecast.json not found in ${folderLabel ?: "the chosen folder"}"
            condition == null ->
                "${forecast.hours.size}h / ${forecast.days.size}d loaded, " +
                    "but nothing covers $at — the file is stale"
            else ->
                "${forecast.hours.size}h / ${forecast.days.size}d  ·  now: " +
                    condition.name.lowercase()
        }
    }
}

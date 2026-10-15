package com.christianh.daily.data

import android.content.Context
import com.christianh.daily.domain.model.Condition
import com.christianh.daily.domain.model.DayForecast
import com.christianh.daily.domain.model.Forecast
import com.christianh.daily.domain.model.HourForecast
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `forecast.json`, read from the same synced folder as `corpus.json` (SRS [R9.2b]).
 *
 * **This is a privacy requirement, not tidiness.** A weather API would mean a network
 * call and a location permission — and the network is the exact condition under which
 * journaling was excluded (D-003). Notes only exist because D-010 removed it. Notes
 * and live weather cannot coexist, and notes win.
 *
 * Written by the desktop by `corpus/tools/forecast.py`, since a forecast is
 * predetermined and does not need fetching at the moment it is shown.
 *
 * ```json
 * {
 *   "hours": [ { "time": "2026-09-14T15:00", "condition": "storm", "temp": 86 } ],
 *   "days":  [ { "date": "2026-09-14", "condition": "rain", "high": 87, "low": 76 } ]
 * }
 * ```
 */
object ForecastSource {

    const val FILE_NAME = "forecast.json"

    /** The condition at [at] — hour, then day, then null. Never throws. */
    fun conditionAt(context: Context, at: LocalDateTime): Condition? =
        load(context).conditionAt(at)

    /** Never throws — a missing file is the normal case, not an error. */
    fun load(context: Context): Forecast = runCatching {
        val json = CorpusSource.siblingJson(context, FILE_NAME) ?: return Forecast.EMPTY
        parse(json)
    }.getOrDefault(Forecast.EMPTY)

    /** Pure — no Android types. */
    fun parse(json: String): Forecast {
        val root = JSONObject(json)

        val hours = LinkedHashMap<LocalDateTime, HourForecast>()
        root.optJSONArray("hours")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = runCatching { LocalDateTime.parse(o.getString("time")) }.getOrNull()
                    ?: continue
                hours[t] = HourForecast(
                    time = t,
                    condition = conditionOf(o.optString("condition")),
                    temp = if (o.has("temp") && !o.isNull("temp")) o.optInt("temp") else null
                )
            }
        }

        val days = LinkedHashMap<LocalDate, DayForecast>()
        root.optJSONArray("days")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull()
                    ?: continue
                days[d] = DayForecast(
                    date = d,
                    condition = conditionOf(o.optString("condition")),
                    high = if (o.has("high") && !o.isNull("high")) o.optInt("high") else null,
                    low = if (o.has("low") && !o.isNull("low")) o.optInt("low") else null
                )
            }
        }

        return Forecast(hours = hours, days = days)
    }

    /** An unrecognised condition reads as CLEAR, matching the publisher's own fallback. */
    private fun conditionOf(name: String): Condition =
        Condition.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Condition.CLEAR
}

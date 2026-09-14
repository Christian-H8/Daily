package com.christianh.daily.data

import android.content.Context
import com.christianh.daily.Domain.model.Condition
import com.christianh.daily.Domain.model.DayForecast
import org.json.JSONObject
import java.time.LocalDate

/**
 * `forecast.json`, read from the same synced folder as `corpus.json` (SRS [R9.2b]).
 *
 * **This is a privacy requirement, not tidiness.** A weather API would mean a network
 * call and a location permission — and the network is the exact condition under which
 * journaling was excluded (D-003). Notes only exist because D-010 removed it. Notes
 * and live weather cannot coexist, and notes win.
 *
 * Written by the desktop, several days ahead, since a forecast is predetermined.
 *
 * ```json
 * { "days": [ { "date": "2026-09-14", "condition": "rain", "high": 31, "low": 26 } ] }
 * ```
 */
object ForecastSource {

    const val FILE_NAME = "forecast.json"

    /** Today's forecast, or null. Never throws — a missing file is the normal case. */
    fun today(context: Context, date: LocalDate): DayForecast? = runCatching {
        val json = CorpusSource.siblingJson(context, FILE_NAME) ?: return null
        parse(json)[date]
    }.getOrNull()

    /** Pure — no Android types. */
    fun parse(json: String): Map<LocalDate, DayForecast> {
        val arr = JSONObject(json).optJSONArray("days") ?: return emptyMap()
        val out = LinkedHashMap<LocalDate, DayForecast>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: continue
            val condition = Condition.entries
                .firstOrNull { it.name.equals(o.optString("condition"), ignoreCase = true) }
                ?: Condition.CLEAR
            out[date] = DayForecast(
                date = date,
                condition = condition,
                high = if (o.has("high")) o.optInt("high") else null,
                low = if (o.has("low")) o.optInt("low") else null
            )
        }
        return out
    }
}

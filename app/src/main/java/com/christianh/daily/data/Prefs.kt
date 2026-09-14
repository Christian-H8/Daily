package com.christianh.daily.data

import android.content.Context
import com.christianh.daily.Domain.model.Condition

/**
 * Settings. Shares the "daily" preferences file with [CorpusSource], which owns the
 * corpus folder URI under its own key.
 *
 * No user content lives here — notes are in [NoteStore].
 */
object Prefs {

    private const val PREFS = "daily"
    private const val KEY_HOUR = "notify_hour"
    private const val KEY_MINUTE = "notify_minute"
    private const val KEY_FORECAST = "forecast_override"

    const val DEFAULT_HOUR = 8
    const val DEFAULT_MINUTE = 0

    private fun p(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hour(context: Context): Int = p(context).getInt(KEY_HOUR, DEFAULT_HOUR)
    fun minute(context: Context): Int = p(context).getInt(KEY_MINUTE, DEFAULT_MINUTE)

    fun setTime(context: Context, hour: Int, minute: Int) {
        p(context).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    /** null means "use the forecast file" (SRS [R9.2d] — forecasts are wrong often enough). */
    fun forecastOverride(context: Context): Condition? =
        p(context).getString(KEY_FORECAST, null)?.let { name ->
            Condition.entries.firstOrNull { it.name == name }
        }

    fun setForecastOverride(context: Context, condition: Condition?) {
        p(context).edit().apply {
            if (condition == null) remove(KEY_FORECAST) else putString(KEY_FORECAST, condition.name)
        }.apply()
    }
}

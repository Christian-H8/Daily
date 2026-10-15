package com.christianh.daily.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A forecast, not an observation (D-009). A forecast for 08:00 written the previous
 * evening is still right at 08:00; an observation from the previous evening is
 * simply wrong, and a wrong ambient cue is worse than none.
 */
enum class Condition { CLEAR, CLOUD, RAIN, STORM, FOG, SNOW }

data class DayForecast(
    val date: LocalDate,
    val condition: Condition,
    val high: Int?,
    val low: Int?
)

data class HourForecast(
    val time: LocalDateTime,
    val condition: Condition,
    val temp: Int?
)

/**
 * What `forecast.json` carries. Hourly for the next 48 hours so the sky changes
 * through the day; daily for ten, as the coarse fallback once the hourly window
 * has run out on a stale file.
 */
data class Forecast(
    val hours: Map<LocalDateTime, HourForecast>,
    val days: Map<LocalDate, DayForecast>
) {
    /**
     * Three tiers, in order: the hour, then the day, then nothing.
     *
     * A forecast file a few days old still has a useful daily row long after its
     * hourly window has passed, and once even that runs out the ambient layer
     * degrades to time-of-day rather than showing yesterday's weather.
     */
    fun conditionAt(at: LocalDateTime): Condition? =
        hours[at.withMinute(0).withSecond(0).withNano(0)]?.condition
            ?: days[at.toLocalDate()]?.condition

    companion object {
        val EMPTY = Forecast(emptyMap(), emptyMap())
    }
}

enum class PartOfDay { DAWN, DAY, DUSK, NIGHT }

fun partOfDay(time: LocalTime): PartOfDay = when (time.hour) {
    in 5..7 -> PartOfDay.DAWN
    in 8..17 -> PartOfDay.DAY
    in 18..20 -> PartOfDay.DUSK
    else -> PartOfDay.NIGHT
}

/**
 * What the ambient layer is handed. Part of day comes from the device clock — no
 * network, no permission. Only [condition] rides the sync, and it is nullable so a
 * missing forecast degrades to time-of-day rather than failing.
 */
data class AmbientState(
    val partOfDay: PartOfDay,
    val condition: Condition?
)

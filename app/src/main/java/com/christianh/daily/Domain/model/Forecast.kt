package com.christianh.daily.Domain.model

import java.time.LocalDate
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

enum class PartOfDay { DAWN, DAY, DUSK, NIGHT }

fun partOfDay(time: LocalTime): PartOfDay = when (time.hour) {
    in 5..7 -> PartOfDay.DAWN
    in 8..17 -> PartOfDay.DAY
    in 18..20 -> PartOfDay.DUSK
    else -> PartOfDay.NIGHT
}

/**
 * What the ambient layer is handed. Season and part of day come from the device
 * clock — no network, no permission. Only [condition] rides the sync, and it is
 * nullable so a missing forecast degrades to time-of-day rather than failing.
 */
data class AmbientState(
    val partOfDay: PartOfDay,
    val condition: Condition?
)

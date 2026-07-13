package com.heartline.ai.ai

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

data class RoutineState(
    val job: String,
    val timeOfDay: String,
    val currentActivity: String,
    val nextActivity: String,
    val isWeekend: Boolean
) {
    fun promptSummary(): String = buildString {
        append("job: $job; $timeOfDay: $currentActivity")
        if (nextActivity.isNotBlank()) append("; later: $nextActivity")
        if (isWeekend) append("; weekend pace")
    }
}

class RoutineEngine(
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() }
) {
    fun timeOfDay(now: LocalTime = nowProvider().toLocalTime()): String = when (now.hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }

    fun snapshot(routineJson: String, now: LocalDateTime = nowProvider()): RoutineState {
        val period = timeOfDay(now.toLocalTime())
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        return runCatching {
            val routine = JSONObject(routineJson)
            val current = if (weekend) {
                routine.optString("weekend").ifBlank { routine.optString(period) }
            } else {
                routine.optString(period)
            }
            val nextPeriod = when (period) {
                "morning" -> "afternoon"
                "afternoon" -> "evening"
                "evening" -> "night"
                else -> "morning"
            }
            RoutineState(
                job = routine.optString("job", "creative work"),
                timeOfDay = period,
                currentActivity = current.ifBlank { "taking a quiet pause between things" },
                nextActivity = routine.optString(nextPeriod),
                isWeekend = weekend
            )
        }.getOrElse {
            RoutineState(
                job = "creative work",
                timeOfDay = period,
                currentActivity = "taking a quiet pause between things",
                nextActivity = "",
                isWeekend = weekend
            )
        }
    }
}

package com.heartline.ai.ai

import java.time.LocalTime

class RoutineEngine {
    fun timeOfDay(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..22 -> "evening"
        else -> "night"
    }
}

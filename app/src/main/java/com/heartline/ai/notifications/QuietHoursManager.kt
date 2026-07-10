package com.heartline.ai.notifications

import com.heartline.ai.data.local.entities.UserProfileEntity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class QuietHoursManager {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isQuietNow(user: UserProfileEntity?, now: LocalTime = LocalTime.now()): Boolean {
        if (user == null) return false
        val start = runCatching { LocalTime.parse(user.quietHoursStart, formatter) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(user.quietHoursEnd, formatter) }.getOrNull() ?: return false
        return if (start <= end) {
            now >= start && now <= end
        } else {
            now >= start || now <= end
        }
    }
}

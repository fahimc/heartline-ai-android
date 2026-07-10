package com.heartline.ai.util

fun String.jsonListText(): List<String> =
    removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .map { it.trim().trim('"') }
        .filter { it.isNotBlank() }

fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

fun Long.chatTime(): String {
    val date = java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return "%02d:%02d".format(date.hour, date.minute)
}

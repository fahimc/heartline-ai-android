package com.heartline.ai.navigation

object Routes {
    const val Onboarding = "onboarding"
    const val Discover = "discover"
    const val Chats = "chats"
    const val Settings = "settings"
    const val ChatThread = "chat/{threadId}"

    fun chat(threadId: String): String = "chat/$threadId"
}

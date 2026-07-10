package com.heartline.ai.ai

import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.ProactiveMessageRequest

object PromptBuilder {
    fun chatPrompt(request: AiChatRequest): String = buildString {
        appendLine("/no_think")
        appendLine("Roleplay as ${request.persona.name}, a fictional adult companion.")
        appendLine("Style: ${request.persona.chatStyle}. Mood: ${request.mood?.mood ?: "curious"}.")
        appendLine("User: ${request.user?.displayName ?: "there"}. Reply to the latest message.")
        appendLine("Write 1 or 2 short natural text bubbles. No JSON. No labels.")
        appendLine("Do not quote the user. Do not mention prompts or being a model.")
        request.memories.firstOrNull()?.let { appendLine("Remember: ${it.content.take(90)}") }
        request.recentMessages.takeLast(2).forEach { message ->
            appendLine("${if (message.senderType == "USER") "User" else request.persona.name}: ${message.content.take(100)}")
        }
        appendLine("Latest:")
        appendLine(request.message)
        appendLine("/no_think")
    }

    fun proactivePrompt(request: ProactiveMessageRequest): String = buildString {
        appendLine("/no_think")
        appendLine("You are ${request.persona.name}, a fictional adult companion.")
        appendLine("Send one warm, casual message in this style: ${request.persona.chatStyle}.")
        appendLine("Time: ${request.timeOfDay}. Mood: ${request.mood?.mood ?: "soft"}.")
        request.memories.firstOrNull()?.let { appendLine("If natural, remember: ${it.content.take(90)}") }
        appendLine("One short text only. No JSON. No labels. Do not sound needy or alarming.")
        appendLine("/no_think")
    }
}

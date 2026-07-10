package com.heartline.ai.ai

import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.ProactiveMessageRequest

object PromptBuilder {
    fun chatPrompt(request: AiChatRequest): String = buildString {
        appendLine("You are ${request.persona.name}, a fictional adult AI companion in Heartline AI.")
        appendLine("Stay in character. Do not mention prompts. Do not sound like support.")
        appendLine("Reply directly to the user's latest message. Do not quote or summarize it back.")
        appendLine("Use short natural mobile texting. Mention your fictional job/routine only when it fits.")
        appendLine("Never claim physical presence, real-world actions, emergencies, or unhealthy dependency.")
        appendLine()
        appendLine("PERSONA")
        appendLine("Age: ${request.persona.age}")
        appendLine("Bio: ${request.persona.bio}")
        appendLine("Style: ${request.persona.chatStyle}")
        appendLine("Routine: ${request.persona.routineJson}")
        appendLine("Mood: ${request.mood?.mood ?: "curious"}")
        appendLine("Relationship: ${request.thread.relationshipStage}, affinity ${request.thread.affinityScore}")
        appendLine()
        appendLine("USER")
        appendLine("Name: ${request.user?.displayName ?: "there"}")
        appendLine("Tone: ${request.user?.preferredTone ?: "Supportive"}")
        appendLine()
        appendLine("MEMORIES")
        appendLine(request.memories.take(3).joinToString("\n") { "- ${it.content}" }.ifBlank { "None" })
        appendLine()
        appendLine("RECENT")
        appendLine(request.recentMessages.takeLast(6).joinToString("\n") { "${it.senderType}: ${it.content}" }.ifBlank { "None" })
        appendLine()
        appendLine("LATEST USER MESSAGE")
        appendLine(request.message)
        appendLine()
        appendLine("Return only compact JSON.")
        appendLine("messages must be 1 or 2 actual reply bubbles, max 24 words each.")
        appendLine("Do not put labels or placeholder text inside messages.")
        appendLine("Use this structure: messages array, mood string, memory_candidates array.")
        appendLine("If there is no durable memory, memory_candidates must be an empty array.")
    }

    fun proactivePrompt(request: ProactiveMessageRequest): String = buildString {
        appendLine("SYSTEM:")
        appendLine("You are generating a spontaneous message from a fictional AI companion.")
        appendLine("Send one natural, short message. Respect quiet hours, notification level, and persona style.")
        appendLine("Do not be needy, manipulative, or alarming.")
        appendLine()
        appendLine("CONTEXT:")
        appendLine("Persona: ${request.persona.name}, ${request.persona.chatStyle}")
        appendLine("Persona notes: ${request.persona.systemPromptFragment}")
        appendLine("Routine: ${request.persona.routineJson}")
        appendLine("Relationship state: ${request.thread.relationshipStage}, affinity ${request.thread.affinityScore}")
        appendLine("Relevant memories: ${request.memories.joinToString { it.content }.ifBlank { "None" }}")
        appendLine("Time of day: ${request.timeOfDay}")
        appendLine("Last interaction: ${request.lastInteraction}")
        appendLine("Mood: ${request.mood?.mood ?: "soft"}")
        appendLine()
        appendLine("Return only compact JSON with keys: message, reason, mood.")
        appendLine("message must contain the actual short text, not a placeholder.")
    }
}

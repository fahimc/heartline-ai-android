package com.heartline.ai.ai

import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.ConversationSummary
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.domain.model.ProactiveMessageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockAiModelProvider : AiModelProvider {
    override fun generateReply(request: AiChatRequest): Flow<String> = flow {
        delay(450)
        val userName = request.user?.displayName?.takeIf { it.isNotBlank() } ?: "you"
        val last = request.message.lowercase()
        val persona = request.persona.name
        val stage = request.thread.relationshipStage
        val messages = when {
            "tired" in last || "stress" in last -> listOf(
                "Come here a second, $userName.",
                "That sounds heavy. Want to tell me the part that is sitting on you most?"
            )
            "good morning" in last || "morning" in last -> listOf(
                "Morning, $userName.",
                "I like hearing from you early. What kind of day are we trying to survive today?"
            )
            "miss" in last -> listOf(
                "That is unfairly sweet.",
                "I missed your little messages too. Tell me one thing from your day."
            )
            "joke" in last || "funny" in last -> listOf(
                "Okay, pressure is on.",
                "I can be charming or ridiculous. Pick your difficulty level."
            )
            else -> listOf(
                styleOpen(persona),
                "I am listening. What happened next?"
            )
        }
        val mood = when {
            "tired" in last || "stress" in last -> "concerned"
            "miss" in last -> "soft"
            else -> listOf("happy", "playful", "curious", "soft").random()
        }
        val memory = if (looksMemorable(request.message)) {
            """,
              "memory_candidates": [
                {"type":"preference","content":"${escape(userName)} mentioned: ${escape(request.message.take(90))}","importance":6}
              ]
            """
        } else {
            """,
              "memory_candidates": []
            """
        }
        emit(
            """
            {
              "messages": [${messages.joinToString(",") { "\"${escape(it)}\"" }}],
              "mood": "$mood"$memory
            }
            """.trimIndent()
        )
    }

    override suspend fun generateProactiveMessage(request: ProactiveMessageRequest): String {
        val name = request.user?.displayName?.takeIf { it.isNotBlank() } ?: "you"
        val message = when (request.timeOfDay) {
            "morning" -> "Morning, $name. How did you sleep?"
            "evening" -> "You have been quiet today. Hope your evening is being gentle with you."
            else -> "Random thought: I wondered how your day was going."
        }
        return """{"message":"${escape(message)}","reason":"${request.timeOfDay}_checkin","mood":"soft"}"""
    }

    override suspend fun extractMemories(conversation: List<MessageEntity>): List<MemoryCandidate> =
        conversation
            .filter { it.senderType == "USER" && looksMemorable(it.content) }
            .takeLast(3)
            .map {
                MemoryCandidate(
                    type = "fact",
                    content = "User shared: ${it.content.take(120)}",
                    importance = 5
                )
            }

    override suspend fun summarizeConversation(conversation: List<MessageEntity>): ConversationSummary {
        val first = conversation.firstOrNull()?.id.orEmpty()
        val last = conversation.lastOrNull()?.id.orEmpty()
        val summary = conversation.takeLast(8).joinToString(" ") { "${it.senderType}: ${it.content}" }.take(500)
        return ConversationSummary(summary, first, last)
    }

    private fun styleOpen(persona: String): String = when (persona) {
        "Lara" -> "You say that like I am not immediately invested."
        "Sofia" -> "I am here with you. Tell me slowly."
        "Chloe" -> "Okay, I am paying attention. Impress me a little."
        "Nia" -> "That just unlocked a side quest in my brain."
        "Elise" -> "There is something soft in the way you said that."
        else -> "I like when you tell me things like that."
    }

    private fun looksMemorable(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("i love", "i like", "i hate", "remember", "my birthday", "my job", "my project", "i prefer")
            .any { it in lower }
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}

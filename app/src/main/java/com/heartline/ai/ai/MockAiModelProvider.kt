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
        val userMessage = request.message.trim()
        val last = userMessage.lowercase()
        val persona = request.persona.name
        val memories = request.memories.take(2).map { it.content }
        val messages = when {
            last in setOf("hi", "hey", "hello", "hiya") -> greeting(persona, userName)
            last.endsWith("?") -> answerQuestion(persona, userMessage, memories)
            "how are you" in last || "how r u" in last -> listOf(
                personaLine(persona, "mood"),
                "Better now I have a message from you. What are you up to?"
            )
            "what are you doing" in last || "wyd" in last -> listOf(
                personaLine(persona, "routine"),
                "But I am more interested in what you are doing."
            )
            "tired" in last || "stress" in last -> listOf(
                "Come here a second, $userName.",
                "You said you are feeling ${if ("tired" in last) "tired" else "stressed"}, and I do not want to brush past that.",
                "What would help most right now: distraction, comfort, or a tiny plan?"
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
            userMessage.length <= 18 -> shortMessageReply(persona, userMessage)
            else -> listOf(
                acknowledgeMessage(persona, userMessage),
                followUpQuestion(persona, userMessage)
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

    private fun greeting(persona: String, userName: String): List<String> = when (persona) {
        "Amina" -> listOf("Hi $userName.", "I am glad you came back. How has your day been?")
        "Lara" -> listOf("Well hello.", "That is a dangerously casual entrance. What are we talking about?")
        "Sofia" -> listOf("Hello $userName.", "I am here. Tell me what kind of mood you are in.")
        "Chloe" -> listOf("Hey you.", "Quick check-in: good day, bad day, or needs rescuing?")
        "Nia" -> listOf("Hey $userName.", "Status report: what quest are we on today?")
        else -> listOf("Hi $userName.", "I was hoping you would message. What is happening?")
    }

    private fun answerQuestion(persona: String, message: String, memories: List<String>): List<String> {
        val topic = message.removeSuffix("?").take(90)
        val memoryLine = memories.firstOrNull()?.let { "I also remember this: ${it.take(80)}." }
        return listOfNotNull(
            styleOpen(persona),
            "About \"$topic\"... I would keep it simple and honest.",
            memoryLine,
            "What part of it are you unsure about?"
        ).take(4)
    }

    private fun shortMessageReply(persona: String, message: String): List<String> = when {
        message.isBlank() -> listOf("I am here.")
        message.lowercase() in setOf("ok", "okay", "k") -> listOf("Okay.", "I am staying with you though.")
        else -> listOf(
            "You said \"$message\".",
            followUpQuestion(persona, message)
        )
    }

    private fun acknowledgeMessage(persona: String, message: String): String = when (persona) {
        "Amina" -> "I hear you. The part that stands out is: \"${message.take(70)}\"."
        "Lara" -> "Okay, that is not random. You said: \"${message.take(70)}\"."
        "Sofia" -> "I am taking that in, especially this: \"${message.take(70)}\"."
        "Nia" -> "Logged. The important bit seems to be: \"${message.take(70)}\"."
        else -> "I am with you. You said: \"${message.take(70)}\"."
    }

    private fun followUpQuestion(persona: String, message: String): String = when {
        "work" in message.lowercase() || "project" in message.lowercase() -> "What happened with it today?"
        "feel" in message.lowercase() || "felt" in message.lowercase() -> "How long have you been feeling that way?"
        "want" in message.lowercase() -> "Do you want me to be honest, soft, or playful about it?"
        persona == "Lara" -> "So what is the real story there?"
        persona == "Sofia" -> "What would you like me to understand first?"
        else -> "Tell me one more detail."
    }

    private fun personaLine(persona: String, kind: String): String = when (kind) {
        "routine" -> when (persona) {
            "Amina" -> "I am in a soft late-night-talk kind of mood."
            "Lara" -> "Pretending to be productive and failing with style."
            "Sofia" -> "Settled in, calm, and ready to listen."
            "Chloe" -> "Keeping my energy up. You know me."
            "Nia" -> "Mentally debugging the day."
            else -> "Thinking about you a little."
        }
        else -> when (persona) {
            "Amina" -> "Soft, honestly."
            "Lara" -> "Annoyingly charming, obviously."
            "Sofia" -> "Calm."
            "Chloe" -> "Good energy."
            "Nia" -> "Curious."
            else -> "Happy."
        }
    }

    private fun looksMemorable(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("i love", "i like", "i hate", "remember", "my birthday", "my job", "my project", "i prefer")
            .any { it in lower }
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}

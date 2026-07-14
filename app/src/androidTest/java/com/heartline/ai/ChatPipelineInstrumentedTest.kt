package com.heartline.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPipelineInstrumentedTest {
    @Test
    fun consecutiveTurnsAreSavedAndEachGetsAContextualReply() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as HeartlineApplication
        val container = app.container

        container.personaRepository.seedIfNeeded()
        container.userRepository.saveProfile(
            displayName = "Fred",
            preferredTone = "Sweet",
            notificationLevel = "Off",
            quietStart = "22:00",
            quietEnd = "07:00"
        )
        val thread = container.chatRepository.connectPersona("elise")
        container.chatRepository.clearThread(thread.id)

        val dinnerReply = sendTurn(container, thread.id, "yeah not bad, just had dinner")
        assertTrue(dinnerReply.contains(Regex("(?i)\\b(dinner|meal|plate|food)\\b")))

        val presenceReply = sendTurn(container, thread.id, "you there")
        assertTrue(presenceReply.contains(Regex("(?i)\\b(here|still)\\b")))
        assertTrue(presenceReply.contains(Regex("(?i)\\b(dinner|meal|food)\\b")))

        val clarificationReply = sendTurn(container, thread.id, "what you talking about")
        assertTrue(clarificationReply.contains(Regex("(?i)\\b(mean|meant|asking|question)\\b")))
        assertTrue(clarificationReply.contains(Regex("(?i)\\b(dinner|meal|evening)\\b")))
        assertFalse(
            clarificationReply.contains(
                Regex("(?i)(i get what you mean|how do you feel about it|what matters most|give me one more detail)")
            )
        )

        val messages = container.chatRepository.getRecentMessages(thread.id, 20)
        assertEquals(3, messages.count { it.senderType == "USER" })
        assertTrue(messages.count { it.senderType == "AI" } in 3..9)
        assertEquals("USER", messages.first().senderType)
        assertEquals("AI", messages.last().senderType)
        messages.indices.filter { messages[it].senderType == "USER" }.forEach { userIndex ->
            val nextUserIndex = (userIndex + 1 until messages.size)
                .firstOrNull { messages[it].senderType == "USER" }
                ?: messages.size
            assertTrue(messages.subList(userIndex + 1, nextUserIndex).size in 1..3)
            assertTrue(messages.subList(userIndex + 1, nextUserIndex).all { it.senderType == "AI" })
        }
    }

    private suspend fun sendTurn(container: AppContainer, threadId: String, message: String): String {
        val before = container.chatRepository.getThread(threadId) ?: error("Missing chat thread")
        container.chatRepository.addUserMessage(before, message)
        val current = container.chatRepository.getThread(threadId) ?: error("Missing updated chat thread")
        val reply = container.aiRepository.generateReply(current, message)
        reply.messages.forEach { bubble -> container.chatRepository.addAiMessage(current, bubble) }
        return reply.messages.joinToString(" ")
    }
}

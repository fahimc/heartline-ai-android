package com.heartline.ai.ai

import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.UserProfileEntity
import com.heartline.ai.data.seed.PersonaSeedData
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.notifications.ProactiveLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ConversationDirectorTest {
    private val fixedNow = LocalDateTime.of(2026, 7, 13, 14, 30)
    private val director = ConversationDirector(responseBankJson()) { fixedNow }
    private val persona = PersonaSeedData.personas().first()
    private val user = UserProfileEntity(
        displayName = "Fred",
        preferredTone = "Sweet",
        notificationLevel = "Normal",
        quietHoursStart = "22:00",
        quietHoursEnd = "07:00"
    )

    @Test
    fun detectsCommonChatIntentsFromNaturalShortMessages() {
        assertEquals("user_at_work", director.detectIntent("good just at work"))
        assertEquals("ask_persona_wellbeing", director.detectIntent("good and you"))
        assertEquals("ask_persona_day", director.detectIntent("what are you up to?"))
        assertEquals("ask_repeat_persona_activity", director.detectIntent("good. what did you say you are doing?"))
        assertEquals("user_presence_check", director.detectIntent("you there"))
        assertEquals("ask_persona_availability", director.detectIntent("are you still awake"))
        assertEquals("ask_clarify_previous_question", director.detectIntent("feel aboutnwhat"))
        assertEquals("ask_clarify_previous_question", director.detectIntent("what you talking about"))
        assertEquals("user_finished_meal", director.detectIntent("yeah not bad, just had dinner"))
        assertEquals("user_correction", director.detectIntent("no i meant tomorrow"))
        assertEquals("user_preference", director.detectIntent("I like late night music"))
        assertEquals("user_tired", director.detectIntent("I am absolutely drained"))
        assertEquals("general_question", director.detectIntent("what"))
    }

    @Test
    fun emptyModelOutputStillProducesContextualReply() {
        val turn = director.selectTurn(request("good just at work"))
        val reply = director.validate("", turn)

        assertTrue(reply.messages.isNotEmpty())
        assertTrue(reply.messages.joinToString(" ").contains(Regex("(?i)work|shift|productive|day")))
        assertFalse(reply.messages.joinToString(" ").contains("Asset Loading", ignoreCase = true))
        assertTrue(reply.messages.sumOf { it.split(Regex("\\s+")).size } <= 40)
    }

    @Test
    fun rawJsonModelOutputIsNeverRenderedAsAChatBubble() {
        val turn = director.selectTurn(request("hi"))
        val rawJson = """
            {"messages":["first bubble","Hi Fred, how is your morning?"],"mood":"happy"}
        """.trimIndent()

        val reply = director.validate(rawJson, turn)
        val joined = reply.messages.joinToString(" ")

        assertTrue(reply.messages.isNotEmpty())
        assertFalse(joined.contains("{"))
        assertFalse(joined.contains("messages", ignoreCase = true))
        assertFalse(joined.contains("first bubble", ignoreCase = true))
    }

    @Test
    fun workReplyDoesNotQuoteUserBackAsTheMainResponse() {
        val turn = director.selectTurn(request("nothing im at work now"))
        val reply = director.validate("You said \"nothing im at work now\". What happened with it today?", turn)
        val joined = reply.messages.joinToString(" ")

        assertTrue(joined.contains(Regex("(?i)work|shift|day|productive")))
        assertFalse(joined.contains("You said", ignoreCase = true))
        assertFalse(joined.contains("What happened with it today", ignoreCase = true))
    }

    @Test
    fun personaDayTurnUsesCurrentRoutineAndPersistentContext() {
        val turn = director.selectTurn(
            request(
                message = "what are you doing?",
                summary = "Fred has a presentation after lunch."
            )
        )

        assertEquals("ask_persona_day", turn.intent)
        assertTrue(turn.currentActivity.contains("cafe photos"))
        assertTrue(turn.conversationSummary.contains("Fred has a presentation"))
        assertTrue(director.rewritePrompt(turn).contains("Prepared reply:"))
        assertTrue(director.rewritePrompt(turn).contains("/no_think"))
        assertFalse(director.rewritePrompt(turn).contains("Recent chat"))
        assertTrue(turn.seed.contains("cafe photos"))
    }

    @Test
    fun novelGenericModelTextCannotReplaceGroundedDinnerReply() {
        val request = request(
            message = "you there",
            recent = listOf(
                aiMessage("How is your evening going?"),
                userMessage("yeah not bad, just had dinner"),
                userMessage("you there")
            )
        )
        val turn = director.selectTurn(request)
        val reply = director.validate("That sounds interesting. Tell me more about it.", turn)
        val text = reply.messages.joinToString(" ")

        assertEquals("user_presence_check", turn.intent)
        assertTrue(text.contains(Regex("(?i)\\b(here|still)\\b")))
        assertTrue(text.contains(Regex("(?i)\\b(dinner|meal|food)\\b")))
        assertFalse(text.contains("interesting", ignoreCase = true))
    }

    @Test
    fun modelCannotInvertWhoAteOrWhoIsTired() {
        val mealTurn = director.selectTurn(request("yeah not bad, just had dinner"))
        val mealReply = director.validate("I had pasta for dinner and it was delicious.", mealTurn)
            .messages.joinToString(" ")
        val tiredTurn = director.selectTurn(request("im exhausted after a long shift"))
        val tiredReply = director.validate("I am exhausted after my shift too.", tiredTurn)
            .messages.joinToString(" ")

        assertFalse(mealReply.contains(Regex("(?i)\\bI (had|ate)\\b")))
        assertTrue(mealReply.contains(Regex("(?i)\\b(dinner|meal|plate)\\b")))
        assertFalse(tiredReply.contains(Regex("(?i)\\bI (am|'m) (tired|exhausted)\\b")))
        assertTrue(tiredReply.contains(Regex("(?i)\\b(tired|exhausted|shift|rest|gentle|worn out)\\b")))
    }

    @Test
    fun clarificationWalksBackToLastMeaningfulTopic() {
        val turn = director.selectTurn(
            request(
                message = "what you talking about",
                recent = listOf(
                    aiMessage("How is your day treating you?"),
                    userMessage("yeah not bad, just had dinner"),
                    userMessage("you there"),
                    aiMessage("I am here. I saw your dinner message. What did you have?"),
                    userMessage("feel aboutnwhat"),
                    aiMessage("How do you feel about it?"),
                    userMessage("what you talking about")
                )
            )
        )
        val reply = director.validate("I understand. What matters most to you?", turn)
            .messages.joinToString(" ")

        assertEquals("ask_clarify_previous_question", turn.intent)
        assertTrue(reply.contains(Regex("(?i)\\b(mean|meant|asking)\\b")))
        assertTrue(reply.contains(Regex("(?i)\\b(dinner|evening|meal)\\b")))
    }

    @Test
    fun repeatedGeneratedLineIsReplacedByFreshDirectedLine() {
        val repeated = "At work, got it. I will keep this low-pressure. Is it busy or fairly calm today?"
        val request = request(
            message = "still at work",
            recent = listOf(aiMessage(repeated), userMessage("still at work"))
        )
        val turn = director.selectTurn(request)
        val reply = director.validate(repeated, turn)

        assertFalse(reply.messages.joinToString(" ").equals(repeated, ignoreCase = true))
    }

    @Test
    fun meaningfulJobFactBecomesMemoryCandidate() {
        val turn = director.selectTurn(request("My job is landscape gardening."))

        assertTrue(turn.memoryCandidates.any { it.type == "fact" && "landscape gardening" in it.content })
    }

    private fun request(
        message: String,
        recent: List<MessageEntity> = listOf(userMessage(message)),
        summary: String = ""
    ) = AiChatRequest(
        persona = persona,
        user = user,
        thread = ChatThreadEntity(
            id = "thread-maya",
            personaId = persona.id,
            relationshipStage = "Getting to know you",
            affinityScore = 24,
            messageCount = 12
        ),
        mood = null,
        memories = emptyList(),
        recentMessages = recent,
        conversationSummary = summary,
        message = message
    )

    private fun userMessage(content: String) = MessageEntity(
        id = "u-${content.hashCode()}",
        threadId = "thread-maya",
        senderType = "USER",
        content = content
    )

    private fun aiMessage(content: String) = MessageEntity(
        id = "a-${content.hashCode()}",
        threadId = "thread-maya",
        senderType = "AI",
        content = content,
        source = "AI_REPLY"
    )

    private fun responseBankJson(): String {
        val candidates = listOf(
            File("app/src/main/assets/companion_response_bank.json"),
            File("src/main/assets/companion_response_bank.json")
        )
        return candidates.first(File::exists).readText()
    }
}

class RoutineAndProactivePolicyTest {
    @Test
    fun routineUsesWeekendLifeInsteadOfWorkdayActivity() {
        val routine = PersonaSeedData.personas().first().routineJson
        val saturday = LocalDateTime.of(2026, 7, 11, 14, 0)
        val state = RoutineEngine { saturday }.snapshot(routine)

        assertTrue(state.isWeekend)
        assertTrue(state.currentActivity.contains("new cafe"))
    }

    @Test
    fun notificationLevelsHaveIncreasingQuotasAndShorterGaps() {
        val light = ProactiveLimits.forLevel("Light")
        val normal = ProactiveLimits.forLevel("Normal")
        val frequent = ProactiveLimits.forLevel("Frequent")

        assertTrue(light.maxTotalPerDay < normal.maxTotalPerDay)
        assertTrue(normal.maxTotalPerDay < frequent.maxTotalPerDay)
        assertEquals(TimeUnit.HOURS.toMillis(6), light.minimumGlobalGapMillis)
        assertTrue(frequent.minimumGlobalGapMillis < normal.minimumGlobalGapMillis)
        assertTrue(light.maximumIdleBeforeGuaranteedMillis > normal.maximumIdleBeforeGuaranteedMillis)
        assertTrue(normal.maximumIdleBeforeGuaranteedMillis > frequent.maximumIdleBeforeGuaranteedMillis)
    }
}

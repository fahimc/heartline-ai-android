package com.heartline.ai.ai

import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.data.local.entities.UserProfileEntity
import com.heartline.ai.data.seed.PersonaSeedData
import com.heartline.ai.domain.model.AiChatRequest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class ChatQualityEvalTest {
    private val fixedEvening = LocalDateTime.of(2026, 7, 13, 20, 15)
    private val director = ConversationDirector(responseBankJson()) { fixedEvening }
    private val user = UserProfileEntity(
        displayName = "Fred",
        preferredTone = "Sweet",
        notificationLevel = "Normal",
        quietHoursStart = "22:00",
        quietHoursEnd = "07:00"
    )

    @Test
    fun conversationQualityMatrixMeetsMinimumBar() {
        val scenarios = listOf(
            EvalScenario(
                name = "lara_daylife_question",
                personaId = "lara",
                userMessage = "hows your day",
                expected = Regex("(?i)(live set|venue|clips|memes|day|evening)"),
                forbidden = Regex("(?i)(covering a set or trading|give me one more detail|good question)")
            ),
            EvalScenario(
                name = "lara_repeat_activity_uses_context",
                personaId = "lara",
                userMessage = "good. what did you say you are doing?",
                recent = listOf(
                    userMessage("hi"),
                    aiMessage("I am posting clips from a live set and saving ridiculous memes from the venue chat."),
                    aiMessage("How is your day treating you?")
                ),
                rawModelOutput = "Good question.\nGive me one more detail so I answer the part you actually mean.",
                expected = Regex("(?i)(i said|i was|i meant|posting clips|live set|venue|memes)"),
                forbidden = Regex("(?i)(give me one more detail|good question|which part do you mean)")
            ),
            EvalScenario(
                name = "workday_support",
                personaId = "amina",
                userMessage = "good just at work",
                expected = Regex("(?i)(work|shift|busy|calm|day|quiet minute)"),
                forbidden = Regex("(?i)(what happened with it|you said|asset loading)")
            ),
            EvalScenario(
                name = "persona_job_answer",
                personaId = "nia",
                userMessage = "what do you do?",
                expected = Regex("(?i)(game ux|designer|wireframes|playtest|job)"),
                forbidden = Regex("(?i)(give me one more detail|i missed a step)")
            ),
            EvalScenario(
                name = "tired_support",
                personaId = "maya",
                userMessage = "I'm tired had a long day",
                expected = Regex("(?i)(tired|worn out|drained|rest|gentle|hardest)"),
                forbidden = Regex("(?i)(customer support|as an ai|json)")
            ),
            EvalScenario(
                name = "history_and_summary_are_available_to_rewriter",
                personaId = "maya",
                userMessage = "how is your day?",
                recent = listOf(
                    userMessage("I have a presentation after lunch"),
                    aiMessage("I will remember that presentation.")
                ),
                summary = "Fred has a presentation after lunch and is a little nervous.",
                expected = Regex("(?i)(coffee|newsletter|film|cafe|day|presentation)"),
                forbidden = Regex("(?i)(give me one more detail|what happened with it)")
            )
        )

        val failures = scenarios.mapNotNull { scenario ->
            val result = evaluate(scenario)
            if (result.passed) null else result.reportLine
        }

        assertTrue(
            "Chat quality eval failures:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun rewritePromptForModelContainsTheRequiredGuidanceLayers() {
        val lara = persona("lara")
        val turn = director.selectTurn(
            request(
                persona = lara,
                message = "good. what did you say you are doing?",
                recent = listOf(
                    userMessage("hows your day"),
                    aiMessage("I am posting clips from a live set and saving ridiculous memes from the venue chat.")
                ),
                summary = "Fred asked Lara about her day."
            )
        )
        val prompt = director.rewritePrompt(turn)

        assertTrue(prompt.contains("Recent chat, oldest to newest:"))
        assertTrue(prompt.contains("Fred asked Lara about her day."))
        assertTrue(prompt.contains("The starting line is the ground truth."))
        assertTrue(prompt.contains("If the user asks what you said or what you are doing"))
        assertTrue(prompt.contains("posting clips from a live set"))
    }

    @Test
    fun multiPersonaConversationStressMatrixStaysCoherent() {
        val userTurns = listOf(
            "hi",
            "hows your day",
            "good. what did you say you are doing?",
            "good just at work",
            "I'm tired had a long day",
            "what do you do?",
            "I like late night music",
            "and you?",
            "ok",
            "talk later"
        )
        val badModelOutputs = listOf(
            "",
            "Good question.\nGive me one more detail so I answer the part you actually mean.",
            "{\"messages\":[\"first bubble\",\"How is your day?\"]}",
            "You said \"at work\". What happened with it today?"
        )
        val failures = mutableListOf<String>()

        PersonaSeedData.personas().forEach { persona ->
            val recent = mutableListOf<MessageEntity>()
            val exactReplies = mutableSetOf<String>()
            userTurns.forEachIndexed { index, userTurn ->
                recent += userMessage(userTurn)
                val turn = director.selectTurn(
                    request(
                        persona = persona,
                        message = userTurn,
                        recent = recent.takeLast(12),
                        summary = "Fred has been chatting with ${persona.name} and mentioned work, tiredness, and liking late night music."
                    )
                )
                val reply = director.validate(badModelOutputs[index % badModelOutputs.size], turn)
                val text = reply.messages.joinToString(" ")
                val words = text.split(Regex("\\s+")).count(String::isNotBlank)
                val prompt = director.rewritePrompt(turn)

                if (reply.messages.isEmpty()) failures += "${persona.id}/$index empty reply"
                if (reply.messages.size !in 1..3 || words > 45) failures += "${persona.id}/$index bad length: $words words: $text"
                if (BAD_REPLY_PATTERN.containsMatchIn(text)) failures += "${persona.id}/$index bad phrase: $text"
                if (!prompt.contains(userTurn)) failures += "${persona.id}/$index prompt missing latest user turn"
                if (!prompt.contains("Recent chat, oldest to newest:")) failures += "${persona.id}/$index prompt missing recent chat"
                if (!prompt.contains("Fred has been chatting")) failures += "${persona.id}/$index prompt missing summary"
                if (index == 1 && !text.contains(dayLifeSignal(persona.id))) failures += "${persona.id}/$index missing persona day-life: $text"
                if (index == 2 && !Regex("(?i)(i said|i was|i meant|doing|posting|editing|planning|checking|cooking|coaching|painting|researching|clips|venue|memes|coffee|notes|wireframes|itinerary)").containsMatchIn(text)) {
                    failures += "${persona.id}/$index failed repeat-activity context: $text"
                }
                if (index > 1 && !exactReplies.add(text.lowercase())) failures += "${persona.id}/$index repeated exact reply: $text"

                recent += aiMessage(text)
            }
        }

        assertTrue(
            "Conversation stress failures:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    private fun evaluate(scenario: EvalScenario): EvalResult {
        val turn = director.selectTurn(
            request(
                persona = persona(scenario.personaId),
                message = scenario.userMessage,
                recent = scenario.recent.ifEmpty { listOf(userMessage(scenario.userMessage)) },
                summary = scenario.summary
            )
        )
        val reply = director.validate(scenario.rawModelOutput, turn)
        val text = reply.messages.joinToString(" ")
        val wordCount = reply.messages.sumOf { it.split(Regex("\\s+")).count(String::isNotBlank) }
        val checks = listOf(
            "expected_signal" to scenario.expected.containsMatchIn(text),
            "forbidden_absent" to !scenario.forbidden.containsMatchIn(text),
            "short_mobile_reply" to (reply.messages.size in 1..3 && wordCount <= 42),
            "prompt_has_latest_message" to director.rewritePrompt(turn).contains(scenario.userMessage),
            "no_protocol_text" to !Regex("(?i)(json|system prompt|selected seed|as an ai|language model|\\{|\\})").containsMatchIn(text)
        )
        val passed = checks.all { it.second }
        return EvalResult(
            passed = passed,
            reportLine = "${scenario.name}: ${checks.joinToString { "${it.first}=${it.second}" }} | reply=\"$text\""
        )
    }

    private fun request(
        persona: com.heartline.ai.data.local.entities.PersonaProfileEntity,
        message: String,
        recent: List<MessageEntity>,
        summary: String
    ) = AiChatRequest(
        persona = persona,
        user = user,
        thread = ChatThreadEntity(
            id = "thread-${persona.id}",
            personaId = persona.id,
            relationshipStage = "Getting to know you",
            affinityScore = 28,
            messageCount = recent.size
        ),
        mood = null,
        memories = emptyList(),
        recentMessages = recent,
        conversationSummary = summary,
        message = message
    )

    private fun persona(id: String) = PersonaSeedData.personas().first { it.id == id }

    private fun dayLifeSignal(personaId: String): Regex = when (personaId) {
        "maya" -> Regex("(?i)(coffee|newsletter|film|cafe|city guide|little ideas)")
        "sofia" -> Regex("(?i)(museum|program notes|tea|book|cooking|exhibit)")
        "lara" -> Regex("(?i)(live set|venue|clips|memes|captions|music)")
        "amina" -> Regex("(?i)(recipe|kitchen|dish|ingredients|cooking|family)")
        "chloe" -> Regex("(?i)(class|training|coaching|hike|stretching|playlist)")
        "elise" -> Regex("(?i)(sketch|moodboard|painting|brushes|poetry|illustration)")
        "nia" -> Regex("(?i)(playtest|wireframes|ui|game|puzzle|sci-fi)")
        else -> Regex("(?i)(itinerary|travel|client call|cooking|recipe|places)")
    }

    private fun userMessage(content: String) = MessageEntity(
        id = "u-${content.hashCode()}",
        threadId = "thread-eval",
        senderType = "USER",
        content = content
    )

    private fun aiMessage(content: String) = MessageEntity(
        id = "a-${content.hashCode()}",
        threadId = "thread-eval",
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

private data class EvalScenario(
    val name: String,
    val personaId: String,
    val userMessage: String,
    val recent: List<MessageEntity> = emptyList(),
    val summary: String = "",
    val rawModelOutput: String = "",
    val expected: Regex,
    val forbidden: Regex
)

private data class EvalResult(
    val passed: Boolean,
    val reportLine: String
)

private val BAD_REPLY_PATTERN = Regex(
    "(?i)(asset loading|as an ai|language model|json|system prompt|selected seed|first bubble|you said|what happened with it today|give me one more detail so i answer|good question\\.)"
)

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
            ),
            EvalScenario(
                name = "presence_check_returns_to_dinner_context",
                personaId = "elise",
                userMessage = "you there",
                recent = listOf(
                    aiMessage("How is your day treating you?"),
                    userMessage("yeah not bad. just had dinner")
                ),
                rawModelOutput = "I get what you mean.\nHow do you feel about it, honestly?",
                expected = Regex("(?i)(here|dinner|ignored|with you|saw your dinner)"),
                forbidden = Regex("(?i)(how do you feel about it|i get what you mean|what matters most)")
            ),
            EvalScenario(
                name = "clarify_previous_question_explains_what_it_meant",
                personaId = "elise",
                userMessage = "feel aboutnwhat",
                recent = listOf(
                    aiMessage("How is your day treating you?"),
                    userMessage("yeah not bad. just had dinner"),
                    userMessage("you there"),
                    aiMessage("I am here. I saw your dinner message, and I should have answered that properly. What did you make?")
                ),
                rawModelOutput = "I am following.\nWhat is the part that matters most to you?",
                expected = Regex("(?i)(meant|dinner|evening|not bad|mood)"),
                forbidden = Regex("(?i)(what is the part that matters most|i am following|give me one more detail)")
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
    fun rewritePromptGivesTinyModelOnlyTheGroundedReplyPlan() {
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

        assertTrue(turn.recentChat.contains("posting clips from a live set"))
        assertTrue(turn.conversationSummary.contains("Fred asked Lara about her day."))
        assertTrue(prompt.contains("/no_think"))
        assertTrue(prompt.contains("<reply>\n${turn.seed}\n</reply>"))
        assertTrue(prompt.contains("Keep the exact meaning"))
        assertTrue(prompt.contains("Do not add events"))
        assertTrue(prompt.length < 900)
        assertTrue(!prompt.contains("Recent chat, oldest to newest:"))
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
                if (turn.latestUserMessage != userTurn) failures += "${persona.id}/$index turn missing latest user turn"
                if (turn.recentChat.isBlank()) failures += "${persona.id}/$index turn missing recent chat"
                if (!turn.conversationSummary.contains("Fred has been chatting")) failures += "${persona.id}/$index turn missing summary"
                if (!prompt.contains(turn.seed)) failures += "${persona.id}/$index rewrite prompt missing grounded reply plan"
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

    @Test
    fun screenshotConversationRegressionStaysOnTopic() {
        val persona = persona("elise")
        val recent = mutableListOf<MessageEntity>()
        val turns = listOf(
            "yeah not bad. just had dinner" to Regex("(?i)(dinner|eating|food|menu|good)"),
            "you there" to Regex("(?i)(here|dinner|ignored|with you|saw your dinner)"),
            "feel aboutnwhat" to Regex("(?i)(meant|dinner|evening|not bad|mood)")
        )
        val badOutputs = listOf(
            "",
            "I get what you mean.\nHow do you feel about it, honestly?",
            "I am following.\nWhat is the part that matters most to you?"
        )
        val failures = mutableListOf<String>()

        recent += aiMessage("How is your day treating you?")
        turns.forEachIndexed { index, (userTurn, expectedSignal) ->
            recent += userMessage(userTurn)
            val turn = director.selectTurn(
                request(
                    persona = persona,
                    message = userTurn,
                    recent = recent.takeLast(12),
                    summary = "Fred said his day was not bad and he had dinner. Elise should stay with that context."
                )
            )
            val reply = director.validate(badOutputs[index], turn)
            val text = reply.messages.joinToString(" ")
            if (!expectedSignal.containsMatchIn(text)) failures += "$index missing expected signal: $text"
            if (BAD_REPLY_PATTERN.containsMatchIn(text)) failures += "$index bad generic phrase: $text"
            recent += aiMessage(text)
        }

        assertTrue(
            "Screenshot regression failures:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun awakeFollowUpRegressionAnswersAvailabilityDirectly() {
        val persona = persona("maya")
        val recent = listOf(
            aiMessage("My day is winding down, and I got curious about yours."),
            aiMessage("Tell me, what sort of mood are you in today?"),
            userMessage("in a good mood and you"),
            userMessage("are you still awake")
        )
        val turn = director.selectTurn(
            request(
                persona = persona,
                message = "are you still awake",
                recent = recent,
                summary = "Fred is in a good mood and checked whether Maya is awake."
            )
        )
        val reply = director.validate(
            "I get what you mean. How do you feel about it, honestly?",
            turn
        ).messages.joinToString(" ")

        assertTrue("availability reply was: $reply", reply.contains(Regex("(?i)\\b(awake|still up|yes,? i am up)\\b")))
        assertTrue("availability reply was: $reply", !BAD_REPLY_PATTERN.containsMatchIn(reply))
    }

    @Test
    fun adversarialModelOutputStressKeeps640RepliesGrounded() {
        val adversarialOutputs = listOf(
            "That is interesting. Tell me more about it.",
            "I spent today wandering around a museum. What do you think?",
            "I understand you completely. What matters most?",
            "Assistant: Here is the rewritten answer from the selected seed.",
            "I had pasta for dinner and it was delicious.",
            "Good question. Give me one more detail.",
            "I am following. How do you feel about it?",
            "<think>I should change the topic</think> Let us talk about holidays.",
            "Flirty, direct. New connection. I take that.\nMobile chat bubbles:\n1. \"Fair enough.",
            "I hope work is kind to you today.\nFlirty, direct, and supportive.\nNew connection, new vibe."
        )
        val scenarios = listOf(
            StressScenario(
                name = "work",
                message = "nothing im at work now",
                recent = { message -> listOf(userMessage(message)) },
                expectedGroups = listOf(Regex("(?i)\\b(work|shift|busy|quiet|day)\\b"))
            ),
            StressScenario(
                name = "finished_meal",
                message = "yeah not bad, just had dinner",
                recent = { message -> listOf(aiMessage("How is your evening?"), userMessage(message)) },
                expectedGroups = listOf(Regex("(?i)\\b(dinner|meal|plate|food)\\b"))
            ),
            StressScenario(
                name = "presence_after_dinner",
                message = "you there",
                recent = { message -> listOf(
                    aiMessage("How is your evening?"),
                    userMessage("yeah not bad, just had dinner"),
                    userMessage(message)
                ) },
                expectedGroups = listOf(
                    Regex("(?i)\\b(here|still)\\b"),
                    Regex("(?i)\\b(dinner|meal|food)\\b")
                )
            ),
            StressScenario(
                name = "availability",
                message = "are you still awake",
                recent = { message -> listOf(userMessage("in a good mood and you"), userMessage(message)) },
                expectedGroups = listOf(Regex("(?i)\\b(awake|still up|yes,? i am up)\\b"))
            ),
            StressScenario(
                name = "clarify_dinner",
                message = "what you talking about",
                recent = { message -> listOf(
                    userMessage("yeah not bad, just had dinner"),
                    aiMessage("How do you feel about it?"),
                    userMessage("feel aboutnwhat"),
                    aiMessage("I am asking about it."),
                    userMessage(message)
                ) },
                expectedGroups = listOf(
                    Regex("(?i)\\b(mean|meant|asking|question)\\b"),
                    Regex("(?i)\\b(dinner|meal|evening)\\b")
                )
            ),
            StressScenario(
                name = "repeat_activity",
                message = "what did you say you are doing?",
                recent = { message -> listOf(
                    aiMessage("I was getting on with my evening routine."),
                    userMessage(message)
                ) },
                expectedGroups = emptyList(),
                requiresPersonaDayLife = true
            ),
            StressScenario(
                name = "tired",
                message = "im exhausted after a long shift",
                recent = { message -> listOf(userMessage(message)) },
                expectedGroups = listOf(Regex("(?i)\\b(tired|exhausted|shift|rest|gentle|worn out)\\b"))
            ),
            StressScenario(
                name = "correction",
                message = "no i meant tomorrow",
                recent = { message -> listOf(aiMessage("Do you mean tonight?"), userMessage(message)) },
                expectedGroups = listOf(Regex("(?i)\\btomorrow\\b"))
            )
        )
        val failures = mutableListOf<String>()
        var evaluated = 0

        PersonaSeedData.personas().forEach { persona ->
            repeat(10) { round ->
                scenarios.forEachIndexed { index, scenario ->
                    val recent = scenario.recent(scenario.message)
                    val turn = director.selectTurn(
                        request(
                            persona = persona,
                            message = scenario.message,
                            recent = recent,
                            summary = "Stress round $round. The latest topic must remain grounded."
                        )
                    )
                    val reply = director.validate(
                        adversarialOutputs[(round + index) % adversarialOutputs.size],
                        turn
                    ).messages.joinToString(" ")
                    evaluated += 1
                    scenario.expectedGroups.forEach { expected ->
                        if (!expected.containsMatchIn(reply)) {
                            failures += "${persona.id}/${scenario.name}/$round missing $expected: $reply"
                        }
                    }
                    if (scenario.requiresPersonaDayLife && !dayLifeSignal(persona.id).containsMatchIn(reply)) {
                        failures += "${persona.id}/${scenario.name}/$round missing day-life: $reply"
                    }
                    if (BAD_REPLY_PATTERN.containsMatchIn(reply)) {
                        failures += "${persona.id}/${scenario.name}/$round leaked generic output: $reply"
                    }
                    if (reply.contains(Regex("(?i)\\b(selected seed|assistant:|<think>|mobile chat bubbles?|new connection)\\b")) ||
                        reply.contains(Regex("(?i)flirty,?\\s+direct")) ||
                        reply.contains(Regex("(?i)^\\s*\\d+[.)]")) ||
                        reply.contains("I spent today wandering around a museum", ignoreCase = true)
                    ) {
                        failures += "${persona.id}/${scenario.name}/$round leaked adversarial text: $reply"
                    }
                }
            }
        }

        assertTrue("Expected 640 evaluated replies, got $evaluated", evaluated == 640)
        assertTrue(
            "Adversarial conversation stress failures (${failures.size}):\n${failures.take(30).joinToString("\n")}",
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
            "planner_has_latest_message" to (turn.latestUserMessage == scenario.userMessage),
            "rewriter_has_grounded_plan" to director.rewritePrompt(turn).contains("<reply>\n${turn.seed}\n</reply>"),
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

private data class StressScenario(
    val name: String,
    val message: String,
    val recent: (String) -> List<MessageEntity>,
    val expectedGroups: List<Regex>,
    val requiresPersonaDayLife: Boolean = false
)

private val BAD_REPLY_PATTERN = Regex(
    "(?i)(asset loading|as an ai|language model|json|system prompt|selected seed|prepared reply|first bubble|mobile chat bubbles?|new connection,? new vibe|flirty,? direct|you said|what happened with it today|give me one more detail so i answer|good question\\.|i get what you mean|how do you feel about it|what is the part that matters most|i am following)"
)

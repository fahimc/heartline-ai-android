package com.heartline.ai.ai

import android.content.Context
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.domain.model.ProactiveMessageRequest
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import kotlin.math.abs

class ConversationDirector private constructor(
    private val bank: ResponseBank,
    private val nowProvider: () -> LocalDateTime
) {
    constructor(context: Context) : this(
        ResponseBank.fromJson(
            context.applicationContext.assets.open("companion_response_bank.json")
                .bufferedReader()
                .use { it.readText() }
        ),
        { LocalDateTime.now() }
    )

    internal constructor(
        responseBankJson: String,
        nowProvider: () -> LocalDateTime = { LocalDateTime.now() }
    ) : this(ResponseBank.fromJson(responseBankJson), nowProvider)

    private val routineEngine = RoutineEngine(nowProvider)

    fun selectTurn(request: AiChatRequest): DirectedTurn {
        val intent = detectIntent(request.message)
        val response = bank.suggestedResponses.firstOrNull { it.intent == intent }
            ?: bank.suggestedResponses.first { it.intent == "general" }
        val routine = routineEngine.snapshot(request.persona.routineJson)
        val recent = recentChat(request.recentMessages, request.persona.name)
        val groundedExamples = groundedExamples(intent, response.examples, request, routine)
        val alternatives = orderedExamples(
            examples = groundedExamples,
            key = "${request.persona.id}:${request.thread.messageCount}:${request.message}",
            recentChat = recent,
            routine = routine,
            userName = request.user?.displayName.orEmpty()
        )
        return DirectedTurn(
            personaName = request.persona.name,
            personaStyle = request.persona.chatStyle,
            personaPersonality = request.persona.personalityJson,
            personaInterests = request.persona.interestsJson,
            affectionStyle = request.persona.affectionStyle,
            humourStyle = request.persona.humourStyle,
            personaBoundaries = request.persona.boundaries,
            personaPrompt = request.persona.systemPromptFragment,
            fictionalRoutine = routine.promptSummary(),
            currentActivity = routine.currentActivity,
            userName = request.user?.displayName ?: "there",
            preferredTone = request.user?.preferredTone ?: "Sweet",
            relationshipStage = request.thread.relationshipStage,
            affinityScore = request.thread.affinityScore,
            mood = moodFor(response, request),
            intent = intent,
            conversationGoal = response.conversationGoal,
            pattern = bank.pattern(response.patterns.firstOrNull()).orEmpty(),
            seed = alternatives.firstOrNull() ?: bank.fallbackReplies.first(),
            fallbackSeeds = alternatives.drop(1) + bank.fallbackReplies,
            requiredSignalGroups = replySignalGroups(intent, request, routine),
            latestUserMessage = request.message,
            recentChat = recent,
            conversationSummary = request.conversationSummary,
            relevantMemories = request.memories.joinToString("; ") { it.content }.take(600),
            emotion = response.emotion,
            animation = response.animation,
            memoryCandidates = memoryCandidates(request.message)
        )
    }

    fun selectProactiveTurn(request: ProactiveMessageRequest): DirectedTurn {
        val now = nowProvider()
        val routine = routineEngine.snapshot(request.persona.routineJson, now)
        val recent = recentChat(request.recentMessages, request.persona.name)
        val candidates = bank.proactiveMessages.filter {
            it.timeWindow.equals(request.timeOfDay, ignoreCase = true)
        }
        val selected = candidates.firstOrNull {
            it.relationshipStages.isEmpty() || it.relationshipStages.any { stage ->
                stage.equals(request.thread.relationshipStage, ignoreCase = true)
            }
        } ?: candidates.firstOrNull() ?: bank.proactiveMessages.first()
        val alternatives = orderedExamples(
            examples = selected.examples,
            key = "${request.persona.id}:${now.dayOfYear}:${now.hour}:${request.thread.messageCount}",
            recentChat = recent,
            routine = routine,
            userName = request.user?.displayName.orEmpty()
        )
        return DirectedTurn(
            personaName = request.persona.name,
            personaStyle = request.persona.chatStyle,
            personaPersonality = request.persona.personalityJson,
            personaInterests = request.persona.interestsJson,
            affectionStyle = request.persona.affectionStyle,
            humourStyle = request.persona.humourStyle,
            personaBoundaries = request.persona.boundaries,
            personaPrompt = request.persona.systemPromptFragment,
            fictionalRoutine = routine.promptSummary(),
            currentActivity = routine.currentActivity,
            userName = request.user?.displayName ?: "there",
            preferredTone = request.user?.preferredTone ?: "Sweet",
            relationshipStage = request.thread.relationshipStage,
            affinityScore = request.thread.affinityScore,
            mood = selected.moods.firstOrNull() ?: request.mood?.mood ?: "caring",
            intent = selected.id,
            conversationGoal = "Start a casual, low-pressure conversation that fits the time of day.",
            pattern = "One natural check-in with a specific detail. Never sound needy or alarming.",
            seed = alternatives.firstOrNull() ?: bank.fallbackReplies.first(),
            fallbackSeeds = alternatives.drop(1) + bank.fallbackReplies,
            requiredSignalGroups = emptyList(),
            latestUserMessage = "",
            recentChat = recent,
            conversationSummary = request.conversationSummary,
            relevantMemories = request.memories.joinToString("; ") { it.content }.take(500),
            emotion = selected.moods.firstOrNull() ?: "caring",
            animation = selected.animation,
            memoryCandidates = emptyList()
        )
    }

    fun rewritePrompt(turn: DirectedTurn): String = buildString {
        appendLine("/no_think")
        appendLine("Rewrite one prepared companion reply. Do not answer the conversation yourself.")
        appendLine("Write as ${turn.personaName}: ${turn.personaStyle}")
        appendLine("Mood: ${turn.mood}. Relationship: ${turn.relationshipStage}.")
        appendLine("Prepared reply: ${turn.seed}")
        appendLine("Keep the exact meaning, speaker perspective, names, topic, and concrete facts.")
        appendLine("Do not add events, food, plans, actions, or questions that are not in the prepared reply.")
        appendLine("Return only 1 to 3 short mobile-chat bubbles, one per line, under 40 words total.")
        appendLine("No analysis, labels, JSON, quotes, role names, or thinking.")
    }

    fun validate(raw: String, turn: DirectedTurn): ValidatedReply {
        val candidate = raw.cleanupModelText()
            .replace(Regex("(?i)^${Regex.escape(turn.personaName)}\\s*[:\\-]\\s*"), "")
            .trim()
        val generated = candidate.toBubbles()
            .filterNot { it.isInvalidModelOutput() }
            .filterNot { it.isInvalidForTurn(turn) }
            .filter { it.wordCount() <= 30 }
            .take(3)
        val repetitionAllowed = turn.intent == "ask_repeat_persona_activity"
        val generatedIsUsable = generated.isNotEmpty() &&
            generated.sumOf { it.wordCount() } <= 40 &&
            preservesReplyPlan(generated, turn) &&
            (repetitionAllowed || !isRepetitive(generated, turn.recentChat))
        val finalBubbles = if (generatedIsUsable) {
            generated
        } else {
            (listOf(turn.seed) + turn.fallbackSeeds)
                .asSequence()
                .map { seed ->
                    seed.cleanupModelText()
                        .toBubbles()
                        .filterNot { it.isInvalidModelOutput() }
                        .filterNot { it.isInvalidForTurn(turn) }
                }
                .firstOrNull { it.isNotEmpty() && (repetitionAllowed || !isRepetitive(it, turn.recentChat)) }
                ?: listOf("Tell me a little more - I want to understand.")
        }
        return ValidatedReply(
            messages = finalBubbles.take(3).map { it.withNaturalEnding() },
            mood = turn.mood,
            emotion = turn.emotion,
            animation = turn.animation,
            memoryCandidates = turn.memoryCandidates,
            usedModelRewrite = generatedIsUsable
        )
    }

    fun extractMemoryCandidates(conversation: List<MessageEntity>): List<MemoryCandidate> =
        conversation
            .filter { it.senderType == "USER" }
            .takeLast(16)
            .flatMap { memoryCandidates(it.content) }
            .distinctBy { it.content.lowercase() }
            .take(8)

    internal fun detectIntent(message: String): String {
        val value = message.lowercase().trim()
        return when {
            Regex("\\b(kill myself|suicide|end my life|hurt myself|self harm)\\b").containsMatchIn(value) -> "user_crisis"
            Regex("\\b(how are you|how about you|and you|what about you|you okay)\\b").containsMatchIn(value) -> "ask_persona_wellbeing"
            Regex("^(?:are you still awake|still awake|you awake|are you awake|still up|are you up)\\s*[?!.]*$").containsMatchIn(value) -> "ask_persona_availability"
            Regex("\\b(you there|are you there|u there|still there|hello\\?|where did you go|are you around)\\b").containsMatchIn(value) -> "user_presence_check"
            Regex("\\b(feel about\\s*what|aboutnwhat|what do you mean|what did you mean|what are you asking|what (?:are |r |you )?talking about|talking about what|what about\\?)\\b").containsMatchIn(value) -> "ask_clarify_previous_question"
            Regex("\\b(what did you say|what were you doing|what was that you said|say you (are|were) doing|what did you mean).*(doing|day|work|job)?\\b").containsMatchIn(value) -> "ask_repeat_persona_activity"
            Regex("\\b(what are you doing|what you doing|what are you up to|what you up to|your day|how was your day)\\b").containsMatchIn(value) -> "ask_persona_day"
            Regex("\\b(what do you do|your job|where do you work|work do you do)\\b").containsMatchIn(value) -> "ask_persona_job"
            Regex("^(no[, ]+)?i (?:meant|mean)\\b|\\bnot that[, ]+i (?:meant|mean)\\b").containsMatchIn(value) -> "user_correction"
            Regex("\\b(tired|exhausted|drained|sleepy|worn out|knackered)\\b").containsMatchIn(value) -> "user_tired"
            Regex("\\b(work|working|job|office|shift|at work|meeting)\\b").containsMatchIn(value) -> "user_at_work"
            Regex("\\b(sad|upset|lonely|anxious|stressed|crying|depressed|awful)\\b").containsMatchIn(value) -> "user_sad"
            Regex("\\b(happy|excited|great|amazing|good news|glad|brilliant)\\b").containsMatchIn(value) -> "user_happy"
            Regex("\\b(busy|no time|later|can't talk|cant talk|swamped)\\b").containsMatchIn(value) -> "user_busy"
            Regex("\\b(bored|boring|nothing to do)\\b").containsMatchIn(value) -> "user_bored"
            Regex("\\b(just had|already had|finished|ate)\\b.*\\b(dinner|lunch|breakfast|food|meal)\\b|\\b(just had|finished) (?:my )?(dinner|lunch|breakfast)\\b").containsMatchIn(value) -> "user_finished_meal"
            Regex("\\b(hungry|eating|dinner|lunch|breakfast|food|cooking|meal)\\b").containsMatchIn(value) -> "user_food"
            Regex("\\b(i like|i love|my favourite|my favorite)\\b").containsMatchIn(value) -> "user_preference"
            Regex("\\b(tomorrow|weekend|tonight|plans|going out|holiday|vacation)\\b").containsMatchIn(value) -> "user_plans"
            Regex("\\b(love you|miss you|cute|beautiful|babe|baby|kiss|gorgeous)\\b").containsMatchIn(value) -> "user_affection"
            Regex("\\b(thanks|thank you|cheers|appreciate)\\b").containsMatchIn(value) -> "user_thanks"
            Regex("\\b(good night|goodnight|going to bed|sleep now)\\b").containsMatchIn(value) -> "user_goodnight"
            Regex("^(morning|good morning)\\b").containsMatchIn(value) -> "user_morning"
            Regex("^(hi|hey|hello|yo|evening)\\b").containsMatchIn(value) -> "user_greeting"
            value in setOf("ok", "okay", "yeah", "yes", "no", "maybe", "fine", "good", "lol", "haha") -> "short_acknowledgement"
            value.endsWith("?") || value in setOf("what", "why", "how") -> "general_question"
            else -> "general"
        }
    }

    private fun moodFor(response: ResponseSeed, request: AiChatRequest): String = when {
        request.thread.affinityScore > 55 && response.emotion == "curious" -> "playful"
        response.emotion.isNotBlank() -> response.emotion
        else -> request.mood?.mood ?: "curious"
    }

    private fun orderedExamples(
        examples: List<String>,
        key: String,
        recentChat: String,
        routine: RoutineState,
        userName: String
    ): List<String> {
        val source = examples.ifEmpty { bank.fallbackReplies }
        if (source.isEmpty()) return emptyList()
        val start = Math.floorMod(key.hashCode(), source.size)
        val ordered = source.indices.map { source[(start + it) % source.size] }
            .map { materialize(it, routine, userName) }
        return ordered.sortedBy { if (isRepetitive(listOf(it), recentChat)) 1 else 0 }
    }

    private fun groundedExamples(
        intent: String,
        defaults: List<String>,
        request: AiChatRequest,
        routine: RoutineState
    ): List<String> {
        val previousAi = request.recentMessages.asReversed().firstOrNull { it.senderType == "AI" }
        val priorTopic = previousConversationTopic(request)
        val currentTopic = topicFrom(request.message, request.recentMessages)
        return when (intent) {
            "user_presence_check" -> {
                val topic = priorTopic
                if (topic == null) {
                    listOf(
                        "I am here. Sorry, I did not mean to leave you hanging. What were you saying?",
                        "Still here. I was with you - carry on.",
                        "I am here. I did not mean for that pause to feel strange."
                    )
                } else {
                    listOf(
                        "I am here. I saw your message about ${topic.reference}. ${topic.followUp}",
                        "Still here. I did not mean to leave you hanging on ${topic.reference}. ${topic.followUp}",
                        "I am here with you. Let us go back to ${topic.reference}. ${topic.followUp}"
                    )
                }
            }
            "ask_persona_availability" -> listOf(
                "I am still awake. I am ${routine.currentActivity}, and I am happy to keep talking.",
                "Still awake. I am winding down with ${routine.currentActivity}, but I am here.",
                "Yes, I am up. I was ${routine.currentActivity} when your message came in."
            )
            "ask_clarify_previous_question" -> when (priorTopic?.key) {
                "food" -> listOf(
                    "I meant how your evening felt after dinner. I worded that badly.",
                    "I was asking about your evening after dinner - whether it felt relaxed or just ordinary.",
                    "I meant the dinner part of your evening. My question was too vague."
                )
                null -> listOfNotNull(
                    previousAi?.content?.takeIf(String::isNotBlank)?.let {
                        "I meant my last question: ${it.trim().take(120)} I worded it badly."
                    },
                    "I was trying to explain my last question, but I made it vague. That was on me."
                )
                else -> listOf(
                    "I meant my question about ${priorTopic.reference}. I worded that badly. ${priorTopic.followUp}",
                    "I was asking about ${priorTopic.reference}. My last message was too vague.",
                    "I meant ${priorTopic.reference}, not something new. I should have said that clearly."
                )
            }
            "user_finished_meal" -> {
                val meal = Regex("(?i)\\b(dinner|lunch|breakfast|meal)\\b").find(request.message)?.value?.lowercase() ?: "meal"
                listOf(
                    "That sounds like a decent ${routine.timeOfDay}. What did you have for $meal?",
                    "Oh nice, $meal done. What did you end up having - and was it good?",
                    "I hope $meal was good. What was on the plate?"
                )
            }
            "user_correction" -> {
                val correction = Regex("(?i)\\b(?:meant|mean)\\s+(.+)").find(request.message)
                    ?.groupValues?.getOrNull(1)?.trim(' ', '.', '?', '!')
                    ?.take(70)
                if (correction.isNullOrBlank()) defaults else listOf(
                    "Oh, $correction - got it. Thanks for correcting me.",
                    "Right, $correction. I understand what you meant now.",
                    "I have you now: $correction. I was following the wrong detail."
                )
            }
            "general" -> when {
                currentTopic?.key == "pet" -> listOf(
                    "Poor ${currentTopic.reference}, and poor you. ${currentTopic.followUp}",
                    "That sounds like a rough night with ${currentTopic.reference}. ${currentTopic.followUp}",
                    "I remembered ${currentTopic.reference}. ${currentTopic.followUp}"
                )
                currentTopic != null -> listOf(
                    "I want to stay with what you said about ${currentTopic.reference}. ${currentTopic.followUp}",
                    "Tell me more about ${currentTopic.reference}. ${currentTopic.followUp}",
                    "I am curious about ${currentTopic.reference}. ${currentTopic.followUp}"
                )
                else -> defaults
            }
            else -> defaults
        }
    }

    private fun replySignalGroups(
        intent: String,
        request: AiChatRequest,
        routine: RoutineState
    ): List<Set<String>> {
        val previousTopic = previousConversationTopic(request)
        val currentTopic = topicFrom(request.message, request.recentMessages)
        val groups: List<Set<String>> = when (intent) {
            "user_presence_check" -> listOf(setOf("here", "around", "still"))
            "ask_persona_availability" -> listOf(setOf("awake", "up", "here", "still"))
            "ask_clarify_previous_question" -> listOf(setOf("mean", "meant", "asking", "question", "vague"))
            "ask_repeat_persona_activity", "ask_persona_day" -> listOf(routine.currentActivity.semanticWords())
            "ask_persona_job" -> listOf(routine.job.semanticWords())
            "user_finished_meal", "user_food" -> listOf(setOf("food", "dinner", "lunch", "breakfast", "meal", "eat"))
            "user_at_work" -> listOf(setOf("work", "shift", "busy", "quiet", "day"))
            "user_tired" -> listOf(setOf("tired", "exhausted", "drained", "rest", "gentle", "shift", "worn"))
            "user_correction" -> currentTopic?.signals?.let(::listOf).orEmpty()
            "general" -> currentTopic?.signals?.let(::listOf).orEmpty()
            else -> emptyList()
        }.filter { it.isNotEmpty() }
        val contextual = when (intent) {
            "user_presence_check", "ask_clarify_previous_question" -> previousTopic?.signals
            else -> null
        }
        return if (contextual.isNullOrEmpty()) groups else groups + listOf(contextual)
    }

    private fun previousConversationTopic(request: AiChatRequest): ConversationTopic? {
        var skippedCurrent = false
        request.recentMessages.asReversed().forEach { message ->
            if (message.senderType == "USER" && !skippedCurrent &&
                message.content.trim().equals(request.message.trim(), ignoreCase = true)
            ) {
                skippedCurrent = true
                return@forEach
            }
            topicFrom(message.content, request.recentMessages)?.let { return it }
        }
        return null
    }

    private fun topicFrom(message: String, recent: List<MessageEntity>): ConversationTopic? {
        val value = message.lowercase().trim()
        if (value.isBlank()) return null
        val petName = Regex("(?i)\\b(?:dog|cat|pet) (?:is called|is named)\\s+([a-z][a-z'-]{1,30})")
            .find(recent.joinToString(" ") { it.content })
            ?.groupValues?.getOrNull(1)
            ?.replaceFirstChar(Char::uppercase)
        if (!petName.isNullOrBlank() && value.contains(petName.lowercase())) {
            val followUp = if (Regex("\\b(awake|sleep|night|up)\\b").containsMatchIn(value)) {
                "What was keeping $petName awake?"
            } else {
                "What happened with $petName?"
            }
            return ConversationTopic("pet", petName, followUp, setOf(petName.lowercase()))
        }
        return when {
            Regex("\\b(dinner|lunch|breakfast|meal|food|eat|ate|cooking)\\b").containsMatchIn(value) ->
                ConversationTopic("food", "dinner", "What did you have?", setOf("food", "dinner", "meal", "eat"))
            Regex("\\b(work|working|job|office|shift|meeting)\\b").containsMatchIn(value) ->
                ConversationTopic("work", "work", "Is it busy or fairly calm?", setOf("work", "shift", "busy", "quiet"))
            Regex("\\b(good mood|happy|excited|great mood)\\b").containsMatchIn(value) ->
                ConversationTopic("mood", "your good mood", "What put you in that mood?", setOf("mood", "happy", "good"))
            Regex("\\b(tired|exhausted|drained|sleepy|long day)\\b").containsMatchIn(value) ->
                ConversationTopic("tired", "how tired you feel", "Are you getting a chance to rest?", setOf("tired", "exhausted", "rest"))
            Regex("\\b(tomorrow|weekend|tonight|plan|plans|holiday|vacation)\\b").containsMatchIn(value) ->
                ConversationTopic("plans", "your plans", "What are you thinking of doing?", setOf("tomorrow", "plans", "weekend", "tonight"))
            Regex("\\b(music|song|album|playlist|concert|gig)\\b").containsMatchIn(value) ->
                ConversationTopic("music", "the music", "What have you been listening to?", setOf("music", "song", "playlist", "concert"))
            Regex("\\b(game|gaming|play|playing|xbox|playstation)\\b").containsMatchIn(value) ->
                ConversationTopic("gaming", "the game", "What are you playing?", setOf("game", "gaming", "play"))
            else -> null
        }
    }

    private fun materialize(template: String, routine: RoutineState, userName: String): String =
        template
            .replace("{activity}", routine.currentActivity)
            .replace("{next_activity}", routine.nextActivity)
            .replace("{job}", routine.job)
            .replace("{time_of_day}", routine.timeOfDay)
            .replace("{user_name}", userName.ifBlank { "you" })

    private fun memoryCandidates(message: String): List<MemoryCandidate> {
        val text = message.trim().replace(Regex("\\s+"), " ")
        if (text.length < 5) return emptyList()
        val candidates = mutableListOf<MemoryCandidate>()
        Regex("(?i)\\b(i work as|my job is|i'm a|im a|i am a)\\s+([^.!?]{3,60})").find(text)?.let {
            candidates += MemoryCandidate("fact", "User's work or job: ${it.groupValues[2].trim()}.", 8)
        }
        Regex("(?i)\\b(i like|i love|my favourite is|my favorite is)\\s+([^.!?]{3,70})").find(text)?.let {
            candidates += MemoryCandidate("preference", "User likes ${it.groupValues.last().trim()}.", 7)
        }
        Regex("(?i)\\b(i don't like|i do not like|i hate|never call me)\\s+([^.!?]{2,70})").find(text)?.let {
            candidates += MemoryCandidate("boundary", "User dislikes or set a boundary about ${it.groupValues.last().trim()}.", 9)
        }
        Regex("(?i)\\b(my (?:dog|cat|pet|friend|partner|brother|sister|mum|mom|dad) (?:is called|is named)\\s+[^.!?]{2,40})").find(text)?.let {
            candidates += MemoryCandidate("fact", "User shared: ${it.groupValues[1].trim()}.", 7)
        }
        if (Regex("(?i)\\b(tomorrow|next week|this weekend|next month)\\b").containsMatchIn(text)) {
            candidates += MemoryCandidate("event", "User has a future plan: ${text.take(120)}", 6)
        }
        if (Regex("(?i)\\bat work\\b|\\bworking\\b").containsMatchIn(text)) {
            candidates += MemoryCandidate("event", "User mentioned being at work today.", 3)
        }
        return candidates.take(4)
    }

    private fun recentChat(messages: List<MessageEntity>, personaName: String): String =
        messages.takeLast(12).joinToString("\n") { message ->
            "${if (message.senderType == "USER") "User" else personaName}: ${message.content.take(140)}"
        }

    private fun isRepetitive(bubbles: List<String>, recentChat: String): Boolean {
        if (recentChat.isBlank()) return false
        val recentLines = recentChat.lines().map { it.substringAfter(':').normalizedWords() }
        val candidates = bubbles + bubbles.joinToString(" ")
        return candidates.any { bubble ->
            val words = bubble.normalizedWords()
            words.size >= 4 && recentLines.any { recent ->
                val union = words union recent
                union.isNotEmpty() && (words intersect recent).size.toDouble() / union.size >= 0.72
            }
        }
    }

    private fun preservesReplyPlan(bubbles: List<String>, turn: DirectedTurn): Boolean {
        val candidate = bubbles.joinToString(" ")
        val candidateWords = candidate.semanticWords()
        val candidateSignals = candidate.signalWords()
        val seedWords = turn.seed.semanticWords()
        if (candidateWords.isEmpty() || seedWords.isEmpty()) return false
        val signalsPresent = turn.requiredSignalGroups.all { group ->
            group.map { it.canonicalMeaningWord() }.any(candidateSignals::contains)
        }
        if (!signalsPresent) return false
        val minimumOverlap = if (seedWords.size <= 4) 1 else 2
        return (candidateWords intersect seedWords).size >= minimumOverlap
    }

    private fun String.cleanupModelText(): String = trim()
        .removePrefix("```json")
        .removePrefix("```text")
        .removePrefix("```")
        .removeSuffix("```")
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace(Regex("(?is)^\\s*thinking\\s*[:\\-].*?(?:\\n\\s*answer\\s*[:\\-]|\\z)"), "")
        .trim()

    private fun String.toBubbles(): List<String> {
        if (isBlank()) return emptyList()
        val lines = lines()
            .map { it.trim().trim('-', '*', '"').trim() }
            .filter { it.isNotBlank() }
        val parts = if (lines.size > 1) {
            lines
        } else {
            split(Regex("(?<=[.!?])\\s+(?=[A-Z])"))
        }
        return parts.map { it.trim().take(220).trim() }.filter { it.isNotBlank() }.take(3)
    }

    private fun String.isInvalidModelOutput(): Boolean =
        contains(Regex("(?i)^(assistant|reply|message|output|answer|rewrite)\\s*[:\\-]")) ||
            contains(Regex("(?i)\\b(json|selected seed|response pattern|system prompt|language model|as an ai)\\b")) ||
            contains(Regex("(?i)\\b(you said|the part that stands out is|what happened with it today|give me one more detail so i answer)\\b")) ||
            contains(Regex("(?i)(^|\\s)(good question\\.?|i get what you mean\\.?|i am following\\.?)($|\\s)")) ||
            contains(Regex("(?i)\\b(how do you feel about it|what is the part that matters most)\\b")) ||
            contains("{") || contains("}") ||
            contains(Regex("(?i)\\b(I am physically there|I came over|I can actually visit)\\b"))

    private fun String.isInvalidForTurn(turn: DirectedTurn): Boolean =
        (turn.intent == "ask_repeat_persona_activity" &&
            contains(Regex("(?i)\\b(give me one more detail|which part|do you mean|i missed a step|good question)\\b"))) ||
            (turn.intent in setOf("user_finished_meal", "user_food") &&
                contains(Regex("(?i)\\b(i had|i ate|my dinner|my lunch|my breakfast)\\b"))) ||
            (turn.intent == "user_tired" &&
                contains(Regex("(?i)\\b(i am|i'm|im) (tired|exhausted|drained)\\b"))) ||
            contains(Regex("(?i)\\b(hey|hi) ${Regex.escape(turn.personaName)}\\b")) ||
            contains(Regex("(?i)\\b${Regex.escape(turn.personaName)} (is|was|has|had|said|posted|works)\\b"))

    private fun String.wordCount(): Int = split(Regex("\\s+")).count { it.isNotBlank() }

    private fun String.normalizedWords(): Set<String> = lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 2 }
        .toSet()

    private fun String.semanticWords(): Set<String> = lowercase()
        .replace(Regex("[^a-z0-9' ]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim('\'').canonicalMeaningWord() }
        .filter { it.length > 2 && it !in MEANING_STOP_WORDS }
        .toSet()

    private fun String.signalWords(): Set<String> = lowercase()
        .replace(Regex("[^a-z0-9' ]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim('\'').canonicalMeaningWord() }
        .filter { it.length > 1 }
        .toSet()

    private fun String.withNaturalEnding(): String {
        val value = trim()
        if (value.isBlank() || value.last() in ".!?") return value
        return "$value."
    }
}

private data class ConversationTopic(
    val key: String,
    val reference: String,
    val followUp: String,
    val signals: Set<String>
)

private fun String.canonicalMeaningWord(): String = when {
    startsWith("dinn") || this in setOf("meal", "meals", "food", "ate", "eating") -> "food"
    startsWith("work") || this in setOf("job", "office") -> "work"
    startsWith("exhaust") || startsWith("drain") -> "tired"
    startsWith("sleep") -> "sleep"
    startsWith("plan") -> "plans"
    startsWith("game") || startsWith("play") -> "game"
    startsWith("ask") -> "asking"
    this == "mean" || this == "means" -> "meant"
    startsWith("post") -> "posting"
    startsWith("edit") -> "editing"
    else -> this
}

private val MEANING_STOP_WORDS = setOf(
    "the", "and", "that", "this", "with", "from", "your", "you", "yours", "our", "ours",
    "was", "were", "are", "have", "has", "had", "did", "does", "what", "when", "where",
    "how", "why", "who", "for", "but", "not", "just", "too", "very", "really", "still",
    "into", "about", "after", "before", "then", "than", "there", "here", "would", "could",
    "should", "will", "can", "want", "tell", "know", "think", "feel", "feels", "felt",
    "message", "chat", "reply", "said", "saying", "little", "more", "good", "okay", "like",
    "been", "being", "myself", "yourself", "they", "them", "their", "she", "her", "hers",
    "him", "his", "its", "it's", "i'm", "im", "ive", "i've", "mine"
)

data class DirectedTurn(
    val personaName: String,
    val personaStyle: String,
    val personaPersonality: String,
    val personaInterests: String,
    val affectionStyle: String,
    val humourStyle: String,
    val personaBoundaries: String,
    val personaPrompt: String,
    val fictionalRoutine: String,
    val currentActivity: String,
    val userName: String,
    val preferredTone: String,
    val relationshipStage: String,
    val affinityScore: Int,
    val mood: String,
    val intent: String,
    val conversationGoal: String,
    val pattern: String,
    val seed: String,
    val fallbackSeeds: List<String>,
    val requiredSignalGroups: List<Set<String>>,
    val latestUserMessage: String,
    val recentChat: String,
    val conversationSummary: String,
    val relevantMemories: String,
    val emotion: String,
    val animation: String,
    val memoryCandidates: List<MemoryCandidate>
)

data class ValidatedReply(
    val messages: List<String>,
    val mood: String,
    val emotion: String,
    val animation: String,
    val memoryCandidates: List<MemoryCandidate>,
    val usedModelRewrite: Boolean
) {
    fun toJson(): String = JSONObject()
        .put("messages", JSONArray(messages))
        .put("mood", mood)
        .put("emotion", emotion)
        .put("animation", animation)
        .put(
            "memory_candidates",
            JSONArray(memoryCandidates.map { candidate ->
                JSONObject()
                    .put("type", candidate.type)
                    .put("content", candidate.content)
                    .put("importance", candidate.importance)
                    .put("confidence", candidate.confidence)
                    .put("isSensitive", candidate.isSensitive)
            })
        )
        .toString()
}

private data class ResponseBank(
    val fallbackReplies: List<String>,
    val patterns: Map<String, String>,
    val proactiveMessages: List<ProactiveSeed>,
    val suggestedResponses: List<ResponseSeed>
) {
    fun pattern(id: String?): String? = patterns[id]

    companion object {
        fun fromJson(raw: String): ResponseBank {
            val root = JSONObject(raw)
            return ResponseBank(
                fallbackReplies = root.optJSONArray("fallback_replies").orEmptyStrings()
                    .ifEmpty { listOf("Tell me a little more - I want to understand.") },
                patterns = root.optJSONArray("response_patterns").orEmptyObjects().associate {
                    it.optString("id") to it.optString("shape")
                },
                proactiveMessages = root.optJSONArray("proactive_messages").orEmptyObjects().map {
                    ProactiveSeed(
                        id = it.optString("id"),
                        timeWindow = it.optString("time_window"),
                        moods = it.optJSONArray("mood").orEmptyStrings(),
                        relationshipStages = it.optJSONArray("relationship_stage").orEmptyStrings(),
                        examples = it.optJSONArray("examples").orEmptyStrings()
                            .ifEmpty { listOfNotNull(it.optString("seed").takeIf(String::isNotBlank)) },
                        animation = it.optString("animation", "soft_smile")
                    )
                },
                suggestedResponses = root.optJSONArray("suggested_responses").orEmptyObjects().map {
                    ResponseSeed(
                        intent = it.optString("intent"),
                        conversationGoal = it.optString("conversation_goal"),
                        emotion = it.optString("emotion", "curious"),
                        animation = it.optString("animation", "soft_smile"),
                        patterns = it.optJSONArray("patterns").orEmptyStrings(),
                        examples = it.optJSONArray("examples").orEmptyStrings()
                    )
                }
            )
        }
    }
}

private data class ResponseSeed(
    val intent: String,
    val conversationGoal: String,
    val emotion: String,
    val animation: String,
    val patterns: List<String>,
    val examples: List<String>
)

private data class ProactiveSeed(
    val id: String,
    val timeWindow: String,
    val moods: List<String>,
    val relationshipStages: List<String>,
    val examples: List<String>,
    val animation: String
)

private fun JSONArray?.orEmptyStrings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

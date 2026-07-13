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
        val alternatives = orderedExamples(
            examples = response.examples,
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
        appendLine("You are ${turn.personaName}, a fictional adult AI companion in Heartline AI.")
        appendLine(turn.personaPrompt)
        appendLine("Personality: ${turn.personaPersonality}")
        appendLine("Interests: ${turn.personaInterests}")
        appendLine("Chat style: ${turn.personaStyle}")
        appendLine("Affection style: ${turn.affectionStyle}")
        appendLine("Humour style: ${turn.humourStyle}")
        appendLine("Boundaries: ${turn.personaBoundaries}")
        appendLine("Fictional day context: ${turn.fictionalRoutine}")
        appendLine("Mood: ${turn.mood}")
        appendLine("Relationship: ${turn.relationshipStage}, affinity ${turn.affinityScore}/100")
        appendLine("The user's preferred tone is ${turn.preferredTone}.")
        if (turn.conversationSummary.isNotBlank()) {
            appendLine("Earlier conversation summary: ${turn.conversationSummary.take(500)}")
        }
        if (turn.relevantMemories.isNotBlank()) appendLine("Relevant user memories: ${turn.relevantMemories}")
        if (turn.recentChat.isNotBlank()) {
            appendLine("Recent chat, oldest to newest:")
            appendLine(turn.recentChat)
        }
        if (turn.latestUserMessage.isNotBlank()) appendLine("User just said: ${turn.latestUserMessage}")
        appendLine("Conversation goal: ${turn.conversationGoal}")
        appendLine("Suggested shape: ${turn.pattern}")
        appendLine("High-quality starting line: ${turn.seed}")
        appendLine("Rewrite that starting line so it sounds unmistakably like ${turn.personaName} and directly fits the chat.")
        appendLine("Return only 1 to 3 short message bubbles, one bubble per line, under 40 words total.")
        appendLine("Do not output analysis, JSON, labels, instructions, or quotes around the reply.")
        appendLine("Do not repeat a recent line or quote the user back.")
        appendLine("You may naturally mention your fictional job or current activity, but never claim physical presence with the user.")
        appendLine("Stay warm, specific, safe, and conversational. Do not use customer-support language.")
    }

    fun validate(raw: String, turn: DirectedTurn): ValidatedReply {
        val candidate = raw.cleanupModelText()
            .replace(Regex("(?i)^${Regex.escape(turn.personaName)}\\s*[:\\-]\\s*"), "")
            .trim()
        val generated = candidate.toBubbles()
            .filterNot { it.isInvalidModelOutput() }
            .filter { it.wordCount() <= 30 }
            .take(3)
        val generatedIsUsable = generated.isNotEmpty() &&
            generated.sumOf { it.wordCount() } <= 40 &&
            !isRepetitive(generated, turn.recentChat)
        val finalBubbles = if (generatedIsUsable) {
            generated
        } else {
            (listOf(turn.seed) + turn.fallbackSeeds)
                .asSequence()
                .map { it.cleanupModelText().toBubbles() }
                .firstOrNull { it.isNotEmpty() && !isRepetitive(it, turn.recentChat) }
                ?: listOf("Tell me a little more - I want to understand.")
        }
        return ValidatedReply(
            messages = finalBubbles.take(3).map { it.withNaturalEnding() },
            mood = turn.mood,
            emotion = turn.emotion,
            animation = turn.animation,
            memoryCandidates = turn.memoryCandidates
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
            Regex("\\b(what are you doing|what you doing|what are you up to|what you up to|your day|how was your day)\\b").containsMatchIn(value) -> "ask_persona_day"
            Regex("\\b(what do you do|your job|where do you work|work do you do)\\b").containsMatchIn(value) -> "ask_persona_job"
            Regex("\\b(work|working|job|office|shift|at work|meeting)\\b").containsMatchIn(value) -> "user_at_work"
            Regex("\\b(tired|exhausted|drained|sleepy|worn out|knackered)\\b").containsMatchIn(value) -> "user_tired"
            Regex("\\b(sad|upset|lonely|anxious|stressed|crying|depressed|awful)\\b").containsMatchIn(value) -> "user_sad"
            Regex("\\b(happy|excited|great|amazing|good news|glad|brilliant)\\b").containsMatchIn(value) -> "user_happy"
            Regex("\\b(busy|no time|later|can't talk|cant talk|swamped)\\b").containsMatchIn(value) -> "user_busy"
            Regex("\\b(bored|boring|nothing to do)\\b").containsMatchIn(value) -> "user_bored"
            Regex("\\b(hungry|eating|dinner|lunch|breakfast|food|cooking)\\b").containsMatchIn(value) -> "user_food"
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
            contains(Regex("(?i)\\b(you said|the part that stands out is|what happened with it today)\\b")) ||
            contains("{") || contains("}") ||
            contains(Regex("(?i)\\b(I am physically there|I came over|I can actually visit)\\b"))

    private fun String.wordCount(): Int = split(Regex("\\s+")).count { it.isNotBlank() }

    private fun String.normalizedWords(): Set<String> = lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length > 2 }
        .toSet()

    private fun String.withNaturalEnding(): String {
        val value = trim()
        if (value.isBlank() || value.last() in ".!?") return value
        return "$value."
    }
}

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
    val memoryCandidates: List<MemoryCandidate>
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

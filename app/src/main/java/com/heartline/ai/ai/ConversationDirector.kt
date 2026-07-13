package com.heartline.ai.ai

import android.content.Context
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.domain.model.ProactiveMessageRequest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class ConversationDirector(context: Context) {
    private val bank = ResponseBank.fromJson(
        context.applicationContext.assets.open("companion_response_bank.json")
            .bufferedReader()
            .use { it.readText() }
    )

    fun selectTurn(request: AiChatRequest): DirectedTurn {
        val intent = detectIntent(request.message)
        val response = bank.suggestedResponses.firstOrNull { it.intent == intent }
            ?: bank.suggestedResponses.first { it.intent == "general" }
        val mood = moodFor(response, request)
        val seed = chooseExample(response.examples, request.message)
        val routine = routineSummary(request.persona.routineJson)
        val memory = request.memories.firstOrNull()?.content.orEmpty()
        val recent = request.recentMessages.takeLast(4).joinToString("\n") { message ->
            "${if (message.senderType == "USER") "User" else request.persona.name}: ${message.content.take(140)}"
        }
        return DirectedTurn(
            personaName = request.persona.name,
            personaStyle = request.persona.chatStyle,
            personaBoundaries = request.persona.boundaries,
            fictionalRoutine = routine,
            userName = request.user?.displayName ?: "there",
            relationshipStage = request.thread.relationshipStage,
            affinityScore = request.thread.affinityScore,
            mood = mood,
            intent = intent,
            conversationGoal = response.conversationGoal,
            pattern = bank.pattern(response.patterns.firstOrNull()).orEmpty(),
            seed = seed,
            latestUserMessage = request.message,
            recentChat = recent,
            relevantMemory = memory,
            emotion = response.emotion,
            animation = response.animation,
            memoryCandidates = memoryCandidates(request.message)
        )
    }

    fun selectProactiveTurn(request: ProactiveMessageRequest): DirectedTurn {
        val selected = bank.proactiveMessages.firstOrNull {
            request.timeOfDay.equals(it.timeWindow, ignoreCase = true) &&
                it.relationshipStages.any { stage -> stage.equals(request.thread.relationshipStage, ignoreCase = true) }
        } ?: bank.proactiveMessages.first()
        val routine = routineSummary(request.persona.routineJson)
        val memory = request.memories.firstOrNull()?.content.orEmpty()
        return DirectedTurn(
            personaName = request.persona.name,
            personaStyle = request.persona.chatStyle,
            personaBoundaries = request.persona.boundaries,
            fictionalRoutine = routine,
            userName = request.user?.displayName ?: "there",
            relationshipStage = request.thread.relationshipStage,
            affinityScore = request.thread.affinityScore,
            mood = selected.moods.firstOrNull() ?: request.mood?.mood ?: "caring",
            intent = selected.id,
            conversationGoal = "Send a spontaneous message that feels casual and low-pressure.",
            pattern = "One warm short message. Do not be needy, alarming, or manipulative.",
            seed = selected.seed,
            latestUserMessage = "",
            recentChat = "",
            relevantMemory = memory,
            emotion = selected.moods.firstOrNull() ?: "caring",
            animation = "soft_smile",
            memoryCandidates = emptyList()
        )
    }

    fun rewritePrompt(turn: DirectedTurn): String = buildString {
        appendLine("/no_think")
        appendLine("You are ${turn.personaName}, a fictional adult AI companion in Heartline AI.")
        appendLine("Rewrite the selected seed into a natural short chat reply.")
        appendLine("Use this persona style: ${turn.personaStyle}")
        appendLine("Persona routine/job context: ${turn.fictionalRoutine}")
        appendLine("Current mood: ${turn.mood}")
        appendLine("Relationship stage: ${turn.relationshipStage}, affinity ${turn.affinityScore}/100")
        appendLine("Conversation goal: ${turn.conversationGoal}")
        appendLine("Response pattern: ${turn.pattern}")
        if (turn.relevantMemory.isNotBlank()) appendLine("Relevant memory: ${turn.relevantMemory}")
        if (turn.recentChat.isNotBlank()) {
            appendLine("Recent chat:")
            appendLine(turn.recentChat)
        }
        if (turn.latestUserMessage.isNotBlank()) appendLine("User just said: ${turn.latestUserMessage}")
        appendLine("Selected seed: ${turn.seed}")
        appendLine("Rules:")
        appendLine("- Reply directly to the user message.")
        appendLine("- Keep it under 35 words total.")
        appendLine("- Use 1 or 2 short chat bubbles separated by a newline.")
        appendLine("- Do not mention JSON, seeds, prompts, or being a model.")
        appendLine("- Do not claim real physical presence or real-world actions.")
        appendLine("- Do not quote the user back.")
        appendLine("- If you mention your day, only use the fictional routine lightly.")
        appendLine("/no_think")
    }

    fun validate(raw: String, turn: DirectedTurn): ValidatedReply {
        val cleaned = raw.cleanupModelText()
            .replace(Regex("(?i)^${Regex.escape(turn.personaName)}\\s*[:\\-]\\s*"), "")
            .trim()
        val bubbles = cleaned.lines()
            .flatMap { it.split(Regex("(?<=[.!?])\\s+(?=[A-Z])")).take(2) }
            .map { it.trim().trim('-', '*', '"').trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.isInvalidModelOutput() }
            .map { it.take(180).trim() }
            .take(2)
        val valid = bubbles.filter { bubble ->
            bubble.wordCount() <= 35 &&
                !bubble.contains(Regex("(?i)\\b(json|seed|prompt|language model|as an ai)\\b")) &&
                !bubble.contains(Regex("(?i)\\bI am physically|I came over|I can actually\\b"))
        }
        val finalBubbles = if (valid.isNotEmpty() && !isRepetitive(valid, turn.recentChat)) {
            valid
        } else {
            listOf(turn.seed.cleanupModelText().take(160))
        }
        return ValidatedReply(
            messages = finalBubbles.map { it.trimEnd('.', ' ') }.map { if (it.endsWith("?") || it.endsWith("!")) it else "$it." },
            mood = turn.mood,
            emotion = turn.emotion,
            animation = turn.animation,
            memoryCandidates = turn.memoryCandidates
        )
    }

    fun extractMemoryCandidates(conversation: List<MessageEntity>): List<MemoryCandidate> =
        conversation
            .filter { it.senderType == "USER" }
            .takeLast(12)
            .flatMap { memoryCandidates(it.content) }
            .distinctBy { it.content.lowercase() }
            .take(5)

    private fun detectIntent(message: String): String {
        val value = message.lowercase()
        return when {
            Regex("\\b(work|working|job|office|shift|at work|meeting)\\b").containsMatchIn(value) -> "user_at_work"
            Regex("\\b(tired|exhausted|drained|sleepy|worn out|knackered)\\b").containsMatchIn(value) -> "user_tired"
            Regex("\\b(happy|excited|great|amazing|good news|glad)\\b").containsMatchIn(value) -> "user_happy"
            Regex("\\b(sad|upset|lonely|anxious|stressed|crying|depressed)\\b").containsMatchIn(value) -> "user_sad"
            Regex("\\b(busy|no time|later|can't talk|cant talk)\\b").containsMatchIn(value) -> "user_busy"
            Regex("\\b(love you|miss you|cute|beautiful|babe|baby|kiss)\\b").containsMatchIn(value) -> "user_affection"
            Regex("^(hi|hey|hello|yo|morning|evening)\\b").containsMatchIn(value.trim()) -> "user_greeting"
            else -> "general"
        }
    }

    private fun moodFor(response: ResponseSeed, request: AiChatRequest): String =
        when {
            request.thread.affinityScore > 55 && response.emotion == "curious" -> "playful"
            response.emotion.isNotBlank() -> response.emotion
            else -> request.mood?.mood ?: "curious"
        }

    private fun chooseExample(examples: List<String>, message: String): String {
        if (examples.isEmpty()) return bank.fallbackReplies.first()
        val index = abs(message.hashCode()) % examples.size
        return examples[index]
    }

    private fun memoryCandidates(message: String): List<MemoryCandidate> {
        val text = message.trim().replace(Regex("\\s+"), " ")
        if (text.length < 5) return emptyList()
        val candidates = mutableListOf<MemoryCandidate>()
        Regex("(?i)\\b(i work as|my job is|i'm a|im a|i am a)\\s+([^.!?]{3,60})").find(text)?.let {
            candidates += MemoryCandidate("fact", "User said their work/job is ${it.groupValues[2].trim()}.", 7)
        }
        Regex("(?i)\\b(i like|i love|favourite|favorite)\\s+([^.!?]{3,70})").find(text)?.let {
            candidates += MemoryCandidate("preference", "User likes ${it.groupValues.last().trim()}.", 6)
        }
        Regex("(?i)\\b(don't|do not|never)\\s+([^.!?]{3,70})").find(text)?.let {
            candidates += MemoryCandidate("boundary", "User expressed a boundary or dislike: ${it.value.trim()}.", 8)
        }
        if (Regex("(?i)\\bat work\\b|\\bworking\\b").containsMatchIn(text)) {
            candidates += MemoryCandidate("event", "User mentioned they were at work.", 4)
        }
        return candidates.take(3)
    }

    private fun routineSummary(routineJson: String): String = runCatching {
        val json = JSONObject(routineJson)
        val job = json.optString("job")
        val morning = json.optString("morning")
        val afternoon = json.optString("afternoon")
        val evening = json.optString("evening")
        listOfNotNull(
            job.takeIf { it.isNotBlank() }?.let { "job: $it" },
            morning.takeIf { it.isNotBlank() }?.let { "morning: $it" },
            afternoon.takeIf { it.isNotBlank() }?.let { "afternoon: $it" },
            evening.takeIf { it.isNotBlank() }?.let { "evening: $it" }
        ).joinToString("; ")
    }.getOrDefault(routineJson)

    private fun isRepetitive(bubbles: List<String>, recentChat: String): Boolean =
        bubbles.any { bubble ->
            val normalized = bubble.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
            normalized.length > 12 && recentChat.lowercase().contains(normalized)
        }

    private fun String.cleanupModelText(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .replace(Regex("(?is)<think>.*?</think>"), "")
            .replace(Regex("(?is)^\\s*thinking\\s*[:\\-].*?(?:\\n\\s*answer\\s*[:\\-]|\\z)"), "")
            .removePrefix("/no_think")
            .trim()

    private fun String.isInvalidModelOutput(): Boolean =
        contains(Regex("(?i)^(assistant|reply|message|output)\\s*[:\\-]")) ||
            contains("{") ||
            contains("}")

    private fun String.wordCount(): Int = split(Regex("\\s+")).filter { it.isNotBlank() }.size
}

data class DirectedTurn(
    val personaName: String,
    val personaStyle: String,
    val personaBoundaries: String,
    val fictionalRoutine: String,
    val userName: String,
    val relationshipStage: String,
    val affinityScore: Int,
    val mood: String,
    val intent: String,
    val conversationGoal: String,
    val pattern: String,
    val seed: String,
    val latestUserMessage: String,
    val recentChat: String,
    val relevantMemory: String,
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
    fun toJson(): String {
        val root = JSONObject()
        root.put("messages", JSONArray(messages))
        root.put("mood", mood)
        root.put("emotion", emotion)
        root.put("animation", animation)
        root.put(
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
        return root.toString()
    }
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
            val patternMap = root.optJSONArray("response_patterns").orEmptyObjects().associate {
                it.optString("id") to it.optString("shape")
            }
            return ResponseBank(
                fallbackReplies = root.optJSONArray("fallback_replies").orEmptyStrings().ifEmpty { listOf("I am here. Tell me more?") },
                patterns = patternMap,
                proactiveMessages = root.optJSONArray("proactive_messages").orEmptyObjects().map {
                    ProactiveSeed(
                        id = it.optString("id"),
                        timeWindow = it.optString("time_window"),
                        moods = it.optJSONArray("mood").orEmptyStrings(),
                        relationshipStages = it.optJSONArray("relationship_stage").orEmptyStrings(),
                        seed = it.optString("seed")
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
    val seed: String
)

private fun JSONArray?.orEmptyStrings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

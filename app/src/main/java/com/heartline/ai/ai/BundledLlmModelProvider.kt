package com.heartline.ai.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.ConversationSummary
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.domain.model.ProactiveMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class BundledLlmModelProvider(
    context: Context,
    private val modelAssetManager: ModelAssetManager
) : AiModelProvider {
    private val appContext = context.applicationContext
    private val director = ConversationDirector(appContext)
    private val engineMutex = Mutex()
    private var engine: Engine? = null

    override fun generateReply(request: AiChatRequest): Flow<String> = flow {
        val turn = director.selectTurn(request)
        val rewritten = generateText(director.rewritePrompt(turn), temperature = 0.42)
        emit(director.validate(rewritten, turn).toJson())
    }.flowOn(Dispatchers.IO)

    override suspend fun generateProactiveMessage(request: ProactiveMessageRequest): String {
        val turn = director.selectProactiveTurn(request)
        val rewritten = generateText(director.rewritePrompt(turn), temperature = 0.5)
        return director.validate(rewritten, turn).toJson()
    }

    suspend fun preload() {
        getEngine()
    }

    override suspend fun extractMemories(conversation: List<MessageEntity>): List<MemoryCandidate> {
        return director.extractMemoryCandidates(conversation)
    }

    override suspend fun summarizeConversation(conversation: List<MessageEntity>): ConversationSummary {
        val first = conversation.firstOrNull()
        val last = conversation.lastOrNull()
        val raw = generateText(summaryPrompt(conversation), temperature = 0.2)
        return ConversationSummary(
            summary = raw.cleanupModelText(),
            startMessageId = first?.id.orEmpty(),
            endMessageId = last?.id.orEmpty()
        )
    }

    private suspend fun generateText(prompt: String, temperature: Double): String = withContext(Dispatchers.IO) {
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(BASE_SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(
                topK = 24,
                topP = 0.88,
                temperature = temperature,
                seed = System.currentTimeMillis().toInt()
            )
        )
        getEngine().createConversation(conversationConfig).use { conversation ->
            conversation.sendMessage(prompt).plainText().cleanupModelText()
        }
    }

    private suspend fun getEngine(): Engine {
        engine?.let { return it }
        return engineMutex.withLock {
            engine?.let { return@withLock it }
            val model = modelAssetManager.getReadyModelFile()
            val cache = File(appContext.cacheDir, "litertlm").apply { mkdirs() }
            Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)),
                    maxNumTokens = 1024,
                    cacheDir = cache.absolutePath
                )
            ).also {
                it.initialize()
                engine = it
            }
        }
    }

    private fun Message.plainText(): String =
        contents.contents.joinToString(separator = "") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> content.toString()
            }
        }

    private fun summaryPrompt(conversation: List<MessageEntity>): String = buildString {
        appendLine("Summarize this chat for future continuity.")
        appendLine("Keep it factual, concise, and do not invent details.")
        appendLine()
        conversation.takeLast(24).forEach { message ->
            appendLine("${message.senderType}: ${message.content}")
        }
    }

    private fun String.cleanupModelText(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

    private companion object {
        const val BASE_SYSTEM_INSTRUCTION =
            "Write short fictional companion chat replies. Be warm, natural, and safe."
    }
}

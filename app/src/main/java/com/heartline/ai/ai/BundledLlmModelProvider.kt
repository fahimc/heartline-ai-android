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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BundledLlmModelProvider(
    context: Context
) : AiModelProvider {
    private val appContext = context.applicationContext
    private val engineMutex = Mutex()
    private var engine: Engine? = null

    override fun generateReply(request: AiChatRequest): Flow<String> = flow {
        emit(generateText(PromptBuilder.chatPrompt(request), temperature = 0.74))
    }.flowOn(Dispatchers.IO)

    override suspend fun generateProactiveMessage(request: ProactiveMessageRequest): String =
        generateText(PromptBuilder.proactivePrompt(request), temperature = 0.82)

    override suspend fun extractMemories(conversation: List<MessageEntity>): List<MemoryCandidate> {
        if (conversation.size < 4) return emptyList()
        val raw = generateText(memoryExtractionPrompt(conversation), temperature = 0.2)
        return parseMemoryCandidates(raw)
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
                topK = 40,
                topP = 0.92,
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
            val model = copyBundledModelToFilesDir()
            val cache = File(appContext.cacheDir, "litertlm").apply { mkdirs() }
            Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = 4096,
                    cacheDir = cache.absolutePath
                )
            ).also {
                it.initialize()
                engine = it
            }
        }
    }

    private suspend fun copyBundledModelToFilesDir(): File = withContext(Dispatchers.IO) {
        val outputDir = File(appContext.filesDir, "models").apply { mkdirs() }
        val output = File(outputDir, MODEL_FILE_NAME)
        val expectedBytes = bundledAssetLength()
        if (output.exists() && expectedBytes > 0L && output.length() == expectedBytes) {
            return@withContext output
        }

        val partial = File(outputDir, "${MODEL_FILE_NAME}.copying")
        if (partial.exists()) partial.delete()
        runCatching {
            appContext.assets.open(MODEL_ASSET_PATH).use { input ->
                partial.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        }.getOrElse { error ->
            partial.delete()
            throw IllegalStateException(
                "Bundled LLM asset '$MODEL_ASSET_PATH' was not found. Run the Gradle build so the model is downloaded into assets.",
                error
            )
        }

        if (expectedBytes > 0L && partial.length() != expectedBytes) {
            val actualBytes = partial.length()
            partial.delete()
            throw IllegalStateException("Bundled LLM copy was $actualBytes bytes; expected $expectedBytes bytes.")
        }
        if (output.exists()) output.delete()
        check(partial.renameTo(output)) { "Could not prepare bundled LLM at ${output.absolutePath}." }
        output
    }

    private fun bundledAssetLength(): Long =
        runCatching {
            appContext.assets.openFd(MODEL_ASSET_PATH).use { it.length }
        }.getOrDefault(EXPECTED_MODEL_BYTES)

    private fun Message.plainText(): String =
        contents.contents.joinToString(separator = "") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> content.toString()
            }
        }

    private fun memoryExtractionPrompt(conversation: List<MessageEntity>): String = buildString {
        appendLine("Extract only durable memories from this Heartline AI chat.")
        appendLine("Do not invent facts. Ignore generic small talk.")
        appendLine("Return only a JSON array. Each item must include type, content, importance, confidence, and isSensitive.")
        appendLine("Valid types: preference, fact, event, relationship, boundary.")
        appendLine()
        conversation.takeLast(12).forEach { message ->
            appendLine("${message.senderType}: ${message.content}")
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

    private fun parseMemoryCandidates(raw: String): List<MemoryCandidate> {
        val array = raw.extractJsonArray() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val content = item.optString("content").trim()
            if (content.isBlank()) return@mapNotNull null
            MemoryCandidate(
                type = item.optString("type", "fact").ifBlank { "fact" },
                content = content,
                importance = item.optInt("importance", 5).coerceIn(1, 10),
                confidence = item.optDouble("confidence", 0.8).toFloat().coerceIn(0f, 1f),
                isSensitive = item.optBoolean("isSensitive", false)
            )
        }
    }

    private fun String.extractJsonArray(): JSONArray? = runCatching {
        val cleaned = cleanupModelText()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start == -1 || end <= start) null else JSONArray(cleaned.substring(start, end + 1))
    }.getOrNull()

    private fun String.cleanupModelText(): String =
        trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

    private companion object {
        const val MODEL_FILE_NAME = "OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm"
        const val MODEL_ASSET_PATH = "models/$MODEL_FILE_NAME"
        const val EXPECTED_MODEL_BYTES = 931_241_056L
        const val BASE_SYSTEM_INSTRUCTION =
            "You are the bundled on-device language model for Heartline AI. " +
                "Follow the user prompt exactly, stay fictional, respect boundaries, and return the requested format."
    }
}

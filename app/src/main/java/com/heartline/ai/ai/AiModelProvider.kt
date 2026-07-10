package com.heartline.ai.ai

import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.ConversationSummary
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.domain.model.ProactiveMessageRequest
import com.heartline.ai.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

interface AiModelProvider {
    fun generateReply(request: AiChatRequest): Flow<String>
    suspend fun generateProactiveMessage(request: ProactiveMessageRequest): String
    suspend fun extractMemories(conversation: List<MessageEntity>): List<MemoryCandidate>
    suspend fun summarizeConversation(conversation: List<MessageEntity>): ConversationSummary
}

interface EmbeddingProvider {
    suspend fun embed(text: String): FloatArray

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var aMag = 0f
        var bMag = 0f
        for (index in a.indices) {
            dot += a[index] * b[index]
            aMag += a[index] * a[index]
            bMag += b[index] * b[index]
        }
        return if (aMag == 0f || bMag == 0f) 0f else dot / kotlin.math.sqrt(aMag * bMag)
    }
}

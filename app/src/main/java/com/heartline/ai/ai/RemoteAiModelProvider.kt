package com.heartline.ai.ai

import com.heartline.ai.data.local.entities.MessageEntity
import com.heartline.ai.domain.model.AiChatRequest
import com.heartline.ai.domain.model.ConversationSummary
import com.heartline.ai.domain.model.MemoryCandidate
import com.heartline.ai.domain.model.ProactiveMessageRequest
import kotlinx.coroutines.flow.Flow

class RemoteAiModelProvider(
    private val fallback: AiModelProvider = MockAiModelProvider()
) : AiModelProvider {
    override fun generateReply(request: AiChatRequest): Flow<String> {
        PromptBuilder.chatPrompt(request)
        return fallback.generateReply(request)
    }

    override suspend fun generateProactiveMessage(request: ProactiveMessageRequest): String {
        PromptBuilder.proactivePrompt(request)
        return fallback.generateProactiveMessage(request)
    }

    override suspend fun extractMemories(conversation: List<MessageEntity>): List<MemoryCandidate> =
        fallback.extractMemories(conversation)

    override suspend fun summarizeConversation(conversation: List<MessageEntity>): ConversationSummary =
        fallback.summarizeConversation(conversation)
}

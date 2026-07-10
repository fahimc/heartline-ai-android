package com.heartline.ai.domain.usecase

import com.heartline.ai.data.local.entities.ChatThreadEntity
import com.heartline.ai.data.repository.AiRepository
import com.heartline.ai.data.repository.ChatRepository
import com.heartline.ai.data.repository.MemoryRepository

class ConnectPersonaUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(personaId: String): ChatThreadEntity =
        chatRepository.connectPersona(personaId)
}

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val aiRepository: AiRepository,
    private val memoryRepository: MemoryRepository
) {
    suspend operator fun invoke(thread: ChatThreadEntity, text: String) {
        chatRepository.addUserMessage(thread, text)
        val reply = aiRepository.generateReply(thread, text)
        reply.messages.forEach { chatRepository.addAiMessage(thread, it) }
        memoryRepository.saveMemoryCandidates(thread.personaId, reply.memoryCandidates)
    }
}

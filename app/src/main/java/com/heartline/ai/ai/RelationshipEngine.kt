package com.heartline.ai.ai

import com.heartline.ai.data.local.entities.ChatThreadEntity
import kotlin.math.min

class RelationshipEngine {
    fun afterUserMessage(thread: ChatThreadEntity): ChatThreadEntity {
        val newCount = thread.messageCount + 1
        val affinity = min(100, thread.affinityScore + if (newCount % 6 == 0) 4 else 1)
        return thread.copy(
            updatedAt = System.currentTimeMillis(),
            affinityScore = affinity,
            messageCount = newCount,
            relationshipStage = stageFor(affinity, newCount)
        )
    }

    private fun stageFor(affinity: Int, count: Int): String = when {
        affinity >= 75 && count >= 80 -> "Affectionate"
        affinity >= 55 && count >= 45 -> "Close"
        affinity >= 35 && count >= 20 -> "Comfortable"
        affinity >= 15 && count >= 8 -> "Getting to know you"
        else -> "New connection"
    }
}

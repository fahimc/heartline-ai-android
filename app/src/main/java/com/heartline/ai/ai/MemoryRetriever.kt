package com.heartline.ai.ai

import com.heartline.ai.data.local.entities.MemoryEntity

class MemoryRetriever {
    fun score(memory: MemoryEntity, query: String): Int {
        val lower = query.lowercase()
        val textScore = lower.split(" ")
            .filter { it.length > 2 }
            .count { token -> token in memory.content.lowercase() } * 3
        val pinned = if (memory.isPinned) 20 else 0
        val recency = if (System.currentTimeMillis() - memory.updatedAt < 7L * 24L * 60L * 60L * 1000L) 4 else 0
        return textScore + pinned + memory.importance + recency
    }
}

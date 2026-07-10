package com.heartline.ai.ai

import com.heartline.ai.domain.model.MemoryCandidate

class MemoryExtractor {
    fun mergeDuplicates(existing: List<String>, candidates: List<MemoryCandidate>): List<MemoryCandidate> =
        candidates.filterNot { candidate ->
            existing.any { it.equals(candidate.content, ignoreCase = true) }
        }
}

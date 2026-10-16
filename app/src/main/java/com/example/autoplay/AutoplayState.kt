package com.example.autoplay

import com.example.model.Song

enum class AutoplayStatus {
    IDLE,
    RECOMMENDING,
    RESOLVING,
    PLAYING,
    FAILED,
    DISABLED
}

/**
 * State representing the active status of the Autoplay Controller.
 */
data class AutoplayState(
    val status: AutoplayStatus = AutoplayStatus.IDLE,
    val currentCandidate: Song? = null,
    val candidateScore: Double? = null,
    val candidateExplanation: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val lastError: String? = null,
    val isAutoplayEnabled: Boolean = true
)

package com.example.recommendation

import com.example.model.Song

/**
 * Represents a scored and explained recommendation candidate.
 */
data class RecommendationCandidate(
    val song: Song,
    val score: Double,
    val explanation: String,
    val matchedSignals: List<String> = emptyList()
)

/**
 * Contextual state provided to the recommendation engine to rank candidates.
 */
data class RecommendationContext(
    val currentSong: Song?,
    val currentQueue: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val failedSongIds: Set<String> = emptySet(),
    val candidatePool: List<Song> = emptyList(),
    val excludedSongIds: Set<String> = emptySet()
)

/**
 * Configurable weights for recommendation ranking signals and penalties.
 */
data class RecommendationWeights(
    val sameArtistWeight: Double = 35.0,
    val collaborationArtistWeight: Double = 20.0,
    val favoriteArtistWeight: Double = 15.0,
    val historyArtistWeight: Double = 10.0,
    val titleKeywordWeight: Double = 20.0,
    val popularityWeight: Double = 10.0,
    val recencyPenaltyWeight: Double = 30.0,
    val artistRepetitionPenaltyWeight: Double = 25.0
)

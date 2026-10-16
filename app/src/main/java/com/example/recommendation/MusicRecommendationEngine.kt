package com.example.recommendation

import com.example.model.Song

/**
 * Pluggable and reusable recommendation engine interface.
 */
interface MusicRecommendationEngine {
    /**
     * Ranks a list of candidate songs using contextual listening signals and weights.
     * Excludes invalid, queued, and already played candidates, returning a sorted list
     * of candidates with relevance scores and human-readable explanations.
     */
    fun rankCandidates(
        candidates: List<Song>,
        context: RecommendationContext,
        weights: RecommendationWeights = RecommendationWeights()
    ): List<RecommendationCandidate>
}

package com.example.recommendation

import com.example.data.repository.MusicRepository
import com.example.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level recommendation provider responsible for sourcing candidate pools
 * from the app's existing repository (search, curated tracks, favorites, history)
 * and scoring them through the [MusicRecommendationEngine].
 */
class RecommendationProvider(
    private val repository: MusicRepository,
    private val engine: MusicRecommendationEngine = ContentBasedRecommendationEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getRecommendations(
        context: RecommendationContext,
        limit: Int = 10,
        weights: RecommendationWeights = RecommendationWeights()
    ): List<RecommendationCandidate> = withContext(ioDispatcher) {
        val candidatePool = mutableListOf<Song>()

        // 1. If explicit candidates were provided in context, include them
        if (context.candidatePool.isNotEmpty()) {
            candidatePool.addAll(context.candidatePool)
        }

        // 2. Fetch artist-based candidates from YouTube / NewPipe search
        val currentSong = context.currentSong
        if (currentSong != null && currentSong.artist.isNotBlank()) {
            try {
                val primaryArtist = currentSong.artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim()
                val searchQuery = primaryArtist?.ifBlank { currentSong.artist } ?: currentSong.artist
                val artistResults = repository.searchSongs(searchQuery)
                candidatePool.addAll(artistResults)
            } catch (_: Throwable) {
                // Network/search error handled gracefully; continue with curated and local pools
            }
        }

        // 3. Add curated catalog tracks
        try {
            candidatePool.addAll(repository.getCuratedSongs())
        } catch (_: Throwable) {}

        // 4. Add user's favorites and history to candidates pool for cross-matching
        candidatePool.addAll(context.favorites)
        candidatePool.addAll(context.history)

        // 5. Score, filter, and rank through the engine
        val ranked = engine.rankCandidates(candidatePool, context, weights)
        return@withContext ranked.take(limit)
    }
}

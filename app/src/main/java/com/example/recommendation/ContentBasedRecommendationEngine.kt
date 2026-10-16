package com.example.recommendation

import com.example.model.Song
import kotlin.math.log10
import kotlin.math.max

/**
 * Practical, content-based recommendation engine inspired by Spotify's
 * Autoplay Similar Content algorithm.
 *
 * Evaluates candidate songs across multiple contextual signals:
 * - Artist similarity (exact, featured/collaborator, user favorite artist, user history artist)
 * - Title keyword overlap (normalized tokens excluding stopwords)
 * - Popularity / engagement signal (log-scaled view counts)
 * - Recency penalty (heavy penalty for tracks played recently)
 * - Diversity penalty (penalty if the same artist was already played repeatedly)
 * - Exclusion filtering (current track, active queue, previously failed streams, duplicates)
 */
class ContentBasedRecommendationEngine : MusicRecommendationEngine {

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "it", "official", "video",
            "audio", "music", "song", "lyrics", "lyric", "hd", "4k",
            "remix", "feat", "ft", "full", "track", "version", "original",
            "live", "hq", "video song", "lyrics video"
        )

        private val ARTIST_DELIMITERS = Regex("[,&/]|\\b(?:feat|ft|vs|with|and)\\b", RegexOption.IGNORE_CASE)
    }

    override fun rankCandidates(
        candidates: List<Song>,
        context: RecommendationContext,
        weights: RecommendationWeights
    ): List<RecommendationCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val excludedIds = buildSet {
            context.currentSong?.id?.let { add(it) }
            addAll(context.failedSongIds)
            addAll(context.excludedSongIds)
            context.currentQueue.forEach { add(it.id) }
        }

        // Deduplicate candidates preserving the first instance
        val uniqueCandidates = candidates
            .filter { it.id !in excludedIds }
            .distinctBy { it.id }

        if (uniqueCandidates.isEmpty()) return emptyList()

        val currentSong = context.currentSong
        val currentPrimaryArtist = currentSong?.let { extractPrimaryArtist(it.artist) }
        val currentArtistTokens = currentSong?.let { extractArtistTokens(it.artist) } ?: emptySet()
        val currentTitleKeywords = currentSong?.let { extractKeywords(it.title) } ?: emptySet()

        // User preference signals
        val favoriteArtists = context.favorites.flatMap { extractArtistTokens(it.artist) }.toSet()
        val historyArtists = context.history.take(15).flatMap { extractArtistTokens(it.artist) }.toSet()

        // Check recent history for artist repetition (diversity safeguard)
        val recentArtistsHistory = context.history.take(2).map { extractPrimaryArtist(it.artist) }
        val recentQueueArtists = context.currentQueue.takeLast(2).map { extractPrimaryArtist(it.artist) }
        val lastTwoPlayedArtists = if (recentQueueArtists.size >= 2) recentQueueArtists else recentArtistsHistory
        val isArtistOverplayed = { artist: String ->
            lastTwoPlayedArtists.size >= 2 && lastTwoPlayedArtists.all { it.equals(artist, ignoreCase = true) }
        }

        val scored = uniqueCandidates.mapNotNull { candidate ->
            scoreCandidate(
                candidate = candidate,
                currentPrimaryArtist = currentPrimaryArtist,
                currentArtistTokens = currentArtistTokens,
                currentTitleKeywords = currentTitleKeywords,
                favoriteArtists = favoriteArtists,
                historyArtists = historyArtists,
                isArtistOverplayed = isArtistOverplayed,
                context = context,
                weights = weights
            )
        }

        return scored.sortedByDescending { it.score }
    }

    private fun scoreCandidate(
        candidate: Song,
        currentPrimaryArtist: String?,
        currentArtistTokens: Set<String>,
        currentTitleKeywords: Set<String>,
        favoriteArtists: Set<String>,
        historyArtists: Set<String>,
        isArtistOverplayed: (String) -> Boolean,
        context: RecommendationContext,
        weights: RecommendationWeights
    ): RecommendationCandidate {
        var score = 0.0
        val signals = mutableListOf<String>()

        val candidatePrimaryArtist = extractPrimaryArtist(candidate.artist)
        val candidateArtistTokens = extractArtistTokens(candidate.artist)
        val candidateTitleKeywords = extractKeywords(candidate.title)

        // 1. Artist Similarity
        if (currentPrimaryArtist != null && candidatePrimaryArtist.equals(currentPrimaryArtist, ignoreCase = true)) {
            score += weights.sameArtistWeight
            signals.add("Same artist ($candidatePrimaryArtist)")
        } else if (currentArtistTokens.isNotEmpty() && candidateArtistTokens.any { it in currentArtistTokens }) {
            score += weights.collaborationArtistWeight
            signals.add("Featured / Collaborator match")
        }

        // 2. Favorite Artist Affinity
        if (candidateArtistTokens.any { it in favoriteArtists }) {
            score += weights.favoriteArtistWeight
            signals.add("Favorite artist match")
        }

        // 3. History Artist Affinity
        if (candidateArtistTokens.any { it in historyArtists }) {
            score += weights.historyArtistWeight
            signals.add("Frequent in history")
        }

        // 4. Title / Theme Keyword Similarity
        if (currentTitleKeywords.isNotEmpty() && candidateTitleKeywords.isNotEmpty()) {
            val commonKeywords = currentTitleKeywords.intersect(candidateTitleKeywords)
            if (commonKeywords.isNotEmpty()) {
                val ratio = commonKeywords.size.toDouble() / max(1, currentTitleKeywords.size)
                val keywordScore = ratio * weights.titleKeywordWeight
                score += keywordScore
                signals.add("Shared themes: ${commonKeywords.take(2).joinToString()}")
            }
        }

        // 5. Popularity / Views signal
        if (candidate.views > 0) {
            val popularityNorm = (log10(candidate.views.toDouble()) / 9.0).coerceIn(0.0, 1.0)
            val popScore = popularityNorm * weights.popularityWeight
            score += popScore
            if (popScore > weights.popularityWeight * 0.7) {
                signals.add("Popular track")
            }
        }

        // 6. Recency Penalty (anti-repetition against recently played history)
        val historyIndex = context.history.indexOfFirst { it.id == candidate.id }
        if (historyIndex != -1) {
            val penalty = if (historyIndex < 5) {
                weights.recencyPenaltyWeight
            } else if (historyIndex < 15) {
                weights.recencyPenaltyWeight * 0.5
            } else {
                weights.recencyPenaltyWeight * 0.2
            }
            score -= penalty
            signals.add("Recently played (-${penalty.toInt()}pts)")
        }

        // 7. Diversity Penalty (anti-monotony if the same artist has played repeatedly)
        if (isArtistOverplayed(candidatePrimaryArtist)) {
            score -= weights.artistRepetitionPenaltyWeight
            signals.add("Artist variety penalty (-${weights.artistRepetitionPenaltyWeight.toInt()}pts)")
        }

        val finalScore = max(0.0, score)
        val explanation = if (signals.isNotEmpty()) {
            signals.joinToString(" • ")
        } else {
            "Suggested for you"
        }

        return RecommendationCandidate(
            song = candidate,
            score = finalScore,
            explanation = explanation,
            matchedSignals = signals
        )
    }

    private fun extractPrimaryArtist(artistStr: String): String {
        return artistStr.split(ARTIST_DELIMITERS).firstOrNull()?.trim()?.lowercase() ?: artistStr.trim().lowercase()
    }

    private fun extractArtistTokens(artistStr: String): Set<String> {
        return artistStr.split(ARTIST_DELIMITERS)
            .map { it.trim().lowercase() }
            .filter { it.length > 1 }
            .toSet()
    }

    private fun extractKeywords(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()
    }
}

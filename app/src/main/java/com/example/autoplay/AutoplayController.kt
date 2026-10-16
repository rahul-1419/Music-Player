package com.example.autoplay

import android.util.Log
import androidx.media3.common.Player
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.player.MusicPlayerManager
import com.example.recommendation.RecommendationContext
import com.example.recommendation.RecommendationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Autoplay Controller coordinates recommendation generation and continuous playback
 * with the existing Media3 player when the playback queue completes.
 */
class AutoplayController(
    private val playerManager: MusicPlayerManager,
    private val recommendationProvider: RecommendationProvider,
    private val repository: MusicRepository,
    private val preferences: AutoplayPreferences,
    private val scope: CoroutineScope,
    private val maxRetries: Int = 5,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    private val historyProvider: (() -> List<Song>)? = null,
    private val favoritesProvider: (() -> List<Song>)? = null
) {
    companion object {
        private const val TAG = "AutoplayController"
    }

    private val mutex = Mutex()
    private var activeAutoplayJob: Job? = null
    private var lastHandledQueueEndSongId: String? = null
    private val failedCandidateIds = mutableSetOf<String>()

    private val _autoplayState = MutableStateFlow(
        AutoplayState(
            isAutoplayEnabled = preferences.isAutoplayEnabled(),
            maxRetries = maxRetries
        )
    )
    val autoplayState: StateFlow<AutoplayState> = _autoplayState.asStateFlow()

    private val userRemovedSongIds = mutableSetOf<String>()

    fun isAutoplayEnabled(): Boolean = preferences.isAutoplayEnabled()

    fun setAutoplayEnabled(enabled: Boolean) {
        preferences.setAutoplayEnabled(enabled)
        _autoplayState.value = _autoplayState.value.copy(
            isAutoplayEnabled = enabled,
            status = if (!enabled && _autoplayState.value.status == AutoplayStatus.RECOMMENDING) {
                AutoplayStatus.DISABLED
            } else _autoplayState.value.status
        )
        if (!enabled) {
            cancelActiveAutoplay("Autoplay disabled by user")
        }
    }

    fun toggleAutoplay(): Boolean {
        val next = !preferences.isAutoplayEnabled()
        setAutoplayEnabled(next)
        return next
    }

    fun recordUserRemovedSong(songId: String) {
        userRemovedSongIds.add(songId)
    }

    fun clearUserRemovedSongs() {
        userRemovedSongIds.clear()
    }

    fun clearSession() {
        userRemovedSongIds.clear()
        cancelActiveAutoplay("New playback session started")
    }

    /**
     * Automatically pre-populates upcoming playback queue with recommended tracks
     * based on the currently playing song without interrupting playback.
     */
    fun generateInitialQueue(currentSong: Song, batchSize: Int = 3) {
        if (!preferences.isAutoplayEnabled()) return

        cancelActiveAutoplay("Generating queue for new song")
        activeAutoplayJob = scope.launch {
            mutex.withLock {
                val currentQueue = playerManager.playerState.value.queue
                val context = RecommendationContext(
                    currentSong = currentSong,
                    currentQueue = currentQueue,
                    history = historyProvider?.invoke() ?: emptyList(),
                    favorites = favoritesProvider?.invoke() ?: emptyList(),
                    failedSongIds = failedCandidateIds.toSet(),
                    excludedSongIds = userRemovedSongIds.toSet()
                )

                try {
                    val candidates = recommendationProvider.getRecommendations(context, limit = 10)
                    val validSongs = mutableListOf<Song>()
                    for (cand in candidates) {
                        if (validSongs.size >= batchSize) break
                        val sid = cand.song.id
                        if (sid in failedCandidateIds || sid in userRemovedSongIds) continue
                        val nowQueue = playerManager.playerState.value.queue
                        if (nowQueue.any { it.id == sid } || validSongs.any { it.id == sid }) continue

                        try {
                            val extracted = kotlinx.coroutines.withContext(ioDispatcher) {
                                repository.extractAudioStream(cand.song)
                            }
                            if (extracted.streamUrl.isNotBlank()) {
                                val resolvedSong = cand.song.copy(
                                    streamUrl = extracted.streamUrl,
                                    bitrate = extracted.bitrate,
                                    audioFormat = extracted.format,
                                    alternativeStreamUrls = extracted.backupUrls
                                )
                                validSongs.add(resolvedSong)
                            } else {
                                failedCandidateIds.add(sid)
                            }
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            failedCandidateIds.add(sid)
                        }
                    }
                    if (validSongs.isNotEmpty()) {
                        playerManager.appendSongsToQueue(validSongs)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Log.w(TAG, "Failed to auto-generate queue: ${t.message}")
                }
            }
        }
    }

    /**
     * Checks if the upcoming queue has fallen below the threshold and replenishes
     * recommendations if Autoplay is enabled.
     */
    fun replenishQueueIfNeeded(currentSong: Song, threshold: Int = 1, batchSize: Int = 3) {
        if (!preferences.isAutoplayEnabled()) return
        val upcoming = playerManager.getUpcomingQueue()
        if (upcoming.size <= threshold) {
            generateInitialQueue(currentSong, batchSize)
        }
    }

    /**
     * Triggered when the player reaches the end of the current queue.
     * Checks prerequisites (repeat modes, enabled state, idempotency) and
     * initiates recommendation resolution.
     */
    fun onQueueEnded(lastSong: Song) {
        val playerState = playerManager.playerState.value

        // Check 1: Respect repeat modes. Autoplay should NEVER trigger on Repeat-One or Repeat-All.
        if (playerState.repeatMode == Player.REPEAT_MODE_ONE || playerState.repeatMode == Player.REPEAT_MODE_ALL) {
            Log.d(TAG, "Ignoring queue end because repeatMode is active (${playerState.repeatMode})")
            return
        }

        // Check 2: Check if Autoplay is enabled in settings
        if (!preferences.isAutoplayEnabled()) {
            Log.d(TAG, "Queue ended, but Autoplay is disabled")
            _autoplayState.value = _autoplayState.value.copy(status = AutoplayStatus.DISABLED)
            return
        }

        // Check 3: Prevent duplicate triggers for the same queue-ending song
        if (lastHandledQueueEndSongId == lastSong.id && activeAutoplayJob?.isActive == true) {
            Log.d(TAG, "Autoplay already actively processing queue end for song: ${lastSong.id}")
            return
        }

        lastHandledQueueEndSongId = lastSong.id
        cancelActiveAutoplay("New queue end triggered")

        activeAutoplayJob = scope.launch {
            mutex.withLock {
                executeAutoplayPipeline(lastSong)
            }
        }
    }

    /**
     * Executes the recommendation search and stream extraction pipeline.
     */
    private suspend fun executeAutoplayPipeline(triggerSong: Song) {
        _autoplayState.value = _autoplayState.value.copy(
            status = AutoplayStatus.RECOMMENDING,
            retryCount = 0,
            lastError = null,
            currentCandidate = null
        )

        val currentQueue = playerManager.playerState.value.queue
        val historyList = historyProvider?.invoke() ?: emptyList()
        val favoritesList = favoritesProvider?.invoke() ?: emptyList()

        val context = RecommendationContext(
            currentSong = triggerSong,
            currentQueue = currentQueue,
            history = historyList,
            favorites = favoritesList,
            failedSongIds = failedCandidateIds.toSet()
        )

        val candidates = try {
            recommendationProvider.getRecommendations(context = context, limit = 10)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "Error fetching recommendations: ${t.message}", t)
            _autoplayState.value = _autoplayState.value.copy(
                status = AutoplayStatus.FAILED,
                lastError = "Failed to fetch recommendations: ${t.localizedMessage ?: "Unknown error"}"
            )
            return
        }

        if (candidates.isEmpty()) {
            Log.w(TAG, "No recommendation candidates found for song ${triggerSong.title}")
            _autoplayState.value = _autoplayState.value.copy(
                status = AutoplayStatus.FAILED,
                lastError = "No similar content available to autoplay"
            )
            return
        }

        // Attempt resolving candidate streams sequentially up to maxRetries
        var attemptCount = 0
        var successfullyStarted = false

        for (candidate in candidates) {
            if (attemptCount >= maxRetries) {
                Log.w(TAG, "Reached max autoplay retries ($maxRetries). Stopping pipeline.")
                break
            }

            if (candidate.song.id in failedCandidateIds) {
                continue
            }

            attemptCount++
            _autoplayState.value = _autoplayState.value.copy(
                status = AutoplayStatus.RESOLVING,
                currentCandidate = candidate.song,
                candidateScore = candidate.score,
                candidateExplanation = candidate.explanation,
                retryCount = attemptCount
            )

            try {
                Log.d(TAG, "Attempting autoplay resolution for candidate #$attemptCount: ${candidate.song.title} (${candidate.song.id})")
                val extracted = kotlinx.coroutines.withContext(ioDispatcher) {
                    repository.extractAudioStream(candidate.song)
                }

                if (extracted.streamUrl.isBlank()) {
                    Log.w(TAG, "Stream URL is blank for candidate: ${candidate.song.title}")
                    failedCandidateIds.add(candidate.song.id)
                    continue
                }

                // Verify that playback context hasn't changed or been cancelled during extraction
                val currentQueueNow = playerManager.playerState.value.queue
                if (currentQueueNow.any { it.id == candidate.song.id }) {
                    // Song was already added or playing
                    Log.d(TAG, "Candidate ${candidate.song.id} already in queue. Skipping.")
                    continue
                }

                val resolvedSong = candidate.song.copy(
                    streamUrl = extracted.streamUrl,
                    bitrate = extracted.bitrate,
                    audioFormat = extracted.format,
                    alternativeStreamUrls = extracted.backupUrls
                )

                // Append and play through existing player
                playerManager.appendAndPlaySong(
                    song = resolvedSong,
                    streamUrl = extracted.streamUrl,
                    bitrate = extracted.bitrate,
                    format = extracted.format,
                    backupUrls = extracted.backupUrls
                )

                repository.recordPlayed(resolvedSong)

                _autoplayState.value = _autoplayState.value.copy(
                    status = AutoplayStatus.PLAYING,
                    currentCandidate = resolvedSong,
                    candidateScore = candidate.score,
                    candidateExplanation = candidate.explanation,
                    retryCount = attemptCount,
                    lastError = null
                )

                successfullyStarted = true
                Log.i(TAG, "Autoplay successfully transitioned to: ${resolvedSong.title} (${candidate.explanation})")
                break

            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, "Extraction failed for candidate ${candidate.song.title}: ${t.message}")
                failedCandidateIds.add(candidate.song.id)
            }
        }

        if (!successfullyStarted) {
            _autoplayState.value = _autoplayState.value.copy(
                status = AutoplayStatus.FAILED,
                lastError = "Could not resolve audio streams for top $attemptCount candidates"
            )
        }
    }

    /**
     * Cancels any active recommendation or stream-resolution jobs.
     * Called when the user manually changes playback, selects a song, or clears queue.
     */
    fun cancelActiveAutoplay(reason: String = "Cancelled") {
        if (activeAutoplayJob?.isActive == true) {
            Log.d(TAG, "Cancelling active autoplay job: $reason")
            activeAutoplayJob?.cancel()
            activeAutoplayJob = null
        }
        if (_autoplayState.value.status == AutoplayStatus.RECOMMENDING ||
            _autoplayState.value.status == AutoplayStatus.RESOLVING
        ) {
            _autoplayState.value = _autoplayState.value.copy(
                status = AutoplayStatus.IDLE,
                currentCandidate = null
            )
        }
    }

    /**
     * Clears failed candidate cache.
     */
    fun clearFailedCandidates() {
        failedCandidateIds.clear()
    }
}

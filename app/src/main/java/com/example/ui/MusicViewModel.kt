package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StreamTuneApp
import com.example.model.Song
import com.example.player.MusicPlayerManager
import com.example.player.PlayerState
import com.example.autoplay.AutoplayController
import com.example.autoplay.AutoplayPreferences
import com.example.autoplay.AutoplayState
import com.example.recommendation.ContentBasedRecommendationEngine
import com.example.recommendation.RecommendationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val songs: List<Song>, val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

enum class NavigationTab {
    EXPLORE,
    SEARCH,
    FAVORITES,
    HISTORY
}

data class StreamResolutionState(
    val isResolving: Boolean = false,
    val songTitle: String? = null,
    val statusMessage: String? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StreamTuneApp
    private val repository = app.repository

    val playerManager = MusicPlayerManager(application, viewModelScope)
    val playerState: StateFlow<PlayerState> = playerManager.playerState

    val autoplayPreferences = AutoplayPreferences(application)
    val recommendationEngine = ContentBasedRecommendationEngine()
    val recommendationProvider = RecommendationProvider(repository, recommendationEngine)

    val autoplayController = AutoplayController(
        playerManager = playerManager,
        recommendationProvider = recommendationProvider,
        repository = repository,
        preferences = autoplayPreferences,
        scope = viewModelScope,
        historyProvider = { history.value },
        favoritesProvider = { favorites.value }
    )

    val autoplayState: StateFlow<AutoplayState> = autoplayController.autoplayState
    val isAutoplayEnabled: StateFlow<Boolean> = autoplayPreferences.isAutoplayEnabledFlow

    private val _currentTab = MutableStateFlow(NavigationTab.EXPLORE)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("Arijit Singh")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _streamResolution = MutableStateFlow(StreamResolutionState())
    val streamResolution: StateFlow<StreamResolutionState> = _streamResolution.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isQueueOpen = MutableStateFlow(false)
    val isQueueOpen: StateFlow<Boolean> = _isQueueOpen.asStateFlow()

    private val _showAudioInfoDialog = MutableStateFlow(false)
    val showAudioInfoDialog: StateFlow<Boolean> = _showAudioInfoDialog.asStateFlow()

    private val _curatedSongs = MutableStateFlow<List<Song>>(emptyList())
    val curatedSongs: StateFlow<List<Song>> = _curatedSongs.asStateFlow()

    val favorites: StateFlow<List<Song>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<Song>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<String>> = repository.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    init {
        // Player callbacks
        playerManager.onQueueEndedCallback = { lastSong ->
            autoplayController.onQueueEnded(lastSong)
        }

        playerManager.onTrackTransitionCallback = { nextSong ->
            playSong(nextSong, playerState.value.queue)
            autoplayController.replenishQueueIfNeeded(nextSong, threshold = 1, batchSize = 3)
        }

        playerManager.onErrorFallback = { failedSong, _ ->
            viewModelScope.launch {
                _streamResolution.value = StreamResolutionState(
                    isResolving = true,
                    songTitle = failedSong.title,
                    statusMessage = "Resolving resilient audio stream..."
                )
                try {
                    val extracted = repository.extractFallbackAudioStream(failedSong.id)
                    _streamResolution.value = StreamResolutionState(
                        isResolving = false,
                        songTitle = failedSong.title,
                        statusMessage = "Stream recovered (${extracted.format} ${extracted.bitrate ?: 128} kbps)"
                    )
                    val updatedSong = failedSong.copy(
                        streamUrl = extracted.streamUrl,
                        bitrate = extracted.bitrate,
                        audioFormat = extracted.format,
                        alternativeStreamUrls = extracted.backupUrls
                    )
                    playerManager.playSongWithStream(
                        song = updatedSong,
                        streamUrl = extracted.streamUrl,
                        bitrate = extracted.bitrate,
                        format = extracted.format,
                        queue = playerState.value.queue,
                        backupUrls = extracted.backupUrls
                    )
                } catch (e: Exception) {
                    _streamResolution.value = StreamResolutionState(
                        isResolving = false,
                        songTitle = failedSong.title,
                        statusMessage = "Recovery error: ${e.message}"
                    )
                }
            }
        }

        // Load initial curated tracks
        _curatedSongs.value = repository.getCuratedSongs()
        // Pre-run search for "Arijit Singh" as requested in prompt
        performSearch("Arijit Singh")
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String = _searchQuery.value) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _searchQuery.value = trimmed

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = SearchUiState.Loading
            repository.addRecentSearch(trimmed)
            try {
                val results = repository.searchSongs(trimmed)
                if (results.isEmpty()) {
                    _searchState.value = SearchUiState.Error("No tracks found for \"$trimmed\". Try another artist or song name.")
                } else {
                    _searchState.value = SearchUiState.Success(results, trimmed)
                }
            } catch (t: Throwable) {
                _searchState.value = SearchUiState.Error(t.localizedMessage ?: "Failed to load search results")
            }
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        // Cancel any active autoplay recommendations when user manually starts a track
        autoplayController.cancelActiveAutoplay("User manually started track ${song.title}")

        viewModelScope.launch {
            _streamResolution.value = StreamResolutionState(
                isResolving = true,
                songTitle = song.title,
                statusMessage = "Resolving audio stream with NewPipeExtractor..."
            )
            playerManager.setResolvingStream(true)

            try {
                val extracted = repository.extractAudioStream(song)
                _streamResolution.value = StreamResolutionState(
                    isResolving = false,
                    songTitle = song.title,
                    statusMessage = "Audio stream ready (${extracted.format} ${extracted.bitrate ?: 128} kbps)"
                )
                val updatedSong = song.copy(
                    streamUrl = extracted.streamUrl,
                    bitrate = extracted.bitrate,
                    audioFormat = extracted.format,
                    alternativeStreamUrls = extracted.backupUrls
                )
                playerManager.playSongWithStream(
                    song = updatedSong,
                    streamUrl = extracted.streamUrl,
                    bitrate = extracted.bitrate,
                    format = extracted.format,
                    queue = queue,
                    backupUrls = extracted.backupUrls
                )
                repository.recordPlayed(song)
            } catch (t: Throwable) {
                _streamResolution.value = StreamResolutionState(
                    isResolving = false,
                    songTitle = song.title,
                    statusMessage = "Extraction error: ${t.message}"
                )
                playerManager.setResolvingStream(false)
            }
        }
    }

    fun playNext() {
        val state = playerState.value
        if (state.queue.isEmpty()) return

        if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) {
            val nextIdx = (state.queueIndex + 1) % state.queue.size
            state.queue.getOrNull(nextIdx)?.let { playSong(it, state.queue) }
        } else if (state.queueIndex < state.queue.size - 1) {
            val nextSong = state.queue[state.queueIndex + 1]
            playSong(nextSong, state.queue)
        } else {
            // Reached the end of queue; if autoplay is active, request autoplay transition
            state.currentSong?.let {
                autoplayController.onQueueEnded(it)
            }
        }
    }

    fun playPrevious() {
        playerManager.playPreviousTrack { prevSong ->
            playSong(prevSong, playerState.value.queue)
        }
    }

    fun toggleAutoplay(): Boolean {
        return autoplayController.toggleAutoplay()
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        autoplayController.setAutoplayEnabled(enabled)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.id == song.id }
            repository.toggleFavorite(song, isFav)
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            repository.removeRecentSearch(query)
        }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun setQueueOpen(open: Boolean) {
        _isQueueOpen.value = open
    }

    fun setShowAudioInfoDialog(show: Boolean) {
        _showAudioInfoDialog.value = show
    }

    /**
     * Plays only the selected song from search results, clearing previous upcoming
     * queue, and starts generating recommendations for that song if autoplay is enabled.
     */
    fun playSongFromSearch(song: Song) {
        autoplayController.clearSession()
        playSong(song, listOf(song))
        if (isAutoplayEnabled.value) {
            autoplayController.generateInitialQueue(song, batchSize = 3)
        }
    }

    fun addSongToQueue(song: Song) {
        playerManager.appendSongToQueue(song)
    }

    fun removeQueuedSong(song: Song) {
        autoplayController.recordUserRemovedSong(song.id)
        playerManager.removeQueuedSongById(song.id)
    }

    fun moveQueuedSong(fromUpcomingIndex: Int, toUpcomingIndex: Int) {
        playerManager.reorderUpcomingQueue(fromUpcomingIndex, toUpcomingIndex)
    }

    fun clearUpcomingQueue() {
        playerManager.clearUpcomingQueue()
    }

    override fun onCleared() {
        super.onCleared()
        autoplayController.cancelActiveAutoplay("ViewModel cleared")
        playerManager.release()
    }
}

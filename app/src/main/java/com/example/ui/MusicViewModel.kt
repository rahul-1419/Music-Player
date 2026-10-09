package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StreamTuneApp
import com.example.model.Song
import com.example.player.MusicPlayerManager
import com.example.player.PlayerState
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
        playerManager.onErrorFallback = { failedSong, _ ->
            viewModelScope.launch {
                _streamResolution.value = StreamResolutionState(
                    isResolving = true,
                    songTitle = failedSong.title,
                    statusMessage = "Resolving alternate audio stream..."
                )
                try {
                    val (streamUrl, qualityInfo) = repository.extractFallbackAudioStream(failedSong.id)
                    val (bitrate, format) = qualityInfo
                    _streamResolution.value = StreamResolutionState(
                        isResolving = false,
                        songTitle = failedSong.title,
                        statusMessage = "Stream recovered ($format ${bitrate ?: 128} kbps)"
                    )
                    playerManager.playSongWithStream(
                        song = failedSong.copy(streamUrl = streamUrl, bitrate = bitrate, audioFormat = format),
                        streamUrl = streamUrl,
                        bitrate = bitrate,
                        format = format,
                        queue = playerState.value.queue
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
        viewModelScope.launch {
            _streamResolution.value = StreamResolutionState(
                isResolving = true,
                songTitle = song.title,
                statusMessage = "Resolving audio stream with NewPipeExtractor..."
            )
            playerManager.setResolvingStream(true)

            try {
                val (streamUrl, qualityInfo) = repository.extractAudioStream(song)
                val (bitrate, format) = qualityInfo
                _streamResolution.value = StreamResolutionState(
                    isResolving = false,
                    songTitle = song.title,
                    statusMessage = "Audio stream ready ($format ${bitrate ?: 128} kbps)"
                )
                playerManager.playSongWithStream(
                    song = song.copy(streamUrl = streamUrl, bitrate = bitrate, audioFormat = format),
                    streamUrl = streamUrl,
                    bitrate = bitrate,
                    format = format,
                    queue = queue
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
        playerManager.playNextTrack { nextSong ->
            playSong(nextSong, playerState.value.queue)
        }
    }

    fun playPrevious() {
        playerManager.playPreviousTrack { prevSong ->
            playSong(prevSong, playerState.value.queue)
        }
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

    fun setShowAudioInfoDialog(show: Boolean) {
        _showAudioInfoDialog.value = show
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

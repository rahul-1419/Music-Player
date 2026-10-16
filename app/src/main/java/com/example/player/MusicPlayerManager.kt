package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.model.Song
import com.example.util.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

data class PlayerState(
    val currentSong: Song? = null,
    val playbackState: PlayerPlaybackState = PlayerPlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isShuffleOn: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null,
    val currentStreamUrl: String? = null,
    val bitrate: Int? = null,
    val audioFormat: String? = null,
    val isResolvingStream: Boolean = false,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1
)

class MusicPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val STREAM_USER_AGENT = NetworkConfig.USER_AGENT
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressUpdateJob: Job? = null
    private var lastFailedSongId: String? = null
    private val pendingCandidateUrls = mutableListOf<String>()

    var onErrorFallback: ((Song, Boolean) -> Unit)? = null
    var onQueueEndedCallback: ((Song) -> Unit)? = null
    var onTrackTransitionCallback: ((Song) -> Unit)? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val defaultHeaders = mapOf(
                "User-Agent" to NetworkConfig.USER_AGENT,
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com",
                "Accept" to "*/*"
            )

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(NetworkConfig.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(defaultHeaders)

            val baseDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            // ResolvingDataSource dynamically injects required headers for googlevideo streams
            val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                baseDataSourceFactory,
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                        val uriStr = dataSpec.uri.toString()
                        if (uriStr.contains("googlevideo.com") || uriStr.contains("youtube.com")) {
                            val headers = HashMap(dataSpec.httpRequestHeaders)
                            headers["Referer"] = "https://www.youtube.com/"
                            headers["Origin"] = "https://www.youtube.com"
                            headers["User-Agent"] = NetworkConfig.USER_AGENT
                            headers["Accept"] = "*/*"
                            return dataSpec.buildUpon()
                                .setHttpRequestHeaders(headers)
                                .build()
                        }
                        return dataSpec
                    }
                }
            )

            val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory)

            val builder = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)

            // Audio becoming noisy can throw on some OEM Android 10 skins
            try {
                builder.setHandleAudioBecomingNoisy(true)
            } catch (_: Throwable) {}

            exoPlayer = builder.build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                shuffleModeEnabled = false

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updatePlaybackState(playbackState, isPlaying)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlaybackState(this@apply.playbackState, isPlaying)
                        if (isPlaying) {
                            startProgressTicker()
                        } else {
                            stopProgressTicker()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val curSong = _playerState.value.currentSong
                        if (curSong != null && pendingCandidateUrls.isNotEmpty()) {
                            val nextUrl = pendingCandidateUrls.removeAt(0)
                            android.util.Log.w("StreamTune", "Playback error on stream: ${error.errorCodeName}. Auto-fallback to next stream candidate...")
                            _playerState.value = _playerState.value.copy(
                                errorMessage = "Switching to alternative stream...",
                                currentStreamUrl = nextUrl
                            )
                            val metadata = MediaMetadata.Builder()
                                .setTitle(curSong.title)
                                .setArtist(curSong.artist)
                                .setArtworkUri(Uri.parse(curSong.thumbnailUrl))
                                .build()
                            val nextMediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(nextUrl))
                                .setMediaId(curSong.id)
                                .setMediaMetadata(metadata)
                                .build()
                            this@apply.setMediaItem(nextMediaItem)
                            this@apply.prepare()
                            this@apply.play()
                        } else if (curSong != null && lastFailedSongId != curSong.id) {
                            lastFailedSongId = curSong.id
                            _playerState.value = _playerState.value.copy(
                                errorMessage = "Switching to resilient stream..."
                            )
                            onErrorFallback?.invoke(curSong, true)
                        } else {
                            _playerState.value = _playerState.value.copy(
                                playbackState = PlayerPlaybackState.ERROR,
                                isPlaying = false,
                                errorMessage = error.localizedMessage ?: "Playback error"
                            )
                            stopProgressTicker()
                        }
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        syncProgress()
                    }
                })
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            _playerState.value = _playerState.value.copy(
                playbackState = PlayerPlaybackState.ERROR,
                errorMessage = "Player init error: ${t.message}"
            )
        }
    }

    private fun updatePlaybackState(state: Int, isPlayingNow: Boolean) {
        val pState = when (state) {
            Player.STATE_BUFFERING -> PlayerPlaybackState.BUFFERING
            Player.STATE_READY -> if (isPlayingNow) PlayerPlaybackState.PLAYING else PlayerPlaybackState.PAUSED
            Player.STATE_ENDED -> {
                handleTrackEnded()
                PlayerPlaybackState.ENDED
            }
            Player.STATE_IDLE -> PlayerPlaybackState.IDLE
            else -> PlayerPlaybackState.IDLE
        }

        val duration = exoPlayer?.duration?.takeIf { it > 0 } ?: _playerState.value.totalDurationMs
        _playerState.value = _playerState.value.copy(
            playbackState = pState,
            isPlaying = isPlayingNow,
            totalDurationMs = duration,
            currentPositionMs = exoPlayer?.currentPosition ?: 0L,
            bufferedPositionMs = exoPlayer?.bufferedPosition ?: 0L
        )
    }

    private fun handleTrackEnded() {
        val state = _playerState.value
        val currentSong = state.currentSong ?: return

        if (state.repeatMode == Player.REPEAT_MODE_ONE) {
            seekTo(0)
            resume()
            return
        }

        if (state.queue.isEmpty()) {
            return
        }

        if (state.repeatMode == Player.REPEAT_MODE_ALL) {
            val nextIdx = if (state.isShuffleOn) {
                (0 until state.queue.size).random()
            } else {
                (state.queueIndex + 1) % state.queue.size
            }
            val nextSong = state.queue.getOrNull(nextIdx) ?: state.queue.first()
            onTrackTransitionCallback?.invoke(nextSong)
            return
        }

        // Repeat mode is OFF:
        if (state.isShuffleOn) {
            if (state.queueIndex >= state.queue.size - 1) {
                onQueueEndedCallback?.invoke(currentSong)
            } else {
                val nextIdx = state.queueIndex + 1
                val nextSong = state.queue.getOrNull(nextIdx)
                if (nextSong != null) {
                    onTrackTransitionCallback?.invoke(nextSong)
                } else {
                    onQueueEndedCallback?.invoke(currentSong)
                }
            }
        } else {
            if (state.queueIndex < state.queue.size - 1) {
                val nextSong = state.queue[state.queueIndex + 1]
                onTrackTransitionCallback?.invoke(nextSong)
            } else {
                onQueueEndedCallback?.invoke(currentSong)
            }
        }
    }

    fun appendSongToQueue(song: Song) {
        val currentQueue = _playerState.value.queue.toMutableList()
        if (currentQueue.none { it.id == song.id }) {
            currentQueue.add(song)
            _playerState.value = _playerState.value.copy(queue = currentQueue)
        }
    }

    fun appendSongsToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val currentQueue = _playerState.value.queue.toMutableList()
        var addedAny = false
        for (song in songs) {
            if (currentQueue.none { it.id == song.id }) {
                currentQueue.add(song)
                addedAny = true
            }
        }
        if (addedAny) {
            _playerState.value = _playerState.value.copy(queue = currentQueue)
        }
    }

    fun addSongToUpcoming(song: Song) {
        appendSongToQueue(song)
    }

    fun removeQueuedSongById(songId: String): Boolean {
        val state = _playerState.value
        val curIdx = state.queueIndex
        val targetIdx = state.queue.indexOfFirst { it.id == songId }
        // Cannot remove the currently playing song from queue while it is playing
        if (targetIdx == -1 || targetIdx == curIdx) return false

        val newQueue = state.queue.toMutableList()
        newQueue.removeAt(targetIdx)
        val newCurIdx = if (targetIdx < curIdx) (curIdx - 1).coerceAtLeast(0) else curIdx

        _playerState.value = state.copy(
            queue = newQueue,
            queueIndex = newCurIdx
        )
        return true
    }

    fun removeQueuedSongAt(index: Int): Boolean {
        val state = _playerState.value
        val curIdx = state.queueIndex
        if (index < 0 || index >= state.queue.size || index == curIdx) return false

        val newQueue = state.queue.toMutableList()
        newQueue.removeAt(index)
        val newCurIdx = if (index < curIdx) (curIdx - 1).coerceAtLeast(0) else curIdx

        _playerState.value = state.copy(
            queue = newQueue,
            queueIndex = newCurIdx
        )
        return true
    }

    fun reorderUpcomingQueue(fromUpcomingIndex: Int, toUpcomingIndex: Int): Boolean {
        val state = _playerState.value
        val curIdx = state.queueIndex
        val upcomingStart = curIdx + 1
        val actualFrom = upcomingStart + fromUpcomingIndex
        val actualTo = upcomingStart + toUpcomingIndex

        if (actualFrom !in upcomingStart until state.queue.size ||
            actualTo !in upcomingStart until state.queue.size ||
            actualFrom == actualTo
        ) {
            return false
        }

        val newQueue = state.queue.toMutableList()
        val item = newQueue.removeAt(actualFrom)
        newQueue.add(actualTo, item)

        _playerState.value = state.copy(queue = newQueue)
        return true
    }

    fun clearUpcomingQueue() {
        val state = _playerState.value
        val curIdx = state.queueIndex
        if (curIdx < 0 || curIdx >= state.queue.size) {
            _playerState.value = state.copy(queue = emptyList(), queueIndex = -1)
            return
        }
        val keptQueue = state.queue.subList(0, curIdx + 1).toList()
        _playerState.value = state.copy(queue = keptQueue)
    }

    fun getUpcomingQueue(): List<Song> {
        val state = _playerState.value
        val curIdx = state.queueIndex
        if (curIdx < 0 || curIdx >= state.queue.size - 1) return emptyList()
        return state.queue.subList(curIdx + 1, state.queue.size)
    }

    fun appendAndPlaySong(
        song: Song,
        streamUrl: String,
        bitrate: Int?,
        format: String?,
        backupUrls: List<String> = song.alternativeStreamUrls
    ) {
        val currentQueue = _playerState.value.queue.toMutableList()
        val existingIdx = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIdx == -1) {
            currentQueue.add(song)
        }
        playSongWithStream(
            song = song,
            streamUrl = streamUrl,
            bitrate = bitrate,
            format = format,
            queue = currentQueue,
            backupUrls = backupUrls
        )
    }

    fun setResolvingStream(isResolving: Boolean) {
        _playerState.value = _playerState.value.copy(isResolvingStream = isResolving)
    }

    fun playSongWithStream(
        song: Song,
        streamUrl: String,
        bitrate: Int?,
        format: String?,
        queue: List<Song> = listOf(song),
        backupUrls: List<String> = song.alternativeStreamUrls
    ) {
        val player = exoPlayer ?: return
        val currentQueue = if (queue.isNotEmpty()) queue else listOf(song)
        val qIndex = currentQueue.indexOfFirst { it.id == song.id }.let { if (it == -1) 0 else it }

        pendingCandidateUrls.clear()
        pendingCandidateUrls.addAll(backupUrls.filter { it != streamUrl })
        lastFailedSongId = null

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.thumbnailUrl))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(song.id)
            .setMediaMetadata(metadata)
            .build()

        _playerState.value = _playerState.value.copy(
            currentSong = song,
            currentStreamUrl = streamUrl,
            bitrate = bitrate,
            audioFormat = format,
            isResolvingStream = false,
            errorMessage = null,
            queue = currentQueue,
            queueIndex = qIndex,
            currentPositionMs = 0L,
            totalDurationMs = song.durationSeconds * 1000L
        )

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        startProgressTicker()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val cur = exoPlayer?.currentPosition ?: 0L
        val dur = exoPlayer?.duration ?: Long.MAX_VALUE
        seekTo((cur + deltaMs).coerceAtMost(dur))
    }

    fun seekBackward(deltaMs: Long = 10000L) {
        val cur = exoPlayer?.currentPosition ?: 0L
        seekTo((cur - deltaMs).coerceAtLeast(0L))
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playerState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.repeatMode = nextMode
        _playerState.value = _playerState.value.copy(repeatMode = nextMode)
    }

    fun toggleShuffle() {
        val nextShuffle = !_playerState.value.isShuffleOn
        exoPlayer?.shuffleModeEnabled = nextShuffle
        _playerState.value = _playerState.value.copy(isShuffleOn = nextShuffle)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun playNextTrack(onNeedResolve: ((Song) -> Unit)? = null) {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        val nextIdx = if (state.isShuffleOn) {
            (0 until state.queue.size).random()
        } else {
            (state.queueIndex + 1) % state.queue.size
        }
        val nextSong = state.queue.getOrNull(nextIdx) ?: return
        onNeedResolve?.invoke(nextSong)
    }

    fun playPreviousTrack(onNeedResolve: ((Song) -> Unit)? = null) {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        val prevIdx = if (state.queueIndex - 1 < 0) state.queue.size - 1 else state.queueIndex - 1
        val prevSong = state.queue.getOrNull(prevIdx) ?: return
        onNeedResolve?.invoke(prevSong)
    }

    private fun startProgressTicker() {
        progressUpdateJob?.cancel()
        progressUpdateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                syncProgress()
                delay(400)
            }
        }
    }

    private fun stopProgressTicker() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun syncProgress() {
        val player = exoPlayer ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: _playerState.value.totalDurationMs
        val buf = player.bufferedPosition
        _playerState.value = _playerState.value.copy(
            currentPositionMs = pos,
            totalDurationMs = dur,
            bufferedPositionMs = buf
        )
    }

    fun release() {
        stopProgressTicker()
        exoPlayer?.release()
        exoPlayer = null
    }
}

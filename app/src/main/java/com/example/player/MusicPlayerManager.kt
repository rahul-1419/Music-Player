package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.model.Song
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
        const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressUpdateJob: Job? = null
    private var lastFailedSongId: String? = null

    var onErrorFallback: ((Song, Boolean) -> Unit)? = null

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

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(STREAM_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(25000)
                .setDefaultRequestProperties(
                    mapOf(
                        "Origin" to "https://www.youtube.com",
                        "Referer" to "https://www.youtube.com/",
                        "Accept" to "*/*",
                        "Accept-Encoding" to "identity;q=1, *;q=0"
                    )
                )

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

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
                        val cause = error.cause
                        val is403 = (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403)
                                || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

                        val curSong = _playerState.value.currentSong
                        if (is403 && curSong != null && lastFailedSongId != curSong.id) {
                            lastFailedSongId = curSong.id
                            // Trigger fallback stream resolution
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
        if (_playerState.value.repeatMode == Player.REPEAT_MODE_ONE) {
            seekTo(0)
            resume()
        } else {
            playNextTrack()
        }
    }

    fun setResolvingStream(isResolving: Boolean) {
        _playerState.value = _playerState.value.copy(isResolvingStream = isResolving)
    }

    fun playSongWithStream(
        song: Song,
        streamUrl: String,
        bitrate: Int?,
        format: String?,
        queue: List<Song> = listOf(song)
    ) {
        val player = exoPlayer ?: return
        val currentQueue = if (queue.isNotEmpty()) queue else listOf(song)
        val qIndex = currentQueue.indexOfFirst { it.id == song.id }.let { if (it == -1) 0 else it }

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

package com.example.autoplay

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MusicDao
import com.example.data.repository.MusicRepository
import com.example.extractor.ExtractedAudio
import com.example.extractor.YouTubeMusicExtractor
import com.example.model.Song
import com.example.player.MusicPlayerManager
import com.example.recommendation.ContentBasedRecommendationEngine
import com.example.recommendation.RecommendationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoplayControllerTest {

    private lateinit var context: Context
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var preferences: AutoplayPreferences
    private lateinit var fakeDao: FakeMusicDao
    private lateinit var fakeExtractor: FakeYouTubeMusicExtractor
    private lateinit var repository: MusicRepository

    private val song1 = Song(id = "s1", title = "Song 1", artist = "Arijit Singh", thumbnailUrl = "thumb1", durationSeconds = 200, webUrl = "http://s1")
    private val song2 = Song(id = "s2", title = "Song 2", artist = "Arijit Singh", thumbnailUrl = "thumb2", durationSeconds = 210, webUrl = "http://s2")
    private val song3 = Song(id = "s3", title = "Song 3", artist = "Arijit Singh", thumbnailUrl = "thumb3", durationSeconds = 220, webUrl = "http://s3")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        playerManager = MusicPlayerManager(context, CoroutineScope(Dispatchers.Unconfined))
        preferences = AutoplayPreferences(context)
        preferences.setAutoplayEnabled(true)

        fakeDao = FakeMusicDao()
        fakeExtractor = FakeYouTubeMusicExtractor()
        repository = MusicRepository(fakeDao, fakeExtractor)
    }

    @After
    fun tearDown() {
        playerManager.release()
    }

    private fun createController(
        scope: CoroutineScope,
        provider: RecommendationProvider = RecommendationProvider(
            repository = repository,
            engine = ContentBasedRecommendationEngine(),
            ioDispatcher = Dispatchers.Unconfined
        ),
        maxRetries: Int = 5,
        historyProvider: (() -> List<Song>)? = null
    ): AutoplayController {
        return AutoplayController(
            playerManager = playerManager,
            recommendationProvider = provider,
            repository = repository,
            preferences = preferences,
            scope = scope,
            maxRetries = maxRetries,
            ioDispatcher = Dispatchers.Unconfined,
            historyProvider = historyProvider
        )
    }

    // 1. A song finishes while other songs remain in the queue
    @Test
    fun `case 1 - song finishes while others remain in queue does not trigger autoplay`() = runTest {
        var queueEndedCalled = false
        var transitionSong: Song? = null

        playerManager.onQueueEndedCallback = { queueEndedCalled = true }
        playerManager.onTrackTransitionCallback = { nextSong -> transitionSong = nextSong }

        // Load 3 songs into queue, currently at index 0 (song1)
        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://stream1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1, song2, song3)
        )

        assertEquals(0, playerManager.playerState.value.queueIndex)

        // Simulate track ended via reflection to trigger handleTrackEnded directly
        val method = MusicPlayerManager::class.java.getDeclaredMethod("handleTrackEnded")
        method.isAccessible = true
        method.invoke(playerManager)

        // Autoplay should NOT have been called because queue has remaining songs!
        assertFalse("Autoplay queueEnded should NOT be called while queue has songs remaining", queueEndedCalled)
        assertEquals("s2", transitionSong?.id)
    }

    // 2. The queue ends and Autoplay is enabled
    @Test
    fun `case 2 - queue ends and Autoplay is enabled triggers recommendations and plays candidate`() = runTest {
        val candidateSong = Song(id = "cand_auto", title = "Auto Recommended", artist = "Arijit Singh", thumbnailUrl = "cand_thumb", durationSeconds = 180, webUrl = "http://cand")
        fakeExtractor.mockSearchResults = listOf(candidateSong)
        fakeExtractor.mockExtractedAudio = ExtractedAudio(streamUrl = "http://auto_stream.mp3", bitrate = 192, format = "M4A")

        val controller = createController(scope = this)

        // Queue has only song1, at index 0 (so ending it is queue end)
        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://stream1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1)
        )

        controller.onQueueEnded(song1)
        advanceUntilIdle()

        assertEquals(AutoplayStatus.PLAYING, controller.autoplayState.value.status)
        assertEquals("cand_auto", controller.autoplayState.value.currentCandidate?.id)
        // Verify candidate was added to queue and is currently playing
        val currentQueue = playerManager.playerState.value.queue
        assertEquals(2, currentQueue.size)
        assertEquals("cand_auto", currentQueue.last().id)
        assertEquals("cand_auto", playerManager.playerState.value.currentSong?.id)
    }

    // 3. The queue ends and Autoplay is disabled
    @Test
    fun `case 3 - queue ends and Autoplay is disabled preserves end of queue behavior`() = runTest {
        preferences.setAutoplayEnabled(false)

        val candidateSong = Song(id = "cand_auto", title = "Auto Recommended", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")
        fakeExtractor.mockSearchResults = listOf(candidateSong)

        val controller = createController(scope = this)

        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://stream1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1)
        )

        controller.onQueueEnded(song1)
        advanceUntilIdle()

        assertEquals(AutoplayStatus.DISABLED, controller.autoplayState.value.status)
        assertEquals(1, playerManager.playerState.value.queue.size)
    }

    // 4. A recommended song is unavailable (fallback to next candidate)
    @Test
    fun `case 4 - recommended song unavailable falls back to next candidate`() = runTest {
        val brokenCandidate = Song(id = "broken_song", title = "Broken Candidate", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "http://broken_song")
        val goodCandidate = Song(id = "good_song", title = "Good Candidate", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "http://good_song")

        fakeExtractor.mockSearchResults = listOf(brokenCandidate, goodCandidate)
        fakeExtractor.extractStreamCallback = { urlOrId ->
            if (urlOrId.contains("broken")) {
                throw IOException("404 Not Found")
            } else {
                ExtractedAudio(streamUrl = "http://good_stream.mp3", bitrate = 192, format = "M4A")
            }
        }

        val controller = createController(scope = this, maxRetries = 3)

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))
        controller.onQueueEnded(song1)
        advanceUntilIdle()

        // Should have retried and succeeded on goodCandidate
        assertEquals(AutoplayStatus.PLAYING, controller.autoplayState.value.status)
        assertEquals("good_song", controller.autoplayState.value.currentCandidate?.id)
        assertEquals(2, controller.autoplayState.value.retryCount)
    }

    // 5. Several candidates fail consecutively (terminates gracefully after retry limit)
    @Test
    fun `case 5 - several candidates fail consecutively stops gracefully at maxRetries`() = runTest {
        val candidates = (1..6).map {
            Song(id = "fail_$it", title = "Fail Candidate $it", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")
        }
        fakeExtractor.mockSearchResults = candidates
        fakeExtractor.extractStreamCallback = { throw IOException("Connection reset") }

        val controller = createController(scope = this, maxRetries = 3)

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))
        controller.onQueueEnded(song1)
        advanceUntilIdle()

        assertEquals(AutoplayStatus.FAILED, controller.autoplayState.value.status)
        assertEquals(3, controller.autoplayState.value.retryCount)
        assertNotNull(controller.autoplayState.value.lastError)
    }

    // 6. The recommendation list is empty
    @Test
    fun `case 6 - recommendation list empty transitions to failed without crash`() = runTest {
        fakeExtractor.mockSearchResults = emptyList()
        fakeExtractor.mockCuratedSongs = emptyList()

        val controller = createController(scope = this)

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))
        controller.onQueueEnded(song1)
        advanceUntilIdle()

        assertEquals(AutoplayStatus.FAILED, controller.autoplayState.value.status)
        assertTrue(controller.autoplayState.value.lastError?.contains("No similar content") == true)
    }

    // 7. Duplicate and recently played songs filtered appropriately
    @Test
    fun `case 7 - duplicate and recently played songs are filtered appropriately`() = runTest {
        val recentSong = Song(id = "recent_song", title = "Recent Song", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")
        val freshSong = Song(id = "fresh_song", title = "Fresh Song", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")

        fakeExtractor.mockSearchResults = listOf(recentSong, recentSong, freshSong) // Duplicate recentSong
        fakeExtractor.mockExtractedAudio = ExtractedAudio(streamUrl = "http://fresh.mp3", bitrate = 192, format = "M4A")

        val controller = createController(
            scope = this,
            historyProvider = { listOf(recentSong) }
        )

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))
        controller.onQueueEnded(song1)
        advanceUntilIdle()

        // Fresh song should be chosen over recent song due to recency penalty
        assertEquals("fresh_song", controller.autoplayState.value.currentCandidate?.id)
    }

    // 8. Network requests fail or time out
    @Test
    fun `case 8 - network requests fail or time out handled safely`() = runTest {
        fakeExtractor.searchThrows = IOException("Network timeout")
        fakeExtractor.mockCuratedSongs = listOf(song2)
        fakeExtractor.mockExtractedAudio = ExtractedAudio(streamUrl = "http://s2.mp3", bitrate = 192, format = "M4A")

        val controller = createController(scope = this)

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))
        controller.onQueueEnded(song1)
        advanceUntilIdle()

        // Search threw network error, but curated fallback songs kept autoplay functioning seamlessly
        assertEquals(AutoplayStatus.PLAYING, controller.autoplayState.value.status)
        assertEquals("s2", controller.autoplayState.value.currentCandidate?.id)
    }

    // 9. The user manually changes the queue during recommendation loading
    @Test
    fun `case 9 - manual queue change cancels ongoing autoplay`() = runTest {
        val candidateSong = Song(id = "cand_slow", title = "Slow Candidate", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")
        fakeExtractor.mockSearchResults = listOf(candidateSong)

        val controller = createController(scope = this)

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))
        controller.onQueueEnded(song1)

        // User manually cancels active autoplay (e.g. by selecting a new song)
        controller.cancelActiveAutoplay("User clicked track")
        advanceUntilIdle()

        assertEquals(AutoplayStatus.IDLE, controller.autoplayState.value.status)
    }

    // 10. Repeat-one, repeat-all, and shuffle behavior
    @Test
    fun `case 10 - repeat-one and repeat-all do not trigger autoplay`() = runTest {
        var queueEndedCalled = false
        playerManager.onQueueEndedCallback = { queueEndedCalled = true }

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))

        // Set repeat one
        playerManager.toggleRepeatMode() // Player.REPEAT_MODE_ALL
        val method = MusicPlayerManager::class.java.getDeclaredMethod("handleTrackEnded")
        method.isAccessible = true
        method.invoke(playerManager)
        assertFalse("Repeat-ALL should loop queue, not trigger queueEnded", queueEndedCalled)

        playerManager.toggleRepeatMode() // Player.REPEAT_MODE_ONE
        method.invoke(playerManager)
        assertFalse("Repeat-ONE should replay track, not trigger queueEnded", queueEndedCalled)
    }

    // 11. Background playback and player lifecycle
    @Test
    fun `case 11 - background playback and lifecycle cleanup releases resources`() = runTest {
        val controller = createController(scope = this)

        controller.cancelActiveAutoplay("Lifecycle onCleared")
        playerManager.release()

        assertEquals(AutoplayStatus.IDLE, controller.autoplayState.value.status)
    }

    // 12. Rapid state changes do not trigger duplicate playback
    @Test
    fun `case 12 - rapid triggers for same track do not duplicate playback`() = runTest {
        val candidateSong = Song(id = "cand_1", title = "Cand 1", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")
        fakeExtractor.mockSearchResults = listOf(candidateSong)

        var resolutionCount = 0
        fakeExtractor.extractStreamCallback = {
            resolutionCount++
            ExtractedAudio(streamUrl = "http://cand.mp3", bitrate = 192, format = "M4A")
        }

        val controller = createController(scope = this)

        playerManager.playSongWithStream(song = song1, streamUrl = "http://s.mp3", bitrate = 128, format = "MP3", queue = listOf(song1))

        // Rapid duplicate triggers for same track
        controller.onQueueEnded(song1)
        controller.onQueueEnded(song1)
        controller.onQueueEnded(song1)
        advanceUntilIdle()

        assertEquals("Should only resolve stream once for the same queue end event", 1, resolutionCount)
        assertEquals(AutoplayStatus.PLAYING, controller.autoplayState.value.status)
    }

    // 13. Selecting a song from search plays only that song and does not enqueue other results
    @Test
    fun `case 13 - selecting search song plays only that song and other results are not enqueued`() = runTest {
        val searchResults = listOf(song1, song2, song3)
        // User selects song2
        playerManager.playSongWithStream(
            song = song2,
            streamUrl = "http://s2.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song2) // only the selected song
        )

        val state = playerManager.playerState.value
        assertEquals("s2", state.currentSong?.id)
        assertEquals(1, state.queue.size)
        assertEquals(listOf(song2), state.queue)
        assertEquals(emptyList<Song>(), playerManager.getUpcomingQueue())
    }

    // 14. Automatic Queue Generation populates upcoming queue with resolved candidates without interrupting current song
    @Test
    fun `case 14 - recommendations populate upcoming queue asynchronously without interrupting current playback`() = runTest {
        val rec1 = Song(id = "rec1", title = "Rec 1", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 180, webUrl = "")
        val rec2 = Song(id = "rec2", title = "Rec 2", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 190, webUrl = "")
        fakeExtractor.mockSearchResults = listOf(rec1, rec2)
        fakeExtractor.mockExtractedAudio = ExtractedAudio(streamUrl = "http://rec.mp3", bitrate = 192, format = "M4A")

        val controller = createController(scope = this)

        // Song1 is currently playing
        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://s1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1)
        )

        controller.generateInitialQueue(song1, batchSize = 2)
        advanceUntilIdle()

        // Current song should remain unchanged
        assertEquals("s1", playerManager.playerState.value.currentSong?.id)
        assertEquals(0, playerManager.playerState.value.queueIndex)

        // Upcoming queue should contain the recommended tracks
        val upcoming = playerManager.getUpcomingQueue()
        assertEquals(2, upcoming.size)
        assertEquals("rec1", upcoming[0].id)
        assertEquals("rec2", upcoming[1].id)
        assertEquals("http://rec.mp3", upcoming[0].streamUrl)
    }

    // 15. Reordering changes actual playback order immediately while preserving current playing song
    @Test
    fun `case 15 - reordering upcoming queue updates real playback order and preserves current song`() = runTest {
        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://s1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1, song2, song3)
        )

        assertEquals("s1", playerManager.playerState.value.currentSong?.id)
        assertEquals(0, playerManager.playerState.value.queueIndex)
        // Upcoming is [song2, song3]
        assertEquals(listOf("s2", "s3"), playerManager.getUpcomingQueue().map { it.id })

        // Reorder upcoming: move index 0 (song2) to index 1 (after song3)
        val success = playerManager.reorderUpcomingQueue(fromUpcomingIndex = 0, toUpcomingIndex = 1)
        assertTrue(success)

        // Upcoming should now be [song3, song2]
        assertEquals(listOf("s3", "s2"), playerManager.getUpcomingQueue().map { it.id })
        // Current playing song and current index should remain intact
        assertEquals("s1", playerManager.playerState.value.currentSong?.id)
        assertEquals(0, playerManager.playerState.value.queueIndex)

        // Next track should now be song3
        var nextTrack: Song? = null
        playerManager.onTrackTransitionCallback = { nextTrack = it }
        val method = MusicPlayerManager::class.java.getDeclaredMethod("handleTrackEnded")
        method.isAccessible = true
        method.invoke(playerManager)

        assertEquals("s3", nextTrack?.id)
    }

    // 16. Removing queued song prevents it from playing and excludes it from future recommendations
    @Test
    fun `case 16 - removing queued song prevents it from playing and prevents re-recommendation`() = runTest {
        val controller = createController(scope = this)

        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://s1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1, song2, song3)
        )

        // User removes song2
        controller.recordUserRemovedSong("s2")
        val removed = playerManager.removeQueuedSongById("s2")
        assertTrue(removed)

        // Upcoming should now only be [song3]
        assertEquals(listOf("s3"), playerManager.getUpcomingQueue().map { it.id })
        assertEquals("s1", playerManager.playerState.value.currentSong?.id)

        // Ensure recommendation engine excludes removed song
        fakeExtractor.mockSearchResults = listOf(song2, song3)
        fakeExtractor.mockExtractedAudio = ExtractedAudio(streamUrl = "http://auto.mp3", bitrate = 192, format = "M4A")
        controller.generateInitialQueue(song1, batchSize = 1)
        advanceUntilIdle()

        // S2 must NOT have been re-added because it was recorded as removed by user!
        assertFalse(playerManager.playerState.value.queue.any { it.id == "s2" })
    }

    // 17. Adding a song to the queue inserts it into the upcoming queue
    @Test
    fun `case 17 - adding song from search inserts into queue without stopping playback`() = runTest {
        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://s1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1)
        )

        playerManager.appendSongToQueue(song2)

        assertEquals("s1", playerManager.playerState.value.currentSong?.id)
        assertEquals(1, playerManager.getUpcomingQueue().size)
        assertEquals("s2", playerManager.getUpcomingQueue().first().id)
    }

    // 18. Clearing upcoming songs empties the upcoming list without stopping the current song
    @Test
    fun `case 18 - clearing upcoming queue does not stop currently playing track`() = runTest {
        playerManager.playSongWithStream(
            song = song1,
            streamUrl = "http://s1.mp3",
            bitrate = 128,
            format = "MP3",
            queue = listOf(song1, song2, song3)
        )

        playerManager.clearUpcomingQueue()

        assertEquals("s1", playerManager.playerState.value.currentSong?.id)
        assertEquals(0, playerManager.playerState.value.queueIndex)
        assertEquals(1, playerManager.playerState.value.queue.size)
        assertTrue(playerManager.getUpcomingQueue().isEmpty())
    }
}

// Lightweight Fakes for Isolated, Fast JVM Testing
class FakeMusicDao : MusicDao {
    private val favorites = mutableListOf<com.example.data.local.FavoriteSongEntity>()
    private val history = mutableListOf<com.example.data.local.HistorySongEntity>()
    private val recentSearches = mutableListOf<com.example.data.local.RecentSearchEntity>()

    override fun getAllFavorites() = flowOf(favorites)
    override fun isFavorite(id: String) = flowOf(favorites.any { it.id == id })
    override suspend fun insertFavorite(song: com.example.data.local.FavoriteSongEntity) { favorites.add(song) }
    override suspend fun deleteFavorite(song: com.example.data.local.FavoriteSongEntity) { favorites.remove(song) }
    override suspend fun removeFavoriteById(id: String) { favorites.removeAll { it.id == id } }
    override fun getHistory() = flowOf(history)
    override suspend fun insertHistory(song: com.example.data.local.HistorySongEntity) { history.add(0, song) }
    override suspend fun clearHistory() { history.clear() }
    override fun getRecentSearches() = flowOf(recentSearches)
    override suspend fun insertRecentSearch(search: com.example.data.local.RecentSearchEntity) { recentSearches.add(search) }
    override suspend fun deleteRecentSearch(query: String) { recentSearches.removeAll { it.query == query } }
    override suspend fun clearRecentSearches() { recentSearches.clear() }
}

class FakeYouTubeMusicExtractor : YouTubeMusicExtractor() {
    var mockSearchResults: List<Song> = emptyList()
    var mockCuratedSongs: List<Song> = emptyList()
    var searchThrows: Throwable? = null
    var mockExtractedAudio: ExtractedAudio = ExtractedAudio(streamUrl = "http://default.mp3", bitrate = 160, format = "M4A")
    var extractStreamCallback: ((String) -> ExtractedAudio)? = null

    override suspend fun searchSongs(query: String): List<Song> {
        searchThrows?.let { throw it }
        return mockSearchResults
    }

    override suspend fun extractAudioStreamUrl(videoUrlOrId: String): ExtractedAudio {
        extractStreamCallback?.let { return it(videoUrlOrId) }
        return mockExtractedAudio
    }

    override fun getCuratedSuggestions(query: String): List<Song> {
        return mockCuratedSongs
    }
}

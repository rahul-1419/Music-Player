package com.example.recommendation

import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentBasedRecommendationEngineTest {

    private lateinit var engine: ContentBasedRecommendationEngine

    private val currentSong = Song(
        id = "song_cur",
        title = "Kesariya - Brahmāstra Love Theme",
        artist = "Arijit Singh, Pritam",
        thumbnailUrl = "https://example.com/kesariya.jpg",
        durationSeconds = 268,
        webUrl = "https://example.com/watch?v=song_cur",
        views = 500_000_000
    )

    @Before
    fun setUp() {
        engine = ContentBasedRecommendationEngine()
    }

    @Test
    fun `candidates with same primary artist receive highest artist similarity score`() {
        val candidateSameArtist = Song(
            id = "same_1",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/1.jpg",
            durationSeconds = 260,
            webUrl = "https://example.com/watch?v=same_1"
        )
        val candidateDifferentArtist = Song(
            id = "diff_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            thumbnailUrl = "https://example.com/2.jpg",
            durationSeconds = 200,
            webUrl = "https://example.com/watch?v=diff_1"
        )

        val context = RecommendationContext(currentSong = currentSong)
        val results = engine.rankCandidates(listOf(candidateDifferentArtist, candidateSameArtist), context)

        assertEquals(2, results.size)
        assertEquals("same_1", results.first().song.id)
        assertTrue("Expected score to be higher for same artist", results[0].score > results[1].score)
        assertTrue(results[0].explanation.contains("Same artist"))
    }

    @Test
    fun `featured or collaborator artist match receives positive score`() {
        val candidateCollaborator = Song(
            id = "collab_1",
            title = "Subhanallah",
            artist = "Pritam, Sreerama Chandra",
            thumbnailUrl = "https://example.com/3.jpg",
            durationSeconds = 240,
            webUrl = "https://example.com/watch?v=collab_1"
        )

        val context = RecommendationContext(currentSong = currentSong)
        val results = engine.rankCandidates(listOf(candidateCollaborator), context)

        assertEquals(1, results.size)
        assertTrue(results.first().score > 0.0)
        assertTrue(results.first().explanation.contains("Featured / Collaborator match"))
    }

    @Test
    fun `currently playing song is strictly excluded from recommendations`() {
        val candidates = listOf(
            currentSong,
            Song(id = "other_1", title = "Ilahi", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")
        )

        val context = RecommendationContext(currentSong = currentSong)
        val results = engine.rankCandidates(candidates, context)

        assertEquals(1, results.size)
        assertEquals("other_1", results.first().song.id)
    }

    @Test
    fun `active queue songs and failed songs are excluded from recommendations`() {
        val queuedSong = Song(id = "queued_1", title = "Hawayein", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 290, webUrl = "")
        val failedSong = Song(id = "failed_1", title = "Broken Audio", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")
        val validSong = Song(id = "valid_1", title = "Channa Mereya", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 280, webUrl = "")

        val context = RecommendationContext(
            currentSong = currentSong,
            currentQueue = listOf(currentSong, queuedSong),
            failedSongIds = setOf(failedSong.id)
        )

        val results = engine.rankCandidates(listOf(queuedSong, failedSong, validSong), context)

        assertEquals(1, results.size)
        assertEquals("valid_1", results.first().song.id)
    }

    @Test
    fun `duplicate candidate songs are de-duplicated`() {
        val dupSong1 = Song(id = "dup_1", title = "Track 1", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")
        val dupSong2 = Song(id = "dup_1", title = "Track 1 Duplicate", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")

        val context = RecommendationContext(currentSong = currentSong)
        val results = engine.rankCandidates(listOf(dupSong1, dupSong2), context)

        assertEquals(1, results.size)
        assertEquals("dup_1", results.first().song.id)
    }

    @Test
    fun `recently played history receives recency penalty`() {
        val freshCandidate = Song(id = "fresh_1", title = "Track Fresh", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")
        val recentCandidate = Song(id = "recent_1", title = "Track Recent", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")

        val context = RecommendationContext(
            currentSong = currentSong,
            history = listOf(recentCandidate) // In top 5 recent history
        )

        val results = engine.rankCandidates(listOf(freshCandidate, recentCandidate), context)

        assertEquals(2, results.size)
        assertEquals("fresh_1", results.first().song.id)
        assertTrue(results[1].explanation.contains("Recently played"))
        assertTrue(results[0].score > results[1].score)
    }

    @Test
    fun `artist diversity penalty is applied when the same artist is repeated`() {
        val candidateSameArtist = Song(id = "cand_same", title = "Song A", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")
        val candidateDifferentArtist = Song(id = "cand_diff", title = "Song B", artist = "Atif Aslam", thumbnailUrl = "", durationSeconds = 200, webUrl = "")

        // History has 2 consecutive Arijit Singh songs
        val historyItem1 = Song(id = "h1", title = "Prev 1", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")
        val historyItem2 = Song(id = "h2", title = "Prev 2", artist = "Arijit Singh", thumbnailUrl = "", durationSeconds = 200, webUrl = "")

        val context = RecommendationContext(
            currentSong = currentSong,
            history = listOf(historyItem1, historyItem2),
            favorites = listOf(candidateDifferentArtist) // Favorite helps the alternate artist
        )

        val results = engine.rankCandidates(listOf(candidateSameArtist, candidateDifferentArtist), context)
        val sameResult = results.first { it.song.id == "cand_same" }

        assertTrue(sameResult.explanation.contains("Artist variety penalty"))
    }

    @Test
    fun `empty candidate list returns empty without error`() {
        val context = RecommendationContext(currentSong = currentSong)
        val results = engine.rankCandidates(emptyList(), context)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `title keyword overlap correctly increases score and lists matched keywords`() {
        val candidateTheme = Song(
            id = "theme_1",
            title = "Acoustic Love Theme Melodies",
            artist = "Different Band",
            thumbnailUrl = "",
            durationSeconds = 200,
            webUrl = ""
        )

        val context = RecommendationContext(currentSong = currentSong) // Current title has "Love" and "Theme"
        val results = engine.rankCandidates(listOf(candidateTheme), context)

        assertEquals(1, results.size)
        assertTrue(results.first().explanation.contains("Shared themes"))
    }
}

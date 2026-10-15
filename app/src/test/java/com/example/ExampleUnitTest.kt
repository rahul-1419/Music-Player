package com.example

import com.example.extractor.YouTubeMusicExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testExtraction() {
        runBlocking {
            val extractor = YouTubeMusicExtractor()
            println("=== TESTING SEARCH ===")
            val songs = extractor.searchSongs("Arijit Singh")
            println("Search returned ${songs.size} songs")
            assertTrue("Expected songs to not be empty", songs.isNotEmpty())

            // Loop test extraction on multiple songs
            println("=== LOOP TESTING MULTIPLE SONGS EXTRACTION ===")
            songs.take(5).forEachIndexed { index, song ->
                println("--- Song #$index: ${song.title} (${song.id}) ---")
                val extracted = extractor.extractAudioStreamUrl(song.id)
                println("Extracted streamUrl: ${extracted.streamUrl.take(70)}..., format: ${extracted.format}, bitrate: ${extracted.bitrate}")
                println("Candidate backup stream URLs count: ${extracted.backupUrls.size}")
                assertNotNull("Stream URL must not be null", extracted.streamUrl)
                assertTrue("Stream URL must not be empty", extracted.streamUrl.isNotEmpty())
            }
            println("=== ALL LOOP TESTS PASSED SUCCESSFULLY ===")
        }
    }
}

package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MusicDatabase
import com.example.data.repository.PlaylistRepository
import com.example.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: MusicDatabase
    private lateinit var playlistRepository: PlaylistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistRepository = PlaylistRepository(db.playlistDao(), db.musicDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `playlist schema and repository creation and track management`() = runBlocking {
        // 1. Create a playlist
        val playlistId = playlistRepository.createPlaylist(
            name = "Acoustic Chill",
            description = "My favorite relaxing songs"
        )
        assertTrue(playlistId > 0)

        // 2. Verify allPlaylists flow emits the playlist
        val allPlaylists = playlistRepository.allPlaylists.first()
        assertEquals(1, allPlaylists.size)
        assertEquals("Acoustic Chill", allPlaylists[0].name)
        assertEquals("My favorite relaxing songs", allPlaylists[0].description)

        // 3. Add tracks to the playlist
        val song1 = Song(
            id = "s1",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/thumb1.jpg",
            durationSeconds = 262,
            webUrl = "https://www.youtube.com/watch?v=s1"
        )
        val song2 = Song(
            id = "s2",
            title = "Kesariya",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/thumb2.jpg",
            durationSeconds = 268,
            webUrl = "https://www.youtube.com/watch?v=s2"
        )

        val added1 = playlistRepository.addSongToPlaylist(playlistId, song1)
        val added2 = playlistRepository.addSongToPlaylist(playlistId, song2)
        assertTrue("Track 1 should be added", added1)
        assertTrue("Track 2 should be added", added2)

        // 4. Duplicate prevention
        val addedDuplicate = playlistRepository.addSongToPlaylist(playlistId, song1)
        assertFalse("Duplicate track should not be added", addedDuplicate)

        // 5. Verify tracks in playlist
        val songsInPlaylist = playlistRepository.getPlaylistSongs(playlistId).first()
        assertEquals(2, songsInPlaylist.size)
        assertEquals("Tum Hi Ho", songsInPlaylist[0].title)
        assertEquals("Kesariya", songsInPlaylist[1].title)

        // 6. Verify PlaylistWithTracks relation
        val playlistWithTracks = playlistRepository.getPlaylistWithTracks(playlistId).first()
        assertNotNull(playlistWithTracks)
        assertEquals(2, playlistWithTracks!!.trackCount)
        assertEquals(530L, playlistWithTracks.totalDurationSeconds)

        // 7. Remove track from playlist
        playlistRepository.removeSongFromPlaylist(playlistId, "s1")
        val songsAfterRemoval = playlistRepository.getPlaylistSongs(playlistId).first()
        assertEquals(1, songsAfterRemoval.size)
        assertEquals("Kesariya", songsAfterRemoval[0].title)

        // 8. Delete playlist (cascades tracks)
        playlistRepository.deletePlaylist(playlistId)
        val playlistsAfterDelete = playlistRepository.allPlaylists.first()
        assertTrue("Playlist list should be empty after deletion", playlistsAfterDelete.isEmpty())
        val deletedWithTracks = playlistRepository.getPlaylistWithTracks(playlistId).first()
        assertNull(deletedWithTracks)
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StreamTune", appName)
    }

    @Test
    fun `song duration formatted correctly`() {
        val song = Song(
            id = "Umqb9KENgmk",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationSeconds = 262,
            webUrl = "https://www.youtube.com/watch?v=Umqb9KENgmk"
        )

        assertEquals("4:22", song.formattedDuration)
    }

    @Test
    fun `song with zero duration handles formatted correctly`() {
        val song = Song(
            id = "test",
            title = "Live Stream",
            artist = "Arijit Singh",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationSeconds = 0,
            webUrl = "https://www.youtube.com/watch?v=test"
        )

        assertEquals("--:--", song.formattedDuration)
    }
}

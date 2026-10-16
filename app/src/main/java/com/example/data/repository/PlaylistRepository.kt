package com.example.data.repository

import com.example.data.local.MusicDao
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.PlaylistWithTracks
import com.example.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val musicDao: MusicDao
) {

    /**
     * Observable stream of all playlists ordered by most recently updated
     */
    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    /**
     * Get a specific playlist along with its ordered tracks
     */
    fun getPlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?> {
        return playlistDao.getPlaylistWithTracks(playlistId)
    }

    /**
     * Get reactive list of Songs for a playlist, automatically mapped with favorite status
     */
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return combine(
            playlistDao.getTracksForPlaylist(playlistId),
            musicDao.getAllFavorites()
        ) { tracks, favorites ->
            val favIds = favorites.map { it.id }.toSet()
            tracks.map { it.toSong(isFavorite = favIds.contains(it.songId)) }
        }
    }

    /**
     * Returns the IDs of all playlists containing the specified song
     */
    fun getPlaylistsContainingSong(songId: String): Flow<List<Long>> {
        return playlistDao.getPlaylistsContainingSong(songId)
    }

    /**
     * Create a new playlist, optionally adding initial songs
     */
    suspend fun createPlaylist(
        name: String,
        description: String = "",
        initialSongs: List<Song> = emptyList()
    ): Long {
        val cleanName = name.trim().ifEmpty { "My Playlist" }
        val coverUrl = initialSongs.firstOrNull()?.thumbnailUrl
        val playlist = PlaylistEntity(
            name = cleanName,
            description = description.trim(),
            coverUrl = coverUrl,
            createdAtTimestamp = System.currentTimeMillis(),
            updatedAtTimestamp = System.currentTimeMillis()
        )
        val playlistId = playlistDao.insertPlaylist(playlist)

        if (initialSongs.isNotEmpty()) {
            val trackEntities = initialSongs.mapIndexed { index, song ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    durationSeconds = song.durationSeconds,
                    webUrl = song.webUrl,
                    position = index,
                    addedAtTimestamp = System.currentTimeMillis() + index
                )
            }
            playlistDao.insertTracks(trackEntities)
        }

        return playlistId
    }

    /**
     * Update playlist title and description
     */
    suspend fun updatePlaylistDetails(playlistId: Long, name: String, description: String) {
        val cleanName = name.trim().ifEmpty { "My Playlist" }
        playlistDao.updatePlaylistDetails(playlistId, cleanName, description.trim())
    }

    /**
     * Delete playlist and cascade delete its tracks
     */
    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    /**
     * Add a single song to a playlist.
     * Returns true if added, false if already present.
     */
    suspend fun addSongToPlaylist(playlistId: Long, song: Song): Boolean {
        if (playlistDao.isSongInPlaylist(playlistId, song.id)) {
            return false
        }

        val track = PlaylistTrackEntity(
            playlistId = playlistId,
            songId = song.id,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl,
            durationSeconds = song.durationSeconds,
            webUrl = song.webUrl,
            addedAtTimestamp = System.currentTimeMillis()
        )
        playlistDao.addTrackToPlaylist(track)
        return true
    }

    /**
     * Add multiple songs to a playlist in bulk
     */
    suspend fun addSongsToPlaylist(playlistId: Long, songs: List<Song>) {
        var currentMaxPos = playlistDao.getMaxPosition(playlistId) ?: -1
        val newTracks = mutableListOf<PlaylistTrackEntity>()

        for (song in songs) {
            if (!playlistDao.isSongInPlaylist(playlistId, song.id)) {
                currentMaxPos++
                newTracks.add(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        durationSeconds = song.durationSeconds,
                        webUrl = song.webUrl,
                        position = currentMaxPos,
                        addedAtTimestamp = System.currentTimeMillis() + currentMaxPos
                    )
                )
            }
        }

        if (newTracks.isNotEmpty()) {
            playlistDao.insertTracks(newTracks)
            val currentPlaylist = playlistDao.getPlaylistByIdDirect(playlistId)
            val newCover = currentPlaylist?.coverUrl ?: newTracks.first().thumbnailUrl
            playlistDao.updatePlaylistMeta(playlistId, newCover)
        }
    }

    /**
     * Remove a song from a playlist and refresh the cover art
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.removeTrackAndUpdateMeta(playlistId, songId)
    }

    /**
     * Check if a song is already present in a playlist
     */
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean {
        return playlistDao.isSongInPlaylist(playlistId, songId)
    }
}

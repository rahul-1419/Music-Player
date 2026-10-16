package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAtTimestamp DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistByIdDirect(playlistId: Long): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAtTimestamp ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAtTimestamp ASC")
    suspend fun getTracksForPlaylistDirect(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT playlistId FROM playlist_tracks WHERE songId = :songId")
    fun getPlaylistsContainingSong(songId: String): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun getTrackCount(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCountDirect(playlistId: Long): Int

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeTrack(playlistId: Long, songId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Query("UPDATE playlists SET updatedAtTimestamp = :timestamp, coverUrl = :coverUrl WHERE id = :playlistId")
    suspend fun updatePlaylistMeta(playlistId: Long, coverUrl: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAtTimestamp = :timestamp WHERE id = :playlistId")
    suspend fun updatePlaylistDetails(playlistId: Long, name: String, description: String, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun addTrackToPlaylist(track: PlaylistTrackEntity) {
        val maxPos = getMaxPosition(track.playlistId) ?: -1
        val newTrack = track.copy(position = maxPos + 1)
        insertTrack(newTrack)

        val currentPlaylist = getPlaylistByIdDirect(track.playlistId)
        val newCover = currentPlaylist?.coverUrl ?: track.thumbnailUrl
        updatePlaylistMeta(track.playlistId, newCover)
    }

    @Transaction
    suspend fun removeTrackAndUpdateMeta(playlistId: Long, songId: String) {
        removeTrack(playlistId, songId)
        val remaining = getTracksForPlaylistDirect(playlistId)
        val newCover = remaining.firstOrNull()?.thumbnailUrl
        updatePlaylistMeta(playlistId, newCover)
    }
}

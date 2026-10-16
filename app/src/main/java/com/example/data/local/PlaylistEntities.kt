package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.model.Song

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverUrl: String? = null,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "position"])
    ]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val webUrl: String,
    val position: Int = 0,
    val addedAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toSong(isFavorite: Boolean = false): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        webUrl = webUrl,
        isFavorite = isFavorite
    )
}

data class PlaylistWithTracks(
    @Embedded
    val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val tracks: List<PlaylistTrackEntity>
) {
    val trackCount: Int
        get() = tracks.size

    val totalDurationSeconds: Long
        get() = tracks.sumOf { it.durationSeconds }

    fun toSongs(favoriteIds: Set<String> = emptySet()): List<Song> {
        return tracks
            .sortedBy { it.position }
            .map { it.toSong(isFavorite = favoriteIds.contains(it.songId)) }
    }
}

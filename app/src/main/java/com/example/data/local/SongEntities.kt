package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Song

@Entity(tableName = "favorites")
data class FavoriteSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val webUrl: String,
    val views: Long = 0,
    val savedAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        webUrl = webUrl,
        isFavorite = true,
        views = views
    )
}

@Entity(tableName = "history")
data class HistorySongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val webUrl: String,
    val views: Long = 0,
    val playedAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toSong(isFavorite: Boolean = false): Song = Song(
        id = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        webUrl = webUrl,
        isFavorite = isFavorite,
        views = views
    )
}

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

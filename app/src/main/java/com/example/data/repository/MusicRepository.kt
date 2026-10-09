package com.example.data.repository

import com.example.data.local.FavoriteSongEntity
import com.example.data.local.HistorySongEntity
import com.example.data.local.MusicDao
import com.example.data.local.RecentSearchEntity
import com.example.extractor.YouTubeMusicExtractor
import com.example.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val musicDao: MusicDao,
    private val extractor: YouTubeMusicExtractor
) {

    fun getFavorites(): Flow<List<Song>> {
        return musicDao.getAllFavorites().map { list ->
            list.map { it.toSong() }
        }
    }

    fun isFavorite(songId: String): Flow<Boolean> {
        return musicDao.isFavorite(songId)
    }

    suspend fun toggleFavorite(song: Song, currentIsFav: Boolean) {
        if (currentIsFav) {
            musicDao.removeFavoriteById(song.id)
        } else {
            musicDao.insertFavorite(
                FavoriteSongEntity(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    durationSeconds = song.durationSeconds,
                    webUrl = song.webUrl,
                    views = song.views
                )
            )
        }
    }

    fun getHistory(): Flow<List<Song>> {
        return musicDao.getHistory().map { list ->
            list.map { it.toSong() }
        }
    }

    suspend fun recordPlayed(song: Song) {
        musicDao.insertHistory(
            HistorySongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationSeconds = song.durationSeconds,
                webUrl = song.webUrl,
                views = song.views
            )
        )
    }

    fun getRecentSearches(): Flow<List<String>> {
        return musicDao.getRecentSearches().map { list ->
            list.map { it.query }
        }
    }

    suspend fun addRecentSearch(query: String) {
        if (query.isNotBlank()) {
            musicDao.insertRecentSearch(RecentSearchEntity(query.trim()))
        }
    }

    suspend fun removeRecentSearch(query: String) {
        musicDao.deleteRecentSearch(query)
    }

    suspend fun searchSongs(query: String): List<Song> {
        return extractor.searchSongs(query)
    }

    suspend fun extractAudioStream(song: Song): Pair<String, Pair<Int?, String?>> {
        return extractor.extractAudioStreamUrl(song.webUrl)
    }

    suspend fun extractFallbackAudioStream(videoId: String): Pair<String, Pair<Int?, String?>> {
        return extractor.extractFallbackAudioStream(videoId)
    }

    fun getCuratedSongs(query: String = ""): List<Song> {
        return extractor.getCuratedSuggestions(query)
    }
}

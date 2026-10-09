package com.example.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val webUrl: String,
    val streamUrl: String? = null,
    val bitrate: Int? = null,
    val audioFormat: String? = null,
    val isFavorite: Boolean = false,
    val views: Long = 0
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

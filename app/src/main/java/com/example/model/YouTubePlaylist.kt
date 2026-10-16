package com.example.model

data class YouTubePlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val itemCount: Long = 0,
    val channelTitle: String = "",
    val channelId: String = ""
)

enum class YouTubeConnectType {
    GOOGLE_OAUTH,
    CHANNEL_HANDLE,
    DEMO_ACCOUNT
}

data class YouTubeAccount(
    val isConnected: Boolean = false,
    val accountName: String = "",
    val accountEmail: String = "",
    val avatarUrl: String = "",
    val channelId: String = "",
    val channelHandle: String = "",
    val accessToken: String = "",
    val connectType: YouTubeConnectType = YouTubeConnectType.GOOGLE_OAUTH
)

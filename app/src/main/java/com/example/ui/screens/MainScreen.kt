package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MusicViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.AudioVisualizerWave
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingSheet
import com.example.ui.components.StreamInfoDialog
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CoralPink
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WaveEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val curatedSongs by viewModel.curatedSongs.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val streamResolution by viewModel.streamResolution.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val showAudioInfoDialog by viewModel.showAudioInfoDialog.collectAsState()

    BackHandler(enabled = isNowPlayingExpanded) {
        viewModel.setNowPlayingExpanded(false)
    }

    BackHandler(enabled = !isNowPlayingExpanded && currentTab != NavigationTab.EXPLORE) {
        viewModel.selectTab(NavigationTab.EXPLORE)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(ElectricViolet, NeonCyan)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "StreamTune",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "YouTube Audio • Ad-Free",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = NeonCyan
                                )
                            }
                        }
                    },
                    actions = {
                        // Extraction indicator chip
                        if (streamResolution.isResolving) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricViolet.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = NeonCyan,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Extracting...",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = NeonCyan
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.setShowAudioInfoDialog(true) },
                                modifier = Modifier.testTag("appbar_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = "Architecture Pipeline",
                                    tint = ElectricViolet
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundDark,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    // Mini player floating bar if a song is loaded
                    if (playerState.currentSong != null) {
                        MiniPlayerBar(
                            playerState = playerState,
                            onBarClick = { viewModel.setNowPlayingExpanded(true) },
                            onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
                            onNextClick = { viewModel.playNext() }
                        )
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = SurfaceDark,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentTab == NavigationTab.EXPLORE,
                            onClick = { viewModel.selectTab(NavigationTab.EXPLORE) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == NavigationTab.EXPLORE) Icons.Default.Explore else Icons.Outlined.Explore,
                                    contentDescription = "Explore"
                                )
                            },
                            label = { Text("Explore") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricViolet,
                                selectedTextColor = ElectricViolet,
                                indicatorColor = ElectricViolet.copy(alpha = 0.2f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("nav_explore")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavigationTab.SEARCH,
                            onClick = { viewModel.selectTab(NavigationTab.SEARCH) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == NavigationTab.SEARCH) Icons.Default.Search else Icons.Outlined.Search,
                                    contentDescription = "Search"
                                )
                            },
                            label = { Text("Search") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonCyan,
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan.copy(alpha = 0.2f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("nav_search")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavigationTab.FAVORITES,
                            onClick = { viewModel.selectTab(NavigationTab.FAVORITES) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == NavigationTab.FAVORITES) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorites"
                                )
                            },
                            label = { Text("Favorites") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CoralPink,
                                selectedTextColor = CoralPink,
                                indicatorColor = CoralPink.copy(alpha = 0.2f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("nav_favorites")
                        )

                        NavigationBarItem(
                            selected = currentTab == NavigationTab.HISTORY,
                            onClick = { viewModel.selectTab(NavigationTab.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == NavigationTab.HISTORY) Icons.Default.History else Icons.Outlined.History,
                                    contentDescription = "History"
                                )
                            },
                            label = { Text("History") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WaveEmerald,
                                selectedTextColor = WaveEmerald,
                                indicatorColor = WaveEmerald.copy(alpha = 0.2f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            },
            containerColor = BackgroundDark
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    NavigationTab.EXPLORE -> {
                        ExploreScreen(
                            curatedSongs = curatedSongs,
                            playerState = playerState,
                            favorites = favorites,
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onFavoriteToggle = { song -> viewModel.toggleFavorite(song) },
                            onQuickSearch = { query ->
                                viewModel.selectTab(NavigationTab.SEARCH)
                                viewModel.onSearchQueryChange(query)
                                viewModel.performSearch(query)
                            }
                        )
                    }

                    NavigationTab.SEARCH -> {
                        SearchScreen(
                            searchQuery = searchQuery,
                            searchState = searchState,
                            recentSearches = recentSearches,
                            favorites = favorites,
                            playerState = playerState,
                            onQueryChange = { viewModel.onSearchQueryChange(it) },
                            onSearch = { viewModel.performSearch(it) },
                            onRemoveRecentSearch = { viewModel.removeRecentSearch(it) },
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onFavoriteToggle = { song -> viewModel.toggleFavorite(song) }
                        )
                    }

                    NavigationTab.FAVORITES -> {
                        FavoritesScreen(
                            favorites = favorites,
                            playerState = playerState,
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onFavoriteToggle = { song -> viewModel.toggleFavorite(song) },
                            onExploreClick = { viewModel.selectTab(NavigationTab.EXPLORE) }
                        )
                    }

                    NavigationTab.HISTORY -> {
                        HistoryScreen(
                            history = history,
                            favorites = favorites,
                            playerState = playerState,
                            onSongClick = { song, list -> viewModel.playSong(song, list) },
                            onFavoriteToggle = { song -> viewModel.toggleFavorite(song) }
                        )
                    }
                }
            }
        }

        // Full Screen Animated Now Playing Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playerState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            val isCurrentFav = favorites.any { it.id == playerState.currentSong?.id }
            NowPlayingSheet(
                playerState = playerState,
                isFavorite = isCurrentFav,
                onCollapse = { viewModel.setNowPlayingExpanded(false) },
                onPlayPause = { viewModel.playerManager.togglePlayPause() },
                onNext = { viewModel.playNext() },
                onPrevious = { viewModel.playPrevious() },
                onSeek = { posMs -> viewModel.playerManager.seekTo(posMs) },
                onSeekForward = { viewModel.playerManager.seekForward(10000L) },
                onSeekBackward = { viewModel.playerManager.seekBackward(10000L) },
                onToggleRepeat = { viewModel.playerManager.toggleRepeatMode() },
                onToggleShuffle = { viewModel.playerManager.toggleShuffle() },
                onSetSpeed = { speed -> viewModel.playerManager.setPlaybackSpeed(speed) },
                onToggleFavorite = {
                    playerState.currentSong?.let { viewModel.toggleFavorite(it) }
                },
                onShowStreamInfo = { viewModel.setShowAudioInfoDialog(true) }
            )
        }

        // Technical Stream Info Dialog
        if (showAudioInfoDialog) {
            StreamInfoDialog(
                playerState = playerState,
                onDismiss = { viewModel.setShowAudioInfoDialog(false) }
            )
        }
    }
}

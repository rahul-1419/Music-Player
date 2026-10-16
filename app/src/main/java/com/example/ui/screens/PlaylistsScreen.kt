package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistWithTracks
import com.example.model.Song
import com.example.model.YouTubeAccount
import com.example.model.YouTubePlaylist
import com.example.player.PlayerState
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CoralPink
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WaveEmerald

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    selectedPlaylistId: Long?,
    selectedPlaylistWithTracks: PlaylistWithTracks?,
    playlistSongs: List<Song>,
    playerState: PlayerState,
    youTubeAccount: YouTubeAccount,
    youTubePlaylists: List<YouTubePlaylist>,
    isLoadingYouTubePlaylists: Boolean,
    selectedYouTubePlaylist: YouTubePlaylist?,
    selectedYouTubePlaylistSongs: List<Song>,
    isLoadingYouTubePlaylistSongs: Boolean,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (name: String, description: String) -> Unit,
    onUpdatePlaylist: (playlistId: Long, name: String, description: String) -> Unit,
    onDeletePlaylist: (playlistId: Long) -> Unit,
    onSongClick: (song: Song, queue: List<Song>) -> Unit,
    onRemoveSongFromPlaylist: (playlistId: Long, songId: String) -> Unit,
    onConnectYouTubeClick: () -> Unit,
    onDisconnectYouTubeClick: () -> Unit,
    onRefreshYouTubePlaylists: () -> Unit,
    onSelectYouTubePlaylist: (YouTubePlaylist?) -> Unit,
    onImportYouTubePlaylist: (YouTubePlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedYouTubePlaylist != null) {
        YouTubePlaylistDetailView(
            playlist = selectedYouTubePlaylist,
            songs = selectedYouTubePlaylistSongs,
            isLoading = isLoadingYouTubePlaylistSongs,
            playerState = playerState,
            onBack = { onSelectYouTubePlaylist(null) },
            onSongClick = onSongClick,
            onImport = { onImportYouTubePlaylist(selectedYouTubePlaylist) },
            modifier = modifier
        )
    } else if (selectedPlaylistId != null && selectedPlaylistWithTracks != null) {
        // Detailed Playlist View
        PlaylistDetailView(
            playlistWithTracks = selectedPlaylistWithTracks,
            songs = playlistSongs,
            playerState = playerState,
            onBack = { onSelectPlaylist(null) },
            onUpdateDetails = { name, desc ->
                onUpdatePlaylist(selectedPlaylistId, name, desc)
            },
            onDeletePlaylist = {
                onDeletePlaylist(selectedPlaylistId)
            },
            onSongClick = onSongClick,
            onRemoveSong = { songId ->
                onRemoveSongFromPlaylist(selectedPlaylistId, songId)
            },
            modifier = modifier
        )
    } else {
        // All Playlists Overview
        PlaylistsOverview(
            playlists = playlists,
            youTubeAccount = youTubeAccount,
            youTubePlaylists = youTubePlaylists,
            isLoadingYouTubePlaylists = isLoadingYouTubePlaylists,
            onSelectPlaylist = onSelectPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            onDeletePlaylist = onDeletePlaylist,
            onConnectYouTubeClick = onConnectYouTubeClick,
            onDisconnectYouTubeClick = onDisconnectYouTubeClick,
            onRefreshYouTubePlaylists = onRefreshYouTubePlaylists,
            onSelectYouTubePlaylist = onSelectYouTubePlaylist,
            onImportYouTubePlaylist = onImportYouTubePlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun PlaylistsOverview(
    playlists: List<PlaylistEntity>,
    youTubeAccount: YouTubeAccount,
    youTubePlaylists: List<YouTubePlaylist>,
    isLoadingYouTubePlaylists: Boolean,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (name: String, description: String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onConnectYouTubeClick: () -> Unit,
    onDisconnectYouTubeClick: () -> Unit,
    onRefreshYouTubePlaylists: () -> Unit,
    onSelectYouTubePlaylist: (YouTubePlaylist) -> Unit,
    onImportYouTubePlaylist: (YouTubePlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedSectionTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("playlists_overview")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title and Action Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "${playlists.size} local • ${youTubePlaylists.size} YouTube",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            if (selectedSectionTab == 0) {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_create_playlist")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Playlist", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onConnectYouTubeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPink),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_header_yt_connect")
                ) {
                    Text(if (youTubeAccount.isConnected) "Account" else "Connect", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sub-tabs: My Library vs YouTube Playlists
        TabRow(
            selectedTabIndex = selectedSectionTab,
            containerColor = SurfaceVariantDark,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSectionTab]),
                    color = if (selectedSectionTab == 0) ElectricViolet else CoralPink
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedSectionTab == 0,
                onClick = { selectedSectionTab = 0 },
                text = {
                    Text(
                        text = "My Library (${playlists.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = selectedSectionTab == 1,
                onClick = { selectedSectionTab = 1 },
                text = {
                    Text(
                        text = "YouTube (${youTubePlaylists.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSectionTab == 0) {
            // Local Library
            if (playlists.isEmpty()) {
                EmptyPlaylistsCard(onCreateClick = { showCreateDialog = true })
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistItemCard(
                            playlist = playlist,
                            onClick = { onSelectPlaylist(playlist.id) },
                            onDelete = { onDeletePlaylist(playlist.id) }
                        )
                    }
                }
            }
        } else {
            // YouTube Account Playlists (Audio Only)
            YouTubePlaylistsSection(
                youTubeAccount = youTubeAccount,
                youTubePlaylists = youTubePlaylists,
                isLoading = isLoadingYouTubePlaylists,
                onConnectClick = onConnectYouTubeClick,
                onDisconnectClick = onDisconnectYouTubeClick,
                onRefreshClick = onRefreshYouTubePlaylists,
                onPlaylistClick = onSelectYouTubePlaylist,
                onImportPlaylist = onImportYouTubePlaylist
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                showCreateDialog = false
                onCreatePlaylist(name, description)
            }
        )
    }
}

@Composable
private fun PlaylistItemCard(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("playlist_card_${playlist.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork / Cover
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ElectricViolet.copy(alpha = 0.3f),
                                NeonCyan.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverUrl != null) {
                    AsyncImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.name,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = WaveEmerald,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (playlist.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Playlist Collection",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Options Overflow Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceVariantDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Playlist", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan)
                        },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = CoralPink) },
                        leadingIcon = {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = CoralPink)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistsCard(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ElectricViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = ElectricViolet,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Build Your Personal Collections",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Save and organize your favorite audio tracks into custom playlists for workout, study, or chill sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_empty_create_playlist")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create First Playlist", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlistWithTracks: PlaylistWithTracks,
    songs: List<Song>,
    playerState: PlayerState,
    onBack: () -> Unit,
    onUpdateDetails: (name: String, description: String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onSongClick: (song: Song, queue: List<Song>) -> Unit,
    onRemoveSong: (songId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlist = playlistWithTracks.playlist
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlist_detail_${playlist.id}")
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_playlist_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { showEditDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Playlist",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Playlist",
                    tint = CoralPink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                // Header Banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ElectricViolet.copy(0.4f), NeonCyan.copy(0.3f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playlist.coverUrl != null) {
                                AsyncImage(
                                    model = playlist.coverUrl,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = WaveEmerald,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextPrimary
                            )

                            if (playlist.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = playlist.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${songs.size} tracks • ${formatTotalDuration(playlistWithTracks.totalDurationSeconds)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan
                            )
                        }
                    }
                }
            }

            // Play & Shuffle Action Buttons
            if (songs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSongClick(songs.first(), songs) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_play_all_playlist")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val shuffled = songs.shuffled()
                                onSongClick(shuffled.first(), shuffled)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_shuffle_playlist")
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = NeonCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Playlist is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add songs to this playlist from Explore, Search, or Favorites",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    val isPlayingThis = playerState.currentSong?.id == song.id

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSongClick(song, songs) }
                            .then(
                                if (isPlayingThis) {
                                    Modifier.border(
                                        1.dp,
                                        ElectricViolet,
                                        RoundedCornerShape(12.dp)
                                    )
                                } else Modifier
                            )
                            .testTag("playlist_track_${song.id}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPlayingThis) SurfaceVariantDark else SurfaceDark
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isPlayingThis) NeonCyan else TextMuted,
                                modifier = Modifier.width(24.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isPlayingThis) NeonCyan else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "${song.artist} • ${song.formattedDuration}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onRemoveSong(song.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove from playlist",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        CreatePlaylistDialog(
            initialName = playlist.name,
            initialDescription = playlist.description,
            titleText = "Edit Playlist",
            confirmButtonText = "Save",
            onDismiss = { showEditDialog = false },
            onConfirm = { name, desc ->
                showEditDialog = false
                onUpdateDetails(name, desc)
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist?", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete \"${playlist.name}\"? This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPink)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

private fun formatTotalDuration(seconds: Long): String {
    if (seconds <= 0) return "0 min"
    val mins = seconds / 60
    val hours = mins / 60
    val remMins = mins % 60
    return if (hours > 0) {
        "${hours}h ${remMins}m"
    } else {
        "${remMins} min"
    }
}

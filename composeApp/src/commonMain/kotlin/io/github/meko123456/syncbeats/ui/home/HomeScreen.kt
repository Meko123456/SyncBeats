@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.meko123456.syncbeats.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.meko123456.syncbeats.core.model.AccountPlaylist
import io.github.meko123456.syncbeats.core.model.HistoryItem
import io.github.meko123456.syncbeats.core.model.SavedPlaylist
import io.github.meko123456.syncbeats.core.model.SearchResult
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onEnterRoom: (roomId: String, autoplay: Boolean) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.enterRoomId) {
        state.enterRoomId?.let { roomId ->
            viewModel.consumeEnterRoom()
            onEnterRoom(roomId, true)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Home") }) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.history.isNotEmpty()) {
                item { SectionHeader("Continue listening") }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    ) {
                        items(state.history, key = { it.key }) { item ->
                            HistoryCard(item) { viewModel.playHistoryInNewRoom(item) }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "My playlists",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add playlist")
                    }
                }
            }
            if (state.playlists.isEmpty()) {
                item {
                    Text(
                        "Paste a link to one of your public or unlisted YouTube playlists with +",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    ) {
                        items(state.playlists, key = { it.key }) { playlist ->
                            PlaylistCard(playlist) { viewModel.openPlaylist(playlist) }
                        }
                    }
                }
            }

            when (state.yt.connected) {
                false -> item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Your YouTube library", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connect your Google account to see your playlists, liked songs and subscriptions here.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = viewModel::connectYouTube) { Text("Connect YouTube") }
                        }
                    }
                }
                true -> {
                    if (state.yt.loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    if (state.yt.playlists.isNotEmpty()) {
                        item { SectionHeader("Your YouTube playlists") }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            ) {
                                items(state.yt.playlists, key = { it.id }) { playlist ->
                                    AccountPlaylistCard(playlist) { viewModel.openAccountPlaylist(playlist) }
                                }
                            }
                        }
                    }
                    if (state.yt.likedSongs.isNotEmpty()) {
                        item { SectionHeader("Liked songs") }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            ) {
                                items(state.yt.likedSongs, key = { it.videoId }) { track ->
                                    TrackCard(track) { viewModel.playTrackInNewRoom(track) }
                                }
                            }
                        }
                    }
                    if (state.yt.subscriptionFeed.isNotEmpty()) {
                        item { SectionHeader("New from subscriptions") }
                        items(state.yt.subscriptionFeed, key = { "sub-" + it.videoId }) { track ->
                            TrendingRow(track) { viewModel.playTrackInNewRoom(track) }
                        }
                    }
                }
                null -> {}
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Trending on YouTube",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::refreshTrending) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
            if (state.loadingTrending) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            items(state.trending, key = { it.videoId }) { track ->
                TrendingRow(track) { viewModel.playTrackInNewRoom(track) }
            }

            state.error?.let { err ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Error: $err", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            importing = state.importing,
            onImport = viewModel::importPlaylist,
            onDismiss = { showImportDialog = false },
        )
    }

    state.openedPlaylist?.let { details ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closePlaylist, sheetState = sheetState) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(details.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${details.tracks.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { viewModel.playPlaylistInNewRoom(details) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play all in a room")
                }
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(details.tracks, key = { it.videoId }) { track ->
                        ListItem(
                            leadingContent = {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                )
                            },
                            headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(track.artist, maxLines = 1) },
                            trailingContent = {
                                TextButton(onClick = { viewModel.playTrackInNewRoom(track) }) {
                                    Text("Play")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.loadingPlaylist) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun HistoryCard(item: HistoryItem, onClick: () -> Unit) {
    Card(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: SavedPlaylist, onClick: () -> Unit) {
    Card(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    playlist.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun TrendingRow(track: SearchResult, onPlay: () -> Unit) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            )
        },
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(track.artist, maxLines = 1) },
        trailingContent = {
            TextButton(onClick = onPlay) { Text("Play in room") }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ImportPlaylistDialog(
    importing: Boolean,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add YouTube playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste a link to a public or unlisted playlist (youtube.com/playlist?list=…)")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Playlist URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (importing) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onImport(url)
                    onDismiss()
                },
                enabled = url.isNotBlank() && !importing,
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AccountPlaylistCard(playlist: AccountPlaylist, onClick: () -> Unit) {
    Card(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    playlist.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun TrackCard(track: SearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(90.dp),
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

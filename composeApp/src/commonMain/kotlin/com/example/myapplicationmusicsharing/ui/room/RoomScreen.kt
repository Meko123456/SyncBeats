@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplicationmusicsharing.ui.room

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.myapplicationmusicsharing.data.QueueItem
import com.example.myapplicationmusicsharing.data.SearchResult
import com.example.myapplicationmusicsharing.ui.PlatformBackHandler
import com.example.myapplicationmusicsharing.util.currentTimeMillis
import kotlinx.coroutines.delay
import org.koin.core.parameter.parametersOf

@Composable
fun RoomScreen(
    roomId: String,
    autoplay: Boolean,
    onLeave: () -> Unit,
    viewModel: RoomViewModel = koinViewModel(parameters = { parametersOf(roomId, autoplay) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }

    val leave = {
        viewModel.leaveRoom()
        onLeave()
    }
    PlatformBackHandler(enabled = true, onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.meta?.name?.ifBlank { null } ?: "Listening room")
                        Text(
                            "Code: ${state.roomId}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSaveRoom) {
                        Icon(
                            if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Unsave room" else "Save room",
                        )
                    }
                    TextButton(onClick = leave) { Text("Leave") }
                },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(onClick = { showLibrary = true }) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = "Library")
                }
                FloatingActionButton(onClick = { showChat = true }) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat")
                }
                FloatingActionButton(onClick = { showSearch = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MembersBar(state)
            NowPlayingCard(
                state = state,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seek,
                onSkipNext = viewModel::skipNext,
                onTakeControl = viewModel::takeControl,
            )
            Text("Queue (${state.queue.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.queue, key = { it.key }) { item ->
                    QueueRow(
                        item = item,
                        onPlay = { viewModel.playFromQueue(item) },
                        onRemove = { viewModel.removeFromQueue(item) },
                    )
                }
            }
            state.error?.let { err ->
                Text("Error: $err", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }
        }

        if (showSearch) {
            SearchSheet(
                results = state.searchResults,
                searching = state.searching,
                onSearch = viewModel::search,
                onPick = {
                    viewModel.addToQueueAndMaybePlay(it)
                    viewModel.clearSearch()
                    showSearch = false
                },
                onDismiss = {
                    viewModel.clearSearch()
                    showSearch = false
                },
            )
        }
        if (showChat) {
            ChatSheet(
                state = state,
                onSend = viewModel::sendChat,
                onDismiss = { showChat = false },
            )
        }
        if (showLibrary) {
            LibrarySheet(
                library = library,
                onOpenPlaylist = viewModel::openLibraryPlaylist,
                onClosePlaylist = viewModel::closeLibraryPlaylist,
                onQueueTrack = {
                    viewModel.addToQueueAndMaybePlay(it)
                },
                onQueueHistory = viewModel::queueHistoryItem,
                onQueueAll = viewModel::queuePlaylist,
                onDismiss = {
                    viewModel.closeLibraryPlaylist()
                    showLibrary = false
                },
            )
        }
    }
}

@Composable
private fun LibrarySheet(
    library: LibraryState,
    onOpenPlaylist: (com.example.myapplicationmusicsharing.data.SavedPlaylist) -> Unit,
    onClosePlaylist: () -> Unit,
    onQueueTrack: (SearchResult) -> Unit,
    onQueueHistory: (com.example.myapplicationmusicsharing.data.HistoryItem) -> Unit,
    onQueueAll: (com.example.myapplicationmusicsharing.data.PlaylistDetails) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val opened = library.opened
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (opened == null) {
                Text("Your library", style = MaterialTheme.typography.titleMedium)
                if (library.loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                    if (library.playlists.isNotEmpty()) {
                        item {
                            Text("My playlists", style = MaterialTheme.typography.labelLarge)
                        }
                        items(library.playlists, key = { it.key }) { playlist ->
                            ListItem(
                                leadingContent = {
                                    AsyncImage(
                                        model = playlist.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                },
                                headlineContent = { Text(playlist.title, maxLines = 1) },
                                supportingContent = { Text("${playlist.trackCount} tracks") },
                                modifier = Modifier.clickable { onOpenPlaylist(playlist) },
                            )
                        }
                    } else {
                        item {
                            Text(
                                "No playlists yet — add one from the Home tab.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (library.history.isNotEmpty()) {
                        item {
                            Text(
                                "Recently played",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(library.history, key = { it.key }) { item ->
                            ListItem(
                                leadingContent = {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                },
                                headlineContent = { Text(item.title, maxLines = 1) },
                                supportingContent = { Text(item.artist, maxLines = 1) },
                                trailingContent = {
                                    TextButton(onClick = { onQueueHistory(item) }) { Text("Queue") }
                                },
                            )
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(opened.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("${opened.tracks.size} tracks", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClosePlaylist) { Text("Back") }
                }
                OutlinedButton(
                    onClick = { onQueueAll(opened) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Queue all") }
                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    items(opened.tracks, key = { it.videoId }) { track ->
                        ListItem(
                            leadingContent = {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                )
                            },
                            headlineContent = { Text(track.title, maxLines = 1) },
                            supportingContent = { Text(track.artist, maxLines = 1) },
                            trailingContent = {
                                TextButton(onClick = { onQueueTrack(track) }) { Text("Queue") }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersBar(state: RoomUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Live:", style = MaterialTheme.typography.labelLarge)
        state.members.forEach { m ->
            val isHost = state.meta?.hostId == m.userId
            Card(shape = RoundedCornerShape(50)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isHost) Icon(Icons.Default.Star, contentDescription = "Host", modifier = Modifier.size(16.dp))
                    Text(m.username.ifBlank { "?" })
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    state: RoomUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onTakeControl: () -> Unit,
) {
    val playback = state.playback
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (playback == null || playback.videoId.isBlank()) {
                Text("Nothing playing", style = MaterialTheme.typography.titleMedium)
                Text("Tap search to queue up a track.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = playback.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playback.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(playback.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    if (state.resolving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                ProgressRow(state, onSeek)
                ControlsRow(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onTakeControl = onTakeControl,
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(state: RoomUiState, onSeek: (Long) -> Unit) {
    val playback = state.playback ?: return

    // Firebase only stores position at the last host action; advance it
    // locally against the (server-corrected) clock while playing.
    var nowMs by remember { mutableLongStateOf(currentTimeMillis()) }
    LaunchedEffect(playback.isPlaying, playback.updatedAt) {
        while (playback.isPlaying) {
            nowMs = currentTimeMillis()
            delay(500)
        }
    }
    val livePosition = if (playback.isPlaying && playback.updatedAt > 0) {
        (playback.positionMs + (nowMs + state.serverOffsetMs - playback.updatedAt))
            .coerceIn(0L, playback.durationMs.coerceAtLeast(playback.positionMs))
    } else {
        playback.positionMs
    }

    var draftPos by remember(playback.positionMs, playback.videoId) {
        mutableFloatStateOf(playback.positionMs.toFloat())
    }
    var dragging by remember { mutableStateOf(false) }
    val duration = (playback.durationMs.takeIf { it > 0 } ?: 1L).toFloat()
    Slider(
        value = if (dragging) draftPos else livePosition.toFloat(),
        onValueChange = {
            dragging = true
            draftPos = it
        },
        onValueChangeFinished = {
            dragging = false
            if (state.isHost) onSeek(draftPos.toLong())
        },
        valueRange = 0f..duration,
        enabled = state.isHost,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatMs(livePosition), style = MaterialTheme.typography.bodySmall)
        Text(formatMs(playback.durationMs), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ControlsRow(
    state: RoomUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onTakeControl: () -> Unit,
) {
    val isHost = state.isHost
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlayPause, enabled = isHost) {
            val icon = if (state.playback?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow
            Icon(icon, contentDescription = "Play/Pause")
        }
        IconButton(onClick = onSkipNext, enabled = isHost) {
            Icon(Icons.Default.SkipNext, contentDescription = "Skip")
        }
        Spacer(Modifier.weight(1f))
        if (!isHost) {
            OutlinedButton(onClick = onTakeControl) { Text("Take control") }
        } else {
            Text("You are host", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, onPlay: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(item.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            TextButton(onClick = onPlay) { Text("Play") }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun SearchSheet(
    results: List<SearchResult>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onPick: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var query by remember { mutableStateOf("") }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Search YouTube", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Default.Search, contentDescription = "Go")
                }
            }
            if (searching) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                items(results, key = { it.videoId }) { r ->
                    ListItem(
                        leadingContent = {
                            AsyncImage(
                                model = r.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            )
                        },
                        headlineContent = { Text(r.title, maxLines = 2) },
                        supportingContent = { Text(r.artist) },
                        trailingContent = {
                            TextButton(onClick = { onPick(r) }) { Text("Queue") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSheet(
    state: RoomUiState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        var draft by remember { mutableStateOf("") }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chat", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp), reverseLayout = false) {
                items(state.chat, key = { it.key }) { msg ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(msg.username, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text(msg.text)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Message") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    if (draft.isNotBlank()) {
                        onSend(draft); draft = ""
                    }
                }) { Text("Send") }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

package io.github.meko123456.syncbeats.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import io.github.meko123456.syncbeats.core.domain.sync.DriftMath
import io.github.meko123456.syncbeats.core.domain.sync.ListenerSnapshot
import io.github.meko123456.syncbeats.core.domain.sync.ListenerStatus
import io.github.meko123456.syncbeats.core.domain.sync.SyncEngine
import io.github.meko123456.syncbeats.core.model.ChatMessage
import io.github.meko123456.syncbeats.core.model.HistoryItem
import io.github.meko123456.syncbeats.core.model.Member
import io.github.meko123456.syncbeats.core.model.PlaybackState
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.QueueItem
import io.github.meko123456.syncbeats.core.model.RoomMeta
import io.github.meko123456.syncbeats.core.model.SavedPlaylist
import io.github.meko123456.syncbeats.core.model.SearchResult
import io.github.meko123456.syncbeats.core.model.currentTimeMillis
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.domain.repository.RoomRepository
import io.github.meko123456.syncbeats.ui.ErrorCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LibraryState(
    val playlists: List<SavedPlaylist> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val opened: PlaylistDetails? = null,
    val loading: Boolean = false,
)

data class RoomUiState(
    val roomId: String = "",
    val serverOffsetMs: Long = 0L,
    val playback: PlaybackState? = null,
    val meta: RoomMeta? = null,
    val queue: List<QueueItem> = emptyList(),
    val members: List<Member> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val currentUserId: String = "",
    val currentUsername: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    val resolving: Boolean = false,
    val error: String? = null,
    /** Whether this listener is actually in step with the room — see [ListenerStatus]. */
    val listenerStatus: ListenerStatus = ListenerStatus.NothingPlaying,
) {
    val isHost: Boolean get() = currentUserId.isNotBlank() && meta?.hostId == currentUserId
}

private data class RoomData(
    val playback: PlaybackState?,
    val meta: RoomMeta?,
    val queue: List<QueueItem>,
    val members: List<Member>,
    val chat: List<ChatMessage>,
)

private data class UiExtras(
    val search: List<SearchResult>,
    val searching: Boolean,
    val resolving: Boolean,
    val error: String?,
    val username: String,
)

class RoomViewModel(
    private val roomId: String,
    private val autoplay: Boolean,
    private val firebase: RoomRepository,
    private val youtube: MusicSource,
    private val authRepo: AuthGateway,
    private val syncEngine: SyncEngine,
    private val playerController: PlayerController,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _search = MutableStateFlow<List<SearchResult>>(emptyList())
    private val _searching = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _username = MutableStateFlow("")

    init {
        // The engine reports load and auto-advance failures as data; turning them into a sentence
        // is this layer's job.
        viewModelScope.launch {
            syncEngine.failures.collect { failure -> _error.value = ErrorCopy.of(failure) }
        }
    }

    private val roomDataFlow = combine(
        firebase.observePlayback(roomId),
        firebase.observeMeta(roomId),
        firebase.observeQueue(roomId),
        firebase.observeMembers(roomId),
        firebase.observeChat(roomId),
    ) { playback, meta, queue, members, chat ->
        RoomData(playback, meta, queue, members, chat)
    }

    private val uiExtrasFlow = combine(
        _search,
        _searching,
        syncEngine.resolving,
        _error,
        _username,
    ) { search, searching, resolving, error, username ->
        UiExtras(search, searching, resolving, error, username)
    }

    /**
     * Drives the sync indicator. Room state only changes when the host does something, but drift
     * accumulates second by second, so the status has to be recomputed on a clock or it would
     * report "in sync" indefinitely while the listener quietly fell behind.
     */
    private val statusTicker = flow {
        while (true) {
            emit(Unit)
            delay(STATUS_TICK_MS)
        }
    }

    val state: StateFlow<RoomUiState> = combine(
        roomDataFlow,
        uiExtrasFlow,
        syncEngine.serverOffset,
        statusTicker,
    ) { data, extras, serverOffset, _ ->
        val user = authRepo.currentUser()
        val uid = user?.uid.orEmpty()
        val emailHandle = user?.email?.substringBefore("@").orEmpty()
        RoomUiState(
            roomId = roomId,
            serverOffsetMs = serverOffset,
            playback = data.playback,
            meta = data.meta,
            queue = data.queue,
            members = data.members,
            chat = data.chat,
            currentUserId = uid,
            currentUsername = extras.username.ifBlank { emailHandle },
            searchResults = extras.search,
            searching = extras.searching,
            resolving = extras.resolving,
            error = extras.error,
            listenerStatus = listenerStatus(data.playback, extras.resolving, serverOffset),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomUiState(roomId = roomId))

    private fun listenerStatus(
        playback: PlaybackState?,
        resolving: Boolean,
        serverOffsetMs: Long,
    ): ListenerStatus {
        val track = playback?.takeIf { it.videoId.isNotBlank() }
        val expected = track?.let {
            DriftMath.expectedPositionMs(
                state = it,
                serverNowMs = DriftMath.serverNowMs(currentTimeMillis(), serverOffsetMs),
            )
        } ?: 0L
        return ListenerStatus.of(
            ListenerSnapshot(
                hasTrack = track != null,
                roomIsPlaying = track?.isPlaying == true,
                resolving = resolving,
                playerConnected = playerController.connected.value,
                playerIsPlaying = playerController.isPlaying(),
                playerPositionMs = playerController.currentPositionMs(),
                expectedPositionMs = expected,
            ),
        )
    }

    /** Whether this room is bookmarked in the current user's saved rooms. */
    val isSaved: StateFlow<Boolean> =
        firebase.observeSavedRooms(authRepo.currentUser()?.uid.orEmpty())
            .map { rooms -> rooms.any { it.key == roomId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _libraryOpened = MutableStateFlow<PlaylistDetails?>(null)
    private val _libraryLoading = MutableStateFlow(false)

    val library: StateFlow<LibraryState> = combine(
        firebase.observePlaylists(authRepo.currentUser()?.uid.orEmpty()),
        firebase.observeHistory(authRepo.currentUser()?.uid.orEmpty()),
        _libraryOpened,
        _libraryLoading,
    ) { playlists, history, opened, loading ->
        LibraryState(
            playlists = playlists,
            history = history.distinctBy { it.videoId },
            opened = opened,
            loading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    private var lastLoggedVideoId: String? = null
    private var hasLeft = false

    init {
        connectPlayer()
        syncEngine.start(roomId)
        joinRoomPresence()
        logPlaysToHistory()
        if (autoplay) autoplayFirstQueued()
    }

    private fun connectPlayer() {
        viewModelScope.launch {
            runCatching { playerController.connect() }
                .onFailure { _error.value = ErrorCopy.of(it, "Could not connect to the player") }
        }
    }

    private fun joinRoomPresence() {
        viewModelScope.launch {
            val user = authRepo.currentUser() ?: return@launch
            val username = authRepo.fetchUsername(user.uid)
                ?: user.email?.substringBefore("@").orEmpty()
            _username.value = username
            // Connection blips fire our server-side onDisconnect cleanup, which
            // silently drops us from the members list. Re-assert presence on
            // every reconnect (canonical Firebase presence pattern).
            firebase.observeConnected().collect { connected ->
                if (connected && !hasLeft) {
                    runCatching {
                        firebase.joinRoom(roomId, user.uid, username)
                    }.onFailure { _error.value = ErrorCopy.of(it, "That did not work") }
                }
            }
        }
    }

    private fun logPlaysToHistory() {
        viewModelScope.launch {
            state.map { it.playback }.filterNotNull().collect { playback ->
                if (playback.videoId.isNotBlank() && playback.videoId != lastLoggedVideoId) {
                    lastLoggedVideoId = playback.videoId
                    authRepo.currentUser()?.uid?.let { uid ->
                        runCatching { firebase.logHistory(uid, playback) }
                    }
                }
            }
        }
    }

    /** Rooms created from Home arrive with a queue but nothing playing; kick it off. */
    private fun autoplayFirstQueued() {
        viewModelScope.launch {
            val ready = withTimeoutOrNull(10_000) {
                state.first { it.meta != null && it.queue.isNotEmpty() }
            } ?: return@launch
            val idle = ready.playback == null || ready.playback.videoId.isBlank()
            if (idle && ready.isHost) {
                playFromQueue(ready.queue.first())
            }
        }
    }

    /** Stops synced playback on this device and drops room presence. */
    fun leaveRoom() {
        hasLeft = true
        val uid = authRepo.currentUser()?.uid
        syncEngine.stop()
        if (uid != null) {
            // App scope: this write must survive this screen's teardown.
            appScope.launch { runCatching { firebase.leaveRoom(roomId, uid) } }
        }
    }

    fun toggleSaveRoom() {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            runCatching {
                if (isSaved.value) {
                    firebase.unsaveRoom(uid, roomId)
                } else {
                    val name = state.value.meta?.name?.ifBlank { null } ?: "Room $roomId"
                    firebase.saveRoom(uid, roomId, name)
                }
            }.onFailure { _error.value = ErrorCopy.of(it, "That did not work") }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _searching.value = true
            runCatching { youtube.search(query) }
                .onSuccess { _search.value = it }
                .onFailure { _error.value = ErrorCopy.of(it, "That did not work") }
            _searching.value = false
        }
    }

    fun clearSearch() {
        _search.value = emptyList()
    }

    fun addToQueueAndMaybePlay(result: SearchResult) {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            val item = result.toQueueItem()
            val currentPlayback = state.value.playback
            val noHost = state.value.meta?.hostId.isNullOrBlank()
            val nothingPlaying = currentPlayback == null || currentPlayback.videoId.isBlank()
            if (nothingPlaying && (state.value.isHost || noHost)) {
                if (noHost) firebase.takeControl(roomId, uid)
                syncEngine.hostLoadTrack(item)
                return@launch
            }
            firebase.addToQueue(roomId, item, uid)
        }
    }

    fun playFromQueue(item: QueueItem) {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            if (!state.value.isHost) firebase.takeControl(roomId, uid)
            syncEngine.hostLoadTrack(item)
            firebase.removeFromQueue(roomId, item.key)
        }
    }

    fun removeFromQueue(item: QueueItem) {
        viewModelScope.launch {
            firebase.removeFromQueue(roomId, item.key)
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch { syncEngine.hostTogglePlayPause() }
    }

    fun seek(positionMs: Long) {
        viewModelScope.launch { syncEngine.hostSeek(positionMs) }
    }

    fun skipNext() {
        viewModelScope.launch {
            val next = state.value.queue.firstOrNull() ?: run {
                syncEngine.hostStop()
                return@launch
            }
            val uid = authRepo.currentUser()?.uid ?: return@launch
            if (!state.value.isHost) firebase.takeControl(roomId, uid)
            syncEngine.hostLoadTrack(next)
            firebase.removeFromQueue(roomId, next.key)
        }
    }

    fun takeControl() {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            firebase.takeControl(roomId, uid)
        }
    }

    // ───────── In-room library ─────────

    fun openLibraryPlaylist(playlist: SavedPlaylist) {
        viewModelScope.launch {
            _libraryLoading.value = true
            runCatching { youtube.playlist(playlist.url) }
                .onSuccess { _libraryOpened.value = it }
                .onFailure { _error.value = ErrorCopy.of(it, "Could not load that playlist") }
            _libraryLoading.value = false
        }
    }

    fun closeLibraryPlaylist() {
        _libraryOpened.value = null
    }

    fun queueHistoryItem(item: HistoryItem) {
        addToQueueAndMaybePlay(
            SearchResult(
                videoId = item.videoId,
                title = item.title,
                artist = item.artist,
                thumbnailUrl = item.thumbnailUrl,
                durationMs = item.durationMs,
            )
        )
    }

    /** Queues a whole playlist; starts the first track if the room is idle. */
    fun queuePlaylist(details: PlaylistDetails) {
        viewModelScope.launch {
            val uid = authRepo.currentUser()?.uid ?: return@launch
            val tracks = details.tracks.take(50)
            val currentPlayback = state.value.playback
            val noHost = state.value.meta?.hostId.isNullOrBlank()
            val nothingPlaying = currentPlayback == null || currentPlayback.videoId.isBlank()
            var remaining = tracks
            if (nothingPlaying && (state.value.isHost || noHost)) {
                if (noHost) firebase.takeControl(roomId, uid)
                remaining.firstOrNull()?.let { first ->
                    syncEngine.hostLoadTrack(first.toQueueItem())
                    remaining = remaining.drop(1)
                }
            }
            runCatching {
                remaining.forEach { firebase.addToQueue(roomId, it.toQueueItem(), uid) }
            }.onFailure { _error.value = ErrorCopy.of(it, "That did not work") }
        }
    }

    private fun SearchResult.toQueueItem() = QueueItem(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
    )

    fun sendChat(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val user = authRepo.currentUser() ?: return@launch
            firebase.sendChat(
                roomId = roomId,
                userId = user.uid,
                username = state.value.currentUsername,
                text = text.trim(),
            )
        }
    }

    private companion object {
        /** Often enough for the indicator to be honest, rarely enough to be free. */
        const val STATUS_TICK_MS = 1_000L
    }

    fun clearError() {
        _error.update { null }
    }
}

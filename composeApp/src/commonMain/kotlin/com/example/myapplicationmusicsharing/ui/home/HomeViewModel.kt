package com.example.myapplicationmusicsharing.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationmusicsharing.data.AccountPlaylist
import com.example.myapplicationmusicsharing.data.AuthRepository
import com.example.myapplicationmusicsharing.data.FirebaseRepository
import com.example.myapplicationmusicsharing.data.GoogleAuthController
import com.example.myapplicationmusicsharing.data.HistoryItem
import com.example.myapplicationmusicsharing.data.MusicSource
import com.example.myapplicationmusicsharing.data.PlaylistDetails
import com.example.myapplicationmusicsharing.data.QueueItem
import com.example.myapplicationmusicsharing.data.SavedPlaylist
import com.example.myapplicationmusicsharing.data.SearchResult
import com.example.myapplicationmusicsharing.data.YouTubeAccountRepository
import com.example.myapplicationmusicsharing.data.signInSuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YtRails(
    /** Null until the connection check finishes. */
    val connected: Boolean? = null,
    val loading: Boolean = false,
    val playlists: List<AccountPlaylist> = emptyList(),
    val likedSongs: List<SearchResult> = emptyList(),
    val subscriptionFeed: List<SearchResult> = emptyList(),
)

data class HomeUiState(
    val history: List<HistoryItem> = emptyList(),
    val playlists: List<SavedPlaylist> = emptyList(),
    val trending: List<SearchResult> = emptyList(),
    val loadingTrending: Boolean = false,
    val importing: Boolean = false,
    val openedPlaylist: PlaylistDetails? = null,
    val loadingPlaylist: Boolean = false,
    val yt: YtRails = YtRails(),
    /** Room code to navigate to (with autoplay); UI consumes it. */
    val enterRoomId: String? = null,
    val error: String? = null,
)

private data class HomeExtras(
    val trending: List<SearchResult>,
    val loadingTrending: Boolean,
    val importing: Boolean,
    val openedPlaylist: PlaylistDetails?,
    val loadingPlaylist: Boolean,
)

private data class HomeSignals(
    val enterRoomId: String?,
    val error: String?,
)

class HomeViewModel(
    private val firebase: FirebaseRepository,
    private val youtube: MusicSource,
    private val authRepo: AuthRepository,
    private val ytAccount: YouTubeAccountRepository,
    private val google: GoogleAuthController,
) : ViewModel() {

    private val uid: String get() = authRepo.currentUser()?.uid.orEmpty()

    private val _trending = MutableStateFlow<List<SearchResult>>(emptyList())
    private val _loadingTrending = MutableStateFlow(false)
    private val _importing = MutableStateFlow(false)
    private val _openedPlaylist = MutableStateFlow<PlaylistDetails?>(null)
    private val _loadingPlaylist = MutableStateFlow(false)
    private val _yt = MutableStateFlow(YtRails())
    private val _enterRoomId = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    private val extrasFlow = combine(
        _trending, _loadingTrending, _importing, _openedPlaylist, _loadingPlaylist,
    ) { trending, loadingTrending, importing, opened, loadingPlaylist ->
        HomeExtras(trending, loadingTrending, importing, opened, loadingPlaylist)
    }

    private val signalsFlow = combine(_enterRoomId, _error) { enter, error ->
        HomeSignals(enter, error)
    }

    val state: StateFlow<HomeUiState> = combine(
        firebase.observeHistory(uid),
        firebase.observePlaylists(uid),
        extrasFlow,
        signalsFlow,
        _yt,
    ) { history, playlists, extras, signals, yt ->
        HomeUiState(
            history = history.distinctBy { it.videoId },
            playlists = playlists,
            trending = extras.trending,
            loadingTrending = extras.loadingTrending,
            importing = extras.importing,
            openedPlaylist = extras.openedPlaylist,
            loadingPlaylist = extras.loadingPlaylist,
            yt = yt,
            enterRoomId = signals.enterRoomId,
            error = signals.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshTrending()
        loadYouTubeRails()
    }

    fun refreshTrending() {
        if (_loadingTrending.value) return
        viewModelScope.launch {
            _loadingTrending.value = true
            runCatching { youtube.trending() }
                .onSuccess { _trending.value = it }
                .onFailure { _error.value = "Trending failed: ${it.message}" }
            _loadingTrending.value = false
        }
    }

    // ───────── YouTube account rails ─────────

    private fun loadYouTubeRails() {
        viewModelScope.launch {
            val connected = runCatching { ytAccount.isConnected() }.getOrDefault(false)
            if (!connected) {
                _yt.value = YtRails(connected = false)
                return@launch
            }
            _yt.value = YtRails(connected = true, loading = true)
            val playlists = runCatching { ytAccount.myPlaylists() }.getOrDefault(emptyList())
            val liked = runCatching { ytAccount.likedSongs() }.getOrDefault(emptyList())
            val feed = runCatching { ytAccount.subscriptionFeed() }.getOrDefault(emptyList())
            _yt.value = YtRails(
                connected = true,
                loading = false,
                playlists = playlists,
                likedSongs = liked,
                subscriptionFeed = feed,
            )
        }
    }

    /** Grants the YouTube scope (also signs into Google if needed), then loads rails. */
    fun connectYouTube() {
        viewModelScope.launch {
            runCatching { google.signInSuspend() }
                .onSuccess { loadYouTubeRails() }
                .onFailure { _error.value = "Could not connect YouTube: ${it.message}" }
        }
    }

    fun openAccountPlaylist(playlist: AccountPlaylist) {
        viewModelScope.launch {
            _loadingPlaylist.value = true
            runCatching { ytAccount.playlistItems(playlist.id) }
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        _error.value = "Playlist is empty"
                    } else {
                        _openedPlaylist.value = PlaylistDetails(
                            url = "account:${playlist.id}",
                            title = playlist.title,
                            thumbnailUrl = playlist.thumbnailUrl,
                            tracks = tracks,
                        )
                    }
                }
                .onFailure { _error.value = "Could not load playlist: ${it.message}" }
            _loadingPlaylist.value = false
        }
    }

    // ───────── Link-imported playlists ─────────

    fun importPlaylist(url: String) {
        if (url.isBlank() || _importing.value) return
        viewModelScope.launch {
            _importing.value = true
            runCatching {
                val details = youtube.playlist(url)
                firebase.savePlaylist(uid, details)
            }.onFailure { _error.value = "Import failed: ${it.message}" }
            _importing.value = false
        }
    }

    fun removePlaylist(playlist: SavedPlaylist) {
        viewModelScope.launch {
            runCatching { firebase.removePlaylist(uid, playlist.key) }
                .onFailure { _error.value = it.message }
        }
    }

    fun openPlaylist(playlist: SavedPlaylist) {
        viewModelScope.launch {
            _loadingPlaylist.value = true
            runCatching { youtube.playlist(playlist.url) }
                .onSuccess { _openedPlaylist.value = it }
                .onFailure { _error.value = "Could not load playlist: ${it.message}" }
            _loadingPlaylist.value = false
        }
    }

    fun closePlaylist() {
        _openedPlaylist.value = null
    }

    // ───────── Play in a fresh room ─────────

    fun playTrackInNewRoom(track: SearchResult) {
        startRoomWith(listOf(track.toQueueItem()), roomName = track.title)
    }

    fun playHistoryInNewRoom(item: HistoryItem) {
        val queueItem = QueueItem(
            videoId = item.videoId,
            title = item.title,
            artist = item.artist,
            thumbnailUrl = item.thumbnailUrl,
            durationMs = item.durationMs,
        )
        startRoomWith(listOf(queueItem), roomName = item.title)
    }

    fun playPlaylistInNewRoom(details: PlaylistDetails) {
        startRoomWith(
            details.tracks.take(MAX_PLAYLIST_QUEUE).map { it.toQueueItem() },
            roomName = details.title,
        )
    }

    private fun startRoomWith(items: List<QueueItem>, roomName: String) {
        val hostId = uid
        if (hostId.isEmpty() || items.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val code = firebase.createRoom(hostId, roomName.take(40))
                items.forEach { firebase.addToQueue(code, it, hostId) }
                code
            }
                .onSuccess { code -> _enterRoomId.value = code }
                .onFailure { _error.value = "Could not start room: ${it.message}" }
        }
    }

    fun consumeEnterRoom() {
        _enterRoomId.value = null
    }

    fun clearError() {
        _error.update { null }
    }

    private fun SearchResult.toQueueItem() = QueueItem(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
    )

    private companion object {
        const val MAX_PLAYLIST_QUEUE = 50
    }
}

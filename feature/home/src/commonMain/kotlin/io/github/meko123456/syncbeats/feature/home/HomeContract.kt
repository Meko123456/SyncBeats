package io.github.meko123456.syncbeats.feature.home

import io.github.meko123456.syncbeats.core.model.AccountPlaylist
import io.github.meko123456.syncbeats.core.model.HistoryItem
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.SavedPlaylist
import io.github.meko123456.syncbeats.core.model.SearchResult

/** The signed-in user's own YouTube rails. */
data class YtRails(
    /** Null until the connection check finishes. */
    val connected: Boolean? = null,
    val loading: Boolean = false,
    val playlists: List<AccountPlaylist> = emptyList(),
    val likedSongs: List<SearchResult> = emptyList(),
    val subscriptionFeed: List<SearchResult> = emptyList(),
)

/** Everything the home screen renders from. */
data class HomeState(
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

/** Everything the user can do on the home screen. */
sealed interface HomeIntent {
    data object RefreshTrending : HomeIntent
    data object ConnectYouTube : HomeIntent
    data class OpenAccountPlaylist(val playlist: AccountPlaylist) : HomeIntent
    data class ImportPlaylist(val url: String) : HomeIntent
    data class RemovePlaylist(val playlist: SavedPlaylist) : HomeIntent
    data class OpenPlaylist(val playlist: SavedPlaylist) : HomeIntent
    data object ClosePlaylist : HomeIntent
    data class PlayTrackInNewRoom(val track: SearchResult) : HomeIntent
    data class PlayHistoryInNewRoom(val item: HistoryItem) : HomeIntent
    data class PlayPlaylistInNewRoom(val details: PlaylistDetails) : HomeIntent
    data object ConsumeEnterRoom : HomeIntent
    data object ClearError : HomeIntent
}

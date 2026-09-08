package io.github.meko123456.syncbeats.feature.room

import io.github.meko123456.syncbeats.core.model.HistoryItem
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.QueueItem
import io.github.meko123456.syncbeats.core.model.SavedPlaylist
import io.github.meko123456.syncbeats.core.model.SearchResult

/**
 * Everything the user can do inside a room.
 *
 * Generated from the ViewModel's own method signatures rather than transcribed, so the intent
 * list cannot drift out of step with what the screen can actually ask for.
 */
sealed interface RoomIntent {
    data object LeaveRoom : RoomIntent
    data object ToggleSaveRoom : RoomIntent
    data class Search(val query: String) : RoomIntent
    data object ClearSearch : RoomIntent
    data class AddToQueueAndMaybePlay(val result: SearchResult) : RoomIntent
    data class PlayFromQueue(val item: QueueItem) : RoomIntent
    data class RemoveFromQueue(val item: QueueItem) : RoomIntent
    data object TogglePlayPause : RoomIntent
    data class Seek(val positionMs: Long) : RoomIntent
    data object SkipNext : RoomIntent
    data object TakeControl : RoomIntent
    data class OpenLibraryPlaylist(val playlist: SavedPlaylist) : RoomIntent
    data object CloseLibraryPlaylist : RoomIntent
    data class QueueHistoryItem(val item: HistoryItem) : RoomIntent
    data class QueuePlaylist(val details: PlaylistDetails) : RoomIntent
    data class SendChat(val text: String) : RoomIntent
    data object ClearError : RoomIntent
}

package io.github.meko123456.syncbeats.feature.lobby

import io.github.meko123456.syncbeats.core.model.SavedRoom

/** Everything the lobby renders from. */
data class LobbyState(
    val savedRooms: List<SavedRoom> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Set when a room was created or validated; the UI navigates and consumes it. */
    val enterRoomId: String? = null,
)

/** Everything the user can do in the lobby. */
sealed interface LobbyIntent {
    data class CreateRoom(val name: String) : LobbyIntent
    data class JoinRoom(val code: String) : LobbyIntent
    data class OpenSavedRoom(val room: SavedRoom) : LobbyIntent
    data class RemoveSavedRoom(val room: SavedRoom) : LobbyIntent
    data object ConsumeEnterRoom : LobbyIntent
    data object ClearError : LobbyIntent
}

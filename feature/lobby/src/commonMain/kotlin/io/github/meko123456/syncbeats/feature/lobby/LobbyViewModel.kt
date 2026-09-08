package io.github.meko123456.syncbeats.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.meko123456.syncbeats.core.designsystem.ErrorCopy
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.domain.repository.RoomRepository
import io.github.meko123456.syncbeats.core.model.RoomCode
import io.github.meko123456.syncbeats.core.model.SavedRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


private data class LobbyLocal(
    val loading: Boolean = false,
    val error: String? = null,
    val enterRoomId: String? = null,
)


class LobbyViewModel(
    private val firebase: RoomRepository,
    private val authRepo: AuthGateway,
) : ViewModel() {

    /** The single entry point for everything the lobby can ask for. */
    fun onIntent(intent: LobbyIntent) {
        when (intent) {
            is LobbyIntent.CreateRoom -> createRoom(intent.name)
            is LobbyIntent.JoinRoom -> joinRoom(intent.code)
            is LobbyIntent.OpenSavedRoom -> openSavedRoom(intent.room)
            is LobbyIntent.RemoveSavedRoom -> removeSavedRoom(intent.room)
            LobbyIntent.ConsumeEnterRoom -> consumeEnterRoom()
            LobbyIntent.ClearError -> clearError()
        }
    }

    private val uid: String get() = authRepo.currentUser()?.uid.orEmpty()

    private val _local = MutableStateFlow(LobbyLocal())

    val state: StateFlow<LobbyState> = combine(
        _local,
        firebase.observeSavedRooms(uid),
    ) { local, savedRooms ->
        LobbyState(
            savedRooms = savedRooms.sortedByDescending { it.savedAt },
            loading = local.loading,
            error = local.error,
            enterRoomId = local.enterRoomId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LobbyState())

    private fun createRoom(name: String) {
        val hostId = uid
        if (hostId.isEmpty()) return
        viewModelScope.launch {
            _local.update { it.copy(loading = true, error = null) }
            runCatching {
                val roomName = name.trim().ifEmpty { "Listening room" }
                val code = firebase.createRoom(hostId, roomName)
                // Rooms created here are deliberate (named) — bookmark them.
                firebase.saveRoom(hostId, code, roomName)
                code
            }
                .onSuccess { code -> _local.update { it.copy(loading = false, enterRoomId = code) } }
                .onFailure { t -> _local.update { it.copy(loading = false, error = ErrorCopy.of(t, "That did not work")) } }
        }
    }

    private fun joinRoom(code: String) {
        val normalized = RoomCode.normalise(code)
        // Checking the characters too, not just the length: "OO0011" is six characters long, and
        // querying for it produced "No room found", which blames the room for a typo.
        RoomCode.problemWith(normalized)?.let { problem ->
            _local.update { it.copy(error = RoomCode.describe(problem)) }
            return
        }
        viewModelScope.launch {
            _local.update { it.copy(loading = true, error = null) }
            runCatching { firebase.findRoom(normalized) }
                .onSuccess { meta ->
                    if (meta == null) {
                        _local.update {
                            it.copy(loading = false, error = "No room found for code $normalized")
                        }
                    } else {
                        _local.update { it.copy(loading = false, enterRoomId = normalized) }
                    }
                }
                .onFailure { t -> _local.update { it.copy(loading = false, error = ErrorCopy.of(t, "That did not work")) } }
        }
    }

    private fun openSavedRoom(room: SavedRoom) {
        joinRoom(room.key)
    }

    private fun removeSavedRoom(room: SavedRoom) {
        viewModelScope.launch {
            runCatching { firebase.unsaveRoom(uid, room.key) }
                .onFailure { t -> _local.update { it.copy(error = ErrorCopy.of(t, "That did not work")) } }
        }
    }

    private fun consumeEnterRoom() {
        _local.update { it.copy(enterRoomId = null) }
    }

    private fun clearError() {
        _local.update { it.copy(error = null) }
    }
}

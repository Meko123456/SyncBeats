package com.example.myapplicationmusicsharing.data

import com.example.myapplicationmusicsharing.Constants
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DatabaseReference
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

class FirebaseRepository {

    private val db get() = Firebase.database

    private fun roomRef(roomId: String): DatabaseReference = db.reference("rooms/$roomId")
    private fun playbackRef(roomId: String) = roomRef(roomId).child("playback")
    private fun queueRef(roomId: String) = roomRef(roomId).child("queue")
    private fun membersRef(roomId: String) = roomRef(roomId).child("members")
    private fun chatRef(roomId: String) = roomRef(roomId).child("chat")
    private fun metaRef(roomId: String) = roomRef(roomId).child("meta")

    /** True while this client has a live connection to the Firebase backend. */
    fun observeConnected(): Flow<Boolean> =
        db.reference(".info/connected").valueEvents.map { it.value<Boolean?>() ?: false }

    fun observeServerTimeOffset(): Flow<Long> =
        db.reference(".info/serverTimeOffset").valueEvents.map {
            (it.value<Double?>() ?: 0.0).toLong()
        }

    fun observePlayback(roomId: String): Flow<PlaybackState?> =
        playbackRef(roomId).valueEvents.map { snapshot ->
            if (snapshot.exists) snapshot.value<PlaybackState?>() else null
        }

    fun observeMeta(roomId: String): Flow<RoomMeta?> =
        metaRef(roomId).valueEvents.map { snapshot ->
            if (snapshot.exists) snapshot.value<RoomMeta?>() else null
        }

    fun observeQueue(roomId: String): Flow<List<QueueItem>> =
        queueRef(roomId).orderByChild("addedAt").valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                runCatching { child.value<QueueItem>() }.getOrNull()
                    ?.also { it.key = child.key.orEmpty() }
            }
        }

    fun observeMembers(roomId: String): Flow<List<Member>> =
        membersRef(roomId).valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                runCatching { child.value<Member>() }.getOrNull()
                    ?.also { it.userId = child.key.orEmpty() }
            }
        }

    fun observeChat(roomId: String): Flow<List<ChatMessage>> =
        chatRef(roomId).orderByChild("sentAt").limitToLast(100).valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                runCatching { child.value<ChatMessage>() }.getOrNull()
                    ?.also { it.key = child.key.orEmpty() }
            }.sortedBy { it.sentAt }
        }

    /** Creates a room with a fresh shareable code and returns that code. */
    suspend fun createRoom(hostId: String, name: String): String {
        repeat(5) {
            val code = generateRoomCode()
            val ref = metaRef(code)
            if (!ref.valueEvents.first().exists) {
                ref.setValue(
                    mapOf(
                        "hostId" to hostId,
                        "name" to name,
                        "createdAt" to ServerValue.TIMESTAMP,
                    )
                )
                return code
            }
        }
        error("Could not allocate a room code, please try again")
    }

    /** Returns the room's meta, or null if no room exists for this code. */
    suspend fun findRoom(roomId: String): RoomMeta? {
        val snapshot = metaRef(roomId).valueEvents.first()
        return if (snapshot.exists) snapshot.value<RoomMeta?>() else null
    }

    private fun generateRoomCode(): String =
        buildString {
            repeat(Constants.ROOM_CODE_LENGTH) {
                append(Constants.ROOM_CODE_ALPHABET.random(Random))
            }
        }

    suspend fun joinRoom(roomId: String, userId: String, username: String) {
        val ref = membersRef(roomId).child(userId)
        // Arm the cleanup before publishing presence so a drop right after the
        // write can never leave a ghost member behind.
        ref.onDisconnect().removeValue()
        ref.setValue(
            mapOf(
                "username" to username,
                "joinedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun leaveRoom(roomId: String, userId: String) {
        val ref = membersRef(roomId).child(userId)
        ref.onDisconnect().cancel()
        ref.removeValue()
    }

    suspend fun takeControl(roomId: String, userId: String) {
        metaRef(roomId).child("hostId").setValue(userId)
    }

    suspend fun setPlayback(roomId: String, state: PlaybackState) {
        playbackRef(roomId).setValue(
            mapOf(
                "videoId" to state.videoId,
                "title" to state.title,
                "artist" to state.artist,
                "thumbnailUrl" to state.thumbnailUrl,
                "durationMs" to state.durationMs,
                "isPlaying" to state.isPlaying,
                "positionMs" to state.positionMs,
                "updatedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun updatePlayingFlag(roomId: String, isPlaying: Boolean, positionMs: Long) {
        playbackRef(roomId).updateChildren(
            mapOf(
                "isPlaying" to isPlaying,
                "positionMs" to positionMs,
                "updatedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun seek(roomId: String, positionMs: Long, isPlaying: Boolean) {
        playbackRef(roomId).updateChildren(
            mapOf(
                "positionMs" to positionMs,
                "isPlaying" to isPlaying,
                "updatedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun addToQueue(roomId: String, item: QueueItem, addedBy: String) {
        queueRef(roomId).push().setValue(
            mapOf(
                "videoId" to item.videoId,
                "title" to item.title,
                "artist" to item.artist,
                "thumbnailUrl" to item.thumbnailUrl,
                "durationMs" to item.durationMs,
                "addedBy" to addedBy,
                "addedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun removeFromQueue(roomId: String, key: String) {
        queueRef(roomId).child(key).removeValue()
    }

    suspend fun sendChat(roomId: String, userId: String, username: String, text: String) {
        chatRef(roomId).push().setValue(
            mapOf(
                "userId" to userId,
                "username" to username,
                "text" to text,
                "sentAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    // ───────── Per-user library ─────────

    private fun playlistsRef(uid: String) = db.reference("users/$uid/playlists")
    private fun historyRef(uid: String) = db.reference("users/$uid/history")
    private fun savedRoomsRef(uid: String) = db.reference("users/$uid/savedRooms")

    fun observeSavedRooms(uid: String): Flow<List<SavedRoom>> =
        savedRoomsRef(uid).orderByChild("savedAt").valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                runCatching { child.value<SavedRoom>() }.getOrNull()
                    ?.also { it.key = child.key.orEmpty() }
            }
        }

    suspend fun saveRoom(uid: String, roomId: String, name: String) {
        savedRoomsRef(uid).child(roomId).setValue(
            mapOf(
                "name" to name,
                "savedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun unsaveRoom(uid: String, roomId: String) {
        savedRoomsRef(uid).child(roomId).removeValue()
    }

    fun observePlaylists(uid: String): Flow<List<SavedPlaylist>> =
        playlistsRef(uid).orderByChild("addedAt").valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                runCatching { child.value<SavedPlaylist>() }.getOrNull()
                    ?.also { it.key = child.key.orEmpty() }
            }.reversed()
        }

    suspend fun savePlaylist(uid: String, details: PlaylistDetails) {
        playlistsRef(uid).push().setValue(
            mapOf(
                "url" to details.url,
                "title" to details.title,
                "thumbnailUrl" to details.thumbnailUrl,
                "trackCount" to details.tracks.size,
                "addedAt" to ServerValue.TIMESTAMP,
            )
        )
    }

    suspend fun removePlaylist(uid: String, key: String) {
        playlistsRef(uid).child(key).removeValue()
    }

    fun observeHistory(uid: String): Flow<List<HistoryItem>> =
        historyRef(uid).orderByChild("playedAt").limitToLast(15).valueEvents.map { snapshot ->
            snapshot.children.mapNotNull { child ->
                runCatching { child.value<HistoryItem>() }.getOrNull()
                    ?.also { it.key = child.key.orEmpty() }
            }.sortedByDescending { it.playedAt }
        }

    suspend fun logHistory(uid: String, state: PlaybackState) {
        if (state.videoId.isBlank()) return
        historyRef(uid).push().setValue(
            mapOf(
                "videoId" to state.videoId,
                "title" to state.title,
                "artist" to state.artist,
                "thumbnailUrl" to state.thumbnailUrl,
                "durationMs" to state.durationMs,
                "playedAt" to ServerValue.TIMESTAMP,
            )
        )
    }
}

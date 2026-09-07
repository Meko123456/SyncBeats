package io.github.meko123456.syncbeats.core.domain.repository

import io.github.meko123456.syncbeats.core.model.ChatMessage
import io.github.meko123456.syncbeats.core.model.HistoryItem
import io.github.meko123456.syncbeats.core.model.Member
import io.github.meko123456.syncbeats.core.model.PlaybackState
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.QueueItem
import io.github.meko123456.syncbeats.core.model.RoomMeta
import io.github.meko123456.syncbeats.core.model.SavedPlaylist
import io.github.meko123456.syncbeats.core.model.SavedRoom
import kotlinx.coroutines.flow.Flow

/**
 * The realtime room: playback position, queue, members, chat, and the per-user library.
 *
 * The port the screens and the sync engine talk to. Firebase is one implementation of it, and the
 * only place `dev.gitlive` appears — before this, the whole app depended on the concrete
 * FirebaseRepository, so nothing above it could be tested without a Firebase project.
 *
 * The signatures are exactly the ones the old class exposed, so this is an extraction and not a
 * redesign: the behaviour cannot drift.
 */
interface RoomRepository {
    fun observeConnected(): Flow<Boolean>
    fun observeServerTimeOffset(): Flow<Long>
    fun observePlayback(roomId: String): Flow<PlaybackState?>
    fun observeMeta(roomId: String): Flow<RoomMeta?>
    fun observeQueue(roomId: String): Flow<List<QueueItem>>
    fun observeMembers(roomId: String): Flow<List<Member>>
    fun observeChat(roomId: String): Flow<List<ChatMessage>>
    suspend fun createRoom(hostId: String, name: String): String
    suspend fun findRoom(roomId: String): RoomMeta?
    suspend fun joinRoom(roomId: String, userId: String, username: String)
    suspend fun leaveRoom(roomId: String, userId: String)
    suspend fun takeControl(roomId: String, userId: String)
    suspend fun setPlayback(roomId: String, state: PlaybackState)
    suspend fun updatePlayingFlag(roomId: String, isPlaying: Boolean, positionMs: Long)
    suspend fun seek(roomId: String, positionMs: Long, isPlaying: Boolean)
    suspend fun addToQueue(roomId: String, item: QueueItem, addedBy: String)
    suspend fun removeFromQueue(roomId: String, key: String)
    suspend fun sendChat(roomId: String, userId: String, username: String, text: String)
    fun observeSavedRooms(uid: String): Flow<List<SavedRoom>>
    suspend fun saveRoom(uid: String, roomId: String, name: String)
    suspend fun unsaveRoom(uid: String, roomId: String)
    fun observePlaylists(uid: String): Flow<List<SavedPlaylist>>
    suspend fun savePlaylist(uid: String, details: PlaylistDetails)
    suspend fun removePlaylist(uid: String, key: String)
    fun observeHistory(uid: String): Flow<List<HistoryItem>>
    suspend fun logHistory(uid: String, state: PlaybackState)
}

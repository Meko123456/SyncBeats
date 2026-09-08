package io.github.meko123456.syncbeats.core.testing

import io.github.meko123456.syncbeats.core.domain.repository.RoomRepository
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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory [RoomRepository].
 *
 * Deliberately a working store rather than a stub: writes are visible to the reads, so a test can
 * assert that creating a room also bookmarks it, or that a queued track actually lands in the
 * queue. Stubs that return empty would let those bugs through.
 *
 * [failWith] makes the next call throw, which is how the error paths get covered.
 */
class FakeRoomRepository : RoomRepository {

    /** Set to make every subsequent suspend call throw, for exercising failure branches. */
    var failWith: Throwable? = null

    var nextRoomCode: String = "ABC234"
    val createdRooms = mutableListOf<Pair<String, String>>()
    val savedRoomCalls = mutableListOf<Triple<String, String, String>>()
    val joined = mutableListOf<Pair<String, String>>()
    val left = mutableListOf<Pair<String, String>>()
    val chatSent = mutableListOf<String>()
    val queueAdds = mutableListOf<QueueItem>()
    val historyLogged = mutableListOf<PlaybackState>()
    var existingRoom: RoomMeta? = null

    /** Codes actually looked up. A validation guard should keep invalid ones out of here. */
    val findRoomCalls = mutableListOf<String>()

    private val playback = MutableStateFlow<PlaybackState?>(null)
    private val meta = MutableStateFlow<RoomMeta?>(null)
    private val queue = MutableStateFlow<List<QueueItem>>(emptyList())
    private val members = MutableStateFlow<List<Member>>(emptyList())
    private val chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val savedRooms = MutableStateFlow<List<SavedRoom>>(emptyList())
    private val playlists = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    private val history = MutableStateFlow<List<HistoryItem>>(emptyList())

    fun emitSavedRooms(rooms: List<SavedRoom>) { savedRooms.value = rooms }
    fun emitPlaylists(items: List<SavedPlaylist>) { playlists.value = items }
    fun emitHistory(items: List<HistoryItem>) { history.value = items }
    fun emitQueue(items: List<QueueItem>) { queue.value = items }
    fun emitPlayback(state: PlaybackState?) { playback.value = state }
    fun emitMeta(value: RoomMeta?) { meta.value = value }
    fun emitMembers(value: List<Member>) { members.value = value }

    private fun guard() { failWith?.let { throw it } }

    override fun observeConnected(): Flow<Boolean> = MutableStateFlow(true)
    override fun observeServerTimeOffset(): Flow<Long> = MutableStateFlow(0L)
    override fun observePlayback(roomId: String): Flow<PlaybackState?> = playback
    override fun observeMeta(roomId: String): Flow<RoomMeta?> = meta
    override fun observeQueue(roomId: String): Flow<List<QueueItem>> = queue
    override fun observeMembers(roomId: String): Flow<List<Member>> = members
    override fun observeChat(roomId: String): Flow<List<ChatMessage>> = chat
    override fun observeSavedRooms(uid: String): Flow<List<SavedRoom>> = savedRooms
    override fun observePlaylists(uid: String): Flow<List<SavedPlaylist>> = playlists
    override fun observeHistory(uid: String): Flow<List<HistoryItem>> = history

    override suspend fun createRoom(hostId: String, name: String): String {
        guard(); createdRooms += hostId to name; return nextRoomCode
    }

    override suspend fun findRoom(roomId: String): RoomMeta? {
        guard(); findRoomCalls += roomId; return existingRoom
    }

    override suspend fun joinRoom(roomId: String, userId: String, username: String) {
        guard(); joined += roomId to userId
    }

    override suspend fun leaveRoom(roomId: String, userId: String) { guard(); left += roomId to userId }

    override suspend fun takeControl(roomId: String, userId: String) {
        guard(); meta.value = meta.value?.copy(hostId = userId)
    }

    override suspend fun setPlayback(roomId: String, state: PlaybackState) { guard(); playback.value = state }

    override suspend fun updatePlayingFlag(roomId: String, isPlaying: Boolean, positionMs: Long) {
        guard(); playback.value = playback.value?.copy(isPlaying = isPlaying, positionMs = positionMs)
    }

    override suspend fun seek(roomId: String, positionMs: Long, isPlaying: Boolean) {
        guard(); playback.value = playback.value?.copy(positionMs = positionMs, isPlaying = isPlaying)
    }

    override suspend fun addToQueue(roomId: String, item: QueueItem, addedBy: String) {
        guard(); queueAdds += item; queue.value = queue.value + item
    }

    override suspend fun removeFromQueue(roomId: String, key: String) {
        guard(); queue.value = queue.value.filterNot { it.key == key }
    }

    override suspend fun sendChat(roomId: String, userId: String, username: String, text: String) {
        guard(); chatSent += text
    }

    override suspend fun saveRoom(uid: String, roomId: String, name: String) {
        guard(); savedRoomCalls += Triple(uid, roomId, name)
    }

    override suspend fun unsaveRoom(uid: String, roomId: String) {
        guard(); savedRooms.value = savedRooms.value.filterNot { it.key == roomId }
    }

    override suspend fun savePlaylist(uid: String, details: PlaylistDetails) { guard() }

    override suspend fun removePlaylist(uid: String, key: String) {
        guard(); playlists.value = playlists.value.filterNot { it.key == key }
    }

    override suspend fun logHistory(uid: String, state: PlaybackState) { guard(); historyLogged += state }
}

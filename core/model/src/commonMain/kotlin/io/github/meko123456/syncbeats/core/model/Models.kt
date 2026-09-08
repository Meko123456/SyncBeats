package io.github.meko123456.syncbeats.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlaybackState(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class QueueItem(
    @Transient var key: String = "",
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
    val addedBy: String = "",
    val addedAt: Long = 0L,
)

@Serializable
data class Member(
    @Transient var userId: String = "",
    val username: String = "",
    val joinedAt: Long = 0L,
)

@Serializable
data class ChatMessage(
    @Transient var key: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val sentAt: Long = 0L,
)

@Serializable
data class RoomMeta(
    val hostId: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
)

@Serializable
data class SavedRoom(
    /** The room code. */
    @Transient var key: String = "",
    val name: String = "",
    val savedAt: Long = 0L,
)

@Serializable
data class SavedPlaylist(
    @Transient var key: String = "",
    val url: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val trackCount: Int = 0,
    val addedAt: Long = 0L,
)

@Serializable
data class HistoryItem(
    @Transient var key: String = "",
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
    val playedAt: Long = 0L,
)

data class PlaylistDetails(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val tracks: List<SearchResult>,
)

data class SearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationMs: Long,
)

data class ResolvedStream(
    val url: String,
    val mimeType: String?,
    val durationMs: Long,
)

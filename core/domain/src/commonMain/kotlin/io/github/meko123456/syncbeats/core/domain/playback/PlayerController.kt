package io.github.meko123456.syncbeats.core.domain.playback

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform audio player driven by [io.github.meko123456.syncbeats.sync.SyncEngine].
 * Android: Media3/ExoPlayer behind a MediaSession. iOS: AVPlayer.
 */
interface PlayerController {
    /** True once the underlying player is ready to receive commands. */
    val connected: StateFlow<Boolean>

    /** Emits once each time the current track plays to its end. */
    val playbackEnded: SharedFlow<Unit>

    /** Binds/creates the platform player. Safe to call repeatedly. */
    suspend fun connect()

    fun loadAndPrepare(
        url: String,
        mimeType: String?,
        title: String,
        artist: String,
        thumb: String,
        startMs: Long,
        playWhenReady: Boolean,
    )

    fun setPlayWhenReady(value: Boolean)
    fun seekTo(positionMs: Long)
    fun currentPositionMs(): Long
    fun isPlaying(): Boolean
    fun stop()
}

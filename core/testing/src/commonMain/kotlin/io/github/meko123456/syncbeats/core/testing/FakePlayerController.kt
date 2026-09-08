package io.github.meko123456.syncbeats.core.testing

import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A [PlayerController] that records commands instead of producing sound. */
class FakePlayerController : PlayerController {

    data class Loaded(val url: String, val title: String, val startMs: Long, val playWhenReady: Boolean)

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected

    private val _playbackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val playbackEnded: SharedFlow<Unit> = _playbackEnded

    val loads = mutableListOf<Loaded>()
    val seeks = mutableListOf<Long>()
    var playWhenReady: Boolean = false; private set
    var stopped = false; private set
    var position: Long = 0L
    var playing: Boolean = false

    /** Emit the end-of-track signal the sync engine advances the queue on. */
    suspend fun endTrack() = _playbackEnded.emit(Unit)

    override suspend fun connect() { _connected.value = true }

    override fun loadAndPrepare(
        url: String,
        mimeType: String?,
        title: String,
        artist: String,
        thumb: String,
        startMs: Long,
        playWhenReady: Boolean,
    ) {
        loads += Loaded(url, title, startMs, playWhenReady)
        this.playWhenReady = playWhenReady
        position = startMs
    }

    override fun setPlayWhenReady(value: Boolean) { playWhenReady = value; playing = value }
    override fun seekTo(positionMs: Long) { seeks += positionMs; position = positionMs }
    override fun currentPositionMs(): Long = position
    override fun isPlaying(): Boolean = playing
    override fun stop() { stopped = true; playing = false }
}

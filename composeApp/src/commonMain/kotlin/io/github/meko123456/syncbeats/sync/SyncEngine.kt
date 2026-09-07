package io.github.meko123456.syncbeats.sync

import io.github.meko123456.syncbeats.core.model.Constants
import io.github.meko123456.syncbeats.core.model.PlaybackState
import io.github.meko123456.syncbeats.core.model.QueueItem
import io.github.meko123456.syncbeats.core.model.RoomMeta
import io.github.meko123456.syncbeats.core.model.currentTimeMillis
import io.github.meko123456.syncbeats.data.AuthRepository
import io.github.meko123456.syncbeats.data.FirebaseRepository
import io.github.meko123456.syncbeats.data.MusicSource
import io.github.meko123456.syncbeats.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncEngine(
    private val firebase: FirebaseRepository,
    private val music: MusicSource,
    private val player: PlayerController,
    private val auth: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _serverOffset = MutableStateFlow(0L)
    val serverOffset: StateFlow<Long> = _serverOffset

    private val _currentState = MutableStateFlow<PlaybackState?>(null)
    val currentState: StateFlow<PlaybackState?> = _currentState

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving

    /**
     * Failures worth telling the listener about. These used to be swallowed by a println, so a
     * track that could not be resolved simply never started and the room sat there in silence
     * with no explanation.
     */
    private val _failures = MutableSharedFlow<SyncFailure>(extraBufferCapacity = 8)
    val failures: SharedFlow<SyncFailure> = _failures

    private var offsetJob: Job? = null
    private val roomJobs = mutableListOf<Job>()
    private var driftJob: Job? = null
    private var lastAppliedVideoId: String? = null
    private var advancing = false

    private var latestMeta: RoomMeta? = null
    private var latestQueue: List<QueueItem> = emptyList()

    var activeRoomId: String? = null
        private set

    fun start(roomId: String) {
        if (activeRoomId == roomId) return
        stop()
        activeRoomId = roomId
        if (offsetJob == null) {
            offsetJob = scope.launch {
                firebase.observeServerTimeOffset().collect { _serverOffset.value = it }
            }
        }
        roomJobs += scope.launch {
            // Wait until the platform player is bound before driving it.
            player.connected.first { it }
            firebase.observePlayback(roomId).collect { state ->
                _currentState.value = state
                if (state != null) applyRemoteState(state)
            }
        }
        roomJobs += scope.launch {
            firebase.observeMeta(roomId).collect { latestMeta = it }
        }
        roomJobs += scope.launch {
            firebase.observeQueue(roomId).collect { latestQueue = it }
        }
        roomJobs += scope.launch {
            player.playbackEnded.collect { onTrackEnded() }
        }
    }

    fun stop() {
        roomJobs.forEach { it.cancel() }
        roomJobs.clear()
        stopDriftLoop()
        activeRoomId = null
        lastAppliedVideoId = null
        latestMeta = null
        latestQueue = emptyList()
        _currentState.value = null
        player.stop()
    }

    private suspend fun applyRemoteState(state: PlaybackState) {
        if (state.videoId.isBlank()) {
            player.stop()
            lastAppliedVideoId = null
            stopDriftLoop()
            return
        }
        val expectedPosition = expectedPositionMs(state)
        // The track ran out while nobody was applying it (e.g. the room sat
        // idle with isPlaying stuck true). Don't start it; let the host clean up.
        if (state.isPlaying && DriftMath.isPastEnd(expectedPosition, state.durationMs)) {
            lastAppliedVideoId = state.videoId
            stopDriftLoop()
            player.stop()
            onTrackEnded()
            return
        }
        if (state.videoId != lastAppliedVideoId) {
            lastAppliedVideoId = state.videoId
            loadTrack(state, expectedPosition)
        } else {
            applyPlayPauseAndDrift(state, expectedPosition)
        }
        manageDriftLoop(state.isPlaying)
    }

    private suspend fun loadTrack(state: PlaybackState, startMs: Long) {
        _resolving.value = true
        try {
            val resolved = music.resolveAudioStream(state.videoId)
            player.loadAndPrepare(
                url = resolved.url,
                mimeType = resolved.mimeType,
                title = state.title,
                artist = state.artist,
                thumb = state.thumbnailUrl,
                startMs = startMs.coerceAtLeast(0L),
                playWhenReady = state.isPlaying,
            )
        } catch (t: Throwable) {
            _failures.emit(SyncFailure(SyncFailure.Kind.LOAD_TRACK, state.title, t))
        } finally {
            _resolving.value = false
        }
    }

    private fun applyPlayPauseAndDrift(state: PlaybackState, expectedPosition: Long) {
        if (DriftMath.shouldSeek(player.currentPositionMs(), expectedPosition)) {
            player.seekTo(DriftMath.seekTargetMs(expectedPosition))
        }
        if (state.isPlaying && !player.isPlaying()) player.setPlayWhenReady(true)
        if (!state.isPlaying && player.isPlaying()) player.setPlayWhenReady(false)
    }

    private fun expectedPositionMs(state: PlaybackState): Long =
        DriftMath.expectedPositionMs(
            state = state,
            serverNowMs = DriftMath.serverNowMs(currentTimeMillis(), _serverOffset.value),
        )

    private fun manageDriftLoop(isPlaying: Boolean) {
        if (isPlaying) {
            if (driftJob?.isActive == true) return
            driftJob = scope.launch {
                while (true) {
                    delay(Constants.DRIFT_CHECK_INTERVAL_MS)
                    val state = _currentState.value ?: continue
                    if (!state.isPlaying) continue
                    val expected = expectedPositionMs(state)
                    // Past the end of the track the player sits in its ended state;
                    // re-seeking there just re-triggers ended events.
                    if (DriftMath.isPastEnd(expected, state.durationMs)) continue
                    if (DriftMath.shouldSeek(player.currentPositionMs(), expected)) {
                        player.seekTo(DriftMath.seekTargetMs(expected))
                    }
                }
            }
        } else {
            stopDriftLoop()
        }
    }

    private fun stopDriftLoop() {
        driftJob?.cancel()
        driftJob = null
    }

    /** Host-only: when the current track finishes, play the next queued one. */
    private suspend fun onTrackEnded() {
        val roomId = activeRoomId ?: return
        val state = _currentState.value ?: return
        if (state.videoId.isBlank()) return
        val uid = auth.currentUser()?.uid ?: return
        // Meta may not have streamed in yet right after joining; fetch it directly.
        val meta = latestMeta
            ?: runCatching { firebase.findRoom(roomId) }.getOrNull()
            ?: return
        if (meta.hostId != uid) return
        if (advancing) return
        advancing = true
        try {
            val next = latestQueue.firstOrNull()
            if (next == null) {
                hostStop()
            } else {
                firebase.removeFromQueue(roomId, next.key)
                hostLoadTrack(next)
            }
        } catch (t: Throwable) {
            _failures.emit(SyncFailure(SyncFailure.Kind.AUTO_ADVANCE, state.title, t))
        } finally {
            advancing = false
        }
    }

    // ───────── Host commands ─────────

    suspend fun hostLoadTrack(item: QueueItem) {
        val roomId = activeRoomId ?: return
        val initial = PlaybackState(
            videoId = item.videoId,
            title = item.title,
            artist = item.artist,
            thumbnailUrl = item.thumbnailUrl,
            durationMs = item.durationMs,
            isPlaying = false,
            positionMs = 0L,
        )
        firebase.setPlayback(roomId, initial)
        delay(Constants.BUFFER_DELAY_MS)
        firebase.updatePlayingFlag(roomId, isPlaying = true, positionMs = 0L)
    }

    suspend fun hostTogglePlayPause() {
        val roomId = activeRoomId ?: return
        val state = _currentState.value ?: return
        val nowPlaying = !state.isPlaying
        val pos = if (nowPlaying) state.positionMs else expectedPositionMs(state).coerceAtLeast(0L)
        firebase.updatePlayingFlag(roomId, isPlaying = nowPlaying, positionMs = pos)
    }

    suspend fun hostSeek(positionMs: Long) {
        val roomId = activeRoomId ?: return
        val state = _currentState.value ?: return
        firebase.seek(roomId, positionMs, state.isPlaying)
    }

    suspend fun hostStop() {
        val roomId = activeRoomId ?: return
        firebase.setPlayback(roomId, PlaybackState())
    }
}

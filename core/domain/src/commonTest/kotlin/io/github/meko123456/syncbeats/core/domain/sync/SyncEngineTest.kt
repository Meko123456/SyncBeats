package io.github.meko123456.syncbeats.core.domain.sync

import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.PlaybackState
import io.github.meko123456.syncbeats.core.model.QueueItem
import io.github.meko123456.syncbeats.core.model.RoomMeta
import io.github.meko123456.syncbeats.core.model.currentTimeMillis
import io.github.meko123456.syncbeats.core.testing.FakeAuthGateway
import io.github.meko123456.syncbeats.core.testing.FakeMusicSource
import io.github.meko123456.syncbeats.core.testing.FakePlayerController
import io.github.meko123456.syncbeats.core.testing.FakeRoomRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine is the one component whose bugs a listener hears: a wrong seek is a stutter, a missed
 * auto-advance is a room that goes silent. It launches into Dispatchers.Main and reads the wall
 * clock, which is why it went untested for so long. Both are manageable: Main is swapped for the
 * test scheduler, and the clock only ever contributes *elapsed time since `updatedAt` while
 * playing*, so a paused state keeps every expectation exact and a playing one stamped "now" keeps
 * it within a few milliseconds.
 *
 * The engine's own scope is not a child of the test scope, so [engineTest] stops it in a
 * `finally`: that cancels the drift loop, whose `delay` would otherwise keep the scheduler busy
 * forever and hang `runTest` at the end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private val dispatcher = StandardTestDispatcher()
    private val rooms = FakeRoomRepository()
    private val music = FakeMusicSource()
    private val player = FakePlayerController()
    private val auth = FakeAuthGateway(current = AuthUser(uid = HOST))
    private lateinit var engine: SyncEngine

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        engine = SyncEngine(rooms, music, player, auth)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun engineTest(body: suspend TestScope.() -> Unit): TestResult = runTest(dispatcher) {
        try {
            body()
        } finally {
            engine.stop()
        }
    }

    /** Subscribes to the room and binds the player, the way RoomViewModel does on entry. */
    private fun TestScope.startAndConnect() {
        engine.start(ROOM)
        runCurrent()
        launch { player.connect() }
        runCurrent()
    }

    private fun track(
        id: String = "v1",
        positionMs: Long = 30_000L,
        isPlaying: Boolean = false,
        updatedAt: Long = 0L,
        durationMs: Long = 200_000L,
    ) = PlaybackState(
        videoId = id,
        title = "Song $id",
        artist = "Artist",
        durationMs = durationMs,
        isPlaying = isPlaying,
        positionMs = positionMs,
        updatedAt = updatedAt,
    )

    // ───────── applying remote state ─────────

    @Test
    fun `nothing is applied until the platform player is connected`() = engineTest {
        rooms.emitPlayback(track())
        engine.start(ROOM)
        runCurrent()

        assertTrue(player.loads.isEmpty(), "loaded before the player was bound")

        launch { player.connect() }
        runCurrent()

        assertEquals(1, player.loads.size)
    }

    @Test
    fun `a new track is resolved and loaded at the host's position`() = engineTest {
        startAndConnect()

        rooms.emitPlayback(track(positionMs = 30_000L))
        runCurrent()

        val load = player.loads.single()
        assertEquals(music.stream.url, load.url)
        assertEquals("Song v1", load.title)
        assertEquals(30_000L, load.startMs)
        assertFalse(load.playWhenReady, "a paused room must not start playing")
        assertEquals("v1", engine.currentState.value?.videoId)
        assertFalse(engine.resolving.value)
    }

    @Test
    fun `the same track arriving again only toggles play and pause`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track())
        runCurrent()

        rooms.emitPlayback(track(isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        assertEquals(1, player.loads.size, "a play/pause change must not reload the stream")
        assertTrue(player.playWhenReady)

        rooms.emitPlayback(track(isPlaying = false, updatedAt = 1L))
        runCurrent()

        assertEquals(1, player.loads.size)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `a player that drifted past the threshold is seeked to where the host is`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track(positionMs = 30_000L))
        runCurrent()

        player.position = 10_000L
        rooms.emitPlayback(track(positionMs = 30_000L, updatedAt = 1L))
        runCurrent()

        assertEquals(listOf(30_000L), player.seeks)
    }

    @Test
    fun `a player inside the threshold or exactly on it is left alone`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track(positionMs = 30_000L))
        runCurrent()

        player.position = 30_300L
        rooms.emitPlayback(track(positionMs = 30_000L, updatedAt = 1L))
        runCurrent()
        player.position = 30_400L
        rooms.emitPlayback(track(positionMs = 30_000L, updatedAt = 2L))
        runCurrent()

        assertTrue(player.seeks.isEmpty(), "seeked for ${player.seeks} - an inaudible drift")
    }

    @Test
    fun `a blank videoId stops the player and forgets the last track`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track())
        runCurrent()

        rooms.emitPlayback(PlaybackState())
        runCurrent()

        assertTrue(player.stopped)

        // The same track coming back is a fresh load, not "already applied".
        rooms.emitPlayback(track(updatedAt = 1L))
        runCurrent()

        assertEquals(2, player.loads.size)
    }

    @Test
    fun `a stream that cannot be resolved is reported and resolving is released`() = engineTest {
        val failures = mutableListOf<SyncFailure>()
        backgroundScope.launch { engine.failures.collect { failures += it } }
        runCurrent()
        music.failWith = IllegalStateException("no stream")
        startAndConnect()

        rooms.emitPlayback(track())
        runCurrent()

        val failure = failures.single()
        assertEquals(SyncFailure.Kind.LOAD_TRACK, failure.kind)
        assertEquals("Song v1", failure.title)
        assertEquals("no stream", failure.cause?.message)
        assertTrue(player.loads.isEmpty())
        assertFalse(engine.resolving.value, "resolving stuck true after a failure")
    }

    // ───────── the drift loop ─────────

    @Test
    fun `while playing the drift loop re-seeks every three seconds`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track(positionMs = 30_000L, isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()
        assertTrue(player.loads.single().playWhenReady)

        player.position = 50_000L
        advanceTimeBy(2_999L)
        assertTrue(player.seeks.isEmpty(), "seeked before the first check was due")

        advanceTimeBy(2L)
        val seek = player.seeks.single()
        assertTrue(seek in 30_000L..30_500L, "seeked to $seek, expected the host's 30 000 ms")

        // Now aligned (the fake moves to the seek target): the next check must not seek again.
        advanceTimeBy(3_000L)
        assertEquals(1, player.seeks.size)
    }

    @Test
    fun `a pause stops the drift loop`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track(positionMs = 30_000L, isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        rooms.emitPlayback(track(positionMs = 30_000L, isPlaying = false, updatedAt = 1L))
        runCurrent()
        val seeksAtPause = player.seeks.size

        player.position = 90_000L
        advanceTimeBy(10_000L)

        assertEquals(seeksAtPause, player.seeks.size, "the loop kept seeking after a pause")
    }

    // ───────── end of track ─────────

    @Test
    fun `when the track ends the host takes the next queued item and plays it`() = engineTest {
        rooms.emitMeta(RoomMeta(hostId = HOST))
        rooms.emitQueue(listOf(QueueItem(key = "q1", videoId = "v2", title = "Next", durationMs = 180_000L)))
        startAndConnect()
        rooms.emitPlayback(track(isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        player.endTrack()
        runCurrent()

        assertTrue(rooms.observeQueue(ROOM).first().isEmpty(), "the item was not taken off the queue")
        assertEquals("v2", engine.currentState.value?.videoId)
        assertFalse(engine.currentState.value!!.isPlaying, "playing before the buffer delay")
        assertEquals("Next", player.loads.last().title)

        advanceTimeBy(1_501L)
        assertTrue(engine.currentState.value!!.isPlaying)
    }

    @Test
    fun `when the track ends with nothing queued the host clears playback`() = engineTest {
        rooms.emitMeta(RoomMeta(hostId = HOST))
        startAndConnect()
        rooms.emitPlayback(track(isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        player.endTrack()
        runCurrent()

        assertEquals("", engine.currentState.value?.videoId)
        assertTrue(player.stopped)
    }

    @Test
    fun `when the track ends a listener who is not the host leaves the room alone`() = engineTest {
        rooms.emitMeta(RoomMeta(hostId = "someone-else"))
        rooms.emitQueue(listOf(QueueItem(key = "q1", videoId = "v2", title = "Next")))
        startAndConnect()
        rooms.emitPlayback(track(isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        player.endTrack()
        runCurrent()
        advanceTimeBy(2_000L)

        assertEquals("v1", engine.currentState.value?.videoId)
        assertEquals(1, rooms.observeQueue(ROOM).first().size)
    }

    @Test
    fun `a state already past the end of the track is never started`() = engineTest {
        rooms.emitMeta(RoomMeta(hostId = HOST))
        startAndConnect()

        rooms.emitPlayback(track(durationMs = 10_000L, positionMs = 20_000L, isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        assertTrue(player.loads.isEmpty(), "started a track that had already finished")
        // ...and as host with an empty queue, cleaned the room up.
        assertEquals("", engine.currentState.value?.videoId)
    }

    // ───────── lifecycle and host commands ─────────

    @Test
    fun `stop unsubscribes from the room and stops the player`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track())
        runCurrent()

        engine.stop()

        assertNull(engine.activeRoomId)
        assertNull(engine.currentState.value)
        assertTrue(player.stopped)

        rooms.emitPlayback(track(updatedAt = 1L))
        runCurrent()
        assertEquals(1, player.loads.size, "still applying state after stop")
    }

    @Test
    fun `hostTogglePlayPause publishes the flipped flag`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track(positionMs = 30_000L))
        runCurrent()

        engine.hostTogglePlayPause()
        runCurrent()
        val playing = engine.currentState.value!!
        assertTrue(playing.isPlaying)
        assertEquals(30_000L, playing.positionMs)

        engine.hostTogglePlayPause()
        runCurrent()
        val paused = engine.currentState.value!!
        assertFalse(paused.isPlaying)
        assertTrue(paused.positionMs in 30_000L..30_500L, "paused at ${paused.positionMs}")
    }

    @Test
    fun `hostSeek moves the room and keeps the playing flag`() = engineTest {
        startAndConnect()
        rooms.emitPlayback(track(isPlaying = true, updatedAt = currentTimeMillis()))
        runCurrent()

        engine.hostSeek(45_000L)
        runCurrent()

        val state = engine.currentState.value!!
        assertEquals(45_000L, state.positionMs)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `host commands without an active room are no-ops`() = engineTest {
        engine.hostTogglePlayPause()
        engine.hostSeek(1_000L)
        engine.hostStop()
        engine.hostLoadTrack(QueueItem(key = "q1", videoId = "v9", title = "Nope"))
        runCurrent()

        assertNull(rooms.observePlayback(ROOM).first())
    }

    private companion object {
        const val ROOM = "ABC234"
        const val HOST = "host-1"
    }
}

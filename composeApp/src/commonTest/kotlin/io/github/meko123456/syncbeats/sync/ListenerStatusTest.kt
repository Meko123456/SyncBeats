package io.github.meko123456.syncbeats.sync

import io.github.meko123456.syncbeats.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenerStatusTest {

    private fun snapshot(
        hasTrack: Boolean = true,
        roomIsPlaying: Boolean = true,
        resolving: Boolean = false,
        playerConnected: Boolean = true,
        playerIsPlaying: Boolean = true,
        playerPositionMs: Long = 30_000,
        expectedPositionMs: Long = 30_000,
    ) = ListenerSnapshot(
        hasTrack, roomIsPlaying, resolving, playerConnected, playerIsPlaying,
        playerPositionMs, expectedPositionMs,
    )

    @Test
    fun `playing in step is in sync`() {
        assertEquals(ListenerStatus.InSync, ListenerStatus.of(snapshot()))
    }

    @Test
    fun `drift past the threshold is reported with its size and direction`() {
        val behind = ListenerStatus.of(snapshot(playerPositionMs = 28_000, expectedPositionMs = 30_400))
        assertTrue(behind is ListenerStatus.CatchingUp, "was $behind")
        assertEquals(2_400, behind.offByMs)
        assertTrue(ListenerStatus.label(behind).contains("behind"), ListenerStatus.label(behind))

        val ahead = ListenerStatus.of(snapshot(playerPositionMs = 32_000, expectedPositionMs = 30_000))
        assertTrue(ahead is ListenerStatus.CatchingUp)
        assertEquals(-2_000, ahead.offByMs)
        assertTrue(ListenerStatus.label(ahead).contains("Ahead"), ListenerStatus.label(ahead))
    }

    @Test
    fun `drift exactly at the threshold is still in sync`() {
        // Same boundary the engine uses to decide whether to seek, so the indicator cannot say
        // "catching up" about a player the engine considers fine.
        val atLimit = snapshot(
            playerPositionMs = 30_000,
            expectedPositionMs = 30_000 + Constants.DRIFT_THRESHOLD_MS,
        )
        assertEquals(ListenerStatus.InSync, ListenerStatus.of(atLimit))
        val justPast = snapshot(
            playerPositionMs = 30_000,
            expectedPositionMs = 30_001 + Constants.DRIFT_THRESHOLD_MS,
        )
        assertTrue(ListenerStatus.of(justPast) is ListenerStatus.CatchingUp)
    }

    @Test
    fun `an empty room says nothing is playing rather than blaming the network`() {
        // And it wins over every other signal: a room with no track is not buffering.
        assertEquals(
            ListenerStatus.NothingPlaying,
            ListenerStatus.of(snapshot(hasTrack = false, resolving = true, playerIsPlaying = false)),
        )
    }

    @Test
    fun `a paused room is not described as being behind`() {
        // Nobody is behind; there is simply nothing to hear. This is the distinction the screen
        // could not previously make.
        val paused = snapshot(roomIsPlaying = false, playerIsPlaying = false, playerPositionMs = 1_000)
        assertEquals(ListenerStatus.Paused, ListenerStatus.of(paused))
    }

    @Test
    fun `the room playing while this player is not is buffering`() {
        assertEquals(
            ListenerStatus.Buffering,
            ListenerStatus.of(snapshot(playerIsPlaying = false)),
        )
    }

    @Test
    fun `resolving the stream is told apart from buffering it`() {
        // Different causes and different fixes: one is the extractor, the other is the network.
        assertEquals(ListenerStatus.Loading, ListenerStatus.of(snapshot(resolving = true)))
    }

    @Test
    fun `a player that has not bound yet is connecting, not buffering`() {
        assertEquals(
            ListenerStatus.Connecting,
            ListenerStatus.of(snapshot(playerConnected = false, playerIsPlaying = false)),
        )
    }

    @Test
    fun `the order of the checks is the design`() {
        // Each state should win over the ones below it, so the listener is told the most specific
        // reason for the silence rather than the last one checked.
        val everythingWrong = snapshot(
            hasTrack = true,
            roomIsPlaying = false,
            resolving = true,
            playerConnected = false,
            playerIsPlaying = false,
            playerPositionMs = 0,
            expectedPositionMs = 90_000,
        )
        assertEquals(ListenerStatus.Loading, ListenerStatus.of(everythingWrong))
        assertEquals(
            ListenerStatus.Connecting,
            ListenerStatus.of(everythingWrong.copy(resolving = false)),
        )
        assertEquals(
            ListenerStatus.Paused,
            ListenerStatus.of(everythingWrong.copy(resolving = false, playerConnected = true)),
        )
    }

    @Test
    fun `every state has a label and none of them are empty`() {
        val all = listOf(
            ListenerStatus.NothingPlaying,
            ListenerStatus.Connecting,
            ListenerStatus.Loading,
            ListenerStatus.Paused,
            ListenerStatus.Buffering,
            ListenerStatus.InSync,
            ListenerStatus.CatchingUp(2_000),
            ListenerStatus.CatchingUp(-2_000),
        )
        for (status in all) {
            assertTrue(ListenerStatus.label(status).isNotBlank(), "$status had no label")
        }
        assertEquals(all.size, all.map { ListenerStatus.label(it) }.toSet().size, "labels must differ")
    }

    @Test
    fun `a tiny drift does not render as zero seconds`() {
        // Rounding 40 ms to one decimal gives 0.0, and "0.0s behind" reads as a bug.
        val label = ListenerStatus.label(ListenerStatus.CatchingUp(40))
        assertFalse(label.contains("0.0"), label)
        assertTrue(label.contains("under 0.1"), label)
    }

    @Test
    fun `only the states a listener can act on are flagged as problems`() {
        assertTrue(ListenerStatus.isProblem(ListenerStatus.CatchingUp(2_000)))
        assertTrue(ListenerStatus.isProblem(ListenerStatus.Buffering))
        assertFalse(ListenerStatus.isProblem(ListenerStatus.InSync))
        assertFalse(ListenerStatus.isProblem(ListenerStatus.Paused))
        assertFalse(ListenerStatus.isProblem(ListenerStatus.NothingPlaying))
    }
}

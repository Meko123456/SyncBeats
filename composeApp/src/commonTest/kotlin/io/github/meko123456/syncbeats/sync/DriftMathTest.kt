package io.github.meko123456.syncbeats.sync

import io.github.meko123456.syncbeats.Constants
import io.github.meko123456.syncbeats.data.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The behaviour a listener would actually notice: whether two phones agree on where the song is,
 * and whether the app seeks when it should and stays quiet when it should not.
 */
class DriftMathTest {

    private val serverNow = 1_756_000_000_000L

    private fun playing(positionMs: Long, updatedAt: Long, durationMs: Long = 210_000) =
        PlaybackState(
            videoId = "abc",
            durationMs = durationMs,
            isPlaying = true,
            positionMs = positionMs,
            updatedAt = updatedAt,
        )

    // ────────────────────────────────── where the track should be

    @Test
    fun `a playing track advances by the time since the host published it`() {
        val state = playing(positionMs = 30_000, updatedAt = serverNow - 5_000)
        assertEquals(35_000, DriftMath.expectedPositionMs(state, serverNow))
    }

    @Test
    fun `a paused room stays exactly where the host left it`() {
        // However long ago that was. A pause that drifts is the most obvious possible bug.
        val paused = playing(positionMs = 30_000, updatedAt = serverNow - 3_600_000).copy(isPlaying = false)
        assertEquals(30_000, DriftMath.expectedPositionMs(paused, serverNow))
    }

    @Test
    fun `two phones with wildly different clocks agree on the same second`() {
        // This is the property the whole feature rests on. Both listeners compute in server time,
        // so a device whose clock is 45 seconds fast must still arrive at the same answer.
        val state = playing(positionMs = 60_000, updatedAt = serverNow - 10_000)

        val fastDeviceNow = serverNow + 45_000
        val fastOffset = -45_000L
        val slowDeviceNow = serverNow - 12_345
        val slowOffset = 12_345L

        val onFast = DriftMath.expectedPositionMs(
            state,
            DriftMath.serverNowMs(fastDeviceNow, fastOffset),
        )
        val onSlow = DriftMath.expectedPositionMs(
            state,
            DriftMath.serverNowMs(slowDeviceNow, slowOffset),
        )
        assertEquals(onFast, onSlow)
        assertEquals(70_000, onFast)
    }

    @Test
    fun `a state stamped in the future does not rewind the track`() {
        // The server offset is an estimate, so this happens. Negative elapsed time would put the
        // track behind the host's own position and force a seek — a stutter caused by arithmetic.
        val state = playing(positionMs = 30_000, updatedAt = serverNow + 2_000)
        assertEquals(30_000, DriftMath.expectedPositionMs(state, serverNow))
    }

    @Test
    fun `the offset is the only thing that combines the two clocks`() {
        assertEquals(1_000, DriftMath.serverNowMs(deviceNowMs = 900, serverOffsetMs = 100))
        assertEquals(800, DriftMath.serverNowMs(deviceNowMs = 900, serverOffsetMs = -100))
    }

    // ────────────────────────────────── whether to seek

    @Test
    fun `a difference below the threshold is left alone`() {
        assertFalse(DriftMath.shouldSeek(playerPositionMs = 30_000, expectedPositionMs = 30_399))
    }

    @Test
    fun `a difference exactly at the threshold is left alone`() {
        // Strictly greater, so a player sitting precisely on the limit does not seek forever.
        val exactly = 30_000 + Constants.DRIFT_THRESHOLD_MS
        assertFalse(DriftMath.shouldSeek(playerPositionMs = 30_000, expectedPositionMs = exactly))
        assertTrue(DriftMath.shouldSeek(playerPositionMs = 30_000, expectedPositionMs = exactly + 1))
    }

    @Test
    fun `drift is corrected whichever way it runs`() {
        // A player that has run ahead is just as wrong as one that has fallen behind.
        assertTrue(DriftMath.shouldSeek(playerPositionMs = 40_000, expectedPositionMs = 30_000))
        assertTrue(DriftMath.shouldSeek(playerPositionMs = 30_000, expectedPositionMs = 40_000))
    }

    @Test
    fun `the threshold is loose enough to survive ordinary jitter`() {
        // 400 ms. Tighter than a couple of hundred milliseconds and every buffering hiccup
        // becomes an audible seek; looser than about a second and listeners hear the gap.
        assertTrue(Constants.DRIFT_THRESHOLD_MS in 200..1_000)
    }

    // ────────────────────────────────── end of track

    @Test
    fun `a track is finished once the expected position reaches its duration`() {
        assertTrue(DriftMath.isPastEnd(expectedPositionMs = 210_000, durationMs = 210_000))
        assertTrue(DriftMath.isPastEnd(expectedPositionMs = 210_001, durationMs = 210_000))
        assertFalse(DriftMath.isPastEnd(expectedPositionMs = 209_999, durationMs = 210_000))
    }

    @Test
    fun `an unknown duration never counts as finished`() {
        // Firebase has no duration until the host publishes one, and zero means unknown. Reading
        // it as "zero length" stops a track before it has started.
        assertFalse(DriftMath.isPastEnd(expectedPositionMs = 0, durationMs = 0))
        assertFalse(DriftMath.isPastEnd(expectedPositionMs = 120_000, durationMs = 0))
    }

    @Test
    fun `a room left playing for hours is recognised as finished rather than restarted`() {
        // The case the engine guards: nobody applied the state, isPlaying stayed true, and the
        // expected position is now far beyond the end of the song.
        val stale = playing(positionMs = 0, updatedAt = serverNow - 7_200_000, durationMs = 210_000)
        val expected = DriftMath.expectedPositionMs(stale, serverNow)
        assertTrue(expected > 7_000_000)
        assertTrue(DriftMath.isPastEnd(expected, stale.durationMs))
    }

    // ────────────────────────────────── what the player is handed

    @Test
    fun `a player is never handed a negative position`() {
        assertEquals(0, DriftMath.seekTargetMs(-5_000))
        assertEquals(0, DriftMath.seekTargetMs(0))
        assertEquals(1_234, DriftMath.seekTargetMs(1_234))
    }

    @Test
    fun `a room with a negative position cannot push the player below zero`() {
        // Nothing should write a negative position, but the value comes off the network.
        val broken = playing(positionMs = -10_000, updatedAt = serverNow)
        val expected = DriftMath.expectedPositionMs(broken, serverNow)
        assertTrue(expected < 0, "the raw expectation is negative: $expected")
        assertEquals(0, DriftMath.seekTargetMs(expected))
    }
}

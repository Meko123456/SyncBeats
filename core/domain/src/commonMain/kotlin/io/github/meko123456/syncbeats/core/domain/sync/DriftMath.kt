package io.github.meko123456.syncbeats.core.domain.sync

import io.github.meko123456.syncbeats.core.model.Constants
import io.github.meko123456.syncbeats.core.model.PlaybackState
import kotlin.math.abs

/**
 * The arithmetic that keeps two phones playing the same second of the same song.
 *
 * It used to live as private methods on [SyncEngine], which needs Firebase, a music source, a
 * platform player and an auth repository to exist, and which read the device clock through a
 * global function. That made the one part of this app whose correctness a listener would actually
 * notice the one part that could not be tested. It is pure now: every input is a parameter,
 * including the time.
 *
 * ## The idea
 *
 * The host publishes "at server time T, the track was at position P, and it is playing". Every
 * listener then computes where the track *should* be now, from its own clock corrected by
 * Firebase's server-time offset, and seeks only if it has drifted far enough to be audible.
 *
 * Two properties matter more than the formula:
 *
 * - **Everything is measured in server time, never device time.** A phone whose clock is a minute
 *   fast must arrive at the same answer as one that is correct, or the two will never agree. That
 *   is what the offset is for, and it is why the device clock is never read directly here.
 * - **Time never runs backwards.** The offset is an estimate, so a state can arrive stamped
 *   slightly in the future. Treating that as negative elapsed time would rewind the track to
 *   before the host's own position and cause a seek — an audible stutter caused purely by
 *   arithmetic.
 */
object DriftMath {

    /** Server time as this device best understands it. The only place the two clocks are combined. */
    fun serverNowMs(deviceNowMs: Long, serverOffsetMs: Long): Long = deviceNowMs + serverOffsetMs

    /**
     * Where the track should be right now.
     *
     * A paused room returns the host's position unchanged however long ago it was published:
     * elapsed time is only real while something is playing.
     */
    fun expectedPositionMs(state: PlaybackState, serverNowMs: Long): Long {
        val elapsed = if (state.isPlaying) (serverNowMs - state.updatedAt).coerceAtLeast(0L) else 0L
        return state.positionMs + elapsed
    }

    /**
     * Whether the difference is worth a seek.
     *
     * Strictly greater than the threshold, so a player exactly at the limit is left alone —
     * seeking on equality would let a player that sits precisely at the boundary seek on every
     * check, which is audible and achieves nothing.
     */
    fun shouldSeek(
        playerPositionMs: Long,
        expectedPositionMs: Long,
        thresholdMs: Long = Constants.DRIFT_THRESHOLD_MS,
    ): Boolean = abs(playerPositionMs - expectedPositionMs) > thresholdMs

    /**
     * Whether the track has already finished.
     *
     * A duration of zero means *unknown*, not *zero length* — Firebase has no value for it until
     * the host publishes one — so it is never treated as finished. Getting this wrong stops a
     * track before it starts.
     */
    fun isPastEnd(expectedPositionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && expectedPositionMs >= durationMs

    /** Never hand a player a negative position, whatever the room state claims. */
    fun seekTargetMs(expectedPositionMs: Long): Long = expectedPositionMs.coerceAtLeast(0L)
}

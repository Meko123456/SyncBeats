package io.github.meko123456.syncbeats.sync

import io.github.meko123456.syncbeats.core.model.Constants
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the app knows about this listener's playback at one moment. */
data class ListenerSnapshot(
    /** Whether the room has a track selected at all. */
    val hasTrack: Boolean,
    /** What the room says: is this track supposed to be playing right now? */
    val roomIsPlaying: Boolean,
    /** The stream URL is being resolved. */
    val resolving: Boolean,
    /** The platform player is bound and ready for commands. */
    val playerConnected: Boolean,
    /** What the local player is actually doing. */
    val playerIsPlaying: Boolean,
    val playerPositionMs: Long,
    /** Where the track should be, from [DriftMath.expectedPositionMs]. */
    val expectedPositionMs: Long,
)

/**
 * What to tell the listener about their own playback.
 *
 * The room screen previously showed a spinner while a stream was being resolved and nothing else,
 * so the two states a listener actually wonders about were invisible: *am I in sync with everyone
 * else*, and *if the music stopped, whose fault is it*. A pause by the host, a stream still
 * loading, a local player still buffering and a listener who has drifted are four different
 * situations with the same symptom — silence — and they need different reactions.
 *
 * Derived rather than reported: no new signal is asked of the platform player, because "the room
 * says playing and the local player is not" is already a sufficient description of buffering, and
 * adding a state to the player interface would mean writing it twice, once per platform.
 */
sealed interface ListenerStatus {

    /** The room has no track selected. */
    data object NothingPlaying : ListenerStatus

    /** Waiting on the platform player to bind. Brief, and worth distinguishing from buffering. */
    data object Connecting : ListenerStatus

    /** Resolving the audio stream. */
    data object Loading : ListenerStatus

    /** The host has paused. Nobody is behind; there is simply nothing to hear. */
    data object Paused : ListenerStatus

    /** The room is playing and this player is not: buffering, or starting up. */
    data object Buffering : ListenerStatus

    /**
     * Playing, but not where it should be. [offByMs] is positive when this listener is behind the
     * room and negative when ahead.
     */
    data class CatchingUp(val offByMs: Long) : ListenerStatus

    /** Playing, within the drift threshold. The state a listener should be in all the time. */
    data object InSync : ListenerStatus

    companion object {

        /**
         * Order matters here, and it is the design rather than an implementation detail: each
         * check answers "is there a more specific reason for the silence than the next one down".
         * A room with no track is not buffering; a paused room is not behind.
         */
        fun of(
            snapshot: ListenerSnapshot,
            driftThresholdMs: Long = Constants.DRIFT_THRESHOLD_MS,
        ): ListenerStatus = when {
            !snapshot.hasTrack -> NothingPlaying
            snapshot.resolving -> Loading
            !snapshot.playerConnected -> Connecting
            !snapshot.roomIsPlaying -> Paused
            !snapshot.playerIsPlaying -> Buffering
            else -> {
                val offBy = snapshot.expectedPositionMs - snapshot.playerPositionMs
                if (abs(offBy) > driftThresholdMs) CatchingUp(offBy) else InSync
            }
        }

        /** The sentence to show. Here so the wording is covered by the same tests as the rules. */
        fun label(status: ListenerStatus): String = when (status) {
            NothingPlaying -> "Nothing playing"
            Connecting -> "Connecting…"
            Loading -> "Loading the track…"
            Paused -> "Paused by the host"
            Buffering -> "Buffering…"
            InSync -> "In sync"
            is CatchingUp -> {
                val seconds = abs(status.offByMs) / 1000.0
                val rounded = (seconds * 10).roundToInt() / 10.0
                val amount = if (rounded < 0.1) "under 0.1" else rounded.toString()
                if (status.offByMs > 0) "Catching up — ${amount}s behind" else "Ahead by ${amount}s"
            }
        }

        /** True when this state is worth drawing attention to rather than stating quietly. */
        fun isProblem(status: ListenerStatus): Boolean =
            status is CatchingUp || status == Buffering
    }
}

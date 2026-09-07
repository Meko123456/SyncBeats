package io.github.meko123456.syncbeats.core.domain.sync


/**
 * Something went wrong while driving playback.
 *
 * Carried out of [SyncEngine] as data rather than turned into a string there: the engine has no
 * business writing UI copy, and the copy needs testing of its own.
 */
data class SyncFailure(
    val kind: Kind,
    /** The track it happened to, for a message that names it. */
    val title: String,
    val cause: Throwable?,
) {
    enum class Kind {
        /** The audio stream could not be resolved or handed to the player. */
        LOAD_TRACK,

        /** The host's automatic move to the next queued track failed. */
        AUTO_ADVANCE,
    }
}

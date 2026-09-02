package io.github.meko123456.syncbeats

object Constants {
    const val ROOM_CODE_LENGTH = 6

    // Excludes 0/O/1/I/L so codes are easy to read aloud.
    const val ROOM_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    const val DRIFT_THRESHOLD_MS = 400L
    const val DRIFT_CHECK_INTERVAL_MS = 3_000L
    const val BUFFER_DELAY_MS = 1_500L
}

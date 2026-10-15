package io.github.meko123456.syncbeats.core.designsystem

/** "1 track", "24 tracks". A one-track playlist said "1 tracks" on home and in the room. */
fun trackCount(count: Int): String = if (count == 1) "1 track" else "$count tracks"

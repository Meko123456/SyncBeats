package io.github.meko123456.syncbeats.core.model

/** Wall-clock epoch millis; corrected against Firebase's server offset where it matters. */
expect fun currentTimeMillis(): Long

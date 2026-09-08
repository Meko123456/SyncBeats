package io.github.meko123456.syncbeats.core.model

/** A playlist on the signed-in user's own YouTube account. */
data class AccountPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val trackCount: Int,
)

package io.github.meko123456.syncbeats.core.model

/** What a completed Google sign-in hands back. */
class GoogleTokens(
    val idToken: String,
    val accessToken: String?,
    val displayName: String?,
)

package io.github.meko123456.syncbeats.core.model

/**
 * The signed-in user, as the app cares about them.
 *
 * Exists so the auth port can hand back a plain value instead of GitLive's `FirebaseUser`. Every
 * caller only ever read `uid`, `email` and `displayName`, so nothing is lost — and the screens and
 * the sync engine stop transitively depending on a Firebase type.
 */
data class AuthUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
)

package io.github.meko123456.syncbeats.core.domain.repository

import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.GoogleTokens

/**
 * Sign-in and the current user.
 *
 * Returns [AuthUser] rather than GitLive's `FirebaseUser`, which is what stops the screens and the
 * sync engine from transitively depending on Firebase. Every caller only ever used uid, email and
 * displayName, so the swap costs nothing.
 */
interface AuthGateway {
    fun currentUser(): AuthUser?

    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun signUp(email: String, password: String, username: String): AuthUser

    /** Signs into Firebase with Google tokens; creates the profile on first login. */
    suspend fun signInWithGoogle(tokens: GoogleTokens): AuthUser

    /** Username chosen at sign-up, or null if the profile is missing. */
    suspend fun fetchUsername(uid: String): String?

    suspend fun signOut()
}

package io.github.meko123456.syncbeats.core.testing

import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.GoogleTokens

/** An [AuthGateway] that signs in whoever you tell it to, or fails on demand. */
class FakeAuthGateway(
    var current: AuthUser? = null,
    /** Username the profile lookup returns; null models a missing profile. */
    var storedUsername: String? = null,
) : AuthGateway {

    var failWith: Throwable? = null
    val signInCalls = mutableListOf<Pair<String, String>>()
    val signUpCalls = mutableListOf<Triple<String, String, String>>()
    var signedOut = false

    override fun currentUser(): AuthUser? = current

    override suspend fun signIn(email: String, password: String): AuthUser {
        failWith?.let { throw it }
        signInCalls += email to password
        return AuthUser(uid = "uid-1", email = email).also { current = it }
    }

    override suspend fun signUp(email: String, password: String, username: String): AuthUser {
        failWith?.let { throw it }
        signUpCalls += Triple(email, password, username)
        return AuthUser(uid = "uid-new", email = email, displayName = username).also { current = it }
    }

    override suspend fun signInWithGoogle(tokens: GoogleTokens): AuthUser {
        failWith?.let { throw it }
        return AuthUser(uid = "uid-g", email = "g@example.com", displayName = tokens.displayName)
            .also { current = it }
    }

    override suspend fun fetchUsername(uid: String): String? = storedUsername

    override suspend fun signOut() {
        signedOut = true
        current = null
    }
}

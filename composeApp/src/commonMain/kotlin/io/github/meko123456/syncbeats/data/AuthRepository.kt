package io.github.meko123456.syncbeats.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.GoogleTokens
import kotlinx.coroutines.flow.first

/**
 * [AuthGateway] over Firebase Auth.
 *
 * Maps GitLive's [FirebaseUser] to the domain's [AuthUser] at this boundary, which is what keeps
 * Firebase out of the screens and the sync engine — they only ever needed uid, email and
 * displayName.
 */
class AuthRepository : AuthGateway {

    override fun currentUser(): AuthUser? = Firebase.auth.currentUser?.toAuthUser()

    override suspend fun signIn(email: String, password: String): AuthUser {
        val result = Firebase.auth.signInWithEmailAndPassword(email, password)
        return (result.user ?: error("Sign-in returned no user")).toAuthUser()
    }

    override suspend fun signUp(email: String, password: String, username: String): AuthUser {
        val result = Firebase.auth.createUserWithEmailAndPassword(email, password)
        val user = result.user ?: error("Sign-up returned no user")
        Firebase.database.reference("users/${user.uid}").setValue(
            mapOf(
                "username" to username,
                "createdAt" to ServerValue.TIMESTAMP,
            )
        )
        return user.toAuthUser()
    }

    /** Signs into Firebase with Google tokens; creates the profile on first login. */
    override suspend fun signInWithGoogle(tokens: GoogleTokens): AuthUser {
        val credential = GoogleAuthProvider.credential(tokens.idToken, tokens.accessToken)
        val result = Firebase.auth.signInWithCredential(credential)
        val user = result.user ?: error("Google sign-in returned no user")
        if (fetchUsername(user.uid) == null) {
            Firebase.database.reference("users/${user.uid}").setValue(
                mapOf(
                    "username" to (tokens.displayName ?: user.displayName ?: "User"),
                    "createdAt" to ServerValue.TIMESTAMP,
                )
            )
        }
        return user.toAuthUser()
    }

    /** Username chosen at sign-up, or null if the profile is missing. */
    override suspend fun fetchUsername(uid: String): String? =
        runCatching {
            Firebase.database.reference("users/$uid/username").valueEvents.first().value<String?>()
        }.getOrNull()

    override suspend fun signOut() = Firebase.auth.signOut()
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, displayName = displayName)

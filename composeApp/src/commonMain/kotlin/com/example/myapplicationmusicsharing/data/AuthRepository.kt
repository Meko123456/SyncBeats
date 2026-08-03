package com.example.myapplicationmusicsharing.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.first

class AuthRepository {

    fun currentUser(): FirebaseUser? = Firebase.auth.currentUser

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = Firebase.auth.signInWithEmailAndPassword(email, password)
        return result.user ?: error("Sign-in returned no user")
    }

    suspend fun signUp(email: String, password: String, username: String): FirebaseUser {
        val result = Firebase.auth.createUserWithEmailAndPassword(email, password)
        val user = result.user ?: error("Sign-up returned no user")
        Firebase.database.reference("users/${user.uid}").setValue(
            mapOf(
                "username" to username,
                "createdAt" to ServerValue.TIMESTAMP,
            )
        )
        return user
    }

    /** Signs into Firebase with Google tokens; creates the profile on first login. */
    suspend fun signInWithGoogle(tokens: GoogleTokens): FirebaseUser {
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
        return user
    }

    /** Username chosen at sign-up, or null if the profile is missing. */
    suspend fun fetchUsername(uid: String): String? =
        runCatching {
            Firebase.database.reference("users/$uid/username").valueEvents.first().value<String?>()
        }.getOrNull()

    suspend fun signOut() = Firebase.auth.signOut()
}

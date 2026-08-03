package com.example.myapplicationmusicsharing.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GoogleTokens(
    val idToken: String,
    val accessToken: String?,
    val displayName: String?,
)

/**
 * Platform Google Sign-In. Callback-based (not suspend) so the iOS side can be
 * implemented in Swift with the GoogleSignIn SDK; Android implements it with
 * Credential Manager + AuthorizationClient.
 */
interface GoogleAuthController {
    /** Interactive sign-in requesting the youtube.readonly scope. */
    fun signIn(callback: (GoogleTokens?, String?) -> Unit)

    /** Fresh access token for YouTube API calls, or null if not connected. */
    fun freshAccessToken(callback: (String?) -> Unit)

    fun signOut()
}

suspend fun GoogleAuthController.signInSuspend(): GoogleTokens =
    suspendCancellableCoroutine { cont ->
        signIn { tokens, error ->
            if (!cont.isCompleted) {
                if (tokens != null) cont.resume(tokens)
                else cont.resumeWith(Result.failure(IllegalStateException(error ?: "Google sign-in failed")))
            }
        }
    }

suspend fun GoogleAuthController.freshAccessTokenSuspend(): String? =
    suspendCancellableCoroutine { cont ->
        freshAccessToken { token ->
            if (!cont.isCompleted) cont.resume(token)
        }
    }

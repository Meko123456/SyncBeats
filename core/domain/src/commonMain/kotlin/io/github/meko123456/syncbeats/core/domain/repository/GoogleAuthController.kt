package io.github.meko123456.syncbeats.core.domain.repository

import io.github.meko123456.syncbeats.core.model.GoogleTokens
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine


/**
 * Platform Google Sign-In. Callback-based (not suspend) so the iOS side can be
 * implemented in Swift with the GoogleSignIn SDK; Android implements it with
 * Credential Manager + AuthorizationClient.
 */
interface GoogleAuthController {
    /**
     * Interactive sign-in requesting the youtube.readonly scope. The callback gets the tokens, or
     * a message saying what went wrong, or neither: neither means the person closed Google's sheet
     * without picking an account, which is a choice rather than a failure.
     */
    fun signIn(callback: (GoogleTokens?, String?) -> Unit)

    /** Fresh access token for YouTube API calls, or null if not connected. */
    fun freshAccessToken(callback: (String?) -> Unit)

    fun signOut()
}

/** What [signInSuspend] throws when the person backed out of Google's sign-in: nothing to report. */
class GoogleSignInCancelled : Exception("Google sign-in was cancelled")

suspend fun GoogleAuthController.signInSuspend(): GoogleTokens =
    suspendCancellableCoroutine { cont ->
        signIn { tokens, error ->
            if (!cont.isCompleted) {
                when {
                    tokens != null -> cont.resume(tokens)
                    error == null -> cont.resumeWith(Result.failure(GoogleSignInCancelled()))
                    else -> cont.resumeWith(Result.failure(IllegalStateException(error)))
                }
            }
        }
    }

suspend fun GoogleAuthController.freshAccessTokenSuspend(): String? =
    suspendCancellableCoroutine { cont ->
        freshAccessToken { token ->
            if (!cont.isCompleted) cont.resume(token)
        }
    }

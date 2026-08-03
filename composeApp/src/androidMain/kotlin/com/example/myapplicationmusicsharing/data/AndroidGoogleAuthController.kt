package com.example.myapplicationmusicsharing.data

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

/**
 * Google Sign-In via Credential Manager (identity for Firebase) plus
 * AuthorizationClient (OAuth access token with the YouTube scope).
 * MainActivity attaches itself for the interactive parts.
 */
class AndroidGoogleAuthController(private val context: Context) : GoogleAuthController {

    private var activityRef: WeakReference<ComponentActivity>? = null

    /** Set by MainActivity; launches the OAuth consent screen when Google asks for it. */
    var launchAuthorization: ((IntentSenderRequest) -> Unit)? = null
    private var pendingAuthorization: ((AuthorizationResult?) -> Unit)? = null

    fun attachActivity(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
    }

    fun onAuthorizationResult(data: Intent?) {
        val callback = pendingAuthorization ?: return
        pendingAuthorization = null
        val result = data?.let {
            runCatching {
                Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(it)
            }.getOrNull()
        }
        callback(result)
    }

    override fun signIn(callback: (GoogleTokens?, String?) -> Unit) {
        val activity = activityRef?.get()
            ?: return callback(null, "No foreground activity")
        activity.lifecycleScope.launch {
            try {
                val webClientId = webClientId()
                    ?: error(
                        "Google sign-in is not configured: enable the Google provider " +
                            "in Firebase and re-download google-services.json"
                    )
                val manager = CredentialManager.create(activity)
                val response = try {
                    val oneTap = GetGoogleIdOption.Builder()
                        .setServerClientId(webClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                    manager.getCredential(
                        activity,
                        GetCredentialRequest.Builder().addCredentialOption(oneTap).build(),
                    )
                } catch (e: NoCredentialException) {
                    // No eligible saved credential (common on emulators/new devices):
                    // fall back to the full interactive Sign in with Google flow.
                    val buttonFlow = GetSignInWithGoogleOption.Builder(webClientId).build()
                    manager.getCredential(
                        activity,
                        GetCredentialRequest.Builder().addCredentialOption(buttonFlow).build(),
                    )
                }
                val googleCred = GoogleIdTokenCredential.createFrom(response.credential.data)
                val accessToken = authorizeYouTube(activity)
                callback(
                    GoogleTokens(
                        idToken = googleCred.idToken,
                        accessToken = accessToken,
                        displayName = googleCred.displayName,
                    ),
                    null,
                )
            } catch (t: Throwable) {
                callback(null, t.message ?: "Google sign-in failed")
            }
        }
    }

    override fun freshAccessToken(callback: (String?) -> Unit) {
        val activity = activityRef?.get() ?: return callback(null)
        activity.lifecycleScope.launch {
            val token = runCatching {
                val result = Identity.getAuthorizationClient(activity)
                    .authorize(youtubeRequest())
                    .awaitTask()
                // Silent path only; interactive consent belongs to signIn().
                if (result.hasResolution()) null else result.accessToken
            }.getOrNull()
            callback(token)
        }
    }

    override fun signOut() {
        val activity = activityRef?.get() ?: return
        activity.lifecycleScope.launch {
            runCatching {
                CredentialManager.create(activity)
                    .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            }
        }
    }

    private suspend fun authorizeYouTube(activity: ComponentActivity): String? {
        val result = Identity.getAuthorizationClient(activity)
            .authorize(youtubeRequest())
            .awaitTask()
        if (!result.hasResolution()) return result.accessToken
        val pendingIntent = result.pendingIntent ?: return null
        val launcher = launchAuthorization ?: return null
        return suspendCancellableCoroutine { cont ->
            pendingAuthorization = { authorization ->
                if (!cont.isCompleted) cont.resume(authorization?.accessToken)
            }
            launcher(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    private fun youtubeRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_SCOPE)))
            .build()

    /** Generated by the google-services plugin once an OAuth client exists. */
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName,
        )
        return if (id == 0) null else context.getString(id)
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (!cont.isCompleted) cont.resume(it) }
    addOnFailureListener { if (!cont.isCompleted) cont.resumeWith(Result.failure(it)) }
    addOnCanceledListener { if (!cont.isCompleted) cont.resumeWith(Result.failure(RuntimeException("Cancelled"))) }
}

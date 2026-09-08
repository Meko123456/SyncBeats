package io.github.meko123456.syncbeats.core.testing

import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.core.model.GoogleTokens

/**
 * A callback-based [GoogleAuthController]. The real interface is callback-shaped so iOS can
 * implement it in Swift, so the fake invokes the callback synchronously — which is enough for the
 * suspend wrappers the ViewModels actually call.
 */
class FakeGoogleAuthController(
    var tokens: GoogleTokens? = GoogleTokens("id-token", "access-token", "Zura"),
    var error: String? = null,
    var accessToken: String? = "access-token",
) : GoogleAuthController {

    var signedOut = false

    override fun signIn(callback: (GoogleTokens?, String?) -> Unit) = callback(tokens, error)

    override fun freshAccessToken(callback: (String?) -> Unit) = callback(accessToken)

    override fun signOut() { signedOut = true }
}

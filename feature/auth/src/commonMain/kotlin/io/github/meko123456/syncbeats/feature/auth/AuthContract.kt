package io.github.meko123456.syncbeats.feature.auth

/** Everything the auth screen renders from. */
data class AuthState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val username: String = "",
    val uid: String = "",
)

/** Everything the user can do on the auth screen. */
sealed interface AuthIntent {
    data class SignIn(val email: String, val password: String) : AuthIntent
    data class SignUp(val email: String, val password: String, val username: String) : AuthIntent
    data object SignInWithGoogle : AuthIntent
    data object SignOut : AuthIntent
}

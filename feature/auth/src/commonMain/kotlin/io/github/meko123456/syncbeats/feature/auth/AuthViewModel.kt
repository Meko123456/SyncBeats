package io.github.meko123456.syncbeats.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.core.domain.repository.signInSuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepo: AuthGateway,
    private val google: GoogleAuthController,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** The single entry point for everything the screen can ask for. */
    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.SignIn -> signIn(intent.email, intent.password)
            is AuthIntent.SignUp -> signUp(intent.email, intent.password, intent.username)
            AuthIntent.SignInWithGoogle -> signInWithGoogle()
            AuthIntent.SignOut -> signOut()
        }
    }

    init {
        val user = authRepo.currentUser()
        if (user != null) {
            _state.update {
                it.copy(
                    signedIn = true,
                    uid = user.uid,
                    username = user.email.orEmpty().substringBefore("@"),
                )
            }
        }
    }

    private fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { authRepo.signIn(email, password) }
                .onSuccess { user ->
                    val username = authRepo.fetchUsername(user.uid)
                        ?: email.substringBefore("@")
                    _state.update {
                        it.copy(
                            signedIn = true,
                            loading = false,
                            uid = user.uid,
                            username = username,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message) }
                }
        }
    }

    private fun signUp(email: String, password: String, username: String) {
        val name = username.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Pick a username first") }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { authRepo.signUp(email.trim(), password, name) }
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            signedIn = true,
                            loading = false,
                            uid = user.uid,
                            username = name,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message) }
                }
        }
    }

    private fun signInWithGoogle() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val tokens = google.signInSuspend()
                tokens to authRepo.signInWithGoogle(tokens)
            }
                .onSuccess { (tokens, user) ->
                    _state.update {
                        it.copy(
                            signedIn = true,
                            loading = false,
                            uid = user.uid,
                            username = tokens.displayName
                                ?: user.displayName
                                ?: user.email.orEmpty().substringBefore("@"),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message) }
                }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            runCatching { google.signOut() }
            runCatching { authRepo.signOut() }
            _state.update { AuthState() }
        }
    }
}

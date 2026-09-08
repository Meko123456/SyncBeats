package io.github.meko123456.syncbeats.feature.auth

import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.GoogleTokens
import io.github.meko123456.syncbeats.core.testing.FakeAuthGateway
import io.github.meko123456.syncbeats.core.testing.FakeGoogleAuthController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sign-in screen's behaviour, tested with no Firebase and no device.
 *
 * This was impossible before the refactor: AuthViewModel took the concrete AuthRepository, which
 * talked straight to Firebase Auth. It now takes the AuthGateway port, so the whole screen can be
 * exercised against a fake — including the validation branches that never reach the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var auth: FakeAuthGateway
    private lateinit var google: FakeGoogleAuthController

    private fun viewModel() = AuthViewModel(auth, google)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = FakeAuthGateway()
        google = FakeGoogleAuthController()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun an_already_signed_in_user_starts_signed_in() = runTest(dispatcher) {
        auth.current = AuthUser(uid = "uid-7", email = "zura@example.com")
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.signedIn)
        assertEquals("uid-7", vm.state.value.uid)
        // The local part of the address is the fallback display name.
        assertEquals("zura", vm.state.value.username)
    }

    @Test
    fun signing_in_uses_the_stored_username_rather_than_the_email() = runTest(dispatcher) {
        auth.storedUsername = "DJ Zura"
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignIn("zura@example.com", "hunter2"))
        advanceUntilIdle()

        assertTrue(vm.state.value.signedIn)
        assertEquals("DJ Zura", vm.state.value.username)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun signing_in_with_no_stored_profile_falls_back_to_the_email_local_part() = runTest(dispatcher) {
        auth.storedUsername = null
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignIn("someone@example.com", "hunter2"))
        advanceUntilIdle()

        assertEquals("someone", vm.state.value.username)
    }

    @Test
    fun a_failed_sign_in_reports_the_error_and_stops_loading() = runTest(dispatcher) {
        auth.failWith = IllegalStateException("wrong password")
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignIn("zura@example.com", "nope"))
        advanceUntilIdle()

        assertFalse(vm.state.value.signedIn)
        assertFalse(vm.state.value.loading, "loading must not be left on after a failure")
        assertEquals("wrong password", vm.state.value.error)
    }

    @Test
    fun signing_up_without_a_username_never_reaches_the_gateway() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignUp("zura@example.com", "hunter2", "   "))
        advanceUntilIdle()

        assertEquals("Pick a username first", vm.state.value.error)
        assertTrue(auth.signUpCalls.isEmpty(), "a local validation failure must not hit the network")
    }

    @Test
    fun signing_up_with_a_short_password_never_reaches_the_gateway() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignUp("zura@example.com", "12345", "Zura"))
        advanceUntilIdle()

        assertEquals("Password must be at least 6 characters", vm.state.value.error)
        assertTrue(auth.signUpCalls.isEmpty())
    }

    @Test
    fun signing_up_trims_the_email_and_the_username() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignUp("  zura@example.com  ", "hunter2", "  Zura  "))
        advanceUntilIdle()

        assertEquals(Triple("zura@example.com", "hunter2", "Zura"), auth.signUpCalls.single())
        assertEquals("Zura", vm.state.value.username)
    }

    @Test
    fun google_sign_in_prefers_the_name_from_the_token() = runTest(dispatcher) {
        // The token carries the freshest display name; the account record may be stale.
        google.tokens = GoogleTokens("id", "access", "Zura From Google")
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignInWithGoogle)
        advanceUntilIdle()

        assertTrue(vm.state.value.signedIn)
        assertEquals("Zura From Google", vm.state.value.username)
    }

    @Test
    fun a_cancelled_google_sign_in_surfaces_its_reason() = runTest(dispatcher) {
        google.tokens = null
        google.error = "cancelled by user"
        val vm = viewModel()

        vm.onIntent(AuthIntent.SignInWithGoogle)
        advanceUntilIdle()

        assertFalse(vm.state.value.signedIn)
        assertEquals("cancelled by user", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun signing_out_clears_the_state_and_both_providers() = runTest(dispatcher) {
        auth.current = AuthUser(uid = "uid-7", email = "zura@example.com")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(AuthIntent.SignOut)
        advanceUntilIdle()

        assertFalse(vm.state.value.signedIn)
        assertEquals("", vm.state.value.uid)
        assertNull(vm.state.value.error)
        assertTrue(auth.signedOut, "Firebase session must be ended")
        assertTrue(google.signedOut, "the Google session must be ended too, or the next sign-in silently reuses it")
    }
}

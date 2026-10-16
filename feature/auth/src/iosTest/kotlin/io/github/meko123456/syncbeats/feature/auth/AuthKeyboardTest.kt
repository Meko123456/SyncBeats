package io.github.meko123456.syncbeats.feature.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the keyboard's action key does on the sign-in form. */
@OptIn(ExperimentalTestApi::class)
class AuthKeyboardTest {

    @Test
    fun doneOnThePasswordSignsIn() = runComposeUiTest {
        var signedIn: Pair<String, String>? = null
        setContent {
            AuthScreen(
                state = AuthState(),
                onSignIn = { email, password -> signedIn = email to password },
                onSignUp = { _, _, _ -> },
                onGoogleSignIn = {},
                navigateNext = {},
            )
        }
        onNodeWithText("Email").performTextInput("rider@example.com")
        onNodeWithText("Password").performTextInput("not-a-real-password")
        onNodeWithText("Password").performImeAction()
        assertEquals("rider@example.com" to "not-a-real-password", signedIn)
    }

    @Test
    fun doneDoesNothingWhileTheButtonWouldBeDisabled() = runComposeUiTest {
        var signedIn: Pair<String, String>? = null
        setContent {
            AuthScreen(
                state = AuthState(),
                onSignIn = { email, password -> signedIn = email to password },
                onSignUp = { _, _, _ -> },
                onGoogleSignIn = {},
                navigateNext = {},
            )
        }
        onNodeWithText("Password").performTextInput("no email yet")
        onNodeWithText("Password").performImeAction()
        assertNull(signedIn)
    }
}

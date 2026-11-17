package io.github.meko123456.syncbeats.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a screen reader is told by the error line, which no ViewModel test can see. */
@OptIn(ExperimentalTestApi::class)
class ErrorLineTest {

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun theLineIsOnePoliteNodeBeforeAndAfterAnError() = runComposeUiTest {
        var error by mutableStateOf<String?>(null)
        setContent { ErrorLine(error = error) }

        // Present and polite from the start, with nothing to say.
        val idle = onNode(polite).assertTextEquals("").fetchSemanticsNode().id

        error = "Could not sign in with Google. Check the connection and try again."
        val said = onNode(polite).assertIsDisplayed().assertTextEquals(error!!).fetchSemanticsNode().id

        // The same node: a text change, which is what gets announced, not a new node.
        assertEquals(idle, said)

        error = null
        assertEquals(idle, onNode(polite).assertTextEquals("").fetchSemanticsNode().id)
    }

    @Test
    fun dismissIsOfferedOnlyWithAnErrorAndAWayToClearIt() = runComposeUiTest {
        var error by mutableStateOf<String?>(null)
        var dismissed = false
        setContent { ErrorLine(error = error, onDismiss = { dismissed = true }) }

        onNodeWithText("Dismiss").assertDoesNotExist()

        error = "That room is full."
        onNodeWithText("Dismiss").assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun aLineWithNoWayToClearHasNoDismiss() = runComposeUiTest {
        setContent { ErrorLine(error = "The password is too short.") }

        onNodeWithText("The password is too short.").assert(polite)
        onNodeWithText("Dismiss").assertDoesNotExist()
    }
}

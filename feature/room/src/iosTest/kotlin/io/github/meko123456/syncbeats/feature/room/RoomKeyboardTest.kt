package io.github.meko123456.syncbeats.feature.room

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the keyboard's action key does in the room's search and chat sheets. */
@OptIn(ExperimentalTestApi::class)
class RoomKeyboardTest {

    @Test
    fun searchOnTheKeyboardSearches() = runComposeUiTest {
        val searched = mutableListOf<String>()
        setContent {
            SearchSheet(results = emptyList(), searching = false, onSearch = { searched += it }, onPick = {}, onDismiss = {})
        }
        onNodeWithText("Search").performTextInput("lofi beats")
        onNodeWithText("Search").performImeAction()
        assertEquals(listOf("lofi beats"), searched)
    }

    @Test
    fun sendOnTheKeyboardSendsAndClearsTheMessage() = runComposeUiTest {
        val sent = mutableListOf<String>()
        setContent {
            ChatSheet(state = RoomUiState(), onSend = { sent += it }, onDismiss = {})
        }
        onNodeWithText("Message").performTextInput("this one is great")
        onNodeWithText("Message").performImeAction()
        assertEquals(listOf("this one is great"), sent)
        // Cleared for the next message, as the Send button leaves it; a blank one is not sent.
        onNodeWithText("Message").performImeAction()
        assertEquals(listOf("this one is great"), sent)
    }
}

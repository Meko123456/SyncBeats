package io.github.meko123456.syncbeats.feature.lobby

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.testing.FakeAuthGateway
import io.github.meko123456.syncbeats.core.testing.FakeRoomRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the keyboard's action key does in the lobby's two fields. */
@OptIn(ExperimentalTestApi::class)
class LobbyKeyboardTest {

    private val rooms = FakeRoomRepository()
    private val auth = FakeAuthGateway(current = AuthUser(uid = "listener-1"))

    @Test
    fun goOnTheRoomNameCreatesTheRoom() = runComposeUiTest {
        var entered: String? = null
        setContent {
            LobbyScreen(onEnterRoom = { entered = it }, onSignOut = {}, viewModel = LobbyViewModel(rooms, auth))
        }
        onNodeWithText("Room name (optional)").performTextInput("Friday mix")
        onNodeWithText("Room name (optional)").performImeAction()
        waitUntil(timeoutMillis = 5_000) { entered != null }
        assertEquals(listOf("listener-1" to "Friday mix"), rooms.createdRooms)
        assertEquals(rooms.nextRoomCode, entered)
    }

    @Test
    fun goOnACodeThatCannotBeOneSaysWhy() = runComposeUiTest {
        setContent {
            LobbyScreen(onEnterRoom = {}, onSignOut = {}, viewModel = LobbyViewModel(rooms, auth))
        }
        onNodeWithText("Room code").performTextInput("ABC")
        onNodeWithText("Room code").performImeAction()
        // The Join button stays disabled for this, and said nothing about why.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Error: Room codes are 6 characters — that one has 3.").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

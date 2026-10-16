package io.github.meko123456.syncbeats.feature.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the keyboard's action key does in the add-playlist dialog. */
@OptIn(ExperimentalTestApi::class)
class ImportKeyboardTest {

    private val link = "https://www.youtube.com/playlist?list=PLexample"

    @Test
    fun goAddsThePlaylistAndClosesTheDialog() = runComposeUiTest {
        val imported = mutableListOf<String>()
        var dismissed = false
        setContent {
            ImportPlaylistDialog(importing = false, onImport = { imported += it }, onDismiss = { dismissed = true })
        }
        onNodeWithText("Playlist URL").performTextInput(link)
        onNodeWithText("Playlist URL").performImeAction()
        assertEquals(listOf(link), imported)
        assertTrue(dismissed)
    }

    @Test
    fun goDoesNothingWithNoLink() = runComposeUiTest {
        val imported = mutableListOf<String>()
        setContent {
            ImportPlaylistDialog(importing = false, onImport = { imported += it }, onDismiss = {})
        }
        onNodeWithText("Playlist URL").performImeAction()
        assertEquals(emptyList(), imported)
    }
}

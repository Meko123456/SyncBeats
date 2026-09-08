package io.github.meko123456.syncbeats.core.designsystem

import io.github.meko123456.syncbeats.core.domain.sync.SyncFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exception types named the way the extractor names them, so the matching is exercised for real. */
private class GeographicRestrictionException(message: String? = null) : Exception(message)
private class AgeRestrictedContentException(message: String? = null) : Exception(message)
private class PaidContentException(message: String? = null) : Exception(message)
private class ContentNotAvailableException(message: String? = null) : Exception(message)
private class ParsingException(message: String? = null) : Exception(message)
private class UnknownHostException(message: String? = null) : Exception(message)
private class SocketTimeoutException(message: String? = null) : Exception(message)
private class SomethingNobodyAnticipated(message: String? = null) : Exception(message)

class ErrorCopyTest {

    private fun loadFailure(cause: Throwable?, title: String = "Bohemian Rhapsody") =
        SyncFailure(SyncFailure.Kind.LOAD_TRACK, title, cause)

    @Test
    fun `a failure with no message never renders as null`() {
        // The bug this replaces: the room screen printed throwable.message straight out, so a
        // failure carrying no message read literally "Error: null".
        val messages = listOf(
            ErrorCopy.of(loadFailure(SomethingNobodyAnticipated())),
            ErrorCopy.of(loadFailure(null)),
            ErrorCopy.of(null, "Could not connect to the player"),
            ErrorCopy.of(SomethingNobodyAnticipated(), "That did not work"),
            ErrorCopy.of(SomethingNobodyAnticipated("null"), "That did not work"),
        )
        for (message in messages) {
            assertFalse(message.contains("null", ignoreCase = true), "\"$message\" mentions null")
            assertTrue(message.isNotBlank(), "empty message")
            assertTrue(message.trim().endsWith("."), "\"$message\" should read as a sentence")
        }
    }

    @Test
    fun `a track that names itself is named in the message`() {
        val message = ErrorCopy.of(loadFailure(ContentNotAvailableException()))
        assertTrue(message.contains("Bohemian Rhapsody"), message)
    }

    @Test
    fun `a track with no title still produces a sentence`() {
        val message = ErrorCopy.of(loadFailure(ContentNotAvailableException(), title = ""))
        assertTrue(message.startsWith("That track"), message)
        assertFalse(message.contains("\"\""), message)
    }

    @Test
    fun `the reasons a listener would respond to differently are told apart`() {
        val geo = ErrorCopy.of(loadFailure(GeographicRestrictionException()))
        val age = ErrorCopy.of(loadFailure(AgeRestrictedContentException()))
        val paid = ErrorCopy.of(loadFailure(PaidContentException()))
        val gone = ErrorCopy.of(loadFailure(ContentNotAvailableException()))
        val unreadable = ErrorCopy.of(loadFailure(ParsingException()))

        assertTrue(geo.contains("country"), geo)
        assertTrue(age.contains("age-restricted"), age)
        assertTrue(paid.contains("subscription"), paid)
        assertTrue(gone.contains("not available"), gone)
        assertTrue(unreadable.contains("YouTube"), unreadable)

        // And they are genuinely different sentences, not one message with a label.
        assertEquals(5, setOf(geo, age, paid, gone, unreadable).size)
    }

    @Test
    fun `a network failure blames the network rather than the track`() {
        val offline = ErrorCopy.of(loadFailure(UnknownHostException("Unable to resolve host \"youtube.com\"")))
        assertTrue(offline.contains("connection", ignoreCase = true), offline)
        assertFalse(offline.contains("Bohemian"), "a dropped connection is not the track's fault: $offline")

        val slow = ErrorCopy.of(loadFailure(SocketTimeoutException("timeout")))
        assertTrue(slow.contains("timed out"), slow)
    }

    @Test
    fun `the message is matched as well as the exception type`() {
        // Extraction errors often arrive wrapped, so the type is generic and only the text says
        // what happened.
        val wrapped = ErrorCopy.of(loadFailure(SomethingNobodyAnticipated("This video is not available in your country")))
        assertTrue(wrapped.contains("country"), wrapped)
    }

    @Test
    fun `an unrecognised failure still says what to try`() {
        val message = ErrorCopy.of(loadFailure(SomethingNobodyAnticipated()))
        assertTrue(message.contains("Try another"), message)
    }

    @Test
    fun `an unrecognised failure keeps the words it did come with`() {
        val message = ErrorCopy.of(SomethingNobodyAnticipated("Player released"), "Could not connect to the player")
        assertTrue(message.contains("Could not connect to the player"), message)
        assertTrue(message.contains("Player released"), message)
    }

    @Test
    fun `a failed auto-advance points at the queue rather than at the track`() {
        val message = ErrorCopy.of(SyncFailure(SyncFailure.Kind.AUTO_ADVANCE, "Next Song", null))
        assertTrue(message.contains("next track"), message)
        assertTrue(message.contains("queue"), message)
    }

    @Test
    fun `no message is ever double-punctuated`() {
        val messages = listOf(
            ErrorCopy.of(loadFailure(SomethingNobodyAnticipated("Something broke."))),
            ErrorCopy.of(SomethingNobodyAnticipated("Something broke."), "That did not work."),
            ErrorCopy.of(loadFailure(ContentNotAvailableException("gone."))),
        )
        for (message in messages) {
            assertFalse(message.contains(".."), "\"$message\" has doubled punctuation")
        }
    }
}

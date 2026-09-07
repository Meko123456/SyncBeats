package io.github.meko123456.syncbeats.ui

import io.github.meko123456.syncbeats.core.domain.sync.SyncFailure

/**
 * Turns a thrown exception into a sentence a listener can act on.
 *
 * Two reasons this is not just `throwable.message`:
 *
 * 1. **`message` is often null**, and the room screen rendered it straight into the UI, so a
 *    failure with no message read "Error: null". Never producing that is the first requirement
 *    here, and there is a test for it.
 * 2. **Extraction failures have causes worth telling apart.** A track that is geo-blocked, one
 *    that is age-restricted and one that has been deleted are three different things a listener
 *    would respond to differently, and NewPipe reports them as distinct exception types — but
 *    the library is Android-only, so this matches on the class name rather than the class. That
 *    is deliberate: it keeps the copy in `commonMain` where it can be tested, and an unrecognised
 *    name simply falls through to the generic sentence.
 */
object ErrorCopy {

    /** A sentence for a failure while loading or advancing a track. */
    fun of(failure: SyncFailure): String = when (failure.kind) {
        SyncFailure.Kind.LOAD_TRACK -> forTrack(failure.title, failure.cause)
        SyncFailure.Kind.AUTO_ADVANCE ->
            "Could not start the next track. " + tail(failure.cause, "Try picking one from the queue.")
    }

    /** A sentence for any throwable, with [fallback] used when nothing better can be said. */
    fun of(cause: Throwable?, fallback: String): String {
        val specific = classify(cause)
        return specific ?: (fallback.trimEnd('.') + ". " + tail(cause, "")).trim()
    }

    private fun forTrack(title: String, cause: Throwable?): String {
        val what = if (title.isBlank()) "That track" else "\"$title\""
        return when (val reason = classify(cause, what)) {
            null -> "$what could not be played. " + tail(cause, "Try another one.")
            else -> reason
        }
    }

    /**
     * Recognises the failures worth naming. Matching is on the exception's class name and message
     * because the extractor's own types live in an Android-only library.
     */
    private fun classify(cause: Throwable?, subject: String = "That track"): String? {
        val name = cause?.let { it::class.simpleName }.orEmpty()
        val message = cause?.message.orEmpty()
        val haystack = (name + " " + message).lowercase()
        return when {
            haystack.containsAny("unresolvedaddress", "unknownhost", "unable to resolve host", "no address associated") ->
                "No connection. Check your network and try again."
            haystack.containsAny("sockettimeout", "timeout", "timed out") ->
                "The connection timed out. Try again."
            haystack.containsAny("geographicrestriction", "not available in your country", "blocked in your country") ->
                "$subject is blocked in this country."
            haystack.containsAny("agerestricted", "age-restricted", "age restricted") ->
                "$subject is age-restricted, so it cannot be streamed."
            haystack.containsAny("paidcontent", "premium", "goplus") ->
                "$subject needs a paid subscription to play."
            haystack.containsAny("contentnotavailable", "not available", "removed", "private video") ->
                "$subject is not available any more."
            haystack.containsAny("parsing", "extraction", "reparse") ->
                "$subject could not be read from YouTube. It may have changed — try another."
            else -> null
        }
    }

    /** The exception's own words, when it has any worth showing, else [otherwise]. */
    private fun tail(cause: Throwable?, otherwise: String): String {
        val message = cause?.message?.trim().orEmpty()
        return if (message.isEmpty() || message.equals("null", ignoreCase = true)) {
            otherwise
        } else {
            message.trimEnd('.') + "."
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean = needles.any { contains(it) }
}

package io.github.meko123456.syncbeats.data

import io.github.meko123456.syncbeats.core.domain.music.extractPlaylistId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Recorded response shapes.
 *
 * This is the part of the app that breaks without anybody changing the app: YouTube reshapes these
 * responses, and the symptom is search quietly returning nothing rather than an exception. Every
 * fixture here is trimmed to the fields the parser reads, in the nesting the real response uses.
 */
class InnerTubeParserTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun searchResponse(items: String) = json(
        """
        {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":
        {"contents":[{"itemSectionRenderer":{"contents":[$items]}}]}}}}}
        """.trimIndent(),
    )

    private fun video(
        id: String = "abc123",
        title: String = "Bohemian Rhapsody",
        owner: String = "Queen",
        length: String? = "5:55",
    ) = """
        {"videoRenderer":{
          "videoId":"$id",
          "title":{"runs":[{"text":"$title"}]},
          "ownerText":{"runs":[{"text":"$owner"}]},
          ${if (length != null) """"lengthText":{"simpleText":"$length"},""" else ""}
          "thumbnail":{"thumbnails":[{"url":"small.jpg"},{"url":"large.jpg"}]}
        }}
    """.trimIndent()

    // ─────────────────────────────── search

    @Test
    fun `a search result is read out of the shape YouTube actually returns`() {
        val result = InnerTubeParser.parseSearch(searchResponse(video())).single()
        assertEquals("abc123", result.videoId)
        assertEquals("Bohemian Rhapsody", result.title)
        assertEquals("Queen", result.artist)
        assertEquals(355_000, result.durationMs)
    }

    @Test
    fun `the largest thumbnail is taken rather than the first`() {
        // The array is ordered smallest first, and a thumbnail that looks blurry on a phone is
        // the kind of thing nobody files a bug about.
        assertEquals("large.jpg", InnerTubeParser.parseSearch(searchResponse(video())).single().thumbnailUrl)
    }

    @Test
    fun `a live stream is dropped rather than offered`() {
        // No lengthText means live. There is no position for listeners to agree on, so offering
        // it would mean a track that joins the room and then cannot be synced to.
        val results = InnerTubeParser.parseSearch(searchResponse(video(length = null)))
        assertTrue(results.isEmpty(), "$results")
    }

    @Test
    fun `an item that is not a video is skipped without taking the page with it`() {
        // Search pages carry channel and playlist renderers among the videos.
        val mixed = """{"channelRenderer":{"channelId":"UC123"}},${video()}"""
        assertEquals(1, InnerTubeParser.parseSearch(searchResponse(mixed)).size)
    }

    @Test
    fun `a video missing its title is skipped rather than shown blank`() {
        val untitled = """{"videoRenderer":{"videoId":"x","lengthText":{"simpleText":"1:00"}}}"""
        assertTrue(InnerTubeParser.parseSearch(searchResponse(untitled)).isEmpty())
    }

    @Test
    fun `a response whose shape has changed comes back empty rather than throwing`() {
        // The failure mode to design for. If YouTube renames a wrapper, the app should show no
        // results and stay usable — and this test should be what tells us, not a user.
        assertTrue(InnerTubeParser.parseSearch(json("""{"contents":{"somethingElse":{}}}""")).isEmpty())
        assertTrue(InnerTubeParser.parseSearch(json("""{}""")).isEmpty())
        assertTrue(InnerTubeParser.parseSearch(null).isEmpty())
    }

    @Test
    fun `the result count is capped`() {
        val many = (1..40).joinToString(",") { video(id = "id$it") }
        assertEquals(InnerTubeParser.MAX_RESULTS, InnerTubeParser.parseSearch(searchResponse(many)).size)
    }

    // ─────────────────────────────── stream selection

    private fun playerResponse(formats: String, status: String = "OK") = json(
        """
        {"playabilityStatus":{"status":"$status","reason":"Sign in to confirm your age"},
         "videoDetails":{"lengthSeconds":"355"},
         "streamingData":{"adaptiveFormats":[$formats]}}
        """.trimIndent(),
    )

    @Test
    fun `the highest-bitrate audio stream wins`() {
        val formats = """
            {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":"128000","url":"low"},
            {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":"160000","url":"high"}
        """.trimIndent()
        val stream = InnerTubeParser.parseStream(playerResponse(formats), "abc")
        assertEquals("high", stream.url)
        assertEquals("audio/webm", stream.mimeType, "the codec list is not part of the type")
        assertEquals(355_000, stream.durationMs)
    }

    @Test
    fun `video formats are ignored even when they are the highest bitrate`() {
        // This app never shows video, so a video stream is not a better option — it is the wrong
        // thing, and it would be picked by any naive "highest bitrate" rule.
        val formats = """
            {"mimeType":"video/mp4; codecs=\"avc1\"","bitrate":"2000000","url":"video"},
            {"mimeType":"audio/mp4","bitrate":"128000","url":"audio"}
        """.trimIndent()
        assertEquals("audio", InnerTubeParser.parseStream(playerResponse(formats), "abc").url)
    }

    @Test
    fun `a format with no url is not chosen`() {
        // Ciphered formats arrive without a plain url, and picking one yields a player that
        // silently plays nothing.
        val formats = """
            {"mimeType":"audio/webm","bitrate":"160000"},
            {"mimeType":"audio/mp4","bitrate":"128000","url":"playable"}
        """.trimIndent()
        assertEquals("playable", InnerTubeParser.parseStream(playerResponse(formats), "abc").url)
    }

    @Test
    fun `an unplayable video says why`() {
        val failure = assertFailsWith<IllegalStateException> {
            InnerTubeParser.parseStream(playerResponse("""{"mimeType":"audio/mp4","url":"u"}""", status = "LOGIN_REQUIRED"), "abc")
        }
        assertTrue(failure.message?.contains("LOGIN_REQUIRED") == true, failure.message)
        assertTrue(failure.message?.contains("age") == true, "the reason is worth passing on")
    }

    @Test
    fun `a response with no audio at all names the video`() {
        val failure = assertFailsWith<IllegalStateException> {
            InnerTubeParser.parseStream(playerResponse("""{"mimeType":"video/mp4","url":"v"}"""), "xyz789")
        }
        assertTrue(failure.message?.contains("xyz789") == true, failure.message)
    }

    // ─────────────────────────────── playlists

    private fun playlistResponse(items: String, title: String? = "Road trip") = json(
        """
        {${if (title != null) """"metadata":{"playlistMetadataRenderer":{"title":"$title"}},""" else ""}
         "contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":
         {"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
         {"playlistVideoListRenderer":{"contents":[$items]}}]}}]}}}}]}}}
        """.trimIndent(),
    )

    private fun playlistItem(id: String = "p1", title: String = "Track", seconds: String = "240") = """
        {"playlistVideoRenderer":{
          "videoId":"$id",
          "title":{"runs":[{"text":"$title"}]},
          "shortBylineText":{"runs":[{"text":"An Artist"}]},
          "lengthSeconds":"$seconds",
          "thumbnail":{"thumbnails":[{"url":"small.jpg"},{"url":"big.jpg"}]}
        }}
    """.trimIndent()

    @Test
    fun `a playlist is read with its tracks and title`() {
        val details = InnerTubeParser.parsePlaylist(
            playlistResponse("${playlistItem("a")},${playlistItem("b")}"),
            "https://youtube.com/playlist?list=PL123",
        )
        assertEquals("Road trip", details.title)
        assertEquals(listOf("a", "b"), details.tracks.map { it.videoId })
        assertEquals(240_000, details.tracks.first().durationMs)
        assertEquals("big.jpg", details.thumbnailUrl, "the playlist wears its first track's art")
    }

    @Test
    fun `a playlist with no title falls back rather than failing`() {
        val details = InnerTubeParser.parsePlaylist(playlistResponse(playlistItem(), title = null), "u")
        assertEquals("Playlist", details.title)
    }

    @Test
    fun `an empty playlist blames the likely cause`() {
        // Far more often private than actually empty, and saying so is more use than nothing.
        val failure = assertFailsWith<IllegalStateException> {
            InnerTubeParser.parsePlaylist(playlistResponse(""), "u")
        }
        assertTrue(failure.message?.contains("public/unlisted") == true, failure.message)
    }

    // ─────────────────────────────── durations and links

    @Test
    fun `durations are read in every shape YouTube writes them`() {
        assertEquals(355_000, InnerTubeParser.parseLengthMs("5:55"))
        assertEquals(3_753_000, InnerTubeParser.parseLengthMs("1:02:33"))
        assertEquals(45_000, InnerTubeParser.parseLengthMs("45"))
    }

    @Test
    fun `an unreadable duration is zero rather than a crash`() {
        assertEquals(0L, InnerTubeParser.parseLengthMs("LIVE"))
        assertEquals(0L, InnerTubeParser.parseLengthMs(""))
        assertEquals(0L, InnerTubeParser.parseLengthMs("4:xx"))
    }

    @Test
    fun `playlist links are recognised in the forms people paste`() {
        assertEquals("PL123", extractPlaylistId("https://www.youtube.com/playlist?list=PL123"))
        assertEquals("PL123", extractPlaylistId("https://youtu.be/abc?list=PL123&index=2"))
        assertEquals("PL123", extractPlaylistId("PL123"))
    }

    @Test
    fun `a link with no playlist in it is refused`() {
        assertEquals(null, extractPlaylistId("https://www.youtube.com/watch?v=abc123"))
        assertEquals(null, extractPlaylistId("some words"))
    }
}

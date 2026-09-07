package io.github.meko123456.syncbeats.core.data

import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.ResolvedStream
import io.github.meko123456.syncbeats.core.model.SearchResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Turns InnerTube responses into the app's own types.
 *
 * Separated from [InnerTubeMusicSource] because this is the part that breaks. YouTube changes the
 * shape of these responses without notice, and when it does the symptom is not an exception — it
 * is search quietly returning nothing, or a playlist coming back empty. That failure is invisible
 * to every other kind of test and trivially caught by a recorded response.
 *
 * Everything here is pure: give it parsed JSON, get back results or an exception. The HTTP call,
 * the client versions and the user agents stay in the source class.
 */
object InnerTubeParser {

    /** Most search pages have well under this; the cap is to stop a runaway response. */
    const val MAX_RESULTS = 25

    fun parseSearch(root: JsonElement?): List<SearchResult> {
        val sections = root["contents"]["twoColumnSearchResultsRenderer"]["primaryContents"]["sectionListRenderer"]["contents"].array
        val results = mutableListOf<SearchResult>()
        for (section in sections) {
            for (item in section["itemSectionRenderer"]["contents"].array) {
                val video = item["videoRenderer"] as? JsonObject ?: continue
                val videoId = video["videoId"].str ?: continue
                // No lengthText means a live stream. There is nothing to sync to on a live
                // stream — the position everyone is meant to agree on does not exist — so they
                // are dropped rather than offered and then failing to play.
                val lengthText = video["lengthText"]["simpleText"].str ?: continue
                val title = video["title"]["runs"][0]["text"].str ?: continue
                results += SearchResult(
                    videoId = videoId,
                    title = title,
                    artist = video["ownerText"]["runs"][0]["text"].str ?: "",
                    thumbnailUrl = video["thumbnail"]["thumbnails"].array.lastOrNull()?.get("url").str ?: "",
                    durationMs = parseLengthMs(lengthText),
                )
                if (results.size >= MAX_RESULTS) return results
            }
        }
        return results
    }

    /**
     * Picks the audio stream to play.
     *
     * Highest bitrate among the audio-only formats: this app plays audio and never shows video, so
     * a video format is not a lower-quality option, it is the wrong thing entirely.
     */
    fun parseStream(root: JsonElement?, videoId: String): ResolvedStream {
        val status = root["playabilityStatus"]["status"].str
        check(status == "OK") {
            "Video not playable: $status (${root["playabilityStatus"]["reason"].str ?: "no reason"})"
        }
        val best = root["streamingData"]["adaptiveFormats"].array
            .filter { it["mimeType"].str.orEmpty().startsWith("audio/") && it["url"].str != null }
            .maxByOrNull { it["bitrate"].str?.toLongOrNull() ?: 0L }
            ?: error("No audio stream in response for $videoId")
        return ResolvedStream(
            url = best["url"].str!!,
            // "audio/mp4; codecs=\"mp4a.40.2\"" — the player wants the type, not the codec list.
            mimeType = best["mimeType"].str?.substringBefore(";"),
            durationMs = (root["videoDetails"]["lengthSeconds"].str?.toLongOrNull() ?: 0L) * 1000L,
        )
    }

    fun parsePlaylist(root: JsonElement?, url: String): PlaylistDetails {
        val tab = root["contents"]["twoColumnBrowseResultsRenderer"]["tabs"][0]["tabRenderer"]["content"]
        val section = tab["sectionListRenderer"]["contents"][0]["itemSectionRenderer"]["contents"][0]
        val tracks = section["playlistVideoListRenderer"]["contents"].array.mapNotNull { item ->
            val video = item["playlistVideoRenderer"] as? JsonObject ?: return@mapNotNull null
            val videoId = video["videoId"].str ?: return@mapNotNull null
            val title = video["title"]["runs"][0]["text"].str ?: return@mapNotNull null
            SearchResult(
                videoId = videoId,
                title = title,
                artist = video["shortBylineText"]["runs"][0]["text"].str ?: "",
                thumbnailUrl = video["thumbnail"]["thumbnails"].array.lastOrNull()?.get("url").str ?: "",
                durationMs = (video["lengthSeconds"].str?.toLongOrNull() ?: 0L) * 1000L,
            )
        }
        // An empty list is far more often a private playlist than an actually empty one, and
        // saying so is more use than handing back nothing.
        if (tracks.isEmpty()) error("Playlist is empty or not accessible (is it public/unlisted?)")
        return PlaylistDetails(
            url = url.trim(),
            title = root["metadata"]["playlistMetadataRenderer"]["title"].str
                ?: root["header"]["playlistHeaderRenderer"]["title"]["simpleText"].str
                ?: "Playlist",
            thumbnailUrl = tracks.first().thumbnailUrl,
            tracks = tracks,
        )
    }

    /** `4:13`, `1:02:33`, or just seconds. Anything else is length unknown rather than an error. */
    fun parseLengthMs(text: String): Long {
        val parts = text.split(":").map { it.trim().toLongOrNull() }
        if (parts.any { it == null }) return 0L
        val numbers = parts.filterNotNull()
        return when (numbers.size) {
            3 -> (numbers[0] * 3600 + numbers[1] * 60 + numbers[2]) * 1000
            2 -> (numbers[0] * 60 + numbers[1]) * 1000
            1 -> numbers[0] * 1000
            else -> 0L
        }
    }
}

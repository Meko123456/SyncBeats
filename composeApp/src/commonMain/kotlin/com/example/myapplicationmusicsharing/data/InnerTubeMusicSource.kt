package com.example.myapplicationmusicsharing.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Minimal client for YouTube's internal InnerTube API — the same API NewPipe
 * wraps on Android. Metadata (search/browse) uses the WEB client; stream
 * resolution uses the IOS client, whose responses carry direct URLs that need
 * no signature deciphering. Like any unofficial client, expect to update
 * client versions when YouTube changes things.
 */
class InnerTubeMusicSource(private val http: HttpClient) : MusicSource {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(endpoint: String, body: JsonObject, ios: Boolean = false): JsonElement {
        val response = http.post("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header(
                "User-Agent",
                if (ios) IOS_USER_AGENT
                else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            setBody(body.toString())
        }
        return json.parseToJsonElement(response.bodyAsText())
    }

    private fun webContext() = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB")
            put("clientVersion", WEB_CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
        }
    }

    private fun iosContext() = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "IOS")
            put("clientVersion", IOS_CLIENT_VERSION)
            put("deviceMake", "Apple")
            put("deviceModel", "iPhone16,2")
            put("osName", "iPhone")
            put("osVersion", "18.1.0.22B83")
            put("hl", "en")
            put("gl", "US")
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val body = buildJsonObject {
            put("context", webContext())
            put("query", query)
            put("params", "EgIQAQ==") // videos only
        }
        val root = call("search", body)
        val sections = root["contents"]["twoColumnSearchResultsRenderer"]["primaryContents"]["sectionListRenderer"]["contents"].array
        val results = mutableListOf<SearchResult>()
        for (section in sections) {
            val items = section["itemSectionRenderer"]["contents"].array
            for (item in items) {
                val video = item["videoRenderer"] as? JsonObject ?: continue
                val videoId = video["videoId"].str ?: continue
                val lengthText = video["lengthText"]["simpleText"].str ?: continue // no length = live
                results += SearchResult(
                    videoId = videoId,
                    title = video["title"]["runs"][0]["text"].str ?: continue,
                    artist = video["ownerText"]["runs"][0]["text"].str ?: "",
                    thumbnailUrl = video["thumbnail"]["thumbnails"].array.lastOrNull()
                        ?.get("url").str ?: "",
                    durationMs = parseLengthMs(lengthText),
                )
                if (results.size >= 25) return results
            }
        }
        return results
    }

    override suspend fun resolveAudioStream(videoId: String): ResolvedStream {
        val body = buildJsonObject {
            put("context", iosContext())
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val root = call("player", body, ios = true)
        val status = root["playabilityStatus"]["status"].str
        check(status == "OK") {
            "Video not playable: $status (${root["playabilityStatus"]["reason"].str ?: "no reason"})"
        }
        val formats = root["streamingData"]["adaptiveFormats"].array
        val best = formats
            .filter { it["mimeType"].str.orEmpty().startsWith("audio/") && it["url"].str != null }
            .maxByOrNull { it["bitrate"].str?.toLongOrNull() ?: (it["bitrate"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L }
            ?: error("No audio stream in response for $videoId")
        val durationMs = (root["videoDetails"]["lengthSeconds"].str?.toLongOrNull() ?: 0L) * 1000L
        return ResolvedStream(
            url = best["url"].str!!,
            mimeType = best["mimeType"].str?.substringBefore(";"),
            durationMs = durationMs,
        )
    }

    override suspend fun trending(): List<SearchResult> = search("top songs this week")

    override suspend fun playlist(url: String): PlaylistDetails {
        val listId = extractPlaylistId(url) ?: error("Not a playlist link (missing list=)")
        val body = buildJsonObject {
            put("context", webContext())
            put("browseId", "VL$listId")
        }
        val root = call("browse", body)
        val tab = root["contents"]["twoColumnBrowseResultsRenderer"]["tabs"][0]["tabRenderer"]["content"]
        val section = tab["sectionListRenderer"]["contents"][0]["itemSectionRenderer"]["contents"][0]
        val items = section["playlistVideoListRenderer"]["contents"].array
        val tracks = items.mapNotNull { item ->
            val video = item["playlistVideoRenderer"] as? JsonObject ?: return@mapNotNull null
            val videoId = video["videoId"].str ?: return@mapNotNull null
            SearchResult(
                videoId = videoId,
                title = video["title"]["runs"][0]["text"].str ?: return@mapNotNull null,
                artist = video["shortBylineText"]["runs"][0]["text"].str ?: "",
                thumbnailUrl = video["thumbnail"]["thumbnails"].array.lastOrNull()?.get("url").str ?: "",
                durationMs = (video["lengthSeconds"].str?.toLongOrNull() ?: 0L) * 1000L,
            )
        }
        if (tracks.isEmpty()) error("Playlist is empty or not accessible (is it public/unlisted?)")
        val title = root["metadata"]["playlistMetadataRenderer"]["title"].str
            ?: root["header"]["playlistHeaderRenderer"]["title"]["simpleText"].str
            ?: "Playlist"
        return PlaylistDetails(
            url = url.trim(),
            title = title,
            thumbnailUrl = tracks.first().thumbnailUrl,
            tracks = tracks,
        )
    }

    private fun parseLengthMs(text: String): Long {
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            2 -> (parts[0] * 60 + parts[1]) * 1000
            1 -> parts[0] * 1000
            else -> 0L
        }
    }

    private companion object {
        const val WEB_CLIENT_VERSION = "2.20250312.04.00"
        const val IOS_CLIENT_VERSION = "20.11.6"
        const val IOS_USER_AGENT =
            "com.google.ios.youtube/20.11.6 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
    }
}

// ── tiny JsonElement navigation helpers ──

private operator fun JsonElement?.get(key: String): JsonElement? =
    (this as? JsonObject)?.get(key)

private operator fun JsonElement?.get(index: Int): JsonElement? =
    (this as? JsonArray)?.getOrNull(index)

private val JsonElement?.array: List<JsonElement>
    get() = (this as? JsonArray) ?: emptyList()

private val JsonElement?.str: String?
    get() = (this as? JsonPrimitive)?.contentOrNull

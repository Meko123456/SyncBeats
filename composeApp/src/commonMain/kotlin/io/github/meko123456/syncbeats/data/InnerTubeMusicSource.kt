package io.github.meko123456.syncbeats.data

import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.ResolvedStream
import io.github.meko123456.syncbeats.core.model.SearchResult
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
        return InnerTubeParser.parseSearch(call("search", body))
    }

    override suspend fun resolveAudioStream(videoId: String): ResolvedStream {
        val body = buildJsonObject {
            put("context", iosContext())
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return InnerTubeParser.parseStream(call("player", body, ios = true), videoId)
    }

    override suspend fun trending(): List<SearchResult> = search("top songs this week")

    override suspend fun playlist(url: String): PlaylistDetails {
        val listId = extractPlaylistId(url) ?: error("Not a playlist link (missing list=)")
        val body = buildJsonObject {
            put("context", webContext())
            put("browseId", "VL$listId")
        }
        return InnerTubeParser.parsePlaylist(call("browse", body), url)
    }

    private companion object {
        const val WEB_CLIENT_VERSION = "2.20250312.04.00"
        const val IOS_CLIENT_VERSION = "20.11.6"
        const val IOS_USER_AGENT =
            "com.google.ios.youtube/20.11.6 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
    }
}


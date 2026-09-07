package io.github.meko123456.syncbeats.data

import io.github.meko123456.syncbeats.core.model.SearchResult
import io.github.meko123456.syncbeats.util.array
import io.github.meko123456.syncbeats.util.get
import io.github.meko123456.syncbeats.util.str
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class AccountPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val trackCount: Int,
)

/**
 * The signed-in user's own YouTube library via the official Data API v3
 * (youtube.readonly scope). Metadata only — playback still resolves through
 * the platform MusicSource.
 */
class YouTubeAccountRepository(
    private val http: HttpClient,
    private val google: GoogleAuthController,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isConnected(): Boolean = google.freshAccessTokenSuspend() != null

    private suspend fun call(endpoint: String, vararg params: Pair<String, String>): JsonElement {
        val token = google.freshAccessTokenSuspend()
            ?: error("YouTube account not connected")
        val response: HttpResponse = http.get("https://www.googleapis.com/youtube/v3/$endpoint") {
            header("Authorization", "Bearer $token")
            params.forEach { (k, v) -> parameter(k, v) }
        }
        val body = response.bodyAsText()
        check(response.status.isSuccess()) {
            val reason = json.parseToJsonElement(body)["error"]["message"].str ?: response.status.toString()
            "YouTube API error: $reason"
        }
        return json.parseToJsonElement(body)
    }

    suspend fun myPlaylists(): List<AccountPlaylist> {
        val root = call(
            "playlists",
            "part" to "snippet,contentDetails",
            "mine" to "true",
            "maxResults" to "50",
        )
        return root["items"].array.mapNotNull { item ->
            AccountPlaylist(
                id = item["id"].str ?: return@mapNotNull null,
                title = item["snippet"]["title"].str ?: "Playlist",
                thumbnailUrl = bestThumb(item["snippet"]["thumbnails"]),
                trackCount = item["contentDetails"]["itemCount"].str?.toIntOrNull() ?: 0,
            )
        }
    }

    suspend fun likedSongs(): List<SearchResult> {
        val root = call(
            "videos",
            "part" to "snippet,contentDetails",
            "myRating" to "like",
            "maxResults" to "25",
        )
        return root["items"].array.mapNotNull { item ->
            SearchResult(
                videoId = item["id"].str ?: return@mapNotNull null,
                title = item["snippet"]["title"].str ?: return@mapNotNull null,
                artist = item["snippet"]["channelTitle"].str ?: "",
                thumbnailUrl = bestThumb(item["snippet"]["thumbnails"]),
                durationMs = parseIso8601DurationMs(item["contentDetails"]["duration"].str),
            )
        }
    }

    suspend fun playlistItems(playlistId: String): List<SearchResult> {
        val root = call(
            "playlistItems",
            "part" to "snippet,contentDetails",
            "playlistId" to playlistId,
            "maxResults" to "50",
        )
        return root["items"].array.mapNotNull { item ->
            val videoId = item["contentDetails"]["videoId"].str ?: return@mapNotNull null
            val title = item["snippet"]["title"].str ?: return@mapNotNull null
            if (title == "Private video" || title == "Deleted video") return@mapNotNull null
            SearchResult(
                videoId = videoId,
                title = title,
                artist = item["snippet"]["videoOwnerChannelTitle"].str ?: "",
                thumbnailUrl = bestThumb(item["snippet"]["thumbnails"]),
                durationMs = 0L, // filled when the stream is resolved
            )
        }
    }

    /** Latest uploads across the first few subscribed channels, newest first. */
    suspend fun subscriptionFeed(): List<SearchResult> {
        val subs = call(
            "subscriptions",
            "part" to "snippet",
            "mine" to "true",
            "maxResults" to "10",
            "order" to "relevance",
        )
        val channelIds = subs["items"].array.mapNotNull {
            it["snippet"]["resourceId"]["channelId"].str
        }.take(6)
        if (channelIds.isEmpty()) return emptyList()

        val channels = call(
            "channels",
            "part" to "contentDetails",
            "id" to channelIds.joinToString(","),
        )
        val uploadPlaylists = channels["items"].array.mapNotNull {
            it["contentDetails"]["relatedPlaylists"]["uploads"].str
        }

        val feed = mutableListOf<Pair<String, SearchResult>>()
        for (playlistId in uploadPlaylists) {
            val items = runCatching {
                call(
                    "playlistItems",
                    "part" to "snippet,contentDetails",
                    "playlistId" to playlistId,
                    "maxResults" to "3",
                )
            }.getOrNull() ?: continue
            items["items"].array.forEach { item ->
                val videoId = item["contentDetails"]["videoId"].str ?: return@forEach
                val publishedAt = item["contentDetails"]["videoPublishedAt"].str
                    ?: item["snippet"]["publishedAt"].str ?: ""
                feed += publishedAt to SearchResult(
                    videoId = videoId,
                    title = item["snippet"]["title"].str ?: return@forEach,
                    artist = item["snippet"]["channelTitle"].str ?: "",
                    thumbnailUrl = bestThumb(item["snippet"]["thumbnails"]),
                    durationMs = 0L,
                )
            }
        }
        return feed.sortedByDescending { it.first }.map { it.second }.take(15)
    }

    private fun bestThumb(thumbnails: JsonElement?): String =
        thumbnails["medium"]["url"].str
            ?: thumbnails["high"]["url"].str
            ?: thumbnails["default"]["url"].str
            ?: ""

    /** "PT1H2M3S" → millis. */
    private fun parseIso8601DurationMs(value: String?): Long {
        if (value == null) return 0L
        val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").find(value) ?: return 0L
        val (h, m, s) = match.destructured
        val hours = h.toLongOrNull() ?: 0L
        val minutes = m.toLongOrNull() ?: 0L
        val seconds = s.toLongOrNull() ?: 0L
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000
    }
}

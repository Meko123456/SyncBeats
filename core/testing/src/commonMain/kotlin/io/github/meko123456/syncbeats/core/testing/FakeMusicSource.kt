package io.github.meko123456.syncbeats.core.testing

import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.ResolvedStream
import io.github.meko123456.syncbeats.core.model.SearchResult

/** A [MusicSource] that returns whatever you hand it. */
class FakeMusicSource(
    var searchResults: List<SearchResult> = emptyList(),
    var trendingResults: List<SearchResult> = emptyList(),
    var playlistDetails: PlaylistDetails? = null,
    var stream: ResolvedStream = ResolvedStream(url = "https://example.test/a.m4a", mimeType = "audio/mp4", durationMs = 1_000),
) : MusicSource {

    var failWith: Throwable? = null
    val searches = mutableListOf<String>()
    val playlistUrls = mutableListOf<String>()

    override suspend fun search(query: String): List<SearchResult> {
        failWith?.let { throw it }
        searches += query
        return searchResults
    }

    override suspend fun resolveAudioStream(videoId: String): ResolvedStream {
        failWith?.let { throw it }
        return stream
    }

    override suspend fun trending(): List<SearchResult> {
        failWith?.let { throw it }
        return trendingResults
    }

    override suspend fun playlist(url: String): PlaylistDetails {
        failWith?.let { throw it }
        playlistUrls += url
        return playlistDetails ?: error("No playlist configured for $url")
    }
}

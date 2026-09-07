package io.github.meko123456.syncbeats.data

import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.ResolvedStream
import io.github.meko123456.syncbeats.core.model.SearchResult

/**
 * Where music metadata and streams come from. Android implements this with
 * NewPipe Extractor; iOS with a lightweight InnerTube client.
 */
interface MusicSource {
    suspend fun search(query: String): List<SearchResult>
    suspend fun resolveAudioStream(videoId: String): ResolvedStream
    suspend fun trending(): List<SearchResult>

    /** Loads a public/unlisted playlist from a pasted YouTube URL. */
    suspend fun playlist(url: String): PlaylistDetails
}

/** Extracts the `list=` playlist id from a pasted YouTube URL (or accepts a bare id). */
fun extractPlaylistId(url: String): String? {
    Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)?.let { return it }
    val trimmed = url.trim()
    return trimmed.takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains(' ') }
}

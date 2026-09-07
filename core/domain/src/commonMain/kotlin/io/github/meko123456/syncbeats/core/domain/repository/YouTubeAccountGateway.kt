package io.github.meko123456.syncbeats.core.domain.repository

import io.github.meko123456.syncbeats.core.model.AccountPlaylist
import io.github.meko123456.syncbeats.core.model.SearchResult

/**
 * The signed-in user's own YouTube library, read through the YouTube Data API.
 *
 * A port so the home screen can offer "my playlists" and "liked songs" without knowing about Ktor,
 * OAuth tokens or the API's JSON shape.
 */
interface YouTubeAccountGateway {
    suspend fun isConnected(): Boolean
    suspend fun myPlaylists(): List<AccountPlaylist>
    suspend fun likedSongs(): List<SearchResult>
    suspend fun playlistItems(playlistId: String): List<SearchResult>
    suspend fun subscriptionFeed(): List<SearchResult>
}

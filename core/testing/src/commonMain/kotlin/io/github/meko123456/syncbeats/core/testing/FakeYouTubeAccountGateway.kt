package io.github.meko123456.syncbeats.core.testing

import io.github.meko123456.syncbeats.core.domain.repository.YouTubeAccountGateway
import io.github.meko123456.syncbeats.core.model.AccountPlaylist
import io.github.meko123456.syncbeats.core.model.SearchResult

/** A [YouTubeAccountGateway] with a configurable connected state and library. */
class FakeYouTubeAccountGateway(
    var connected: Boolean = false,
    var playlists: List<AccountPlaylist> = emptyList(),
    var liked: List<SearchResult> = emptyList(),
    var subscriptions: List<SearchResult> = emptyList(),
    var items: List<SearchResult> = emptyList(),
) : YouTubeAccountGateway {

    var failWith: Throwable? = null
    val requestedPlaylistIds = mutableListOf<String>()

    override suspend fun isConnected(): Boolean = connected

    override suspend fun myPlaylists(): List<AccountPlaylist> {
        failWith?.let { throw it }; return playlists
    }

    override suspend fun likedSongs(): List<SearchResult> {
        failWith?.let { throw it }; return liked
    }

    override suspend fun playlistItems(playlistId: String): List<SearchResult> {
        failWith?.let { throw it }
        requestedPlaylistIds += playlistId
        return items
    }

    override suspend fun subscriptionFeed(): List<SearchResult> {
        failWith?.let { throw it }; return subscriptions
    }
}

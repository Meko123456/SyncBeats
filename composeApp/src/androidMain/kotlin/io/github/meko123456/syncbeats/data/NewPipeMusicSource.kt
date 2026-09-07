package io.github.meko123456.syncbeats.data

import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.ResolvedStream
import io.github.meko123456.syncbeats.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Android music source backed by NewPipe Extractor (JVM-only). */
class NewPipeMusicSource : MusicSource {

    private val youtube get() = ServiceList.YouTube

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val extractor = youtube.getSearchExtractor(
            query,
            listOf("music_songs"),
            "",
        )
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSearchResult() }
            .take(25)
    }

    override suspend fun resolveAudioStream(videoId: String): ResolvedStream =
        withContext(Dispatchers.IO) {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val info: StreamInfo = StreamInfo.getInfo(NewPipe.getService(youtube.serviceId), videoUrl)
            val best = info.audioStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }
                ?: error("No audio stream available for $videoId")
            ResolvedStream(
                url = best.content,
                mimeType = best.format?.mimeType,
                durationMs = info.duration * 1000L,
            )
        }

    override suspend fun trending(): List<SearchResult> = withContext(Dispatchers.IO) {
        val service = NewPipe.getService(youtube.serviceId)
        val kioskList = service.kioskList
        val linkHandler = kioskList.getListLinkHandlerFactoryByType(kioskList.defaultKioskId)
            .fromId(kioskList.defaultKioskId)
        val info = KioskInfo.getInfo(service, linkHandler.url)
        val fromKiosk = info.relatedItems.filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSearchResult() }
            // Live streams have no duration and can't be position-synced.
            .filter { it.durationMs > 0 }
            .take(20)
        // Some regions' trending page is all live news; fall back to music search.
        fromKiosk.ifEmpty { search("top songs this week") }
    }

    override suspend fun playlist(url: String): PlaylistDetails = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(NewPipe.getService(youtube.serviceId), url.trim())
        val tracks = info.relatedItems.filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSearchResult() }
        if (tracks.isEmpty()) error("Playlist is empty or not accessible (is it public/unlisted?)")
        PlaylistDetails(
            url = url.trim(),
            title = info.name.orEmpty().ifBlank { "Playlist" },
            thumbnailUrl = info.thumbnails.firstOrNull()?.url
                ?: tracks.first().thumbnailUrl,
            tracks = tracks,
        )
    }

    private fun StreamInfoItem.toSearchResult(): SearchResult? {
        val url = url ?: return null
        val id = extractVideoId(url) ?: return null
        return SearchResult(
            videoId = id,
            title = name ?: "",
            artist = uploaderName ?: "",
            thumbnailUrl = thumbnails.firstOrNull()?.url ?: "",
            durationMs = if (duration > 0) duration * 1000L else 0L,
        )
    }

    private fun extractVideoId(url: String): String? {
        val regex = Regex("[?&]v=([A-Za-z0-9_-]{11})")
        regex.find(url)?.groupValues?.get(1)?.let { return it }
        val shortRegex = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")
        shortRegex.find(url)?.groupValues?.get(1)?.let { return it }
        return null
    }
}

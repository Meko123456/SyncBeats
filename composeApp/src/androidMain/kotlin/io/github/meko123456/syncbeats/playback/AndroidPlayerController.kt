package io.github.meko123456.syncbeats.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** Media3/ExoPlayer behind a MediaSession, bound via MediaController. */
class AndroidPlayerController(
    private val context: Context,
) : PlayerController {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _playbackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val playbackEnded: SharedFlow<Unit> = _playbackEnded.asSharedFlow()

    private var endedForCurrentItem = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                if (!endedForCurrentItem) {
                    endedForCurrentItem = true
                    _playbackEnded.tryEmit(Unit)
                }
            } else {
                endedForCurrentItem = false
            }
        }
    }

    override suspend fun connect() {
        suspendCancellableCoroutine { cont ->
            val current = controller
            if (current != null && current.isConnected) {
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    val c = future.get()
                    c.addListener(playerListener)
                    controller = c
                    _connected.value = true
                    if (!cont.isCompleted) cont.resume(Unit)
                } catch (t: Throwable) {
                    if (!cont.isCompleted) cont.resumeWith(Result.failure(t))
                }
            }, MoreExecutors.directExecutor())
        }
    }

    override fun loadAndPrepare(
        url: String,
        mimeType: String?,
        title: String,
        artist: String,
        thumb: String,
        startMs: Long,
        playWhenReady: Boolean,
    ) {
        val c = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .apply { mimeType?.let { setMimeType(it) } }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(if (thumb.isNotBlank()) thumb.toUri() else null)
                    .build()
            )
            .build()
        c.setMediaItem(mediaItem, startMs)
        c.prepare()
        c.playWhenReady = playWhenReady
    }

    override fun setPlayWhenReady(value: Boolean) {
        controller?.playWhenReady = value
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    override fun isPlaying(): Boolean = controller?.isPlaying ?: false

    override fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
    }
}

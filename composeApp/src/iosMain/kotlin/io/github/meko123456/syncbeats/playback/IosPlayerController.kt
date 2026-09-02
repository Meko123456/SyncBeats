@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.meko123456.syncbeats.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

/** AVPlayer-backed player. Streams the resolved audio URL directly. */
class IosPlayerController : PlayerController {

    private var player: AVPlayer? = null
    private var endObserver: NSObjectProtocol? = null

    // AVPlayer needs no service binding; it's always ready.
    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _playbackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val playbackEnded: SharedFlow<Unit> = _playbackEnded.asSharedFlow()

    override suspend fun connect() {
        configureAudioSession()
    }

    private fun configureAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
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
        configureAudioSession()
        removeEndObserver()
        val nsUrl = NSURL.URLWithString(url) ?: return
        val item = AVPlayerItem(uRL = nsUrl)
        val avPlayer = AVPlayer(playerItem = item)
        player = avPlayer
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            _playbackEnded.tryEmit(Unit)
        }
        if (startMs > 0) {
            avPlayer.seekToTime(CMTimeMakeWithSeconds(startMs / 1000.0, 1000))
        }
        if (playWhenReady) avPlayer.play()
    }

    private fun removeEndObserver() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
    }

    override fun setPlayWhenReady(value: Boolean) {
        if (value) player?.play() else player?.pause()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
    }

    override fun currentPositionMs(): Long {
        val time = player?.currentTime() ?: return 0L
        val seconds = CMTimeGetSeconds(time)
        return if (seconds.isNaN()) 0L else (seconds * 1000).toLong()
    }

    override fun isPlaying(): Boolean = (player?.rate ?: 0f) > 0f

    override fun stop() {
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        removeEndObserver()
        player = null
    }
}

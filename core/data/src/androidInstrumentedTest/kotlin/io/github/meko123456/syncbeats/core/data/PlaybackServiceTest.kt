package io.github.meko123456.syncbeats.core.data

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does media3 still play audio on a real device?
 *
 * The compiler answers whether the APIs still exist; it cannot answer this. Every media3 upgrade
 * risks the session layer rather than the player — the service binding, the controller handshake,
 * the foreground-service rules that change with each Android release — and the app reaches its
 * player only through that layer, so that is what this binds to. Nothing here is a mock: it is
 * the real [PlaybackService], bound the way [AndroidPlayerController] binds it.
 *
 * Deliberately no network and no sign-in. The tone is generated here, so this runs against any
 * build on any device and is the check to run before accepting a media3 bump.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    private lateinit var controller: MediaController
    private lateinit var toneFile: File

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun bind() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        toneFile = File(context.cacheDir, "tone.wav").apply { writeBytes(sineWav(millis = 1_000)) }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controller = future.get(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue("controller bound but reports disconnected", controller.isConnected)
    }

    @After
    fun unbind() {
        if (::controller.isInitialized) onMain { controller.release() }
        if (::toneFile.isInitialized) toneFile.delete()
    }

    @Test
    fun serviceHandsBackAConnectedController() {
        // The handshake itself. If media3 changes how a session is published or discovered this
        // fails first, and it fails with a clear cause rather than as silence in the app.
        assertTrue(controller.isConnected)
    }

    @Test
    fun aPreparedItemBecomesReadyWithItsRealDuration() {
        val ready = awaitState(Player.STATE_READY) {
            controller.setMediaItem(MediaItem.fromUri(toneFile.toURI().toString()))
            controller.prepare()
        }
        assertTrue("player never reached STATE_READY", ready)

        val duration = onMainGet { controller.duration }
        // A decoded second, not a guess: if the pipeline silently fails the duration is UNSET.
        assertTrue("duration was $duration, expected about 1000ms", duration in 900..1_100)
    }

    @Test
    fun playbackActuallyAdvancesAndThenEnds() {
        // Reaching READY only proves it loaded. This proves it plays — the position moves — and
        // then that STATE_ENDED arrives, which is the signal the app auto-advances the queue on.
        assertTrue(
            "player never reached STATE_READY",
            awaitState(Player.STATE_READY) {
                controller.setMediaItem(MediaItem.fromUri(toneFile.toURI().toString()))
                controller.prepare()
            },
        )

        onMain { controller.play() }
        val ended = awaitState(Player.STATE_ENDED) {}
        assertTrue("a one-second tone never reached STATE_ENDED", ended)
        assertTrue("position never advanced", onMainGet { controller.currentPosition } > 0)
    }

    @Test
    fun theQueueSurvivesTheRoundTripThroughTheSession() {
        // Items cross a process boundary to the service and back, so "it is in the queue" is a
        // real question, not a tautology.
        val items = listOf("one", "two", "three").map {
            MediaItem.Builder().setUri(toneFile.toURI().toString()).setMediaId(it).build()
        }
        onMain { controller.setMediaItems(items) }
        assertEquals(3, onMainGet { controller.mediaItemCount })
        assertEquals("two", onMainGet { controller.getMediaItemAt(1).mediaId })
    }

    // ─────────────────────────────── helpers

    /** Player calls are main-thread only, and the test runs on the instrumentation thread. */
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> onMainGet(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Runs [trigger] on the main thread and waits for [target], or gives up. */
    private fun awaitState(target: Int, trigger: () -> Unit): Boolean {
        val latch = CountDownLatch(1)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == target) latch.countDown()
            }
        }
        onMain {
            controller.addListener(listener)
            if (controller.playbackState == target) latch.countDown()
            trigger()
        }
        val reached = latch.await(STATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        onMain { controller.removeListener(listener) }
        return reached
    }

    /**
     * A 16-bit mono 44.1kHz sine, built here so the test carries no asset and no network. A file
     * on disk also means a decode failure looks like a decode failure, not a download problem.
     */
    private fun sineWav(millis: Int, sampleRate: Int = 44_100, hz: Double = 440.0): ByteArray {
        val frames = sampleRate * millis / 1000
        val dataBytes = frames * 2
        val out = ByteArrayOutputStream(44 + dataBytes)

        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) = repeat(4) { out.write((v shr (it * 8)) and 0xFF) }
        fun le16(v: Int) = repeat(2) { out.write((v shr (it * 8)) and 0xFF) }

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)
        le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        ascii("data"); le32(dataBytes)
        repeat(frames) { i ->
            le16(((sin(2 * PI * hz * i / sampleRate) * 0.4 * Short.MAX_VALUE).toInt()) and 0xFFFF)
        }
        return out.toByteArray()
    }

    private companion object {
        const val BIND_TIMEOUT_SECONDS = 10L
        const val STATE_TIMEOUT_SECONDS = 15L
    }
}

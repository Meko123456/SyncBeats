package io.github.meko123456.syncbeats

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.meko123456.syncbeats.core.data.di.dataModule
import io.github.meko123456.syncbeats.core.data.di.platformModule
import io.github.meko123456.syncbeats.core.domain.di.APP_SCOPE
import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.core.domain.repository.RoomRepository
import io.github.meko123456.syncbeats.core.domain.repository.YouTubeAccountGateway
import io.github.meko123456.syncbeats.core.domain.sync.SyncEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication

/**
 * Can the dependency graph actually be built?
 *
 * A missing Koin binding is invisible to the compiler: `get()` is reified at the call site, so the
 * app compiles happily and then dies the first time a screen is opened. Splitting the graph across
 * nine modules multiplies the places that can go wrong, and three of the four ViewModels sit behind
 * sign-in — so a manual walkthrough on a fresh device can only ever reach the first one.
 *
 * This resolves every dependency the four ViewModels are constructed from, against the real
 * modules and a real Android context. If each of these can be produced, every ViewModel's
 * constructor can be satisfied.
 */
@RunWith(AndroidJUnit4::class)
class KoinGraphTest {

    private var koin: Koin? = null

    private fun graph(): Koin = koinApplication {
        androidContext(ApplicationProvider.getApplicationContext())
        modules(dataModule, platformModule)
    }.koin.also { koin = it }

    @After
    fun tearDown() {
        koin?.close()
        koin = null
    }

    @Test
    fun everyPortTheScreensDependOnResolves() {
        val k = graph()
        // The domain ports — bound in dataModule to their Firebase/Ktor implementations.
        assertNotNull("RoomRepository", k.get<RoomRepository>())
        assertNotNull("AuthGateway", k.get<AuthGateway>())
        assertNotNull("YouTubeAccountGateway", k.get<YouTubeAccountGateway>())
        assertNotNull("HttpClient", k.get<HttpClient>())
    }

    @Test
    fun theSyncEngineAndItsCollaboratorsResolve() {
        val k = graph()
        // SyncEngine takes four ports; resolving it proves all four are bound too.
        assertNotNull("SyncEngine", k.get<SyncEngine>())
        assertNotNull("PlayerController", k.get<PlayerController>())
        assertNotNull("MusicSource", k.get<MusicSource>())
    }

    @Test
    fun theAndroidOnlyBindingsResolve() {
        val k = graph()
        // Supplied by platformModule's Android actual — the piece iOS implements in Swift.
        assertNotNull("GoogleAuthController", k.get<GoogleAuthController>())
    }

    @Test
    fun theApplicationScopeQualifierResolves() {
        val k = graph()
        // RoomViewModel's last constructor argument. The qualifier lives in :core:domain so a
        // feature can name it without depending on :core:data — this proves that wiring holds.
        assertNotNull("APP_SCOPE CoroutineScope", k.get<CoroutineScope>(APP_SCOPE))
    }
}

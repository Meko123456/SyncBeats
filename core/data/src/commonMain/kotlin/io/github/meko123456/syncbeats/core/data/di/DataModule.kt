package io.github.meko123456.syncbeats.core.data.di

import io.github.meko123456.syncbeats.core.data.AuthRepository
import io.github.meko123456.syncbeats.core.data.FirebaseRepository
import io.github.meko123456.syncbeats.core.data.YouTubeAccountRepository
import io.github.meko123456.syncbeats.core.domain.di.APP_SCOPE
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.domain.repository.RoomRepository
import io.github.meko123456.syncbeats.core.domain.repository.YouTubeAccountGateway
import io.github.meko123456.syncbeats.core.domain.sync.SyncEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides PlayerController, MusicSource and (on Android) GoogleAuthController. */
expect val platformModule: Module

/**
 * Everything platform-agnostic in the data layer: the HTTP client, the three port
 * implementations bound to their domain interfaces, and the sync engine composed from them.
 *
 * The ViewModels are deliberately NOT here. They belong to the feature modules that own their
 * screens — registering them in the data layer is what would make the data module depend on the
 * UI, which is the edge this whole refactor exists to remove.
 */
val dataModule = module {
    // Outlives screens; used for writes that must survive navigation teardown.
    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    single { HttpClient() }
    single<RoomRepository> { FirebaseRepository() }
    single<AuthGateway> { AuthRepository() }
    single<YouTubeAccountGateway> { YouTubeAccountRepository(get(), get()) }
    single { SyncEngine(get(), get(), get(), get()) }
}

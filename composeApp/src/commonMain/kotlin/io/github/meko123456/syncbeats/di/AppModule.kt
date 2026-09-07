package io.github.meko123456.syncbeats.di

import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.core.domain.sync.SyncEngine
import io.github.meko123456.syncbeats.core.domain.repository.AuthGateway
import io.github.meko123456.syncbeats.core.domain.repository.RoomRepository
import io.github.meko123456.syncbeats.core.domain.repository.YouTubeAccountGateway
import io.github.meko123456.syncbeats.data.AuthRepository
import io.github.meko123456.syncbeats.data.FirebaseRepository
import io.github.meko123456.syncbeats.data.YouTubeAccountRepository
import io.github.meko123456.syncbeats.ui.auth.AuthViewModel
import io.github.meko123456.syncbeats.ui.home.HomeViewModel
import io.github.meko123456.syncbeats.ui.lobby.LobbyViewModel
import io.github.meko123456.syncbeats.ui.room.RoomViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Provides PlayerController, MusicSource and (on Android) GoogleAuthController. */
expect val platformModule: Module

val APP_SCOPE = named("appScope")

val commonModule = module {
    // Outlives screens; used for writes that must survive navigation teardown.
    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    single { HttpClient() }
    single<RoomRepository> { FirebaseRepository() }
    single<AuthGateway> { AuthRepository() }
    single<YouTubeAccountGateway> { YouTubeAccountRepository(get(), get()) }
    single { SyncEngine(get(), get(), get(), get()) }

    viewModel { AuthViewModel(get(), get()) }
    viewModel { LobbyViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { (roomId: String, autoplay: Boolean) ->
        RoomViewModel(roomId, autoplay, get(), get(), get(), get(), get(), get(APP_SCOPE))
    }
}

fun initKoin(extra: (KoinApplication.() -> Unit)? = null) {
    startKoin {
        extra?.invoke(this)
        modules(commonModule, platformModule)
    }
}

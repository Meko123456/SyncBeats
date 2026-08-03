package com.example.myapplicationmusicsharing.di

import com.example.myapplicationmusicsharing.data.AuthRepository
import com.example.myapplicationmusicsharing.data.FirebaseRepository
import com.example.myapplicationmusicsharing.data.YouTubeAccountRepository
import com.example.myapplicationmusicsharing.sync.SyncEngine
import com.example.myapplicationmusicsharing.ui.auth.AuthViewModel
import com.example.myapplicationmusicsharing.ui.home.HomeViewModel
import com.example.myapplicationmusicsharing.ui.lobby.LobbyViewModel
import com.example.myapplicationmusicsharing.ui.room.RoomViewModel
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
    single { FirebaseRepository() }
    single { AuthRepository() }
    single { YouTubeAccountRepository(get(), get()) }
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

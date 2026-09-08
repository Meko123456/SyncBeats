package io.github.meko123456.syncbeats.di

import io.github.meko123456.syncbeats.core.data.di.dataModule
import io.github.meko123456.syncbeats.core.data.di.platformModule
import io.github.meko123456.syncbeats.feature.auth.authModule
import io.github.meko123456.syncbeats.feature.home.homeModule
import io.github.meko123456.syncbeats.feature.lobby.lobbyModule
import io.github.meko123456.syncbeats.feature.room.roomModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

/**
 * Starts Koin with the whole module graph.
 *
 * The app is the only place that knows the full list, and it no longer declares a single binding
 * of its own: the data layer binds the ports, each feature registers its own ViewModel, and the
 * platform module supplies the player, the music source and Google sign-in. Nothing below the UI
 * has to know a screen exists.
 */
fun initKoin(extra: (KoinApplication.() -> Unit)? = null) {
    startKoin {
        extra?.invoke(this)
        modules(dataModule, platformModule, authModule, lobbyModule, homeModule, roomModule)
    }
}

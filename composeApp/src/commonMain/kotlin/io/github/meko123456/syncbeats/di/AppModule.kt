package io.github.meko123456.syncbeats.di

import io.github.meko123456.syncbeats.core.domain.di.APP_SCOPE
import io.github.meko123456.syncbeats.core.data.di.dataModule
import io.github.meko123456.syncbeats.feature.auth.authModule
import io.github.meko123456.syncbeats.feature.lobby.lobbyModule
import io.github.meko123456.syncbeats.core.data.di.platformModule
import io.github.meko123456.syncbeats.ui.home.HomeViewModel
import io.github.meko123456.syncbeats.ui.room.RoomViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The app's ViewModels, resolved from the ports the data module binds.
 *
 * The app is the only place that knows the whole module list — the data layer no longer registers
 * screens, so nothing below the UI depends on it.
 */
val appModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { (roomId: String, autoplay: Boolean) ->
        RoomViewModel(roomId, autoplay, get(), get(), get(), get(), get(), get(APP_SCOPE))
    }
}

fun initKoin(extra: (KoinApplication.() -> Unit)? = null) {
    startKoin {
        extra?.invoke(this)
        modules(dataModule, platformModule, authModule, lobbyModule, appModule)
    }
}

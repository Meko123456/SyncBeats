package io.github.meko123456.syncbeats.feature.lobby

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin bindings for the lobby feature. */
val lobbyModule = module {
    viewModel { LobbyViewModel(get(), get()) }
}

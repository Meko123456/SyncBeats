package io.github.meko123456.syncbeats.feature.room

import io.github.meko123456.syncbeats.core.domain.di.APP_SCOPE
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin bindings for the room feature.
 *
 * roomId and autoplay are assisted parameters supplied by the navigation call, and APP_SCOPE is
 * the application-lifetime scope so a write like "leave room" completes even after the screen is
 * gone. The qualifier lives in :core:domain precisely so this module can name it without
 * depending on :core:data.
 */
val roomModule = module {
    viewModel { (roomId: String, autoplay: Boolean) ->
        RoomViewModel(roomId, autoplay, get(), get(), get(), get(), get(), get(APP_SCOPE))
    }
}

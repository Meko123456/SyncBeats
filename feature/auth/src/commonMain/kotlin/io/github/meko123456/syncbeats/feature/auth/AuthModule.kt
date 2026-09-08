package io.github.meko123456.syncbeats.feature.auth

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin bindings for the auth feature. */
val authModule = module {
    viewModel { AuthViewModel(get(), get()) }
}

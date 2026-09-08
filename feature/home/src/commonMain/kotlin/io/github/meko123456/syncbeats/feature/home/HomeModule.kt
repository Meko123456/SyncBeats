package io.github.meko123456.syncbeats.feature.home

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin bindings for the home feature. */
val homeModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
}

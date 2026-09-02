package io.github.meko123456.syncbeats.di

import io.github.meko123456.syncbeats.data.InnerTubeMusicSource
import io.github.meko123456.syncbeats.data.MusicSource
import io.github.meko123456.syncbeats.playback.IosPlayerController
import io.github.meko123456.syncbeats.playback.PlayerController
import org.koin.core.module.Module
import org.koin.dsl.module

// GoogleAuthController is implemented in Swift and injected via initialize().
actual val platformModule: Module = module {
    single<PlayerController> { IosPlayerController() }
    single<MusicSource> { InnerTubeMusicSource(get()) }
}

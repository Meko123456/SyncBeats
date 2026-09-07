package io.github.meko123456.syncbeats.core.data.di

import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.core.data.InnerTubeMusicSource
import io.github.meko123456.syncbeats.core.data.IosPlayerController
import org.koin.core.module.Module
import org.koin.dsl.module

// GoogleAuthController is implemented in Swift and injected via initialize().
actual val platformModule: Module = module {
    single<PlayerController> { IosPlayerController() }
    single<MusicSource> { InnerTubeMusicSource(get()) }
}

package io.github.meko123456.syncbeats.di

import io.github.meko123456.syncbeats.core.domain.music.MusicSource
import io.github.meko123456.syncbeats.core.domain.playback.PlayerController
import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.data.AndroidGoogleAuthController
import io.github.meko123456.syncbeats.data.NewPipeMusicSource
import io.github.meko123456.syncbeats.playback.AndroidPlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<PlayerController> { AndroidPlayerController(androidContext()) }
    single<MusicSource> { NewPipeMusicSource() }
    single { AndroidGoogleAuthController(androidContext()) } bind GoogleAuthController::class
}

package io.github.meko123456.syncbeats.di

import io.github.meko123456.syncbeats.data.AndroidGoogleAuthController
import io.github.meko123456.syncbeats.data.GoogleAuthController
import io.github.meko123456.syncbeats.data.MusicSource
import io.github.meko123456.syncbeats.data.NewPipeMusicSource
import io.github.meko123456.syncbeats.playback.AndroidPlayerController
import io.github.meko123456.syncbeats.playback.PlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<PlayerController> { AndroidPlayerController(androidContext()) }
    single<MusicSource> { NewPipeMusicSource() }
    single { AndroidGoogleAuthController(androidContext()) } bind GoogleAuthController::class
}

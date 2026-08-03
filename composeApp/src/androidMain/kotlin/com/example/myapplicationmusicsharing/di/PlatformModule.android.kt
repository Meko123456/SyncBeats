package com.example.myapplicationmusicsharing.di

import com.example.myapplicationmusicsharing.data.AndroidGoogleAuthController
import com.example.myapplicationmusicsharing.data.GoogleAuthController
import com.example.myapplicationmusicsharing.data.MusicSource
import com.example.myapplicationmusicsharing.data.NewPipeMusicSource
import com.example.myapplicationmusicsharing.playback.AndroidPlayerController
import com.example.myapplicationmusicsharing.playback.PlayerController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<PlayerController> { AndroidPlayerController(androidContext()) }
    single<MusicSource> { NewPipeMusicSource() }
    single { AndroidGoogleAuthController(androidContext()) } bind GoogleAuthController::class
}

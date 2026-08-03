package com.example.myapplicationmusicsharing.di

import com.example.myapplicationmusicsharing.data.InnerTubeMusicSource
import com.example.myapplicationmusicsharing.data.MusicSource
import com.example.myapplicationmusicsharing.playback.IosPlayerController
import com.example.myapplicationmusicsharing.playback.PlayerController
import org.koin.core.module.Module
import org.koin.dsl.module

// GoogleAuthController is implemented in Swift and injected via initialize().
actual val platformModule: Module = module {
    single<PlayerController> { IosPlayerController() }
    single<MusicSource> { InnerTubeMusicSource(get()) }
}

package com.example.myapplicationmusicsharing

import android.app.Application
import com.example.myapplicationmusicsharing.data.NewPipeDownloaderImpl
import com.example.myapplicationmusicsharing.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.schabi.newpipe.extractor.NewPipe

class SyncBeatsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloaderImpl.instance())
        initKoin {
            androidContext(this@SyncBeatsApp)
        }
    }
}

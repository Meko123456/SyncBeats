package io.github.meko123456.syncbeats

import android.app.Application
import io.github.meko123456.syncbeats.data.NewPipeDownloaderImpl
import io.github.meko123456.syncbeats.di.initKoin
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

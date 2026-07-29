package com.uzopb.ragg

import android.app.Application
import com.uzopb.ragg.di.initRaggUiKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class RaggApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initRaggUiKoin {
            androidLogger(Level.ERROR)
            androidContext(this@RaggApplication)
        }
    }
}

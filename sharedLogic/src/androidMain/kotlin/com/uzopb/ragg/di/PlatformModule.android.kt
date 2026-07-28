package com.uzopb.ragg.di

import com.uzopb.ragg.device.AndroidHardwareProbe
import com.uzopb.ragg.device.HardwareProbe
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttp }
    single<HardwareProbe> { AndroidHardwareProbe(androidContext()) }
}

package com.uzopb.ragg.di

import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.IosHardwareProbe
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Darwin }
    single<HardwareProbe> { IosHardwareProbe() }
}

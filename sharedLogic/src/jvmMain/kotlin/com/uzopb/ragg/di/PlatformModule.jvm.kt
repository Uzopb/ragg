package com.uzopb.ragg.di

import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.JvmHardwareProbe
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { CIO }
    single<HardwareProbe> { JvmHardwareProbe() }
}

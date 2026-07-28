package com.uzopb.ragg.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.uzopb.ragg.cache.CachePaths
import com.uzopb.ragg.cache.IosCachePaths
import com.uzopb.ragg.cache.IosPlatformFileSystem
import com.uzopb.ragg.cache.PlatformFileSystem
import com.uzopb.ragg.db.RaggDatabase
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.IosHardwareProbe
import com.uzopb.ragg.models.AlwaysOnline
import com.uzopb.ragg.models.NetworkStatus
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Darwin }
    single<HardwareProbe> { IosHardwareProbe() }
    single<PlatformFileSystem> { IosPlatformFileSystem() }
    single<CachePaths> { IosCachePaths(fs = get()) }
    single<NetworkStatus> { AlwaysOnline }
    single<SqlDriver> {
        NativeSqliteDriver(RaggDatabase.Schema, "ragg.db")
    }
}

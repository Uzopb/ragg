package com.uzopb.ragg.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uzopb.ragg.cache.CachePaths
import com.uzopb.ragg.cache.JvmCachePaths
import com.uzopb.ragg.cache.JvmPlatformFileSystem
import com.uzopb.ragg.cache.PlatformFileSystem
import com.uzopb.ragg.db.RaggDatabase
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.JvmHardwareProbe
import com.uzopb.ragg.models.AlwaysOnline
import com.uzopb.ragg.models.NetworkStatus
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import java.io.File
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { CIO }
    single<HardwareProbe> { JvmHardwareProbe() }
    single<PlatformFileSystem> { JvmPlatformFileSystem() }
    single<CachePaths> { JvmCachePaths(fs = get()) }
    single<NetworkStatus> { AlwaysOnline }
    single<SqlDriver> {
        val cache = get<CachePaths>()
        val fs = get<PlatformFileSystem>()
        val dbDir = fs.join(cache.modelsDir, "..")
        fs.mkdirp(dbDir)
        val dbPath = fs.join(dbDir, "ragg.db")
        val fresh = !File(dbPath).exists()
        JdbcSqliteDriver("jdbc:sqlite:$dbPath").also { driver ->
            if (fresh) {
                RaggDatabase.Schema.create(driver)
            }
        }
    }
}

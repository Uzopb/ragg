package com.uzopb.ragg.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.uzopb.ragg.cache.AndroidCachePaths
import com.uzopb.ragg.cache.AndroidPlatformFileSystem
import com.uzopb.ragg.cache.CachePaths
import com.uzopb.ragg.cache.PlatformFileSystem
import com.uzopb.ragg.db.RaggDatabase
import com.uzopb.ragg.device.AndroidHardwareProbe
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.models.NetworkStatus
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttp }
    single<HardwareProbe> { AndroidHardwareProbe(androidContext()) }
    single<PlatformFileSystem> { AndroidPlatformFileSystem() }
    single<CachePaths> { AndroidCachePaths(androidContext(), fs = get()) }
    single<NetworkStatus> { AndroidNetworkStatus(androidContext()) }
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = RaggDatabase.Schema,
            context = androidContext(),
            name = "ragg.db",
        )
    }
}

/**
 * Online по активной сети с INTERNET capability.
 */
class AndroidNetworkStatus(
    private val context: Context,
) : NetworkStatus {
    override fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

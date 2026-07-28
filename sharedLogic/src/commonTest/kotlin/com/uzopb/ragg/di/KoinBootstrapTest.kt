package com.uzopb.ragg.di

import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.PlatformKind
import com.uzopb.ragg.models.EtalonBenchmarkService
import com.uzopb.ragg.models.ModelCatalog
import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

/**
 * Koin резолвит HttpClient, HardwareProbe и models (каталог / бенч эталона).
 */
class KoinBootstrapTest : KoinTest {

    @BeforeTest
    fun setUp() {
        startKoin { modules(raggModules()) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun resolvesHttpClient() {
        val client = get<HttpClient>()
        assertNotNull(client)
    }

    @Test
    fun resolvesHardwareProbe() {
        val probe = get<HardwareProbe>()
        val profile = probe.probe()
        assertTrue(profile.platform == PlatformKind.Desktop, "jvmTest → Desktop")
        assertTrue(profile.ram.totalMb > 0, "RAM должна быть заполнена")
        assertTrue(profile.cpu.cores >= 1, "CPU cores >= 1")
    }

    @Test
    fun resolvesModelCatalogAndEtalonBenchmark() {
        val catalog = get<ModelCatalog>()
        assertTrue(catalog.all().isNotEmpty())
        assertNotNull(get<EtalonBenchmarkService>())
    }
}

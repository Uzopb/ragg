package com.uzopb.ragg.di

import com.uzopb.ragg.device.HardwareProbe
import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

/**
 * Smoke: Koin резолвит HttpClient и заглушку HardwareProbe.
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
        assertNotNull(get<HttpClient>())
    }

    @Test
    fun resolvesHardwareProbe() {
        assertNotNull(get<HardwareProbe>().probe())
    }
}

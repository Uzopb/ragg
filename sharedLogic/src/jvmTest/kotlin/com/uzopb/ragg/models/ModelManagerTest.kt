package com.uzopb.ragg.models

import com.uzopb.ragg.ai.MockLlmEngine
import com.uzopb.ragg.cache.CachePaths
import com.uzopb.ragg.cache.JvmPlatformFileSystem
import com.uzopb.ragg.cache.PlatformFileSystem
import com.uzopb.ragg.cache.sha256Hex
import com.uzopb.ragg.device.CpuInfo
import com.uzopb.ragg.device.GpuInfo
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.HardwareProfile
import com.uzopb.ragg.device.PlatformKind
import com.uzopb.ragg.device.RamInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Этап 3: ModelManager — offline/online etalon, Mock-бенч, weaker/stronger, delete.
 */
class ModelManagerTest {

    private lateinit var tmpRoot: File
    private lateinit var fs: PlatformFileSystem
    private lateinit var cachePaths: CachePaths
    private lateinit var payload: ByteArray
    private lateinit var catalog: ModelCatalog

    private val profile = HardwareProfile(
        platform = PlatformKind.Android,
        ram = RamInfo(totalMb = 6_144, availableMb = 3_200),
        cpu = CpuInfo(cores = 8, maxFreqMhz = 2050, name = "mt6785"),
        gpu = GpuInfo(name = "Mali-G76 MC4", api = "GLES"),
        socOrChipset = "Helio G95",
    )

    private val probe = HardwareProbe { profile }

    @BeforeTest
    fun setUp() {
        tmpRoot = Files.createTempDirectory("ragg-mm-").toFile()
        fs = JvmPlatformFileSystem()
        cachePaths = object : CachePaths {
            override val modelsDir: String = File(tmpRoot, "models").absolutePath
            override val documentsDir: String = File(tmpRoot, "documents").absolutePath
        }
        fs.mkdirp(cachePaths.modelsDir)
        fs.mkdirp(cachePaths.documentsDir)

        payload = "ragg-mock-gguf-v1".encodeToByteArray()
        val sha = sha256Hex(payload)
        val etalon = ModelArtifact(
            id = "etalon-mock",
            displayName = "Etalon Mock",
            role = ModelRole.Llm,
            sizeBytes = payload.size.toLong(),
            minRamMb = 512,
            paramBillions = 0.5f,
            quantBits = 4,
            quantName = "Q4_K_M",
            contextLength = 2048,
            approxLayers = 24,
            downloadUrl = "https://example.test/etalon.gguf",
            sha256 = sha,
            languages = listOf("en"),
            isEtalon = true,
        )
        val weaker = etalon.copy(
            id = "weaker-mock",
            displayName = "Weaker Mock",
            paramBillions = 0.3f,
            approxLayers = 20,
            downloadUrl = "https://example.test/weaker.gguf",
            isEtalon = false,
        )
        val stronger = etalon.copy(
            id = "stronger-mock",
            displayName = "Stronger Mock",
            paramBillions = 1.5f,
            approxLayers = 28,
            sizeBytes = payload.size.toLong(),
            downloadUrl = "https://example.test/stronger.gguf",
            isEtalon = false,
        )
        val embed = etalon.copy(
            id = "embed-mock",
            displayName = "Embed Mock",
            role = ModelRole.Embedding,
            paramBillions = 0.1f,
            embeddingDim = 384,
            downloadUrl = "https://example.test/embed.gguf",
            isEtalon = false,
        )
        catalog = ModelCatalog(listOf(etalon, weaker, stronger, embed))
    }

    @AfterTest
    fun tearDown() {
        tmpRoot.deleteRecursively()
    }

    @Test
    fun runEtalonBenchmark_offlineWithoutFile_needsNetwork() {
        runBlocking {
            val manager = manager(AlwaysOffline, respondBytes = false)
            val err = assertFailsWith<EtalonBenchmarkException> {
                manager.runEtalonBenchmark()
            }
            assertTrue(err.message!!.contains("нужна сеть"))
        }
    }

    @Test
    fun runEtalonBenchmark_online_downloadsMockBench_writesAnchor_andGroups() {
        runBlocking {
            val manager = manager(AlwaysOnline, respondBytes = true)
            val t = manager.runEtalonBenchmark()
            assertEquals(5.0f, t)

            val cards = manager.currentCards()
            val etalonCard = cards.first { it.model.isEtalon }
            assertEquals(LocalModelStatus.Downloaded, etalonCard.localStatus)
            assertNotNull(etalonCard.estimatedTokPerSec)

            val groups = manager.recommendations()
            assertTrue(groups.recommended.any { it.model.isEtalon })
            assertTrue(
                cards.any { it.relativeClass == RelativeSpeedClass.Weaker },
                "ожидали weaker в карточках",
            )
            assertTrue(
                cards.any { it.relativeClass == RelativeSpeedClass.Stronger },
                "ожидали stronger в карточках",
            )
        }
    }

    @Test
    fun deleteEtalon_clearsInstalled_andAllowsRestart() {
        runBlocking {
            val manager = manager(AlwaysOnline, respondBytes = true)
            manager.runEtalonBenchmark()
            manager.delete("etalon-mock")
            assertEquals(
                LocalModelStatus.NotDownloaded,
                manager.currentCards().first { it.model.isEtalon }.localStatus,
            )
            val manager2 = ModelManager(
                catalog = catalog,
                hardwareProbe = probe,
                downloader = ModelDownloader(
                    httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }),
                    cachePaths = cachePaths,
                    fs = fs,
                ),
                installed = InMemoryInstalledModelStore(),
                calibrationStore = InMemoryCalibrationStore(),
                networkStatus = AlwaysOffline,
                llmEngine = MockLlmEngine(),
                nowMs = { 1L },
            )
            assertFailsWith<EtalonBenchmarkException> { manager2.runEtalonBenchmark() }
        }
    }

    @Test
    fun sha256_empty_matchesKnownVector() {
        // NIST: SHA256("") =
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    private fun manager(network: NetworkStatus, respondBytes: Boolean): ModelManager {
        val engine = MockEngine { request ->
            if (!respondBytes) {
                respond("offline", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
                )
            }
        }
        return ModelManager(
            catalog = catalog,
            hardwareProbe = probe,
            downloader = ModelDownloader(
                httpClient = HttpClient(engine),
                cachePaths = cachePaths,
                fs = fs,
            ),
            installed = InMemoryInstalledModelStore(),
            calibrationStore = InMemoryCalibrationStore(),
            networkStatus = network,
            llmEngine = MockLlmEngine(),
            mockTokPerSec = 5.0f,
            nowMs = { 42L },
        )
    }
}

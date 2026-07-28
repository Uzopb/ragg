package com.uzopb.ragg.models

import com.uzopb.ragg.device.CpuInfo
import com.uzopb.ragg.device.GpuInfo
import com.uzopb.ragg.device.HardwareProfile
import com.uzopb.ragg.device.PlatformKind
import com.uzopb.ragg.device.RamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Stage 2: catalog, cost(), RAM fit, weaker/stronger scale, fingerprint, mock etalon bench.
 */
class PerfEstimatorTest {

    private val midPhone = HardwareProfile(
        platform = PlatformKind.Android,
        ram = RamInfo(totalMb = 6_144, availableMb = 3_200),
        cpu = CpuInfo(cores = 8, maxFreqMhz = 2050, name = "mt6785"),
        gpu = GpuInfo(name = "Mali-G76 MC4", api = "GLES"),
        socOrChipset = "Helio G95",
    )

    @Test
    fun catalog_hasSingleEtalon_andGgufOnly() {
        val catalog = ModelCatalog.DEFAULT
        assertEquals(1, catalog.all().count { it.isEtalon })
        assertTrue(catalog.all().all { it.format == "gguf" })
        assertTrue(catalog.all().all { it.sha256.length == 64 })
        assertNotNull(catalog.embeddings().singleOrNull())
    }

    @Test
    fun cost_1_5b_q4_greaterThan_0_5b_q4() {
        val etalon = ModelCatalog.DEFAULT.etalon()
        val stronger = ModelCatalog.DEFAULT.byId("qwen2.5-1.5b-instruct-q4_k_m")!!
        assertTrue(
            ModelCost.cost(stronger, etalon) > ModelCost.cost(etalon, etalon),
            "cost(1.5B Q4) > cost(0.5B Q4)",
        )
    }

    @Test
    fun midPhone_0_5b_fits_3b_tightOrInsufficient() {
        val cards = PerfEstimator.estimate(midPhone)
        val etalonCard = cards.first { it.model.isEtalon }
        val threeB = cards.first { it.model.id == "qwen2.5-3b-instruct-q4_k_m" }

        assertEquals(FitLevel.Fits, etalonCard.fit, "mid 6GB → 0.5B Fits")
        assertTrue(
            threeB.fit == FitLevel.Tight || threeB.fit == FitLevel.Insufficient,
            "mid 6GB → 3B Tight/Insufficient, факт=${threeB.fit}",
        )
        assertNull(etalonCard.estimatedTokPerSec, "без якоря tok/s нет")
        assertEquals(Confidence.Low, etalonCard.confidence)
        assertTrue(etalonCard.reason.contains("без якоря"))
    }

    @Test
    fun afterBench_T5_weakerStronger_andEstTokScale() {
        val etalon = ModelCatalog.DEFAULT.etalon()
        val fingerprint = PerfEstimator.deviceFingerprint(midPhone)
        val calibration = Calibration(
            modelId = etalon.id,
            backend = com.uzopb.ragg.device.InferBackend.Cpu,
            tokPerSec = 5.0f,
            deviceFingerprint = fingerprint,
            measuredAtEpochMs = 1L,
        )

        val cards = PerfEstimator.estimate(midPhone, calibration = calibration)
        val weaker = cards.first { it.model.id == "smollm2-360m-instruct-q4_k_m" }
        val stronger = cards.first { it.model.id == "qwen2.5-1.5b-instruct-q4_k_m" }
        val etalonCard = cards.first { it.model.isEtalon }

        assertEquals(RelativeSpeedClass.Weaker, weaker.relativeClass)
        assertEquals(RelativeSpeedClass.Stronger, stronger.relativeClass)
        assertEquals(RelativeSpeedClass.Etalon, etalonCard.relativeClass)

        val weakerTok = midOf(weaker.estimatedTokPerSec!!)
        val strongerTok = midOf(stronger.estimatedTokPerSec!!)
        assertTrue(weakerTok > 5.0f, "estTok слабее > T, факт=$weakerTok")
        assertTrue(strongerTok < 5.0f, "estTok сильнее < T, факт=$strongerTok")
        assertEquals(Confidence.High, etalonCard.confidence)
        assertEquals(Confidence.Medium, stronger.confidence)
    }

    @Test
    fun foreignFingerprint_ignored() {
        val etalon = ModelCatalog.DEFAULT.etalon()
        val foreign = Calibration(
            modelId = etalon.id,
            backend = com.uzopb.ragg.device.InferBackend.Cpu,
            tokPerSec = 5.0f,
            deviceFingerprint = "other-device",
            measuredAtEpochMs = 1L,
        )
        val cards = PerfEstimator.estimate(midPhone, calibration = foreign)
        assertTrue(cards.filter { it.model.role == ModelRole.Llm }.all { it.estimatedTokPerSec == null })
        assertTrue(cards.filter { it.model.role == ModelRole.Llm }.all { it.confidence == Confidence.Low })
    }

    @Test
    fun runEtalonBenchmark_offlineWithoutFile_needsNetwork() {
        runBlocking {
            val store = InMemoryCalibrationStore()
            val local = InMemoryLocalModelRegistry()
            val service = EtalonBenchmarkService(
                catalog = ModelCatalog.DEFAULT,
                localModels = local,
                calibrationStore = store,
                mockTokPerSec = 5.0f,
                nowMs = { 42L },
            )

            val err = assertFailsWith<EtalonBenchmarkException> {
                service.runEtalonBenchmark(midPhone, networkAvailable = false)
            }
            assertTrue(err.message!!.contains("нужна сеть"))
            assertNull(store.get(PerfEstimator.deviceFingerprint(midPhone)))
        }
    }

    @Test
    fun runEtalonBenchmark_withNetwork_writesAnchor() {
        runBlocking {
            val store = InMemoryCalibrationStore()
            val local = InMemoryLocalModelRegistry()
            val service = EtalonBenchmarkService(
                catalog = ModelCatalog.DEFAULT,
                localModels = local,
                calibrationStore = store,
                mockTokPerSec = 5.0f,
                nowMs = { 99L },
            )

            val t = service.runEtalonBenchmark(midPhone, networkAvailable = true)
            assertEquals(5.0f, t)
            val anchor = assertNotNull(store.get(PerfEstimator.deviceFingerprint(midPhone)))
            assertEquals(ModelCatalog.DEFAULT.etalon().id, anchor.modelId)
            assertEquals(PerfEstimator.deviceFingerprint(midPhone), anchor.deviceFingerprint)
            assertTrue(local.isPresent(anchor.modelId))

            val cards = PerfEstimator.estimate(
                midPhone,
                calibration = anchor,
                localStatuses = local.statuses(),
            )
            assertNotNull(cards.first { it.model.isEtalon }.estimatedTokPerSec)
        }
    }

    private fun midOf(range: ClosedFloatingPointRange<Float>): Float =
        (range.start + range.endInclusive) / 2f
}

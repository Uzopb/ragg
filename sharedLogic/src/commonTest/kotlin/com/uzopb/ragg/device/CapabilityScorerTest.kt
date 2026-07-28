package com.uzopb.ragg.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 1 acceptance: synthetic mid-phone → Mid; desktop 16GB+8c → DesktopHigh/High.
 */
class CapabilityScorerTest {

    @Test
    fun midPhone_6gb_helioG95_isMid() {
        val profile = HardwareProfile(
            platform = PlatformKind.Android,
            ram = RamInfo(totalMb = 6_144, availableMb = 3_200),
            cpu = CpuInfo(
                cores = 8,
                maxFreqMhz = 2050,
                name = "mt6785",
                abi = "arm64-v8a",
            ),
            gpu = GpuInfo(name = "Mali-G76 MC4", api = "GLES"),
            socOrChipset = "Helio G95",
        )

        val score = CapabilityScorer.score(profile)

        assertEquals(DeviceTier.Mid, score.tier, "mid-phone 6GB должен быть Mid")
        assertEquals(InferBackend.Cpu, score.preferredBackend, "phone → CPU advisory")
        assertEquals(0f, score.gpuScore, "mid Mali непригоден для LLM → gpuScore=0")
        assertTrue(score.cpuScore in 0.25f..0.65f, "cpuScore mid-phone в разумном диапазоне")
    }

    @Test
    fun desktop_16gb_8cores_isDesktopHighOrHigh() {
        val profile = HardwareProfile(
            platform = PlatformKind.Desktop,
            ram = RamInfo(totalMb = 16_384, availableMb = 10_000),
            cpu = CpuInfo(
                cores = 8,
                maxFreqMhz = 3600,
                name = "AMD Ryzen 7 5700X",
                abi = "amd64",
            ),
            gpu = GpuInfo(name = "NVIDIA GeForce RTX 3060", api = "lspci", vramMbHint = 12_288),
            socOrChipset = null,
        )

        val score = CapabilityScorer.score(profile)

        assertTrue(
            score.tier == DeviceTier.DesktopHigh || score.tier == DeviceTier.High,
            "desktop 16GB+8c → DesktopHigh или High, факт=${score.tier}",
        )
        assertTrue(score.cpuScore >= 0.55f, "сильный desktop CPU")
        assertTrue(score.gpuScore > 0f, "дискретный GPU должен давать gpuScore>0")
        assertEquals(InferBackend.Gpu, score.preferredBackend, "DesktopHigh + useful GPU → Gpu advisory")
    }

    @Test
    fun lowPhone_3gb_isLow() {
        val profile = HardwareProfile(
            platform = PlatformKind.Android,
            ram = RamInfo(totalMb = 3_072, availableMb = 1_200),
            cpu = CpuInfo(cores = 4, maxFreqMhz = 1800, name = "unknown"),
            gpu = GpuInfo(name = null, api = null),
            socOrChipset = null,
        )

        val score = CapabilityScorer.score(profile)
        assertEquals(DeviceTier.Low, score.tier)
        assertEquals(InferBackend.Cpu, score.preferredBackend)
    }
}

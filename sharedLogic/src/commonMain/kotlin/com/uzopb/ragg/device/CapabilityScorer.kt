package com.uzopb.ragg.device

import kotlin.math.min

/**
 * Скоринг железа для tier / fit / advisory backend **до** калибровки.
 *
 * Нормализация по внутренним константам (cores/freq/RAM) — не заявка на абсолютный tok/s.
 *
 * Формула:
 * `cpuScore = 0.45*norm(cores) + 0.35*norm(freq) + 0.20*socBoost`
 * `gpuScore = 0`, если GPU непригоден для LLM, иначе lookup/эвристика (шкала 0…1).
 */
object CapabilityScorer {

    /** Опорные константы нормализации (не tok/s). */
    private const val REF_CORES = 8f
    private const val REF_FREQ_MHZ = 3000f
    private const val REF_RAM_MB = 8192f

    private const val DEFAULT_FREQ_MHZ = 2000f
    private const val DEFAULT_SOC_BOOST = 0.45f

    /**
     * Строит [CapabilityScore] по уже снятому [HardwareProfile].
     */
    fun score(profile: HardwareProfile): CapabilityScore {
        val soc = KnownSocTable.lookup(profile.socOrChipset, profile.cpu.name)
        val gpuHint = KnownGpuTable.lookup(profile.gpu.name)

        val coresNorm = norm(profile.cpu.cores.toFloat(), REF_CORES)
        val freqMhz = profile.cpu.maxFreqMhz?.toFloat() ?: DEFAULT_FREQ_MHZ
        val freqNorm = norm(freqMhz, REF_FREQ_MHZ)
        val socBoost = soc?.socBoost ?: DEFAULT_SOC_BOOST

        val cpuFromFormula = 0.45f * coresNorm + 0.35f * freqNorm + 0.20f * socBoost
        // Если SoC известен — слегка подтягиваем к таблице (шкала 0…100 → 0…1)
        val cpuScore = if (soc != null) {
            0.7f * cpuFromFormula + 0.3f * (soc.cpuHint / 100f)
        } else {
            cpuFromFormula
        }.coerceIn(0f, 1f)

        val gpuScore = resolveGpuScore(profile, soc, gpuHint)
        val ramScore = norm(profile.ram.totalMb.toFloat(), REF_RAM_MB).coerceIn(0f, 1.5f).let {
            min(1f, it)
        }

        val tier = assignTier(profile, cpuScore, ramScore)
        val preferredBackend = preferredBackend(profile, tier, gpuScore, gpuHint)

        return CapabilityScore(
            cpuScore = cpuScore,
            gpuScore = gpuScore,
            ramScore = ramScore,
            tier = tier,
            preferredBackend = preferredBackend,
        )
    }

    private fun resolveGpuScore(
        profile: HardwareProfile,
        soc: KnownSocTable.SocHint?,
        gpuHint: KnownGpuTable.GpuHint?,
    ): Float {
        if (gpuHint != null) {
            return if (gpuHint.llmUseful) (gpuHint.scoreHint / 100f).coerceIn(0f, 1f) else 0f
        }
        if (soc != null && soc.gpuHint <= 20f) {
            // Mid phone SoC без явного GPU-имени → LLM на GPU бесполезен
            return 0f
        }
        val name = profile.gpu.name.orEmpty()
        if (name.isBlank() || name.equals("unknown", ignoreCase = true)) {
            return if (profile.platform == PlatformKind.Desktop) 0.2f else 0f
        }
        // Неизвестный desktop GPU — слабая эвристика; phone — 0
        return if (profile.platform == PlatformKind.Desktop) 0.35f else 0f
    }

    private fun assignTier(
        profile: HardwareProfile,
        cpuScore: Float,
        ramScore: Float,
    ): DeviceTier {
        val ramMb = profile.ram.totalMb
        if (profile.platform == PlatformKind.Desktop) {
            return when {
                ramMb >= 16_384 && cpuScore >= 0.55f -> DeviceTier.DesktopHigh
                ramMb >= 8_192 && cpuScore >= 0.45f -> DeviceTier.High
                else -> DeviceTier.Mid
            }
        }
        // Phone / iOS
        return when {
            ramMb < 4_096 || cpuScore < 0.32f -> DeviceTier.Low
            ramMb < 8_192 || cpuScore < 0.58f -> DeviceTier.Mid
            else -> DeviceTier.High
        }
    }

    /**
     * v1: на phone и mid Mali всегда CPU; Gpu только advisory для DesktopHigh + сильный GPU.
     */
    private fun preferredBackend(
        profile: HardwareProfile,
        tier: DeviceTier,
        gpuScore: Float,
        gpuHint: KnownGpuTable.GpuHint?,
    ): InferBackend {
        if (profile.platform != PlatformKind.Desktop) return InferBackend.Cpu
        if (tier != DeviceTier.DesktopHigh) return InferBackend.Cpu
        if (gpuHint?.llmUseful == true && gpuScore >= 0.5f) return InferBackend.Gpu
        return InferBackend.Cpu
    }

    private fun norm(value: Float, ref: Float): Float =
        (value / ref).coerceIn(0f, 1f)
}

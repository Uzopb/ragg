package com.uzopb.ragg.device

/**
 * Платформа устройства для [HardwareProfile].
 */
enum class PlatformKind {
    Android,
    Desktop,
    Ios,
}

/**
 * Снимок железа: единый контракт для Android / Desktop / iOS (этап 1).
 *
 * Не является эталоном tok/s — для ранжирования до калибровки используется
 * [CapabilityScore], а якорь производительности — бенч эталона (этапы 2–3/6).
 */
data class HardwareProfile(
    val platform: PlatformKind,
    val ram: RamInfo,
    val cpu: CpuInfo,
    val gpu: GpuInfo,
    val socOrChipset: String?,
)

/**
 * Оперативная память в мегабайтах.
 */
data class RamInfo(
    val totalMb: Long,
    val availableMb: Long,
)

/**
 * Характеристики CPU; поля с `?` — best-effort по платформе.
 */
data class CpuInfo(
    val cores: Int,
    val performanceCores: Int? = null,
    val maxFreqMhz: Int? = null,
    val name: String? = null,
    val abi: String? = null,
)

/**
 * GPU / графический стек; [vramMbHint] часто неизвестен на phone.
 */
data class GpuInfo(
    val name: String?,
    val api: String?,
    val vramMbHint: Long? = null,
)

/**
 * Advisory-бэкенд инференса (v1 runtime всегда CPU; GPU — намёк для fit/UI и этапа 7).
 */
enum class InferBackend {
    Cpu,
    Gpu,
}

/**
 * Класс устройства для fit / рекомендаций до калибровки.
 */
enum class DeviceTier {
    Low,
    Mid,
    High,
    DesktopHigh,
}

/**
 * Относительные баллы tier/fit; **не** абсолютный tok/s.
 *
 * @property preferredBackend advisory; в v1 фактический llama.cpp — CPU-first.
 */
data class CapabilityScore(
    val cpuScore: Float,
    val gpuScore: Float,
    val ramScore: Float,
    val tier: DeviceTier,
    val preferredBackend: InferBackend,
)

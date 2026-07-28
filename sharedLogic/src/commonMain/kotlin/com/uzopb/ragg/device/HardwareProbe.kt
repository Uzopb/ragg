package com.uzopb.ragg.device

/**
 * Заглушка профилирования железа (этап 0). Полный контракт — этап 1.
 */
fun interface HardwareProbe {
    fun probe(): HardwareProfile
}

/**
 * Минимальный снимок устройства до этапа 1.
 */
data class HardwareProfile(
    val platformLabel: String,
)

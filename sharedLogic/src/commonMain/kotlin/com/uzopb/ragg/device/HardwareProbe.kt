package com.uzopb.ragg.device

/**
 * Снимает [HardwareProfile] текущего устройства (expect/actual по платформе).
 */
fun interface HardwareProbe {
    fun probe(): HardwareProfile
}

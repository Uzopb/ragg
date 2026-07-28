package com.uzopb.ragg.device

import platform.Foundation.NSProcessInfo

/**
 * iOS stub-проба: physicalMemory + processorCount, API hint = Metal.
 */
class IosHardwareProbe : HardwareProbe {

    override fun probe(): HardwareProfile {
        val info = NSProcessInfo.processInfo
        val totalMb = (info.physicalMemory / BYTES_PER_MB.toULong()).toLong()
        val cores = info.processorCount.toInt().coerceAtLeast(1)
        return HardwareProfile(
            platform = PlatformKind.Ios,
            ram = RamInfo(
                totalMb = totalMb,
                availableMb = totalMb, // точный avail на stub не снимаем
            ),
            cpu = CpuInfo(
                cores = cores,
                performanceCores = null,
                maxFreqMhz = null,
                name = info.operatingSystemVersionString,
                abi = "arm64",
            ),
            gpu = GpuInfo(
                name = "Apple GPU",
                api = "Metal",
                vramMbHint = null,
            ),
            socOrChipset = null,
        )
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

package com.uzopb.ragg.device

class JvmHardwareProbe : HardwareProbe {
    override fun probe(): HardwareProfile = HardwareProfile(platformLabel = "Desktop")
}

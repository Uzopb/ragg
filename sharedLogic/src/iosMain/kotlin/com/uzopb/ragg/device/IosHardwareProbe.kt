package com.uzopb.ragg.device

class IosHardwareProbe : HardwareProbe {
    override fun probe(): HardwareProfile = HardwareProfile(platformLabel = "iOS")
}

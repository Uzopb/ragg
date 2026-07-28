package com.uzopb.ragg.device

import android.content.Context

class AndroidHardwareProbe(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : HardwareProbe {
    override fun probe(): HardwareProfile = HardwareProfile(platformLabel = "Android")
}

package com.uzopb.ragg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.uzopb.ragg.ai.llama.isLlamaNativeLinked
import com.uzopb.ragg.device.HardwareProbe
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    MaterialTheme {
        val hardwareProbe = koinInject<HardwareProbe>()
        val profile = remember(hardwareProbe) { hardwareProbe.probe() }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RAGG", style = MaterialTheme.typography.displayMedium)
            Text("probe=${profile.platformLabel}")
            Text("llama.cpp linked=${isLlamaNativeLinked()}")
        }
    }
}

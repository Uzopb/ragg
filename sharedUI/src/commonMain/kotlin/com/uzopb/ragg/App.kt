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
import com.uzopb.ragg.device.CapabilityScorer
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.models.ModelCatalog
import com.uzopb.ragg.models.PerfEstimator
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    MaterialTheme {
        val hardwareProbe = koinInject<HardwareProbe>()
        val profile = remember(hardwareProbe) { hardwareProbe.probe() }
        val score = remember(profile) { CapabilityScorer.score(profile) }
        val cards = remember(profile) { PerfEstimator.estimate(profile, ModelCatalog.DEFAULT) }
        val etalonFit = cards.firstOrNull { it.model.isEtalon }?.fit

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RAGG", style = MaterialTheme.typography.displayMedium)
            Text("${profile.platform} · tier=${score.tier} · backend=${score.preferredBackend}")
            Text(
                "RAM ${profile.ram.totalMb}MB · CPU ${profile.cpu.cores}c" +
                    (profile.cpu.maxFreqMhz?.let { " · ${it}MHz" } ?: "") +
                    (profile.gpu.name?.let { " · GPU $it" } ?: ""),
            )
            Text(
                "scores cpu=${fmt(score.cpuScore)} gpu=${fmt(score.gpuScore)} ram=${fmt(score.ramScore)}",
            )
            Text("catalog=${cards.size} · etalon fit=$etalonFit · без якоря")
            Text("llama.cpp linked=${isLlamaNativeLinked()}")
        }
    }
}

private fun fmt(value: Float): String {
    val scaled = (value * 100f).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

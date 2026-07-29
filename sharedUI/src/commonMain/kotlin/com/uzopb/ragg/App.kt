package com.uzopb.ragg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.Navigator
import com.uzopb.ragg.ui.components.PearlAtmosphere
import com.uzopb.ragg.ui.home.HomeScreen
import com.uzopb.ragg.ui.theme.RaggTheme

@Composable
@Preview
fun App() {
    RaggTheme {
        Box(Modifier.fillMaxSize()) {
            PearlAtmosphere()
            Box(Modifier.fillMaxSize().safeContentPadding()) {
                Navigator(HomeScreen)
            }
        }
    }
}

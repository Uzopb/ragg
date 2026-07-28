package com.uzopb.ragg

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.uzopb.ragg.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "RAGG",
        ) {
            App()
        }
    }
}

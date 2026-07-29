package com.uzopb.ragg.di

import org.koin.dsl.KoinAppDeclaration

/** Старт Koin с ScreenModels UI (этап 4). */
fun initRaggUiKoin(appDeclaration: KoinAppDeclaration = {}) {
    initKoin(extraModules = listOf(uiModule), appDeclaration = appDeclaration)
}

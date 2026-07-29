package com.uzopb.ragg.di

import com.uzopb.ragg.ui.home.HomeScreenModel
import com.uzopb.ragg.ui.models.ModelManagerScreenModel
import com.uzopb.ragg.ui.resources.ResourceManagerScreenModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** Voyager ScreenModels (этап 4). */
val uiModule: Module = module {
    factory { HomeScreenModel(get(), get()) }
    factory { ModelManagerScreenModel(get(), get()) }
    factory { ResourceManagerScreenModel(get()) }
}

package com.uzopb.ragg.di

import com.uzopb.ragg.ai.EmbeddingEngine
import com.uzopb.ragg.ai.LlmEngine
import com.uzopb.ragg.ai.MockEmbeddingEngine
import com.uzopb.ragg.ai.MockLlmEngine
import com.uzopb.ragg.db.DatabaseGate
import com.uzopb.ragg.db.StubDatabaseGate
import com.uzopb.ragg.models.CalibrationStore
import com.uzopb.ragg.models.EtalonBenchmarkService
import com.uzopb.ragg.models.InMemoryCalibrationStore
import com.uzopb.ragg.models.InMemoryLocalModelRegistry
import com.uzopb.ragg.models.LocalModelRegistry
import com.uzopb.ragg.models.ModelCatalog
import com.uzopb.ragg.models.ModelsDomain
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(raggModules())
    }
}

fun raggModules(): List<Module> = listOf(
    platformModule,
    networkModule,
    databaseModule,
    aiModule,
    modelsModule,
)

expect val platformModule: Module

val networkModule: Module = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    single {
        HttpClient(get<HttpClientEngineFactory<*>>()) {
            install(ContentNegotiation) {
                json(get())
            }
        }
    }
}

val databaseModule: Module = module {
    single<DatabaseGate> { StubDatabaseGate }
}

val aiModule: Module = module {
    single<EmbeddingEngine> { MockEmbeddingEngine() }
    single<LlmEngine> { MockLlmEngine() }
}

val modelsModule: Module = module {
    single { ModelsDomain }
    single { ModelCatalog.DEFAULT }
    single<CalibrationStore> { InMemoryCalibrationStore() }
    single<LocalModelRegistry> { InMemoryLocalModelRegistry() }
    single {
        EtalonBenchmarkService(
            catalog = get(),
            localModels = get(),
            calibrationStore = get(),
        )
    }
}

package com.uzopb.ragg.di

import com.uzopb.ragg.ai.EmbeddingEngine
import com.uzopb.ragg.ai.LlmEngine
import com.uzopb.ragg.ai.MockEmbeddingEngine
import com.uzopb.ragg.ai.MockLlmEngine
import com.uzopb.ragg.ai.SessionGate
import com.uzopb.ragg.chat.ChatRepository
import com.uzopb.ragg.db.DatabaseGate
import com.uzopb.ragg.db.RaggDatabase
import com.uzopb.ragg.db.SqlCalibrationStore
import com.uzopb.ragg.db.SqlDelightDatabaseGate
import com.uzopb.ragg.db.SqlInstalledModelStore
import com.uzopb.ragg.docs.MockResourceManager
import com.uzopb.ragg.epochNowMs
import com.uzopb.ragg.models.CacheStorageStatsProvider
import com.uzopb.ragg.models.CalibrationStore
import com.uzopb.ragg.models.EtalonBenchmarkService
import com.uzopb.ragg.models.InMemoryLocalModelRegistry
import com.uzopb.ragg.models.InstalledModelStore
import com.uzopb.ragg.models.LocalModelRegistry
import com.uzopb.ragg.models.ModelCatalog
import com.uzopb.ragg.models.ModelDownloader
import com.uzopb.ragg.models.ModelManager
import com.uzopb.ragg.models.ModelsDomain
import com.uzopb.ragg.models.StorageStatsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
) {
    startKoin {
        appDeclaration()
        modules(raggModules() + extraModules)
    }
}

fun raggModules(): List<Module> = listOf(
    platformModule,
    networkModule,
    databaseModule,
    aiModule,
    modelsModule,
    chatModule,
    resourcesModule,
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
    single { RaggDatabase(get()) }
    single<DatabaseGate> { SqlDelightDatabaseGate(get()) }
    single<CalibrationStore> { SqlCalibrationStore(get()) }
    single<InstalledModelStore> { SqlInstalledModelStore(get()) }
}

val aiModule: Module = module {
    single<EmbeddingEngine> { MockEmbeddingEngine() }
    single<LlmEngine> { MockLlmEngine() }
    single { SessionGate() }
}

val modelsModule: Module = module {
    single { ModelsDomain }
    single { ModelCatalog.DEFAULT }
    single {
        ModelDownloader(
            httpClient = get(),
            cachePaths = get(),
            fs = get(),
        )
    }
    single<StorageStatsProvider> {
        CacheStorageStatsProvider(
            cachePaths = get(),
            fs = get(),
            installed = get(),
        )
    }
    single {
        ModelManager(
            catalog = get(),
            hardwareProbe = get(),
            downloader = get(),
            installed = get(),
            calibrationStore = get(),
            networkStatus = get(),
            llmEngine = get(),
            nowMs = { epochNowMs() },
        )
    }
    // совместимость с тестами/API этапа 2
    single<LocalModelRegistry> { InMemoryLocalModelRegistry() }
    single {
        EtalonBenchmarkService(
            catalog = get(),
            localModels = get(),
            calibrationStore = get(),
            nowMs = { epochNowMs() },
        )
    }
}

val chatModule: Module = module {
    single {
        ChatRepository(
            sessionGate = get(),
            llmEngine = get(),
            installed = get(),
            nowMs = { epochNowMs() },
        )
    }
}

val resourcesModule: Module = module {
    single {
        MockResourceManager(
            sessionGate = get(),
            storageStatsProvider = get(),
            chatRepository = get(),
        )
    }
}

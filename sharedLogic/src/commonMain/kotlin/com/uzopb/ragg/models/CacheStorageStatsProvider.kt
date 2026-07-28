package com.uzopb.ragg.models

import com.uzopb.ragg.cache.CachePaths
import com.uzopb.ragg.cache.PlatformFileSystem

/**
 * Лёгкий подсчёт файлов кэша (этап 3; per-Corpus — позже).
 */
class CacheStorageStatsProvider(
    private val cachePaths: CachePaths,
    private val fs: PlatformFileSystem,
    private val installed: InstalledModelStore,
    private val databaseBytesProvider: () -> Long = { 0L },
) : StorageStatsProvider {
    override fun stats(): StorageStats {
        val sources = fs.directorySize(cachePaths.documentsDir)
        val modelsFromFs = fs.directorySize(cachePaths.modelsDir)
        val modelsFromDb = installed.totalBytes()
        // предпочитаем факт на диске; если пусто — сумма из registry
        val models = if (modelsFromFs > 0L) modelsFromFs else modelsFromDb
        val database = databaseBytesProvider()
        return StorageStats(
            sourcesBytes = sources,
            databaseBytes = database,
            modelsBytes = models,
            totalBytes = sources + database + models,
        )
    }
}

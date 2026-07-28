package com.uzopb.ragg.models

/**
 * Статистика диска для экрана Ресурсы (per-Corpus — этап 5a).
 */
data class StorageStats(
    val sourcesBytes: Long,
    val databaseBytes: Long,
    val modelsBytes: Long,
    val totalBytes: Long,
    val perCorpus: List<CorpusStorageRow> = emptyList(),
)

/**
 * Строка per-Corpus (заглушка до схемы Corpus).
 */
data class CorpusStorageRow(
    val corpusId: String,
    val displayName: String,
    val vectorsBytes: Long,
)

/**
 * Подсчёт [StorageStats]: модели из кэша/БД; исходники — documentsDir; БД — 0 до этапа 5a.
 */
interface StorageStatsProvider {
    fun stats(): StorageStats
}

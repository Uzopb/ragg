package com.uzopb.ragg.models

import com.uzopb.ragg.device.InferBackend

/**
 * Роль артефакта в RAG: генерация или эмбеддинг (отдельные GGUF-контексты).
 */
enum class ModelRole {
    Llm,
    Embedding,
}

/**
 * Насколько модель влезает в текущий available RAM (mmap + headroom, не «файл = RSS»).
 */
enum class FitLevel {
    Fits,
    Tight,
    Insufficient,
}

/**
 * Уверенность оценки tok/s: High — якорь эталона; Medium — scale через [cost]; Low — без калибровки.
 */
enum class Confidence {
    High,
    Medium,
    Low,
}

/**
 * Локальный статус файла модели (до SQLDelight этапа 3 — in-memory registry).
 */
enum class LocalModelStatus {
    NotDownloaded,
    Downloaded,
    Active,
}

/**
 * Класс относительно эталона после бенча (для групп рекомендаций).
 */
enum class RelativeSpeedClass {
    Weaker,
    Etalon,
    EtalonTier,
    Stronger,
}

/**
 * Комфорт интерактивной генерации по [estTok] (после якоря).
 */
enum class ComfortLevel {
    Comfortable,
    Slow,
    Impractical,
}

/**
 * Метаданные GGUF из зашитого каталога. Веса **только** download; поля `bundledInApp` нет.
 *
 * @property sha256 обязателен для v1 (проверка на этапе 3 при скачивании).
 * @property isEtalon ровно одна LLM в каталоге — якорь калибровки.
 */
data class ModelArtifact(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val format: String = "gguf",
    val sizeBytes: Long,
    val minRamMb: Int,
    val paramBillions: Float,
    val quantBits: Int,
    val quantName: String,
    val contextLength: Int,
    val approxLayers: Int,
    val embeddingDim: Int? = null,
    val downloadUrl: String,
    val sha256: String,
    val languages: List<String>,
    val isEtalon: Boolean = false,
)

/**
 * Карточка fit / оценки для UI Моделей (без Compose).
 *
 * [preferredBackend] — advisory (v1 runtime = CPU).
 * [estimatedTokPerSec] / [estimatedEmbedMs] — `null`, пока нет совместимого якоря.
 */
data class ModelFitCard(
    val model: ModelArtifact,
    val fit: FitLevel,
    val preferredBackend: InferBackend,
    val estimatedTokPerSec: ClosedFloatingPointRange<Float>?,
    val estimatedEmbedMs: ClosedFloatingPointRange<Float>?,
    val reason: String,
    val confidence: Confidence,
    val localStatus: LocalModelStatus,
    val relativeClass: RelativeSpeedClass? = null,
    val comfort: ComfortLevel? = null,
)

/**
 * Группы экрана Модели после калибровки.
 */
data class RecommendationGroups(
    val recommended: List<ModelFitCard>,
    val canGoStronger: List<ModelFitCard>,
    val notWorth: List<ModelFitCard>,
)

/**
 * Якорь производительности: бенч эталона на **этом** устройстве.
 * Чужой [deviceFingerprint] в [PerfEstimator] игнорируется.
 */
data class Calibration(
    val modelId: String,
    val backend: InferBackend,
    val tokPerSec: Float,
    val deviceFingerprint: String,
    val measuredAtEpochMs: Long,
)

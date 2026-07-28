package com.uzopb.ragg.models

/**
 * Прогресс скачивания GGUF.
 */
data class DownloadProgress(
    val modelId: String,
    val bytesReceived: Long,
    val contentLength: Long?,
)

/**
 * Ошибка download / проверки целостности.
 */
class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

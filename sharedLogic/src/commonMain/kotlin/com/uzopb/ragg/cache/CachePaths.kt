package com.uzopb.ragg.cache

/**
 * Канонические каталоги кэша приложения (модели GGUF и документы).
 *
 * Android: `filesDir/models`, `filesDir/documents`.
 * JVM: `~/.cache/ragg/...`.
 * iOS: Application Support.
 */
interface CachePaths {
    /** Каталог скачанных GGUF. */
    val modelsDir: String

    /** Каталог исходников документов (drop-in позже). */
    val documentsDir: String
}

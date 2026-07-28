package com.uzopb.ragg.cache

/**
 * Минимальный FS-контракт для download (tmp→rename) и StorageStats.
 * Платформенные actual — тонкие обёртки над java.nio / File / NSFileManager.
 */
interface PlatformFileSystem {
    /** Собирает путь из сегментов. */
    fun join(parent: String, vararg parts: String): String

    /** Создаёт каталог и родителей при необходимости. */
    fun mkdirp(path: String)

    fun exists(path: String): Boolean

    fun length(path: String): Long

    /** Удаляет файл или пустой каталог; отсутствие — no-op. */
    fun deleteIfExists(path: String)

    /**
     * Атомарный rename/move (на той же ФС).
     * Pre: [from] существует; родитель [to] существует.
     */
    fun atomicMove(from: String, to: String)

    /** Перезаписывает файл целиком. */
    fun writeBytes(path: String, bytes: ByteArray)

    /**
     * Читает файл чанками (для sha256 без загрузки целиком в RAM).
     * @param consume возвращает false — прервать чтение.
     */
    fun readChunks(path: String, chunkSize: Int = DEFAULT_CHUNK, consume: (ByteArray, Int) -> Boolean)

    /**
     * Пишет поток байт в [path]; [write] получает sink.
     * Вызывающий обязан закрыть sink (через [FileWriteSink.close]).
     */
    fun openWrite(path: String): FileWriteSink

    /** Сумма размеров файлов в дереве (рекурсивно); нет пути → 0. */
    fun directorySize(path: String): Long

    companion object {
        const val DEFAULT_CHUNK: Int = 64 * 1024
    }
}

/**
 * Последовательная запись в файл (download во `.tmp`).
 */
interface FileWriteSink {
    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size)
    fun flush()
    fun close()
}

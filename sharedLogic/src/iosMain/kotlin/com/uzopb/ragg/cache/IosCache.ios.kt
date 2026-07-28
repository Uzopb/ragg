package com.uzopb.ragg.cache

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

/**
 * iOS-реализация [PlatformFileSystem] через NSFileManager.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformFileSystem : PlatformFileSystem {
    private val fm = NSFileManager.defaultManager

    override fun join(parent: String, vararg parts: String): String {
        var path = parent.trimEnd('/')
        for (p in parts) {
            path = "$path/${p.trim('/')}"
        }
        return path
    }

    override fun mkdirp(path: String) {
        fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override fun exists(path: String): Boolean = fm.fileExistsAtPath(path)

    override fun length(path: String): Long {
        val attrs = fm.attributesOfItemAtPath(path, error = null) ?: return 0L
        val size = attrs["NSFileSize"] as? Long ?: (attrs["NSFileSize"] as? Number)?.toLong()
        return size ?: 0L
    }

    override fun deleteIfExists(path: String) {
        if (exists(path)) {
            fm.removeItemAtPath(path, error = null)
        }
    }

    override fun atomicMove(from: String, to: String) {
        val parent = to.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) mkdirp(parent)
        if (exists(to)) deleteIfExists(to)
        fm.moveItemAtPath(from, to, error = null)
    }

    override fun writeBytes(path: String, bytes: ByteArray) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) mkdirp(parent)
        val sink = openWrite(path)
        try {
            sink.write(bytes)
            sink.flush()
        } finally {
            sink.close()
        }
    }

    override fun readChunks(path: String, chunkSize: Int, consume: (ByteArray, Int) -> Boolean) {
        val stream = NSInputStream(uRL = NSURL.fileURLWithPath(path))
        stream.open()
        try {
            val buf = ByteArray(chunkSize)
            while (true) {
                val n = buf.usePinned { pinned ->
                    stream.read(pinned.addressOf(0), chunkSize.toULong()).toInt()
                }
                if (n <= 0) break
                if (!consume(buf, n)) break
            }
        } finally {
            stream.close()
        }
    }

    override fun openWrite(path: String): FileWriteSink {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) mkdirp(parent)
        val stream = NSOutputStream(toFileAtPath = path, append = false)
            ?: error("не удалось открыть запись: $path")
        stream.open()
        return object : FileWriteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                bytes.usePinned { pinned ->
                    stream.write(pinned.addressOf(offset), length.toULong())
                }
            }

            override fun flush() {
                // NSOutputStream не буферизует отдельно
            }

            override fun close() {
                stream.close()
            }
        }
    }

    override fun directorySize(path: String): Long {
        if (!exists(path)) return 0L
        val enumerator = fm.enumeratorAtPath(path) ?: return length(path)
        var total = 0L
        while (true) {
            val relative = enumerator.nextObject() as? String ?: break
            val full = join(path, relative)
            total += length(full)
        }
        if (total == 0L) return length(path)
        return total
    }
}

/**
 * Кэш iOS: Application Support / ragg / models|documents.
 */
class IosCachePaths(
    fs: PlatformFileSystem = IosPlatformFileSystem(),
) : CachePaths {
    private val root: String = run {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        )
        val base = (paths.firstOrNull() as? String) ?: "."
        fs.join(base, "ragg")
    }

    override val modelsDir: String = fs.join(root, "models")
    override val documentsDir: String = fs.join(root, "documents")

    init {
        fs.mkdirp(modelsDir)
        fs.mkdirp(documentsDir)
    }
}

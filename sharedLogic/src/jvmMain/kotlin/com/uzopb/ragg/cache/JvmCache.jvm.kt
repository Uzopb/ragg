package com.uzopb.ragg.cache

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JVM-реализация [PlatformFileSystem] на java.io / NIO.
 */
class JvmPlatformFileSystem : PlatformFileSystem {
    override fun join(parent: String, vararg parts: String): String {
        var f = File(parent)
        for (p in parts) f = File(f, p)
        return f.path
    }

    override fun mkdirp(path: String) {
        File(path).mkdirs()
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun length(path: String): Long {
        val f = File(path)
        return if (f.isFile) f.length() else 0L
    }

    override fun deleteIfExists(path: String) {
        File(path).delete()
    }

    override fun atomicMove(from: String, to: String) {
        val target = File(to)
        target.parentFile?.mkdirs()
        Files.move(
            File(from).toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    override fun writeBytes(path: String, bytes: ByteArray) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    override fun readChunks(path: String, chunkSize: Int, consume: (ByteArray, Int) -> Boolean) {
        File(path).inputStream().use { input ->
            val buf = ByteArray(chunkSize)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                if (!consume(buf, n)) break
            }
        }
    }

    override fun openWrite(path: String): FileWriteSink {
        val f = File(path)
        f.parentFile?.mkdirs()
        val out = FileOutputStream(f)
        return object : FileWriteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                out.write(bytes, offset, length)
            }

            override fun flush() {
                out.flush()
            }

            override fun close() {
                out.close()
            }
        }
    }

    override fun directorySize(path: String): Long {
        val root = File(path)
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/**
 * Кэш JVM: `~/.cache/ragg/models` и `documents`.
 */
class JvmCachePaths(
    homeDir: String = System.getProperty("user.home") ?: ".",
    fs: PlatformFileSystem = JvmPlatformFileSystem(),
) : CachePaths {
    override val modelsDir: String = fs.join(homeDir, ".cache", "ragg", "models")
    override val documentsDir: String = fs.join(homeDir, ".cache", "ragg", "documents")

    init {
        fs.mkdirp(modelsDir)
        fs.mkdirp(documentsDir)
    }
}

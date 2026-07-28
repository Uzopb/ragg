package com.uzopb.ragg.models

import com.uzopb.ragg.cache.CachePaths
import com.uzopb.ragg.cache.PlatformFileSystem
import com.uzopb.ragg.cache.Sha256Hasher
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Скачивание GGUF в [CachePaths.modelsDir]: проверка локального файла → иначе `.tmp` → rename.
 *
 * Pre: [ModelArtifact.sha256] и [ModelArtifact.sizeBytes] заданы.
 * Post: файл на месте с совпадающими size и sha256.
 */
class ModelDownloader(
    private val httpClient: HttpClient,
    private val cachePaths: CachePaths,
    private val fs: PlatformFileSystem,
) {
    private val jobsMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    /** Итоговый путь файла модели в кэше. */
    fun targetPath(artifact: ModelArtifact): String =
        fs.join(cachePaths.modelsDir, "${artifact.id}.gguf")

    /**
     * Локальный файл валиден по размеру и sha256.
     */
    fun verifyLocal(artifact: ModelArtifact): Boolean {
        val path = targetPath(artifact)
        if (!fs.exists(path)) return false
        if (fs.length(path) != artifact.sizeBytes) return false
        return fileSha256(path) == artifact.sha256.lowercase()
    }

    /**
     * Скачивает модель; при валидном локальном файле сразу Completes с полным прогрессом.
     * Отмена — [cancel].
     */
    fun download(artifact: ModelArtifact): Flow<DownloadProgress> = flow {
        val path = targetPath(artifact)
        fs.mkdirp(cachePaths.modelsDir)

        if (verifyLocal(artifact)) {
            emit(DownloadProgress(artifact.id, artifact.sizeBytes, artifact.sizeBytes))
            return@flow
        }

        val job = currentCoroutineContext()[Job]
        if (job != null) {
            jobsMutex.withLock { jobs[artifact.id] = job }
        }

        val tmp = "$path.tmp"
        fs.deleteIfExists(tmp)
        fs.deleteIfExists(path)

        try {
            httpClient.prepareGet(artifact.downloadUrl).execute { response ->
                if (!response.status.isSuccess()) {
                    throw ModelDownloadException(
                        "HTTP ${response.status.value} при скачивании ${artifact.id}",
                    )
                }
                val contentLength = response.contentLength()
                val channel = response.bodyAsChannel()
                val hasher = Sha256Hasher()
                val sink = fs.openWrite(tmp)
                var received = 0L
                val buf = ByteArray(PlatformFileSystem.DEFAULT_CHUNK)
                try {
                    while (!channel.isClosedForRead) {
                        currentCoroutineContext().ensureActive()
                        val n = channel.readAvailable(buf, 0, buf.size)
                        if (n < 0) break
                        if (n == 0) continue
                        sink.write(buf, 0, n)
                        hasher.update(buf, 0, n)
                        received += n
                        emit(DownloadProgress(artifact.id, received, contentLength))
                    }
                    sink.flush()
                } finally {
                    sink.close()
                }

                val hex = hasher.digestHex()
                if (received != artifact.sizeBytes) {
                    fs.deleteIfExists(tmp)
                    throw ModelDownloadException(
                        "размер ${artifact.id}: ожидали ${artifact.sizeBytes}, получили $received",
                    )
                }
                if (hex != artifact.sha256.lowercase()) {
                    fs.deleteIfExists(tmp)
                    throw ModelDownloadException(
                        "sha256 ${artifact.id}: ожидали ${artifact.sha256}, получили $hex",
                    )
                }
                fs.atomicMove(tmp, path)
                emit(DownloadProgress(artifact.id, received, artifact.sizeBytes))
            }
        } catch (e: CancellationException) {
            fs.deleteIfExists(tmp)
            throw e
        } catch (e: ModelDownloadException) {
            fs.deleteIfExists(tmp)
            throw e
        } catch (e: Exception) {
            fs.deleteIfExists(tmp)
            throw ModelDownloadException("ошибка скачивания ${artifact.id}: ${e.message}", e)
        } finally {
            jobsMutex.withLock { jobs.remove(artifact.id) }
        }
    }

    /** Отменяет активную загрузку [modelId], если есть. */
    suspend fun cancel(modelId: String) {
        val job = jobsMutex.withLock { jobs.remove(modelId) }
        job?.cancel()
    }

    /** Удаляет файл модели из кэша (без записи в БД). */
    fun deleteFile(artifact: ModelArtifact) {
        fs.deleteIfExists(targetPath(artifact))
        fs.deleteIfExists(targetPath(artifact) + ".tmp")
    }

    private fun fileSha256(path: String): String {
        val hasher = Sha256Hasher()
        fs.readChunks(path) { buf, n ->
            hasher.update(buf, 0, n)
            true
        }
        return hasher.digestHex()
    }
}

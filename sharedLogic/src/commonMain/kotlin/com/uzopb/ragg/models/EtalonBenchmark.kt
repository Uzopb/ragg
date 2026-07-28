package com.uzopb.ragg.models

import com.uzopb.ragg.device.HardwareProfile
import com.uzopb.ragg.device.InferBackend

/**
 * Хранилище якоря калибровки. На этапе 2 — in-memory; SQLDelight — этап 3/5a.
 */
interface CalibrationStore {
    fun get(): Calibration?
    fun save(calibration: Calibration)
    fun clear()
}

/**
 * In-memory реализация [CalibrationStore] для этапа 2 и тестов.
 */
class InMemoryCalibrationStore : CalibrationStore {
    private var value: Calibration? = null

    override fun get(): Calibration? = value

    override fun save(calibration: Calibration) {
        value = calibration
    }

    override fun clear() {
        value = null
    }
}

/**
 * Учёт скачанных моделей без CachePaths/Ktor (этап 3).
 */
interface LocalModelRegistry {
    fun status(modelId: String): LocalModelStatus
    fun isPresent(modelId: String): Boolean
    fun markDownloaded(modelId: String)
    fun markActive(modelId: String)
    fun remove(modelId: String)
    fun statuses(): Map<String, LocalModelStatus>
}

/**
 * In-memory registry установленных моделей (mock download для этапа 2).
 */
class InMemoryLocalModelRegistry : LocalModelRegistry {
    private val map = mutableMapOf<String, LocalModelStatus>()

    override fun status(modelId: String): LocalModelStatus =
        map[modelId] ?: LocalModelStatus.NotDownloaded

    override fun isPresent(modelId: String): Boolean {
        val s = status(modelId)
        return s == LocalModelStatus.Downloaded || s == LocalModelStatus.Active
    }

    override fun markDownloaded(modelId: String) {
        map[modelId] = LocalModelStatus.Downloaded
    }

    override fun markActive(modelId: String) {
        // Одна активная LLM/роль уточнится в ModelManager этапа 3; здесь — простой флаг.
        map[modelId] = LocalModelStatus.Active
    }

    override fun remove(modelId: String) {
        map.remove(modelId)
    }

    override fun statuses(): Map<String, LocalModelStatus> = map.toMap()
}

/**
 * Ошибка калибровки эталона (offline без файла и т.п.).
 */
class EtalonBenchmarkException(message: String) : Exception(message)

/**
 * Бенч эталона для якоря [Calibration].
 *
 * До этапа 6 — **синтетический** tok/s (Mock). Реальный download — этап 3 (Ktor);
 * здесь при наличии сети и отсутствии файла — mock «скачали».
 *
 * Pre: etalon в каталоге; файл на месте **или** сеть доступна.
 * Post: якорь записан в [CalibrationStore] с fingerprint профиля.
 */
class EtalonBenchmarkService(
    private val catalog: ModelCatalog,
    private val localModels: LocalModelRegistry,
    private val calibrationStore: CalibrationStore,
    private val mockTokPerSec: Float = DEFAULT_MOCK_TOK_PER_SEC,
    private val nowMs: () -> Long = { 0L },
) {
    /**
     * Прогон эталона → [Calibration.tokPerSec].
     *
     * @throws EtalonBenchmarkException если файла нет и [networkAvailable]=false («нужна сеть»).
     */
    suspend fun runEtalonBenchmark(
        profile: HardwareProfile,
        networkAvailable: Boolean,
    ): Float {
        val etalon = catalog.etalon()
        if (!localModels.isPresent(etalon.id)) {
            if (!networkAvailable) {
                throw EtalonBenchmarkException("нужна сеть")
            }
            // Этап 2/3 граница: имитация download; реальный Ktor — ModelDownloader этапа 3.
            localModels.markDownloaded(etalon.id)
        }
        val tok = mockTokPerSec
        calibrationStore.save(
            Calibration(
                modelId = etalon.id,
                backend = InferBackend.Cpu,
                tokPerSec = tok,
                deviceFingerprint = PerfEstimator.deviceFingerprint(profile),
                measuredAtEpochMs = nowMs(),
            ),
        )
        return tok
    }

    companion object {
        /** Синтетический якорь до реального GGUF-бенча (этап 6). */
        const val DEFAULT_MOCK_TOK_PER_SEC: Float = 5.0f
    }
}

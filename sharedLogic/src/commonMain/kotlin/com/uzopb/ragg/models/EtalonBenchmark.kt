package com.uzopb.ragg.models

/**
 * Хранилище якоря калибровки. Ключ выборки — [deviceFingerprint].
 */
interface CalibrationStore {
    fun get(deviceFingerprint: String): Calibration?
    fun save(calibration: Calibration)
    fun clear()
}

/**
 * In-memory реализация [CalibrationStore] для тестов и этапа 2.
 */
class InMemoryCalibrationStore : CalibrationStore {
    private val byFingerprint = mutableMapOf<String, Calibration>()

    override fun get(deviceFingerprint: String): Calibration? = byFingerprint[deviceFingerprint]

    override fun save(calibration: Calibration) {
        byFingerprint[calibration.deviceFingerprint] = calibration
    }

    override fun clear() {
        byFingerprint.clear()
    }
}

/**
 * Учёт установленных моделей (файл + роль + active).
 */
interface InstalledModelStore {
    fun status(modelId: String): LocalModelStatus
    fun isPresent(modelId: String): Boolean =
        status(modelId) != LocalModelStatus.NotDownloaded

    fun upsert(
        id: String,
        path: String,
        bytes: Long,
        sha256: String,
        role: ModelRole,
        active: Boolean = false,
    )

    fun setActive(id: String, role: ModelRole)
    fun remove(id: String)
    fun statuses(): Map<String, LocalModelStatus>
    fun totalBytes(): Long
}

/**
 * In-memory registry установленных моделей.
 */
class InMemoryInstalledModelStore : InstalledModelStore {
    private data class Row(
        val path: String,
        val bytes: Long,
        val sha256: String,
        val role: ModelRole,
        val active: Boolean,
    )

    private val map = mutableMapOf<String, Row>()

    override fun status(modelId: String): LocalModelStatus {
        val row = map[modelId] ?: return LocalModelStatus.NotDownloaded
        return if (row.active) LocalModelStatus.Active else LocalModelStatus.Downloaded
    }

    override fun upsert(
        id: String,
        path: String,
        bytes: Long,
        sha256: String,
        role: ModelRole,
        active: Boolean,
    ) {
        map[id] = Row(path, bytes, sha256, role, active)
    }

    override fun setActive(id: String, role: ModelRole) {
        val row = map[id] ?: error("модель $id не установлена")
        map.replaceAll { key, value ->
            if (value.role != role) {
                value
            } else {
                value.copy(active = key == id)
            }
        }
        // на случай рассинхрона role в записи
        map[id] = (map[id] ?: row).copy(role = role, active = true)
    }

    override fun remove(id: String) {
        map.remove(id)
    }

    override fun statuses(): Map<String, LocalModelStatus> =
        map.mapValues { (_, v) ->
            if (v.active) LocalModelStatus.Active else LocalModelStatus.Downloaded
        }

    override fun totalBytes(): Long = map.values.sumOf { it.bytes }
}

/**
 * Учёт установленных моделей (совместимость с EtalonBenchmarkService этапа 2).
 * Новый код — [InstalledModelStore].
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
 * In-memory [LocalModelRegistry] (совместимость с тестами этапа 2).
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
 * Бенч эталона для якоря [Calibration] (этап 2 API; этап 3 — [ModelManager]).
 *
 * До этапа 6 — **синтетический** tok/s (Mock).
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
        profile: com.uzopb.ragg.device.HardwareProfile,
        networkAvailable: Boolean,
    ): Float {
        val etalon = catalog.etalon()
        if (!localModels.isPresent(etalon.id)) {
            if (!networkAvailable) {
                throw EtalonBenchmarkException("нужна сеть")
            }
            localModels.markDownloaded(etalon.id)
        }
        val tok = mockTokPerSec
        calibrationStore.save(
            Calibration(
                modelId = etalon.id,
                backend = com.uzopb.ragg.device.InferBackend.Cpu,
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

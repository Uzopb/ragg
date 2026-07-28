package com.uzopb.ragg.models

import com.uzopb.ragg.ai.LlmEngine
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.device.InferBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach

/**
 * Фасад менеджера моделей: карточки fit, download, активация, калибровка (Mock до этапа 6).
 *
 * Старт приложения **не** зависит от этого фасада (Home открывается всегда).
 */
class ModelManager(
    private val catalog: ModelCatalog,
    private val hardwareProbe: HardwareProbe,
    private val downloader: ModelDownloader,
    private val installed: InstalledModelStore,
    private val calibrationStore: CalibrationStore,
    private val networkStatus: NetworkStatus,
    private val llmEngine: LlmEngine,
    private val mockTokPerSec: Float = EtalonBenchmarkService.DEFAULT_MOCK_TOK_PER_SEC,
    private val nowMs: () -> Long = { 0L },
) {
    private val cardsState = MutableStateFlow(buildCards())
    private val progressFlow = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 64)

    /** Карточки каталога с локальным статусом и якорем (если есть). */
    fun observeCards(): Flow<List<ModelFitCard>> = cardsState.asStateFlow()

    /** Текущий снимок карточек. */
    fun currentCards(): List<ModelFitCard> = cardsState.value

    /** Прогресс активных загрузок. */
    fun observeDownloadProgress(): SharedFlow<DownloadProgress> = progressFlow.asSharedFlow()

    /**
     * Калибровка эталона: при отсутствии файла — download (нужна сеть), затем Mock-бенч.
     *
     * @throws EtalonBenchmarkException offline без скачанного etalon («нужна сеть»).
     */
    suspend fun runEtalonBenchmark(): Float {
        val etalon = catalog.etalon()
        if (!installed.isPresent(etalon.id)) {
            if (!networkStatus.isOnline()) {
                throw EtalonBenchmarkException("нужна сеть")
            }
            download(etalon.id)
        }
        return runBenchmark(etalon.id)
    }

    /**
     * Группы рекомендаций weaker / etalon / stronger по текущим карточкам.
     */
    fun recommendations(): RecommendationGroups =
        PerfEstimator.recommendationGroups(cardsState.value)

    /**
     * Скачивание GGUF по id каталога; запись в [InstalledModelStore].
     */
    suspend fun download(modelId: String) {
        val artifact = catalog.byId(modelId)
            ?: throw ModelDownloadException("неизвестная модель: $modelId")
        if (!installed.isPresent(modelId) && !networkStatus.isOnline()) {
            // уже есть валидный файл на диске — downloader.verifyLocal пропустит сеть
            if (!downloader.verifyLocal(artifact)) {
                throw EtalonBenchmarkException("нужна сеть")
            }
        }
        downloader.download(artifact)
            .onEach { progressFlow.emit(it) }
            .collect { /* drain */ }

        val path = downloader.targetPath(artifact)
        val wasActive = installed.status(modelId) == LocalModelStatus.Active
        installed.upsert(
            id = artifact.id,
            path = path,
            bytes = artifact.sizeBytes,
            sha256 = artifact.sha256,
            role = artifact.role,
            active = wasActive,
        )
        refreshCards()
    }

    suspend fun cancel(modelId: String) {
        downloader.cancel(modelId)
    }

    /**
     * Удаляет файл и запись; эталон можно удалить → снова пустые «Установленные».
     */
    suspend fun delete(modelId: String) {
        val artifact = catalog.byId(modelId)
        if (artifact != null) {
            downloader.deleteFile(artifact)
        }
        installed.remove(modelId)
        refreshCards()
    }

    /**
     * Активирует модель; одна active на [ModelRole].
     */
    suspend fun setActive(modelId: String) {
        val artifact = catalog.byId(modelId)
            ?: error("неизвестная модель: $modelId")
        if (!installed.isPresent(modelId)) {
            error("модель $modelId не установлена")
        }
        installed.setActive(modelId, artifact.role)
        refreshCards()
    }

    /**
     * Живой прогон (до этапа 6 — Mock через [LlmEngine]); пишет якорь для LLM.
     * Повторный download не нужен, если файл на месте.
     */
    suspend fun runBenchmark(modelId: String): Float {
        val artifact = catalog.byId(modelId)
            ?: throw EtalonBenchmarkException("неизвестная модель: $modelId")
        if (!installed.isPresent(modelId) && !downloader.verifyLocal(artifact)) {
            throw EtalonBenchmarkException("модель не скачана: $modelId")
        }
        // касание Mock-движка (реальный tok/s — этап 6)
        llmEngine.complete("ragg-bench:$modelId").collect { }

        val profile = hardwareProbe.probe()
        val fingerprint = PerfEstimator.deviceFingerprint(profile)
        val tok = mockTokPerSec
        if (artifact.role == ModelRole.Llm) {
            calibrationStore.save(
                Calibration(
                    modelId = artifact.id,
                    backend = InferBackend.Cpu,
                    tokPerSec = tok,
                    deviceFingerprint = fingerprint,
                    measuredAtEpochMs = nowMs(),
                ),
            )
        }
        refreshCards()
        return tok
    }

    private fun refreshCards() {
        cardsState.value = buildCards()
    }

    private fun buildCards(): List<ModelFitCard> {
        val profile = hardwareProbe.probe()
        val fingerprint = PerfEstimator.deviceFingerprint(profile)
        return PerfEstimator.estimate(
            profile = profile,
            catalog = catalog,
            calibration = calibrationStore.get(fingerprint),
            localStatuses = installed.statuses(),
        )
    }
}

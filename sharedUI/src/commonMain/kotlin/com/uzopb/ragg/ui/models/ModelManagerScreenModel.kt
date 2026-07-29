package com.uzopb.ragg.ui.models

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.uzopb.ragg.device.HardwareProbe
import com.uzopb.ragg.models.CalibrationUiState
import com.uzopb.ragg.models.DownloadProgress
import com.uzopb.ragg.models.EtalonBenchmarkException
import com.uzopb.ragg.models.FitLevel
import com.uzopb.ragg.models.LocalModelStatus
import com.uzopb.ragg.models.ModelFitCard
import com.uzopb.ragg.models.ModelManager
import com.uzopb.ragg.models.ModelRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Экран Модели: калибровка, download, activate / delete (wire к ModelManager этапа 3).
 */
class ModelManagerScreenModel(
    private val modelManager: ModelManager,
    private val hardwareProbe: HardwareProbe,
) : ScreenModel {
    val cards: StateFlow<List<ModelFitCard>> = modelManager.observeCards()
        .stateIn(screenModelScope, SharingStarted.Eagerly, modelManager.currentCards())

    private val calibrationState = MutableStateFlow<CalibrationUiState>(resolveCalibration())
    private val busyIdState = MutableStateFlow<String?>(null)
    private val toastState = MutableStateFlow<String?>(null)
    private val downloadPctState = MutableStateFlow<Float?>(null)

    val calibration: StateFlow<CalibrationUiState> = calibrationState.asStateFlow()
    val busyId: StateFlow<String?> = busyIdState.asStateFlow()
    val toast: StateFlow<String?> = toastState.asStateFlow()
    val downloadPct: StateFlow<Float?> = downloadPctState.asStateFlow()

    init {
        screenModelScope.launch {
            modelManager.observeDownloadProgress().collect { p: DownloadProgress ->
                val total = p.contentLength
                downloadPctState.value = if (total != null && total > 0) {
                    p.bytesReceived.toFloat() / total.toFloat()
                } else {
                    null
                }
            }
        }
        screenModelScope.launch {
            modelManager.observeCards().collect {
                calibrationState.value = resolveCalibration()
            }
        }
    }

    fun installed(): List<ModelFitCard> =
        cards.value.filter { it.localStatus != LocalModelStatus.NotDownloaded }

    fun catalog(): List<ModelFitCard> =
        cards.value.filter { it.localStatus == LocalModelStatus.NotDownloaded }

    fun anchorText(): String? {
        val ready = calibrationState.value as? CalibrationUiState.Ready ?: return null
        return "Ориентир: это устройство · эталон · ${fmtTok(ready.etalonTokPerSec)} ток/с"
    }

    fun startSetup() {
        screenModelScope.launch {
            try {
                calibrationState.value = CalibrationUiState.PreparingHardware
                toastState.value = "Скачивание эталона…"
                calibrationState.value = CalibrationUiState.DownloadingEtalon(downloadPctState.value)
                val tok = modelManager.runEtalonBenchmark()
                val profile = hardwareProbe.probe()
                calibrationState.value = CalibrationUiState.Ready(
                    profile = profile,
                    etalonTokPerSec = tok,
                    groups = modelManager.recommendations(),
                )
                toastState.value = "Якорь: ${fmtTok(tok)} ток/с"
            } catch (e: EtalonBenchmarkException) {
                calibrationState.value = CalibrationUiState.Error(e.message ?: "ошибка")
                toastState.value = e.message
            } catch (t: Throwable) {
                calibrationState.value = CalibrationUiState.Error(t.message ?: "ошибка")
                toastState.value = t.message
            } finally {
                downloadPctState.value = null
            }
        }
    }

    fun download(modelId: String) {
        screenModelScope.launch {
            busyIdState.value = modelId
            try {
                modelManager.download(modelId)
                toastState.value = "Скачано"
                calibrationState.value = resolveCalibration()
            } catch (t: Throwable) {
                toastState.value = t.message ?: "ошибка загрузки"
            } finally {
                busyIdState.value = null
                downloadPctState.value = null
            }
        }
    }

    fun activate(modelId: String) {
        screenModelScope.launch {
            try {
                modelManager.setActive(modelId)
                toastState.value = "Активна"
            } catch (t: Throwable) {
                toastState.value = t.message
            }
        }
    }

    fun bench(modelId: String) {
        screenModelScope.launch {
            busyIdState.value = modelId
            try {
                val tok = modelManager.runBenchmark(modelId)
                toastState.value = "Оценка: ${fmtTok(tok)} ток/с"
                calibrationState.value = resolveCalibration()
            } catch (t: Throwable) {
                toastState.value = t.message
            } finally {
                busyIdState.value = null
            }
        }
    }

    fun delete(modelId: String) {
        screenModelScope.launch {
            try {
                modelManager.delete(modelId)
                toastState.value = "Удалено"
                calibrationState.value = resolveCalibration()
            } catch (t: Throwable) {
                toastState.value = t.message
            }
        }
    }

    fun downloadRecommended() {
        screenModelScope.launch {
            val groups = modelManager.recommendations()
            val targets = buildList {
                val emb = cards.value.firstOrNull {
                    it.model.role == ModelRole.Embedding &&
                        it.localStatus == LocalModelStatus.NotDownloaded &&
                        it.fit != FitLevel.Insufficient
                }
                if (emb != null) add(emb.model.id)
                val llm = groups.recommended.firstOrNull {
                    it.model.role == ModelRole.Llm && it.localStatus == LocalModelStatus.NotDownloaded
                } ?: cards.value.firstOrNull {
                    it.model.isEtalon && it.localStatus == LocalModelStatus.NotDownloaded
                }
                if (llm != null) add(llm.model.id)
            }.distinct()
            if (targets.isEmpty()) {
                toastState.value = "Рекомендованный набор уже установлен"
                return@launch
            }
            try {
                targets.forEach { id ->
                    busyIdState.value = id
                    modelManager.download(id)
                }
                targets.lastOrNull()?.let { modelManager.setActive(it) }
                toastState.value = "Набор скачан"
                calibrationState.value = resolveCalibration()
            } catch (t: Throwable) {
                toastState.value = t.message
            } finally {
                busyIdState.value = null
            }
        }
    }

    fun clearToast() {
        toastState.value = null
    }

    private fun resolveCalibration(): CalibrationUiState {
        val profile = hardwareProbe.probe()
        val cardsNow = modelManager.currentCards()
        val etalonCard = cardsNow.firstOrNull { it.model.isEtalon }
        val tok = etalonCard?.estimatedTokPerSec?.start
        val hasAnchor = tok != null &&
            etalonCard.confidence != com.uzopb.ragg.models.Confidence.Low
        return if (hasAnchor && tok != null) {
            CalibrationUiState.Ready(
                profile = profile,
                etalonTokPerSec = tok,
                groups = modelManager.recommendations(),
            )
        } else {
            CalibrationUiState.NotCalibrated
        }
    }

    private fun fmtTok(v: Float): String = ((v * 10).toInt() / 10f).toString()
}

package com.uzopb.ragg.models

import com.uzopb.ragg.device.HardwareProfile

/**
 * Состояния калибровки на экране Модели (встроены, не отдельный онбординг).
 */
sealed interface CalibrationUiState {
    data object NotCalibrated : CalibrationUiState
    data object PreparingHardware : CalibrationUiState
    data class DownloadingEtalon(val progress: Float?) : CalibrationUiState
    data class Benchmarking(val phase: String, val progress: Float?) : CalibrationUiState
    data class Ready(
        val profile: HardwareProfile,
        val etalonTokPerSec: Float,
        val groups: RecommendationGroups,
    ) : CalibrationUiState
    data class Error(val message: String) : CalibrationUiState
}

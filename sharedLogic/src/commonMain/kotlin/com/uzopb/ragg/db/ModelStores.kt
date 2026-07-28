package com.uzopb.ragg.db

import app.cash.sqldelight.db.SqlDriver
import com.uzopb.ragg.device.InferBackend
import com.uzopb.ragg.models.Calibration
import com.uzopb.ragg.models.CalibrationStore
import com.uzopb.ragg.models.InstalledModelStore
import com.uzopb.ragg.models.LocalModelStatus
import com.uzopb.ragg.models.ModelRole

/**
 * Обёртка над SQLDelight: готовность схемы InstalledModel / Calibration.
 */
class SqlDelightDatabaseGate(
    val database: RaggDatabase,
) : DatabaseGate {
    override val isReady: Boolean = true
}

fun createRaggDatabase(driver: SqlDriver, createSchema: Boolean): RaggDatabase {
    if (createSchema) {
        RaggDatabase.Schema.create(driver)
    }
    return RaggDatabase(driver)
}

/**
 * [CalibrationStore] на таблице CalibrationAnchor (PK = deviceFingerprint).
 */
class SqlCalibrationStore(
    private val db: RaggDatabase,
) : CalibrationStore {
    override fun get(deviceFingerprint: String): Calibration? {
        val row = db.calibrationQueries.selectByFingerprint(deviceFingerprint).executeAsOneOrNull()
            ?: return null
        return Calibration(
            modelId = row.modelId,
            backend = InferBackend.valueOf(row.backend),
            tokPerSec = row.tokPerSec.toFloat(),
            deviceFingerprint = row.deviceFingerprint,
            measuredAtEpochMs = row.measuredAt,
        )
    }

    override fun save(calibration: Calibration) {
        db.calibrationQueries.upsert(
            modelId = calibration.modelId,
            backend = calibration.backend.name,
            tokPerSec = calibration.tokPerSec.toDouble(),
            deviceFingerprint = calibration.deviceFingerprint,
            measuredAt = calibration.measuredAtEpochMs,
        )
    }

    override fun clear() {
        db.calibrationQueries.clearAll()
    }
}

/**
 * Учёт установленных моделей в SQLDelight.
 */
class SqlInstalledModelStore(
    private val db: RaggDatabase,
) : InstalledModelStore {

    override fun status(modelId: String): LocalModelStatus {
        val row = db.installedModelQueries.selectById(modelId).executeAsOneOrNull()
            ?: return LocalModelStatus.NotDownloaded
        return if (row.active != 0L) LocalModelStatus.Active else LocalModelStatus.Downloaded
    }

    override fun upsert(
        id: String,
        path: String,
        bytes: Long,
        sha256: String,
        role: ModelRole,
        active: Boolean,
    ) {
        db.installedModelQueries.upsert(
            id = id,
            path = path,
            bytes = bytes,
            sha256 = sha256,
            role = role.name,
            active = if (active) 1L else 0L,
        )
    }

    override fun setActive(id: String, role: ModelRole) {
        db.installedModelQueries.setActiveForRole(id, role.name)
    }

    override fun remove(id: String) {
        db.installedModelQueries.deleteById(id)
    }

    override fun statuses(): Map<String, LocalModelStatus> =
        db.installedModelQueries.selectAll().executeAsList().associate { row ->
            row.id to if (row.active != 0L) LocalModelStatus.Active else LocalModelStatus.Downloaded
        }

    override fun totalBytes(): Long =
        db.installedModelQueries.selectAll().executeAsList().sumOf { it.bytes }
}

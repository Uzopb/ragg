package com.uzopb.ragg.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uzopb.ragg.device.InferBackend
import com.uzopb.ragg.models.Calibration
import com.uzopb.ragg.models.LocalModelStatus
import com.uzopb.ragg.models.ModelRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * SQLDelight stores этапа 3 (InstalledModel / Calibration).
 */
class ModelStoresTest {

    @Test
    fun installedAndCalibration_roundTrip() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createRaggDatabase(driver, createSchema = true)
        val installed = SqlInstalledModelStore(db)
        val calibration = SqlCalibrationStore(db)

        installed.upsert(
            id = "m1",
            path = "/tmp/m1.gguf",
            bytes = 100L,
            sha256 = "ab".repeat(32),
            role = ModelRole.Llm,
            active = false,
        )
        assertEquals(LocalModelStatus.Downloaded, installed.status("m1"))
        installed.setActive("m1", ModelRole.Llm)
        assertEquals(LocalModelStatus.Active, installed.status("m1"))
        assertEquals(100L, installed.totalBytes())

        val fp = "android|6144|8"
        assertNull(calibration.get(fp))
        calibration.save(
            Calibration(
                modelId = "m1",
                backend = InferBackend.Cpu,
                tokPerSec = 5.0f,
                deviceFingerprint = fp,
                measuredAtEpochMs = 7L,
            ),
        )
        val got = assertNotNull(calibration.get(fp))
        assertEquals(5.0f, got.tokPerSec)
        assertEquals("m1", got.modelId)

        installed.remove("m1")
        assertEquals(LocalModelStatus.NotDownloaded, installed.status("m1"))
    }
}

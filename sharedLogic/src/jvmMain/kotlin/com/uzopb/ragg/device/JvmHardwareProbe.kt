package com.uzopb.ragg.device

import com.sun.management.OperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

/**
 * Desktop (JVM) проба: RAM через MXBean, CPU через Runtime/`/proc/cpuinfo`, GPU — lspci/эвристика.
 *
 * Vulkan/LWJGL не подключаем в этапе 1: fallback достаточно для tier/fit.
 */
class JvmHardwareProbe : HardwareProbe {

    override fun probe(): HardwareProfile {
        val ram = probeRam()
        val cpu = probeCpu()
        val gpu = probeGpu()
        val profile = HardwareProfile(
            platform = PlatformKind.Desktop,
            ram = ram,
            cpu = cpu,
            gpu = gpu,
            socOrChipset = cpu.name,
        )
        println("RAGG HardwareProfile=$profile score=${CapabilityScorer.score(profile)}")
        return profile
    }

    private fun probeRam(): RamInfo {
        val osBean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
        val total = osBean?.totalMemorySize?.takeIf { it > 0 }
            ?: Runtime.getRuntime().maxMemory()
        val free = osBean?.freeMemorySize?.takeIf { it >= 0 }
            ?: Runtime.getRuntime().freeMemory()
        return RamInfo(
            totalMb = total / BYTES_PER_MB,
            availableMb = free / BYTES_PER_MB,
        )
    }

    private fun probeCpu(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val arch = System.getProperty("os.arch")
        val fromProc = readLinuxCpuInfo()
        return CpuInfo(
            cores = cores,
            performanceCores = null,
            maxFreqMhz = fromProc.maxFreqMhz ?: readLinuxMaxFreqMhz(cores),
            name = fromProc.modelName,
            abi = arch,
        )
    }

    private fun probeGpu(): GpuInfo {
        val lspci = runCommand("lspci", timeoutSec = 2)
        val fromLspci = lspci
            ?.lineSequence()
            ?.firstOrNull { line ->
                line.contains("VGA", ignoreCase = true) ||
                    line.contains("3D", ignoreCase = true) ||
                    line.contains("Display", ignoreCase = true)
            }
            ?.substringAfter(": ")
            ?.trim()

        val name = fromLspci
            ?: System.getenv("RAGG_GPU_NAME")
            ?: "unknown"

        val api = when {
            fromLspci != null -> "lspci"
            else -> null
        }

        return GpuInfo(
            name = name,
            api = api,
            vramMbHint = null,
        )
    }

    private data class LinuxCpuInfo(
        val modelName: String?,
        val maxFreqMhz: Int?,
    )

    private fun readLinuxCpuInfo(): LinuxCpuInfo {
        val file = File("/proc/cpuinfo")
        if (!file.canRead()) return LinuxCpuInfo(null, null)
        var model: String? = null
        var mhz: Double? = null
        file.useLines { lines ->
            for (line in lines) {
                when {
                    model == null && line.startsWith("model name") ->
                        model = line.substringAfter(':').trim()
                    mhz == null && line.startsWith("cpu MHz") ->
                        mhz = line.substringAfter(':').trim().toDoubleOrNull()
                }
                if (model != null && mhz != null) break
            }
        }
        return LinuxCpuInfo(modelName = model, maxFreqMhz = mhz?.toInt())
    }

    private fun readLinuxMaxFreqMhz(cores: Int): Int? {
        var maxKhz = 0L
        for (i in 0 until cores) {
            val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (!f.canRead()) continue
            val khz = f.readText().trim().toLongOrNull() ?: continue
            if (khz > maxKhz) maxKhz = khz
        }
        return if (maxKhz > 0) (maxKhz / 1000L).toInt() else null
    }

    private fun runCommand(command: String, timeoutSec: Long): String? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().readText().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

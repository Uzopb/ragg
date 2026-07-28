package com.uzopb.ragg.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Android-проба: RAM через ActivityManager, CPU/SoC через Build и sysfs, GPU через GLES/EGL.
 */
class AndroidHardwareProbe(
    private val context: Context,
) : HardwareProbe {

    override fun probe(): HardwareProfile {
        val ram = probeRam()
        val cpu = probeCpu()
        val gpu = probeGpu()
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else {
            null
        }
        val soc = listOfNotNull(
            socModel,
            Build.HARDWARE.takeIf { it.isNotBlank() },
            Build.BOARD.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" / ").ifBlank { null }

        val profile = HardwareProfile(
            platform = PlatformKind.Android,
            ram = ram,
            cpu = cpu,
            gpu = gpu,
            socOrChipset = soc,
        )
        Log.d(TAG, "HardwareProfile=$profile score=${CapabilityScorer.score(profile)}")
        return profile
    }

    private fun probeRam(): RamInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamInfo(
            totalMb = info.totalMem / BYTES_PER_MB,
            availableMb = info.availMem / BYTES_PER_MB,
        )
    }

    private fun probeCpu(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
        return CpuInfo(
            cores = cores,
            performanceCores = null,
            maxFreqMhz = readMaxCpuFreqMhz(cores),
            name = Build.HARDWARE.takeIf { it.isNotBlank() },
            abi = abi,
        )
    }

    private fun probeGpu(): GpuInfo {
        val renderer = queryGlRenderer()
        val api = buildList {
            if (hasFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                hasFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            ) {
                add("Vulkan")
            }
            if (renderer != null) add("GLES")
            // API 27+: NNAPI обычно доступен; точный feature-флаг не везде стабилен
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) add("NNAPI")
        }.joinToString("+").ifBlank { null }

        return GpuInfo(
            name = renderer,
            api = api,
            vramMbHint = null,
        )
    }

    private fun hasFeature(feature: String): Boolean =
        context.packageManager.hasSystemFeature(feature)

    private fun readMaxCpuFreqMhz(cores: Int): Int? {
        var maxKhz = 0L
        for (i in 0 until cores) {
            val file = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (!file.canRead()) continue
            val khz = file.readText().trim().toLongOrNull() ?: continue
            if (khz > maxKhz) maxKhz = khz
        }
        return if (maxKhz > 0) (maxKhz / 1000L).toInt() else null
    }

    /**
     * Best-effort GL_RENDERER через короткоживущий offscreen EGL-контекст.
     */
    private fun queryGlRenderer(): String? {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfig, 0) ||
                numConfig[0] == 0
            ) {
                EGL14.eglTerminate(display)
                return null
            }
            val config = configs[0] ?: run {
                EGL14.eglTerminate(display)
                return null
            }
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) {
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                return null
            }
            EGL14.eglMakeCurrent(display, surface, surface, context)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            renderer?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private companion object {
        const val TAG = "RaggHardware"
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

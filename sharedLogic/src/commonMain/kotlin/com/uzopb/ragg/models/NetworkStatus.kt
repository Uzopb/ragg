package com.uzopb.ragg.models

/**
 * Доступность сети для download эталона / моделей.
 */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}

/** Всегда online (удобно для Desktop/тестов без реальной проверки). */
object AlwaysOnline : NetworkStatus {
    override fun isOnline(): Boolean = true
}

/** Всегда offline. */
object AlwaysOffline : NetworkStatus {
    override fun isOnline(): Boolean = false
}

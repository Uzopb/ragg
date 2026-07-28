package com.uzopb.ragg.db

/**
 * Stage 0 placeholder. SQLDelight schema + drivers arrive in stage 5a.
 */
interface DatabaseGate {
    val isReady: Boolean
}

object StubDatabaseGate : DatabaseGate {
    override val isReady: Boolean = false
}

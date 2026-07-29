package com.uzopb.ragg.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Процессный флаг `vectorizing` до полноценного [ModelSession] (этап 5b).
 *
 * UI Home подписан на [vectorizing]: при `true` → `ChatState.Blocked(Indexing)`.
 */
class SessionGate {
    private val vectorizingState = MutableStateFlow(false)

    /** Идёт ли applyDraft / mock-индексация. */
    val vectorizing: StateFlow<Boolean> = vectorizingState.asStateFlow()

    /** Выставляется mock-индексером на время фаз UnloadLlm…Commit. */
    fun setVectorizing(value: Boolean) {
        vectorizingState.value = value
    }
}

package com.uzopb.ragg.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface EmbeddingEngine {
    suspend fun embed(texts: List<String>): List<FloatArray>
}

interface LlmEngine {
    fun complete(prompt: String): Flow<String>
}

class MockEmbeddingEngine : EmbeddingEngine {
    override suspend fun embed(texts: List<String>): List<FloatArray> =
        texts.map { FloatArray(8) { 0f } }
}

class MockLlmEngine : LlmEngine {
    override fun complete(prompt: String): Flow<String> =
        flowOf("[mock] $prompt")
}

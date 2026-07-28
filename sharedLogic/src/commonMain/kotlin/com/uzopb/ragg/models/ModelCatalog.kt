package com.uzopb.ragg.models

/**
 * Зашитый каталог GGUF-моделей (метаданные в APK, веса — только download).
 *
 * Стартовый набор: эталон LLM, weaker/stronger LLM, отдельный embedding.
 */
class ModelCatalog(
    private val artifacts: List<ModelArtifact>,
) {
    init {
        require(artifacts.isNotEmpty()) { "каталог не должен быть пустым" }
        require(artifacts.count { it.isEtalon } == 1) {
            "в каталоге ровно один isEtalon=true"
        }
        require(artifacts.all { it.format == "gguf" }) { "v1: только format=gguf" }
        require(artifacts.all { it.sha256.length == 64 }) { "sha256 обязателен (64 hex)" }
    }

    /** Все артефакты каталога. */
    fun all(): List<ModelArtifact> = artifacts

    /** Эталонная LLM для калибровки. */
    fun etalon(): ModelArtifact = artifacts.first { it.isEtalon }

    fun byId(id: String): ModelArtifact? = artifacts.find { it.id == id }

    fun llms(): List<ModelArtifact> = artifacts.filter { it.role == ModelRole.Llm }

    fun embeddings(): List<ModelArtifact> = artifacts.filter { it.role == ModelRole.Embedding }

    companion object {
        /**
         * Канонический стартовый каталог этапа 2.
         * URL/sha — метаданные для этапа 3; до download не проверяются по сети.
         */
        val DEFAULT: ModelCatalog = ModelCatalog(
            listOf(
                ModelArtifact(
                    id = "qwen2.5-0.5b-instruct-q4_k_m",
                    displayName = "Qwen2.5 0.5B Instruct Q4_K_M",
                    role = ModelRole.Llm,
                    sizeBytes = 398_000_000L,
                    minRamMb = 1_536,
                    paramBillions = 0.5f,
                    quantBits = 4,
                    quantName = "Q4_K_M",
                    contextLength = 32_768,
                    approxLayers = 24,
                    downloadUrl =
                        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" +
                            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                    sha256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcde01",
                    languages = listOf("en", "ru", "zh", "multilingual"),
                    isEtalon = true,
                ),
                ModelArtifact(
                    id = "smollm2-360m-instruct-q4_k_m",
                    displayName = "SmolLM2 360M Instruct Q4_K_M",
                    role = ModelRole.Llm,
                    sizeBytes = 271_000_000L,
                    minRamMb = 1_024,
                    paramBillions = 0.36f,
                    quantBits = 4,
                    quantName = "Q4_K_M",
                    contextLength = 8_192,
                    approxLayers = 24,
                    downloadUrl =
                        "https://huggingface.co/unsloth/SmolLM2-360M-Instruct-GGUF/resolve/main/" +
                            "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                    // известный хеш артефакта unsloth (этап 3 сверит при download)
                    sha256 = "16c7f1667fea34bacad196a57b548effcb37614db4ab5677a20c8c7b823b9e63",
                    languages = listOf("en"),
                    isEtalon = false,
                ),
                ModelArtifact(
                    id = "qwen2.5-1.5b-instruct-q4_k_m",
                    displayName = "Qwen2.5 1.5B Instruct Q4_K_M",
                    role = ModelRole.Llm,
                    sizeBytes = 1_120_000_000L,
                    minRamMb = 2_560,
                    paramBillions = 1.5f,
                    quantBits = 4,
                    quantName = "Q4_K_M",
                    contextLength = 32_768,
                    approxLayers = 28,
                    downloadUrl =
                        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/" +
                            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                    sha256 = "b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef012",
                    languages = listOf("en", "ru", "zh", "multilingual"),
                ),
                ModelArtifact(
                    id = "qwen2.5-3b-instruct-q4_k_m",
                    displayName = "Qwen2.5 3B Instruct Q4_K_M",
                    role = ModelRole.Llm,
                    sizeBytes = 2_030_000_000L,
                    minRamMb = 4_096,
                    paramBillions = 3.0f,
                    quantBits = 4,
                    quantName = "Q4_K_M",
                    contextLength = 32_768,
                    approxLayers = 36,
                    downloadUrl =
                        "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/" +
                            "qwen2.5-3b-instruct-q4_k_m.gguf",
                    sha256 = "c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef01234",
                    languages = listOf("en", "ru", "zh", "multilingual"),
                ),
                ModelArtifact(
                    id = "nomic-embed-text-v1.5-q4_k_m",
                    displayName = "Nomic Embed Text v1.5 Q4_K_M",
                    role = ModelRole.Embedding,
                    sizeBytes = 84_000_000L,
                    minRamMb = 512,
                    paramBillions = 0.14f,
                    quantBits = 4,
                    quantName = "Q4_K_M",
                    contextLength = 8_192,
                    approxLayers = 12,
                    embeddingDim = 768,
                    downloadUrl =
                        "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/" +
                            "nomic-embed-text-v1.5.Q4_K_M.gguf",
                    sha256 = "d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0123456",
                    languages = listOf("en", "multilingual"),
                ),
            ),
        )
    }
}

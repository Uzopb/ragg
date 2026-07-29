package com.uzopb.ragg.docs

/**
 * Прогресс mock/реального applyDraft (фазы как в демо и плане).
 *
 * Removals-only: без LoadEmbed / Running с эмбеддингом — короткий путь к Commit.
 */
sealed interface VectorizeProgress {
    data object UnloadingLlm : VectorizeProgress
    data class LoadingEmbed(val modelId: String) : VectorizeProgress
    data class Running(
        val done: Int,
        val total: Int,
        val added: Int,
        val removed: Int,
    ) : VectorizeProgress
    data object UnloadingEmbed : VectorizeProgress
    data object Committing : VectorizeProgress
    data object Cancelling : VectorizeProgress
    data object Done : VectorizeProgress
    data class Failed(val message: String, val rolledBack: Boolean) : VectorizeProgress
    data object Cancelled : VectorizeProgress
}

/**
 * Векторная база (Corpus). В этапе 4 — in-memory mock; схема SQLDelight — 5a.
 */
data class CorpusInfo(
    val id: String,
    val title: String,
    val documentCount: Int,
    val vectorBytes: Long,
)

/**
 * Документ в менеджере ресурсов: [included] = индекс, [draftIncluded] = черновик чекбоксов.
 */
data class ResourceDocument(
    val id: String,
    val title: String,
    val sourceBytes: Long,
    val vectorBytes: Long,
    val included: Boolean,
    val draftIncluded: Boolean,
) {
    /** Ожидает добавления в индекс после «Обновить». */
    val pendingAdd: Boolean get() = draftIncluded && !included

    /** Ожидает снятия с индекса. */
    val pendingRemove: Boolean get() = included && !draftIncluded
}

/**
 * Снимок экрана Ресурсы.
 */
data class ResourceSnapshot(
    val corpora: List<CorpusInfo>,
    val activeCorpusId: String,
    val documents: List<ResourceDocument>,
    val sourcesBytes: Long,
    val databaseBytes: Long,
    val modelsBytes: Long,
) {
    val totalBytes: Long get() = sourcesBytes + databaseBytes + modelsBytes
}

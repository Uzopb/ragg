package com.uzopb.ragg.docs

import com.uzopb.ragg.ai.SessionGate
import com.uzopb.ragg.chat.ChatRepository
import com.uzopb.ragg.models.StorageStatsProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

private const val EMBED_RATIO = 1.35

/**
 * Mock менеджер ресурсов этапа 4: draft состава, Corpus CRUD, фазы applyDraft.
 *
 * Реальный IndexTransaction / emb — этап 5.
 */
class MockResourceManager(
    private val sessionGate: SessionGate,
    private val storageStatsProvider: StorageStatsProvider,
    private val chatRepository: ChatRepository,
    private val idGen: () -> String = { "x-${Random.nextLong().toULong().toString(16)}" },
) {
    private val corpora = MutableStateFlow(
        listOf(
            CorpusInfo(
                id = ChatRepository.DEFAULT_CORPUS_ID,
                title = "Default",
                documentCount = 2,
                vectorBytes = 0L,
            ),
        ),
    )
    private val activeCorpusId = MutableStateFlow(ChatRepository.DEFAULT_CORPUS_ID)
    private val documents = MutableStateFlow(seedDocs())
    private val snapshotState = MutableStateFlow(buildSnapshot())
    private val progressState = MutableStateFlow<VectorizeProgress?>(null)

    @Volatile
    private var cancelRequested: Boolean = false

    fun observeSnapshot(): StateFlow<ResourceSnapshot> = snapshotState.asStateFlow()

    fun observeProgress(): StateFlow<VectorizeProgress?> = progressState.asStateFlow()

    fun toggleDraft(documentId: String) {
        if (sessionGate.vectorizing.value) return
        documents.update { list ->
            list.map {
                if (it.id == documentId) it.copy(draftIncluded = !it.draftIncluded) else it
            }
        }
        publish()
    }

    fun addDocument(title: String, bytes: Long = 12_000L) {
        if (sessionGate.vectorizing.value) return
        val doc = ResourceDocument(
            id = idGen(),
            title = title,
            sourceBytes = bytes,
            vectorBytes = 0L,
            included = false,
            draftIncluded = true,
        )
        documents.update { it + doc }
        publish()
    }

    fun deleteDocument(documentId: String) {
        if (sessionGate.vectorizing.value) return
        documents.update { list -> list.filterNot { it.id == documentId } }
        publish()
    }

    fun createCorpus(title: String) {
        if (sessionGate.vectorizing.value) return
        val corpus = CorpusInfo(
            id = idGen(),
            title = title.ifBlank { "Corpus" },
            documentCount = 0,
            vectorBytes = 0L,
        )
        corpora.update { it + corpus }
        publish()
    }

    fun renameCorpus(corpusId: String, title: String) {
        if (sessionGate.vectorizing.value) return
        corpora.update { list ->
            list.map { if (it.id == corpusId) it.copy(title = title.ifBlank { it.title }) else it }
        }
        publish()
    }

    fun deleteCorpus(corpusId: String) {
        if (sessionGate.vectorizing.value) return
        if (corpusId == ChatRepository.DEFAULT_CORPUS_ID) return
        corpora.update { list -> list.filterNot { it.id == corpusId } }
        if (activeCorpusId.value == corpusId) {
            activeCorpusId.value = ChatRepository.DEFAULT_CORPUS_ID
            chatRepository.setActiveCorpus(ChatRepository.DEFAULT_CORPUS_ID)
        }
        publish()
    }

    /** Делает Corpus активной для текущего чата (I9). */
    fun activateCorpus(corpusId: String) {
        if (corpora.value.none { it.id == corpusId }) return
        activeCorpusId.value = corpusId
        chatRepository.setActiveCorpus(corpusId)
        publish()
    }

    /**
     * Mock applyDraft: до 5 фаз; removals-only — без LoadEmbed.
     * Выставляет [SessionGate.setVectorizing]; отмена — [cancelActiveJob].
     */
    fun applyDraft(): Flow<VectorizeProgress> = flow {
        if (sessionGate.vectorizing.value) {
            emit(VectorizeProgress.Failed("уже идёт индексация", rolledBack = true))
            return@flow
        }
        cancelRequested = false
        sessionGate.setVectorizing(true)
        chatRepository.onVectorizingChanged(true)
        try {
            val current = documents.value
            val toAdd = current.filter { it.pendingAdd }
            val toRemove = current.filter { it.pendingRemove }
            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                emit(VectorizeProgress.Done)
                return@flow
            }
            val needEmbed = toAdd.isNotEmpty()

            emitPhase(VectorizeProgress.UnloadingLlm)
            delayPhase(420)
            if (cancelled()) {
                emitCancelled()
                return@flow
            }

            if (needEmbed) {
                emitPhase(VectorizeProgress.LoadingEmbed("mock-embed"))
                delayPhase(520)
                if (cancelled()) {
                    emitCancelled()
                    return@flow
                }

                val total = toAdd.size
                for ((index, _) in toAdd.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (cancelled()) {
                        emitCancelled()
                        return@flow
                    }
                    emitPhase(
                        VectorizeProgress.Running(
                            done = index + 1,
                            total = total,
                            added = toAdd.size,
                            removed = toRemove.size,
                        ),
                    )
                    delayPhase(380)
                }

                emitPhase(VectorizeProgress.UnloadingEmbed)
                delayPhase(360)
                if (cancelled()) {
                    emitCancelled()
                    return@flow
                }
            }

            emitPhase(VectorizeProgress.Committing)
            delayPhase(280)
            if (cancelled()) {
                emitCancelled()
                return@flow
            }

            commitDraft(toAdd.map { it.id }.toSet(), toRemove.map { it.id }.toSet())
            emitPhase(VectorizeProgress.Done)
        } catch (t: Throwable) {
            emitPhase(VectorizeProgress.Failed(t.message ?: "ошибка индексации", rolledBack = true))
        } finally {
            sessionGate.setVectorizing(false)
            chatRepository.onVectorizingChanged(false)
            delay(200)
            progressState.value = null
            publish()
        }
    }

    /** Отмена mock-таймера (этап 4); Live без изменений. */
    fun cancelActiveJob() {
        cancelRequested = true
        progressState.value = VectorizeProgress.Cancelling
    }

    private suspend fun FlowCollector<VectorizeProgress>.emitPhase(progress: VectorizeProgress) {
        progressState.value = progress
        emit(progress)
    }

    private suspend fun FlowCollector<VectorizeProgress>.emitCancelled() {
        emitPhase(VectorizeProgress.Cancelling)
        delay(220)
        emitPhase(VectorizeProgress.Cancelled)
    }

    private fun cancelled(): Boolean = cancelRequested

    private suspend fun delayPhase(ms: Long) {
        var left = ms
        while (left > 0) {
            currentCoroutineContext().ensureActive()
            if (cancelled()) return
            val step = minOf(40L, left)
            delay(step)
            left -= step
        }
    }

    private fun commitDraft(addIds: Set<String>, removeIds: Set<String>) {
        documents.update { list ->
            list.map { doc ->
                when {
                    doc.id in addIds -> doc.copy(
                        included = true,
                        draftIncluded = true,
                        vectorBytes = (doc.sourceBytes * EMBED_RATIO).toLong(),
                    )
                    doc.id in removeIds -> doc.copy(
                        included = false,
                        draftIncluded = false,
                        vectorBytes = 0L,
                    )
                    else -> doc
                }
            }
        }
        refreshCorpusStats()
    }

    private fun refreshCorpusStats() {
        val included = documents.value.filter { it.included }
        corpora.update { list ->
            list.map { corpus ->
                if (corpus.id == activeCorpusId.value) {
                    corpus.copy(
                        documentCount = included.size,
                        vectorBytes = included.sumOf { it.vectorBytes },
                    )
                } else {
                    corpus
                }
            }
        }
    }

    private fun publish() {
        snapshotState.value = buildSnapshot()
    }

    private fun buildSnapshot(): ResourceSnapshot {
        val docs = documents.value
        val disk = storageStatsProvider.stats()
        val sources = docs.sumOf { it.sourceBytes }
        val database = docs.filter { it.included }.sumOf { it.vectorBytes }
        return ResourceSnapshot(
            corpora = corpora.value,
            activeCorpusId = activeCorpusId.value,
            documents = docs.sortedBy { it.title.lowercase() },
            sourcesBytes = sources,
            databaseBytes = database,
            modelsBytes = disk.modelsBytes,
        )
    }

    private fun seedDocs(): List<ResourceDocument> = listOf(
        ResourceDocument(
            id = "d1",
            title = "handbook.txt",
            sourceBytes = 128_000L,
            vectorBytes = (128_000 * EMBED_RATIO).toLong(),
            included = true,
            draftIncluded = true,
        ),
        ResourceDocument(
            id = "d2",
            title = "faq-product.md",
            sourceBytes = 42_000L,
            vectorBytes = (42_000 * EMBED_RATIO).toLong(),
            included = true,
            draftIncluded = true,
        ),
        ResourceDocument(
            id = "d3",
            title = "notes-meeting.txt",
            sourceBytes = 8_400L,
            vectorBytes = 0L,
            included = false,
            draftIncluded = false,
        ),
    )
}

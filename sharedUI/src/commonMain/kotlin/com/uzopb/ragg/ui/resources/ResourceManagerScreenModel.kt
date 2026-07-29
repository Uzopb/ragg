package com.uzopb.ragg.ui.resources

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.uzopb.ragg.docs.MockResourceManager
import com.uzopb.ragg.docs.ResourceSnapshot
import com.uzopb.ragg.docs.VectorizeProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Экран Ресурсы: draft состава, Corpus, mock-векторизация до 5 фаз.
 */
class ResourceManagerScreenModel(
    private val resources: MockResourceManager,
) : ScreenModel {
    val snapshot: StateFlow<ResourceSnapshot> = resources.observeSnapshot()
        .stateIn(screenModelScope, SharingStarted.Eagerly, resources.observeSnapshot().value)

    val progress: StateFlow<VectorizeProgress?> = resources.observeProgress()
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    private var applyJob: Job? = null

    fun toggleDraft(id: String) = resources.toggleDraft(id)

    fun addDocument() = resources.addDocument("drop-in-${snapshot.value.documents.size + 1}.txt")

    fun deleteDocument(id: String) = resources.deleteDocument(id)

    fun createCorpus() = resources.createCorpus("База ${snapshot.value.corpora.size + 1}")

    fun renameCorpus(id: String, title: String) = resources.renameCorpus(id, title)

    fun deleteCorpus(id: String) = resources.deleteCorpus(id)

    fun activateCorpus(id: String) = resources.activateCorpus(id)

    fun applyDraft() {
        if (applyJob?.isActive == true) return
        applyJob = screenModelScope.launch {
            resources.applyDraft().collect { /* progress через StateFlow */ }
        }
    }

    fun cancel() {
        resources.cancelActiveJob()
    }

    fun phaseLabel(p: VectorizeProgress?): String = when (p) {
        null -> ""
        VectorizeProgress.UnloadingLlm -> "Выгрузка LLM…"
        is VectorizeProgress.LoadingEmbed -> "Загрузка embedding…"
        is VectorizeProgress.Running -> "Векторизация ${p.done}/${p.total}"
        VectorizeProgress.UnloadingEmbed -> "Выгрузка embedding…"
        VectorizeProgress.Committing -> "Commit…"
        VectorizeProgress.Cancelling -> "Отмена…"
        VectorizeProgress.Done -> "Готово"
        is VectorizeProgress.Failed -> "Ошибка: ${p.message}"
        VectorizeProgress.Cancelled -> "Отменено"
    }

    fun phaseHint(p: VectorizeProgress?): String = when (p) {
        is VectorizeProgress.Running ->
            "+${p.added} / −${p.removed} · Staging→commit"
        VectorizeProgress.Cancelling, VectorizeProgress.Cancelled ->
            "Staging очищен · Live без изменений"
        VectorizeProgress.Done ->
            "Индекс обновлён · emb выгружена"
        else -> "Serial mock · emb эфемерна · commit/rollback"
    }

    fun phaseProgress(p: VectorizeProgress?): Float = when (p) {
        VectorizeProgress.UnloadingLlm -> 0.12f
        is VectorizeProgress.LoadingEmbed -> 0.28f
        is VectorizeProgress.Running ->
            0.28f + 0.45f * (p.done.toFloat() / p.total.coerceAtLeast(1))
        VectorizeProgress.UnloadingEmbed -> 0.78f
        VectorizeProgress.Committing -> 0.92f
        VectorizeProgress.Done -> 1f
        VectorizeProgress.Cancelling, VectorizeProgress.Cancelled -> 1f
        is VectorizeProgress.Failed -> 1f
        null -> 0f
    }

    fun isVectorizing(p: VectorizeProgress?): Boolean =
        p != null && p !is VectorizeProgress.Done &&
            p !is VectorizeProgress.Failed && p !is VectorizeProgress.Cancelled
}

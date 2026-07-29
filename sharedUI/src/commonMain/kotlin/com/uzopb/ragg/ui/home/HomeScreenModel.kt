package com.uzopb.ragg.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.uzopb.ragg.chat.BlockReason
import com.uzopb.ragg.chat.Chat
import com.uzopb.ragg.chat.ChatRepository
import com.uzopb.ragg.chat.ChatState
import com.uzopb.ragg.chat.ChatSummary
import com.uzopb.ragg.models.InstalledModelStore
import com.uzopb.ragg.models.LocalModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Оркестрация Home: чат, drawers, TXT; уважает Blocked(Indexing).
 */
class HomeScreenModel(
    private val chatRepository: ChatRepository,
    private val installed: InstalledModelStore,
) : ScreenModel {
    val chat: StateFlow<Chat> = chatRepository.observeActiveChat()
        .stateIn(screenModelScope, SharingStarted.Eagerly, chatRepository.observeActiveChat().value)

    val chatState: StateFlow<ChatState> = chatRepository.observeChatState()
        .stateIn(screenModelScope, SharingStarted.Eagerly, chatRepository.observeChatState().value)

    val summaries: StateFlow<List<ChatSummary>> = chatRepository.observeChatSummaries()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    private val menuOpenState = MutableStateFlow(false)
    private val historyOpenState = MutableStateFlow(false)
    private val histQueryState = MutableStateFlow("")
    private val draftState = MutableStateFlow("")
    private val toastState = MutableStateFlow<String?>(null)

    val menuOpen: StateFlow<Boolean> = menuOpenState.asStateFlow()
    val historyOpen: StateFlow<Boolean> = historyOpenState.asStateFlow()
    val histQuery: StateFlow<String> = histQueryState.asStateFlow()
    val draft: StateFlow<String> = draftState.asStateFlow()
    val toast: StateFlow<String?> = toastState.asStateFlow()

    fun openMenu() {
        menuOpenState.value = true
    }

    fun closeMenu() {
        menuOpenState.value = false
    }

    fun openHistory() {
        menuOpenState.value = false
        historyOpenState.value = true
        histQueryState.value = ""
    }

    fun closeHistory() {
        historyOpenState.value = false
    }

    fun setHistQuery(q: String) {
        histQueryState.value = q
    }

    fun filteredHistory(): List<ChatSummary> = chatRepository.search(histQueryState.value)

    fun selectChat(id: String) {
        chatRepository.selectChat(id)
        historyOpenState.value = false
    }

    fun deleteChat(id: String) {
        chatRepository.deleteChat(id, ChatRepository.DEFAULT_CORPUS_ID)
    }

    fun newChat() {
        chatRepository.newChat(ChatRepository.DEFAULT_CORPUS_ID)
        toastState.value = "Новый чат"
    }

    fun setDraft(value: String) {
        draftState.value = value
    }

    fun send() {
        val text = draftState.value
        draftState.value = ""
        screenModelScope.launch {
            chatRepository.send(text)
        }
    }

    fun exportTxt() {
        val txt = chatRepository.exportActiveTxt()
        toastState.value = if (txt.isBlank()) "Нечего сохранять" else "TXT готов (${txt.length} симв.)"
    }

    fun clearToast() {
        toastState.value = null
    }

    fun clearBlock() {
        chatRepository.clearTransientBlock()
    }

    fun hasModels(): Boolean =
        installed.statuses().any { (_, s) ->
            s == LocalModelStatus.Active || s == LocalModelStatus.Downloaded
        }

    fun blockHint(state: ChatState): String? = when (state) {
        is ChatState.Blocked -> when (state.reason) {
            BlockReason.Indexing -> "Идёт индексация…"
            BlockReason.LeaseBusy -> "Модель занята…"
            BlockReason.NoModels ->
                "Моделей ещё нет. Меню → Модели → «Начать»."
            BlockReason.NoActiveCorpus -> "Нет активной базы. Меню → Ресурсы."
        }
        is ChatState.Error -> state.message
        else -> null
    }

    fun composerBlocked(state: ChatState): Boolean =
        state is ChatState.Blocked ||
            state is ChatState.Loading ||
            state is ChatState.Streaming
}

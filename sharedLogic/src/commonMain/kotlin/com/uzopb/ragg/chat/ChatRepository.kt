package com.uzopb.ragg.chat

import com.uzopb.ragg.ai.LlmEngine
import com.uzopb.ragg.ai.SessionGate
import com.uzopb.ragg.models.InstalledModelStore
import com.uzopb.ragg.models.LocalModelStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * In-memory история чатов и mock-стриминг ответа (этап 4).
 *
 * Реальный RAG через ModelSession — этап 6.
 */
class ChatRepository(
    private val sessionGate: SessionGate,
    private val llmEngine: LlmEngine,
    private val installed: InstalledModelStore,
    private val nowMs: () -> Long = { 0L },
    private val idGen: () -> String = { "c-${Random.nextLong().toULong().toString(16)}" },
) {
    private val chats = MutableStateFlow(seedChats())
    private val activeId = MutableStateFlow(chats.value.first().id)
    private val activeChatState = MutableStateFlow(requireActive())
    private val chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    private val summariesState = MutableStateFlow(summaries())

    fun observeActiveChat(): StateFlow<Chat> = activeChatState.asStateFlow()

    fun observeChatState(): StateFlow<ChatState> = chatState.asStateFlow()

    fun observeChatSummaries(): StateFlow<List<ChatSummary>> = summariesState.asStateFlow()

    /** Поиск по названию и тексту сообщений. */
    fun search(query: String): List<ChatSummary> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return summariesState.value
        return chats.value
            .filter { chat ->
                chat.title.lowercase().contains(q) ||
                    chat.messages.any { it.text.lowercase().contains(q) }
            }
            .map { it.toSummary() }
            .sortedByDescending { it.updatedAtEpochMs }
    }

    fun selectChat(chatId: String) {
        if (chats.value.none { it.id == chatId }) return
        activeId.value = chatId
        refreshActive()
        chatState.value = blockedOrIdle()
    }

    fun newChat(defaultCorpusId: String) {
        val chat = Chat(
            id = idGen(),
            title = "Новый чат",
            activeCorpusId = defaultCorpusId,
            updatedAtEpochMs = nowMs(),
            messages = emptyList(),
        )
        chats.update { listOf(chat) + it }
        activeId.value = chat.id
        publish()
        chatState.value = blockedOrIdle()
    }

    fun deleteChat(chatId: String, defaultCorpusId: String) {
        chats.update { list -> list.filterNot { it.id == chatId } }
        if (chats.value.isEmpty()) {
            newChat(defaultCorpusId)
            return
        }
        if (activeId.value == chatId) {
            activeId.value = chats.value.first().id
        }
        publish()
    }

    fun setActiveCorpus(corpusId: String) {
        mutateActive { it.copy(activeCorpusId = corpusId, updatedAtEpochMs = nowMs()) }
    }

    /**
     * Отправка сообщения: mock-ответ; уважает [SessionGate.vectorizing] и наличие модели.
     */
    suspend fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (sessionGate.vectorizing.value) {
            chatState.value = ChatState.Blocked(BlockReason.Indexing)
            return
        }
        if (!hasAnyModel()) {
            chatState.value = ChatState.Blocked(BlockReason.NoModels)
            return
        }
        val active = activeChatState.value
        if (active.activeCorpusId.isBlank()) {
            chatState.value = ChatState.Blocked(BlockReason.NoActiveCorpus)
            return
        }

        val userMsg = ChatMessage(idGen(), ChatRole.User, trimmed)
        mutateActive { chat ->
            val title = if (chat.messages.isEmpty()) trimmed.take(40) else chat.title
            chat.copy(
                title = title,
                messages = chat.messages + userMsg,
                updatedAtEpochMs = nowMs(),
            )
        }

        chatState.value = ChatState.Loading
        val assistantId = idGen()
        var acc = ""
        try {
            llmEngine.complete(mockPrompt(trimmed)).collect { chunk ->
                if (sessionGate.vectorizing.value) {
                    chatState.value = ChatState.Blocked(BlockReason.Indexing)
                    return@collect
                }
                acc += chunk
                chatState.value = ChatState.Streaming(acc)
                delay(24)
            }
            if (acc.isEmpty()) {
                acc = mockAnswer(trimmed)
                for (i in 1..acc.length) {
                    if (sessionGate.vectorizing.value) {
                        chatState.value = ChatState.Blocked(BlockReason.Indexing)
                        return
                    }
                    chatState.value = ChatState.Streaming(acc.take(i))
                    delay(10)
                }
            }
            if (chatState.value is ChatState.Blocked) return
            mutateActive { chat ->
                chat.copy(
                    messages = chat.messages + ChatMessage(assistantId, ChatRole.Assistant, acc),
                    updatedAtEpochMs = nowMs(),
                )
            }
            chatState.value = ChatState.Idle
        } catch (t: Throwable) {
            chatState.value = ChatState.Error(t.message ?: "ошибка ответа")
        }
    }

    /** Сброс временного Blocked/Error (кроме активной индексации). */
    fun clearTransientBlock() {
        if (sessionGate.vectorizing.value) {
            chatState.value = ChatState.Blocked(BlockReason.Indexing)
            return
        }
        when (chatState.value) {
            is ChatState.Blocked, is ChatState.Error -> chatState.value = ChatState.Idle
            else -> Unit
        }
    }

    /** Подписка на [SessionGate.vectorizing] → обязательный Blocked(Indexing). */
    fun onVectorizingChanged(vectorizing: Boolean) {
        if (vectorizing) {
            chatState.value = ChatState.Blocked(BlockReason.Indexing)
        } else {
            val current = chatState.value
            if (current is ChatState.Blocked &&
                (current.reason == BlockReason.Indexing || current.reason == BlockReason.LeaseBusy)
            ) {
                chatState.value = ChatState.Idle
            }
        }
    }

    /** Экспорт активного чата в TXT. */
    fun exportActiveTxt(): String {
        val chat = activeChatState.value
        return buildString {
            appendLine("# ${chat.title}")
            appendLine()
            chat.messages.forEach { msg ->
                val who = when (msg.role) {
                    ChatRole.User -> "Вы"
                    ChatRole.Assistant -> "RAGG"
                }
                appendLine("$who:")
                appendLine(msg.text)
                appendLine()
            }
        }
    }

    private fun blockedOrIdle(): ChatState =
        if (sessionGate.vectorizing.value) {
            ChatState.Blocked(BlockReason.Indexing)
        } else {
            ChatState.Idle
        }

    private fun hasAnyModel(): Boolean =
        installed.statuses().any { (_, status) ->
            status == LocalModelStatus.Active || status == LocalModelStatus.Downloaded
        }

    private fun mockPrompt(user: String): String =
        "По handbook.txt: краткий ответ на «$user»."

    private fun mockAnswer(user: String): String =
        "По handbook.txt: ${user.trimEnd('?')}. " +
            "Возврат возможен в течение 14 дней при сохранении упаковки. Нужен чек или номер заказа."

    private fun requireActive(): Chat =
        chats.value.first { it.id == activeId.value }

    private fun refreshActive() {
        activeChatState.value = requireActive()
    }

    private fun publish() {
        refreshActive()
        summariesState.value = summaries()
    }

    private fun mutateActive(transform: (Chat) -> Chat) {
        val id = activeId.value
        chats.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
        publish()
    }

    private fun summaries(): List<ChatSummary> =
        chats.value.map { it.toSummary() }.sortedByDescending { it.updatedAtEpochMs }

    private fun Chat.toSummary(): ChatSummary =
        ChatSummary(
            id = id,
            title = title,
            preview = messages.lastOrNull()?.text.orEmpty().take(80),
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private fun seedChats(): List<Chat> {
        val now = nowMs().coerceAtLeast(1L)
        return listOf(
            Chat(
                id = "c1",
                title = "Политика возврата",
                activeCorpusId = DEFAULT_CORPUS_ID,
                updatedAtEpochMs = now - 3_600_000L,
                messages = listOf(
                    ChatMessage("m1", ChatRole.User, "Какая политика возврата?"),
                    ChatMessage(
                        "m2",
                        ChatRole.Assistant,
                        "По handbook.txt возврат возможен в течение 14 дней при сохранении упаковки. Нужен чек или номер заказа.",
                    ),
                ),
            ),
        )
    }

    companion object {
        const val DEFAULT_CORPUS_ID: String = "corpus-default"
    }
}

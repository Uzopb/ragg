package com.uzopb.ragg.chat

/**
 * Состояние composer / ответа на Home.
 *
 * При индексации UI **обязан** перейти в [Blocked] с [BlockReason.Indexing] (I2).
 */
sealed interface ChatState {
    data object Idle : ChatState
    data object Loading : ChatState
    data class Streaming(val text: String) : ChatState
    data class Blocked(val reason: BlockReason) : ChatState
    data class Error(val message: String) : ChatState
}

/**
 * Причина блокировки composer на Home.
 */
enum class BlockReason {
    Indexing,
    LeaseBusy,
    NoActiveCorpus,
    NoModels,
}

/**
 * Роль сообщения в диалоге.
 */
enum class ChatRole {
    User,
    Assistant,
}

/**
 * Одно сообщение чата.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
)

/**
 * Диалог с привязкой к одной активной Corpus (I9).
 */
data class Chat(
    val id: String,
    val title: String,
    val activeCorpusId: String,
    val updatedAtEpochMs: Long,
    val messages: List<ChatMessage>,
)

/**
 * Краткая строка для HistoryDrawer.
 */
data class ChatSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtEpochMs: Long,
)

package com.uzopb.ragg.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.uzopb.ragg.chat.ChatMessage
import com.uzopb.ragg.chat.ChatRole
import com.uzopb.ragg.chat.ChatState
import com.uzopb.ragg.chat.ChatSummary
import com.uzopb.ragg.ui.components.IconBtn
import com.uzopb.ragg.ui.components.MenuIcon
import com.uzopb.ragg.ui.components.PlusIcon
import com.uzopb.ragg.ui.components.SaveIcon
import com.uzopb.ragg.ui.components.SearchIcon
import com.uzopb.ragg.ui.components.SendIcon
import com.uzopb.ragg.ui.components.TrashIcon
import com.uzopb.ragg.ui.models.ModelManagerScreen
import com.uzopb.ragg.ui.resources.ResourceManagerScreen
import com.uzopb.ragg.ui.theme.RaggColors

/**
 * Главный экран: чат. Старт приложения всегда здесь (без онбординга моделей).
 */
object HomeScreen : Screen {
    @Composable
    override fun Content() {
        val model = getScreenModel<HomeScreenModel>()
        val navigator = LocalNavigator.currentOrThrow
        val chat by model.chat.collectAsState()
        val chatState by model.chatState.collectAsState()
        val menuOpen by model.menuOpen.collectAsState()
        val historyOpen by model.historyOpen.collectAsState()
        val histQuery by model.histQuery.collectAsState()
        val draft by model.draft.collectAsState()
        val toast by model.toast.collectAsState()
        val summaries by model.summaries.collectAsState()

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ChatTopBar(
                    title = chat.title,
                    onMenu = model::openMenu,
                    onSave = model::exportTxt,
                    onNew = model::newChat,
                )
                MessagesPane(
                    messages = chat.messages,
                    chatState = chatState,
                    hasModels = model.hasModels(),
                    modifier = Modifier.weight(1f),
                )
                ComposerBar(
                    draft = draft,
                    chatState = chatState,
                    blocked = model.composerBlocked(chatState),
                    hint = model.blockHint(chatState),
                    onDraftChange = model::setDraft,
                    onSend = model::send,
                    onClearBlock = model::clearBlock,
                )
            }

            if (toast != null) {
                LaunchedEffect(toast) {
                    kotlinx.coroutines.delay(2200)
                    model.clearToast()
                }
                Toast(toast!!, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
            }

            DrawerHost(
                visible = menuOpen,
                onDismiss = model::closeMenu,
            ) {
                MenuDrawerContent(
                    onHistory = model::openHistory,
                    onModels = {
                        model.closeMenu()
                        navigator.push(ModelManagerScreen)
                    },
                    onResources = {
                        model.closeMenu()
                        navigator.push(ResourceManagerScreen)
                    },
                )
            }

            DrawerHost(
                visible = historyOpen,
                onDismiss = model::closeHistory,
            ) {
                HistoryDrawerContent(
                    query = histQuery,
                    items = if (histQuery.isBlank()) summaries else model.filteredHistory(),
                    onQuery = model::setHistQuery,
                    onSelect = model::selectChat,
                    onDelete = model::deleteChat,
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(onClick = onMenu, contentDescription = "Меню") { MenuIcon() }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = "RAGG",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        IconBtn(onClick = onSave, contentDescription = "Сохранить как TXT") { SaveIcon() }
        Spacer(Modifier.width(6.dp))
        IconBtn(onClick = onNew, contentDescription = "Новый чат") { PlusIcon() }
    }
}

@Composable
private fun MessagesPane(
    messages: List<ChatMessage>,
    chatState: ChatState,
    hasModels: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, chatState) {
        val last = messages.size
        if (last > 0) listState.animateScrollToItem(last)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (messages.isEmpty() && chatState !is ChatState.Streaming) {
            item {
                EmptyChat(hasModels = hasModels)
            }
        }
        items(messages, key = { it.id }) { msg ->
            MessageBubble(msg)
        }
        when (chatState) {
            is ChatState.Streaming -> item {
                MessageBubble(
                    ChatMessage("streaming", ChatRole.Assistant, chatState.text),
                )
            }
            is ChatState.Loading -> item {
                Text("…", color = RaggColors.Muted, modifier = Modifier.padding(8.dp))
            }
            else -> Unit
        }
    }
}

@Composable
private fun EmptyChat(hasModels: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            "Только чат",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasModels) {
                "Новый диалог или история. Модели и документы — в меню."
            } else {
                "Новый диалог или история. Модели ещё не настроены — меню → Модели."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatRole.User
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (isUser) "Вы" else "RAGG",
            style = MaterialTheme.typography.labelSmall,
            color = RaggColors.Gray500,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) RaggColors.Gray800.copy(alpha = 0.92f)
                    else Color.White.copy(alpha = 0.72f),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = msg.text,
                color = if (isUser) RaggColors.Pearl50 else RaggColors.Ink,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ComposerBar(
    draft: String,
    chatState: ChatState,
    blocked: Boolean,
    hint: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearBlock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (hint != null && chatState is ChatState.Blocked) {
            Text(
                text = hint,
                color = RaggColors.Warn,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearBlock)
                    .padding(bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (blocked && chatState is ChatState.Blocked) {
                        RaggColors.Pearl200.copy(alpha = 0.7f)
                    } else {
                        Color.White.copy(alpha = 0.78f)
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = !blocked,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp),
                textStyle = TextStyle(color = RaggColors.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(RaggColors.Accent),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            if (chatState is ChatState.Blocked &&
                                chatState.reason == com.uzopb.ragg.chat.BlockReason.Indexing
                            ) {
                                "Идёт индексация…"
                            } else {
                                "Спросите по вашим документам…"
                            },
                            color = RaggColors.Gray400,
                        )
                    }
                    inner()
                },
            )
            IconBtn(
                onClick = onSend,
                contentDescription = "Отправить",
                enabled = !blocked && draft.isNotBlank(),
                active = !blocked && draft.isNotBlank(),
            ) { SendIcon() }
        }
    }
}

@Composable
private fun DrawerHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(300.dp)
                .background(Color.White.copy(alpha = 0.94f)),
        ) {
            content()
        }
    }
}

@Composable
private fun MenuDrawerContent(
    onHistory: () -> Unit,
    onModels: () -> Unit,
    onResources: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "RAGG",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Text("Локальный RAG", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))
        DrawerItem("История", onHistory)
        DrawerItem("Модели", onModels)
        DrawerItem("Ресурсы", onResources)
        Spacer(Modifier.weight(1f))
        Text("Этап 4 · mock индекс", style = MaterialTheme.typography.labelSmall, color = RaggColors.Gray400)
    }
}

@Composable
private fun DrawerItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun HistoryDrawerContent(
    query: String,
    items: List<ChatSummary>,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("RAGG", fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text("История", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RaggColors.Pearl100)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon()
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 14.sp, color = RaggColors.Ink),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Поиск…", color = RaggColors.Gray400)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text(
                if (query.isBlank()) "Пока пусто" else "Ничего не найдено",
                color = RaggColors.Muted,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(item.id) }
                            .background(RaggColors.Pearl50.copy(alpha = 0.6f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (item.preview.isNotBlank()) {
                                Text(item.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            }
                        }
                        IconBtn(
                            onClick = { onDelete(item.id) },
                            contentDescription = "Удалить",
                            danger = true,
                            size = 34.dp,
                        ) { TrashIcon() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RaggColors.Gray800.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = RaggColors.Pearl50, fontSize = 13.sp)
    }
}

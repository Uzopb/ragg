package com.uzopb.ragg.ui.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.uzopb.ragg.docs.CorpusInfo
import com.uzopb.ragg.docs.ResourceDocument
import com.uzopb.ragg.docs.VectorizeProgress
import com.uzopb.ragg.ui.components.CheckIcon
import com.uzopb.ragg.ui.components.CloseIcon
import com.uzopb.ragg.ui.components.IconBtn
import com.uzopb.ragg.ui.components.PlusIcon
import com.uzopb.ragg.ui.components.ProgressBar
import com.uzopb.ragg.ui.components.RefreshIcon
import com.uzopb.ragg.ui.components.TextBtn
import com.uzopb.ragg.ui.components.TrashIcon
import com.uzopb.ragg.ui.components.formatBytes
import com.uzopb.ragg.ui.theme.RaggColors

/** Менеджер ресурсов; ✕ → Home. Mock applyDraft блокирует чат через SessionGate. */
object ResourceManagerScreen : Screen {
    @Composable
    override fun Content() {
        val model = getScreenModel<ResourceManagerScreenModel>()
        val navigator = LocalNavigator.currentOrThrow
        val snapshot by model.snapshot.collectAsState()
        val progress by model.progress.collectAsState()
        val vectorizing = model.isVectorizing(progress)

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text("Ресурсы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconBtn(onClick = { navigator.pop() }, contentDescription = "Закрыть") { CloseIcon() }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                StorageStatsRow(snapshot.sourcesBytes, snapshot.databaseBytes, snapshot.modelsBytes, snapshot.totalBytes)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Отметьте источники и нажмите обновить: LLM выгрузится, embedding — только на векторизацию, затем выгрузится. Только удаления — без embedding.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                GroupHead(
                    title = "Векторные базы",
                    actions = {
                        IconBtn(
                            onClick = model::createCorpus,
                            contentDescription = "Создать",
                            enabled = !vectorizing,
                            size = 34.dp,
                        ) { PlusIcon(size = 16.dp) }
                    },
                )
                snapshot.corpora.forEach { corpus ->
                    CorpusRow(
                        corpus = corpus,
                        active = corpus.id == snapshot.activeCorpusId,
                        locked = vectorizing,
                        onActivate = { model.activateCorpus(corpus.id) },
                        onDelete = { model.deleteCorpus(corpus.id) },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(16.dp))
                GroupHead(
                    title = "Документы",
                    actions = {
                        IconBtn(
                            onClick = model::applyDraft,
                            contentDescription = "Обновить индекс",
                            enabled = !vectorizing,
                            size = 34.dp,
                        ) { RefreshIcon() }
                        Spacer(Modifier.width(4.dp))
                        IconBtn(
                            onClick = model::addDocument,
                            contentDescription = "Добавить",
                            enabled = !vectorizing,
                            size = 34.dp,
                        ) { PlusIcon(size = 16.dp) }
                    },
                )

                if (progress != null) {
                    VectorizeBlock(
                        phase = model.phaseLabel(progress),
                        hint = model.phaseHint(progress),
                        pct = model.phaseProgress(progress),
                        cancellable = vectorizing && progress !is VectorizeProgress.Cancelling,
                        onCancel = model::cancel,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                snapshot.documents.forEach { doc ->
                    DocCard(
                        doc = doc,
                        locked = vectorizing,
                        onToggle = { model.toggleDraft(doc.id) },
                        onDelete = { model.deleteDocument(doc.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StorageStatsRow(
    sources: Long,
    database: Long,
    models: Long,
    total: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCell("Исходники", formatBytes(sources), Modifier.weight(1f))
        StatCell("БД", formatBytes(database), Modifier.weight(1f))
        StatCell("Модели", formatBytes(models), Modifier.weight(1f))
        StatCell("Всего", formatBytes(total), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = RaggColors.Gray500)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun GroupHead(title: String, actions: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = RaggColors.Gray500,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun CorpusRow(
    corpus: CorpusInfo,
    active: Boolean,
    locked: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) RaggColors.Ok.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(corpus.title, fontWeight = FontWeight.SemiBold)
            Text(
                "${corpus.documentCount} док. · ${formatBytes(corpus.vectorBytes)}" +
                    if (active) " · активна" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextBtn(
            text = if (active) "Активна" else "Сделать активной",
            onClick = onActivate,
            enabled = !locked && !active,
        )
        if (corpus.id != com.uzopb.ragg.chat.ChatRepository.DEFAULT_CORPUS_ID) {
            IconBtn(
                onClick = onDelete,
                contentDescription = "Удалить базу",
                danger = true,
                enabled = !locked,
                size = 34.dp,
            ) { TrashIcon() }
        }
    }
}

@Composable
private fun VectorizeBlock(
    phase: String,
    hint: String,
    pct: Float,
    cancellable: Boolean,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RaggColors.Pearl200.copy(alpha = 0.85f))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(phase, fontWeight = FontWeight.SemiBold)
            Text("${(pct * 100).toInt()}%", color = RaggColors.Muted)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(progress = pct)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (cancellable) {
                TextBtn(text = "Отмена", onClick = onCancel, danger = true)
            }
        }
    }
}

@Composable
private fun DocCard(
    doc: ResourceDocument,
    locked: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = when {
        doc.pendingAdd -> RaggColors.Ok.copy(alpha = 0.45f)
        doc.pendingRemove -> RaggColors.Warn.copy(alpha = 0.5f)
        doc.included -> RaggColors.Pearl300
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = !locked, onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (doc.draftIncluded) RaggColors.Ok.copy(alpha = 0.18f)
                    else RaggColors.Pearl200,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (doc.draftIncluded) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides RaggColors.Ok,
                ) { CheckIcon() }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.title, fontWeight = FontWeight.SemiBold)
            Text(docStatus(doc), style = MaterialTheme.typography.bodyMedium)
        }
        IconBtn(
            onClick = onDelete,
            contentDescription = "Удалить",
            danger = true,
            enabled = !locked,
            size = 34.dp,
        ) { TrashIcon() }
    }
}

private fun docStatus(doc: ResourceDocument): String {
    val base = when {
        doc.pendingAdd -> "будет добавлен"
        doc.pendingRemove -> "будет снят"
        doc.included -> "в индексе"
        else -> "не в индексе"
    }
    val size = formatBytes(doc.sourceBytes)
    val vec = if (doc.included && doc.vectorBytes > 0) " · индекс ${formatBytes(doc.vectorBytes)}" else ""
    return "$base · $size$vec"
}

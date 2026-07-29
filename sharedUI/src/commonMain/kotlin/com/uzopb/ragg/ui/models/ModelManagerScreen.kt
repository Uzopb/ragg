package com.uzopb.ragg.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.uzopb.ragg.models.CalibrationUiState
import com.uzopb.ragg.models.ComfortLevel
import com.uzopb.ragg.models.FitLevel
import com.uzopb.ragg.models.LocalModelStatus
import com.uzopb.ragg.models.ModelFitCard
import com.uzopb.ragg.models.ModelRole
import com.uzopb.ragg.ui.components.CloseIcon
import com.uzopb.ragg.ui.components.DownloadIcon
import com.uzopb.ragg.ui.components.IconBtn
import com.uzopb.ragg.ui.components.PrimaryBtn
import com.uzopb.ragg.ui.components.ProgressBar
import com.uzopb.ragg.ui.components.TextBtn
import com.uzopb.ragg.ui.components.TrashIcon
import com.uzopb.ragg.ui.components.formatBytes
import com.uzopb.ragg.ui.theme.RaggColors

/** Полный экран настройки моделей; ✕ → Home. */
object ModelManagerScreen : Screen {
    @Composable
    override fun Content() {
        val model = getScreenModel<ModelManagerScreenModel>()
        val navigator = LocalNavigator.currentOrThrow
        val cards by model.cards.collectAsState()
        val calibration by model.calibration.collectAsState()
        val busyId by model.busyId.collectAsState()
        val toast by model.toast.collectAsState()
        val downloadPct by model.downloadPct.collectAsState()

        // перечитать installed/catalog при обновлении cards
        val installed = cards.filter { it.localStatus != LocalModelStatus.NotDownloaded }
        val catalog = cards.filter { it.localStatus == LocalModelStatus.NotDownloaded }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar(onClose = { navigator.pop() })
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    model.anchorText()?.let { anchor ->
                        AnchorPill(anchor)
                        Spacer(Modifier.height(12.dp))
                    }

                    GroupTitle("Установленные")
                    if (installed.isEmpty()) {
                        EmptyInstalled(
                            calibrating = calibration is CalibrationUiState.PreparingHardware ||
                                calibration is CalibrationUiState.DownloadingEtalon ||
                                calibration is CalibrationUiState.Benchmarking,
                            progress = downloadPct,
                            error = (calibration as? CalibrationUiState.Error)?.message,
                            onStart = model::startSetup,
                        )
                    } else {
                        installed.forEach { card ->
                            ModelCard(
                                card = card,
                                manager = true,
                                busy = busyId == card.model.id,
                                onActivate = { model.activate(card.model.id) },
                                onBench = { model.bench(card.model.id) },
                                onDelete = { model.delete(card.model.id) },
                                onDownload = {},
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    if (calibration is CalibrationUiState.Ready && catalog.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val ready = calibration as CalibrationUiState.Ready
                        if (ready.groups.recommended.any { it.localStatus == LocalModelStatus.NotDownloaded } ||
                            ready.groups.canGoStronger.any { it.localStatus == LocalModelStatus.NotDownloaded }
                        ) {
                            PrimaryBtn(
                                text = "Скачать рекомендованный набор",
                                onClick = model::downloadRecommended,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    GroupTitle("Каталог")
                    if (catalog.isEmpty()) {
                        Text("Каталог пуст", color = RaggColors.Muted)
                    } else {
                        catalog.forEach { card ->
                            ModelCard(
                                card = card,
                                manager = true,
                                busy = busyId == card.model.id,
                                onActivate = {},
                                onBench = {},
                                onDelete = {},
                                onDownload = { model.download(card.model.id) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (toast != null) {
                LaunchedEffect(toast) {
                    kotlinx.coroutines.delay(2200)
                    model.clearToast()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaggColors.Gray800.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(toast!!, color = RaggColors.Pearl50, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Text("Модели", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        IconBtn(onClick = onClose, contentDescription = "Закрыть") { CloseIcon() }
    }
}

@Composable
private fun AnchorPill(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(RaggColors.Pearl200.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = RaggColors.Gray500,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun EmptyInstalled(
    calibrating: Boolean,
    progress: Float?,
    error: String?,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Нет скачанных моделей.", fontWeight = FontWeight.SemiBold)
            TextBtn(text = "Начать", onClick = onStart, enabled = !calibrating)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Первичная настройка: профиль железа и эталонная GGUF (~калибровка на этом устройстве).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "После бенча появится якорь ток/с и рекомендации weaker / stronger.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (calibrating) {
            Spacer(Modifier.height(12.dp))
            ProgressBar(progress = progress ?: 0.35f)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = RaggColors.Danger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModelCard(
    card: ModelFitCard,
    manager: Boolean,
    busy: Boolean,
    onActivate: () -> Unit,
    onBench: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
) {
    val m = card.model
    val installed = card.localStatus != LocalModelStatus.NotDownloaded
    val active = card.localStatus == LocalModelStatus.Active
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Badge(card)
            }
            Text(
                metaLine(card),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
        if (manager) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!installed) {
                    IconBtn(
                        onClick = onDownload,
                        contentDescription = "Скачать",
                        enabled = !busy && card.fit != FitLevel.Insufficient,
                    ) { DownloadIcon() }
                } else {
                    if (m.role == ModelRole.Llm) {
                        IconBtn(
                            onClick = onActivate,
                            contentDescription = "Активировать",
                            active = active,
                            enabled = !busy,
                        ) {
                            Text(if (active) "●" else "○", fontSize = 12.sp)
                        }
                        IconBtn(
                            onClick = onBench,
                            contentDescription = "Прогнать вживую",
                            enabled = !busy,
                        ) {
                            Text("↻", fontSize = 14.sp)
                        }
                    }
                    IconBtn(
                        onClick = onDelete,
                        contentDescription = "Удалить",
                        danger = true,
                        enabled = !busy,
                    ) { TrashIcon() }
                }
            }
        }
    }
}

@Composable
private fun Badge(card: ModelFitCard) {
    val (label, color) = when {
        card.model.isEtalon -> "эталон" to RaggColors.Ok
        card.model.role == ModelRole.Embedding -> "embedding" to RaggColors.Ok
        card.fit == FitLevel.Insufficient -> "не стоит" to RaggColors.Danger
        card.comfort == ComfortLevel.Impractical -> "не стоит" to RaggColors.Danger
        card.comfort == ComfortLevel.Slow -> "медленнее" to RaggColors.Warn
        else -> return
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun metaLine(card: ModelFitCard): String {
    val m = card.model
    val role = if (m.role == ModelRole.Embedding) "embed" else "LLM"
    val size = formatBytes(m.sizeBytes)
    val tok = card.estimatedTokPerSec?.let { "~${(it.start * 10).toInt() / 10f} ток/с" }
    val fit = card.fit.name
    return listOfNotNull(role, size, tok, fit, card.reason.takeIf { it.isNotBlank() }?.take(40)).joinToString(" · ")
}

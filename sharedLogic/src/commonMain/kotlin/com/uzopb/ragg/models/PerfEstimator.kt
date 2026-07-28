package com.uzopb.ragg.models

import com.uzopb.ragg.device.CapabilityScore
import com.uzopb.ragg.device.CapabilityScorer
import com.uzopb.ragg.device.HardwareProfile
import com.uzopb.ragg.device.InferBackend

/**
 * Оценка fit и ориентира tok/s / embedMs по профилю и каталогу.
 *
 * Якорь tok/s — [Calibration] с **совпадающим** [Calibration.deviceFingerprint];
 * чужой отпечаток игнорируется (карточки без якоря: fit по RAM + «без якоря»).
 */
object PerfEstimator {

    /** Порог comfortable tok/s (план: ~3.0). */
    const val MIN_COMFORT_TOK_PER_SEC: Float = 3.0f

    private const val SLOW_TOK_PER_SEC: Float = 1.0f

    /** Доля размера GGUF, учитываемая как рабочий RSS при mmap. */
    private const val MMAP_RSS_FACTOR: Float = 0.45f

    /** Headroom: hot-set активной Corpus + KV / краткий embedCtx + ОС. */
    private const val HEADROOM_LLM_MB: Float = 1_300f
    private const val HEADROOM_EMBED_MB: Float = 1_050f

    /**
     * Строит [ModelFitCard] для каждой модели каталога.
     *
     * @param localStatuses статусы файлов; отсутствующий id → [LocalModelStatus.NotDownloaded]
     */
    fun estimate(
        profile: HardwareProfile,
        catalog: ModelCatalog = ModelCatalog.DEFAULT,
        calibration: Calibration? = null,
        localStatuses: Map<String, LocalModelStatus> = emptyMap(),
        score: CapabilityScore = CapabilityScorer.score(profile),
    ): List<ModelFitCard> {
        val etalon = catalog.etalon()
        val fingerprint = deviceFingerprint(profile)
        val anchor = calibration?.takeIf { it.deviceFingerprint == fingerprint }
        val t = anchor?.tokPerSec

        return catalog.all().map { model ->
            val fit = ramFit(profile.ram.availableMb, model)
            val status = localStatuses[model.id] ?: LocalModelStatus.NotDownloaded
            when (model.role) {
                ModelRole.Llm -> llmCard(model, etalon, fit, score.preferredBackend, t, status)
                ModelRole.Embedding -> embedCard(model, fit, score.preferredBackend, status)
            }
        }
    }

    /**
     * Группы рекомендаций для экрана Модели (после калибровки осмысленны полностью).
     */
    fun recommendationGroups(cards: List<ModelFitCard>): RecommendationGroups {
        val llms = cards.filter { it.model.role == ModelRole.Llm }
        val embeds = cards.filter { it.model.role == ModelRole.Embedding }

        val recommended = buildList {
            llms.filter {
                it.relativeClass == RelativeSpeedClass.Etalon ||
                    (
                        it.relativeClass == RelativeSpeedClass.Weaker &&
                            it.comfort == ComfortLevel.Comfortable &&
                            it.fit == FitLevel.Fits
                        )
            }.let { addAll(it) }
            embeds.filter { it.fit == FitLevel.Fits || it.fit == FitLevel.Tight }.let { addAll(it) }
        }

        val canGoStronger = llms.filter {
            it.relativeClass == RelativeSpeedClass.Stronger &&
                it.fit == FitLevel.Fits &&
                it.comfort != ComfortLevel.Impractical
        }

        val notWorth = cards.filter {
            it.fit == FitLevel.Insufficient || it.comfort == ComfortLevel.Impractical
        }

        return RecommendationGroups(
            recommended = recommended.distinctBy { it.model.id },
            canGoStronger = canGoStronger,
            notWorth = notWorth.distinctBy { it.model.id },
        )
    }

    /**
     * Отпечаток устройства для привязки [Calibration] к железу.
     */
    fun deviceFingerprint(profile: HardwareProfile): String =
        listOf(
            profile.platform.name,
            profile.ram.totalMb.toString(),
            profile.cpu.cores.toString(),
            profile.cpu.maxFreqMhz?.toString().orEmpty(),
            profile.socOrChipset.orEmpty(),
            profile.cpu.name.orEmpty(),
        ).joinToString("|")

    /**
     * RAM fit: mmap RSS ≪ sizeBytes + headroom под hot-set / KV / краткий embedCtx.
     * Не суммировать «полный размер файла = RAM».
     */
    fun ramFit(availableMb: Long, model: ModelArtifact): FitLevel {
        val sizeMb = model.sizeBytes / (1024f * 1024f)
        val rssMb = sizeMb * MMAP_RSS_FACTOR
        val headroom = if (model.role == ModelRole.Llm) HEADROOM_LLM_MB else HEADROOM_EMBED_MB
        val needMb = rssMb + headroom
        val avail = availableMb.toFloat().coerceAtLeast(1f)
        // Явный порог каталога: модель с minRam выше доступной — сразу Insufficient
        if (model.minRamMb > availableMb) return FitLevel.Insufficient
        return when {
            needMb <= avail * 0.55f -> FitLevel.Fits
            needMb <= avail * 0.90f -> FitLevel.Tight
            else -> FitLevel.Insufficient
        }
    }

    private fun llmCard(
        model: ModelArtifact,
        etalon: ModelArtifact,
        fit: FitLevel,
        backend: InferBackend,
        measuredT: Float?,
        status: LocalModelStatus,
    ): ModelFitCard {
        if (measuredT == null) {
            return ModelFitCard(
                model = model,
                fit = fit,
                preferredBackend = backend,
                estimatedTokPerSec = null,
                estimatedEmbedMs = null,
                reason = fitReason(fit) + "; без якоря",
                confidence = Confidence.Low,
                localStatus = status,
                relativeClass = null,
                comfort = null,
            )
        }
        val rel = ModelCost.relativeClass(model, etalon)
        val est = ModelCost.estTokPerSec(model, etalon, measuredT)
        val comfort = comfortOf(est, fit)
        val band = (est * 0.85f)..(est * 1.15f)
        val confidence =
            if (model.id == etalon.id) Confidence.High else Confidence.Medium
        return ModelFitCard(
            model = model,
            fit = fit,
            preferredBackend = backend,
            estimatedTokPerSec = band,
            estimatedEmbedMs = null,
            reason = buildString {
                append(fitReason(fit))
                append("; ")
                append(rel.name.lowercase())
                append(" · ~")
                append(format1(est))
                append(" ток/с")
            },
            confidence = confidence,
            localStatus = status,
            relativeClass = rel,
            comfort = comfort,
        )
    }

    private fun embedCard(
        model: ModelArtifact,
        fit: FitLevel,
        backend: InferBackend,
        status: LocalModelStatus,
    ): ModelFitCard {
        val dim = (model.embeddingDim ?: 384).toFloat()
        val sizeMb = model.sizeBytes / (1024f * 1024f)
        // Грубая оценка ms на query-embed до retrieve (бюджет плана ~≤2 с на mid-phone)
        val midMs = 40f + dim * 0.08f + sizeMb * 0.9f
        val band = (midMs * 0.7f)..(midMs * 1.4f)
        return ModelFitCard(
            model = model,
            fit = fit,
            preferredBackend = backend,
            estimatedTokPerSec = null,
            estimatedEmbedMs = band,
            reason = fitReason(fit) + "; embed ~${format1(midMs)} ms",
            confidence = Confidence.Medium,
            localStatus = status,
            relativeClass = null,
            comfort = null,
        )
    }

    private fun comfortOf(estTok: Float, fit: FitLevel): ComfortLevel = when {
        fit == FitLevel.Insufficient || estTok < SLOW_TOK_PER_SEC -> ComfortLevel.Impractical
        estTok >= MIN_COMFORT_TOK_PER_SEC -> ComfortLevel.Comfortable
        else -> ComfortLevel.Slow
    }

    private fun fitReason(fit: FitLevel): String = when (fit) {
        FitLevel.Fits -> "влезает в available RAM"
        FitLevel.Tight -> "впритык по RAM"
        FitLevel.Insufficient -> "недостаточно RAM"
    }

    private fun format1(value: Float): String {
        val scaled = (value * 10f).toInt()
        return "${scaled / 10}.${scaled % 10}"
    }
}

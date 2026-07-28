package com.uzopb.ragg.device

/**
 * Известные SoC / чипсеты: относительные подсказки cpu/gpu (0…100) для tier до калибровки.
 *
 * GPU≈низкий на mid Mali ⇒ LLM → CPU (I: preferredBackend advisory, v1 runtime CPU).
 */
object KnownSocTable {

    data class SocHint(
        val cpuHint: Float,
        val gpuHint: Float,
        /** Нормализованный socBoost 0…1 для формулы cpuScore. */
        val socBoost: Float,
    )

    private val entries: List<Pair<Regex, SocHint>> = listOf(
        Regex("helio\\s*g95|mt6785", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 35f, gpuHint = 15f, socBoost = 0.35f),
        Regex("helio\\s*g99|mt6789", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 40f, gpuHint = 18f, socBoost = 0.40f),
        Regex("dimensity\\s*7|mt68[23]", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 48f, gpuHint = 25f, socBoost = 0.48f),
        Regex("dimensity\\s*8|dimensity\\s*9|mt68[89]", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 62f, gpuHint = 40f, socBoost = 0.62f),
        Regex("snapdragon\\s*6[0-9]{2}|sdm6|sm6[0-9]", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 38f, gpuHint = 22f, socBoost = 0.38f),
        Regex("snapdragon\\s*7[0-9]{2}|sm7[0-9]", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 52f, gpuHint = 35f, socBoost = 0.52f),
        Regex("snapdragon\\s*8|sm8[0-9]", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 72f, gpuHint = 55f, socBoost = 0.72f),
        Regex("exynos\\s*1[2-9]|exynos\\s*2", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 55f, gpuHint = 30f, socBoost = 0.55f),
        Regex("tensor\\s*g|google\\s*tensor", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 58f, gpuHint = 28f, socBoost = 0.58f),
        Regex("apple\\s*m[1-4]|apple\\s*a1[5-9]", RegexOption.IGNORE_CASE) to
            SocHint(cpuHint = 85f, gpuHint = 70f, socBoost = 0.85f),
    )

    /**
     * Ищет подсказку по [socOrChipset] и/или имени CPU.
     */
    fun lookup(socOrChipset: String?, cpuName: String?): SocHint? {
        val haystack = listOfNotNull(socOrChipset, cpuName).joinToString(" ")
        if (haystack.isBlank()) return null
        return entries.firstOrNull { (regex, _) -> regex.containsMatchIn(haystack) }?.second
    }
}

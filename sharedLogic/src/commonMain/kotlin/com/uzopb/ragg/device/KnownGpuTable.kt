package com.uzopb.ragg.device

/**
 * Известные GPU: относительный балл 0…100 и пригодность для LLM-offload (advisory).
 *
 * Mid Mali / слабые Adreno → [llmUseful]=false ⇒ gpuScore=0 в скоринге.
 */
object KnownGpuTable {

    data class GpuHint(
        val scoreHint: Float,
        val llmUseful: Boolean,
    )

    private val entries: List<Pair<Regex, GpuHint>> = listOf(
        // Mobile: mid Mali — для v1 LLM бесполезен
        Regex("mali[- ]?g5[0-9]|mali[- ]?g6[0-9]|mali[- ]?g7[0-6]", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 15f, llmUseful = false),
        Regex("mali[- ]?g7[8-9]|mali[- ]?g[89]", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 28f, llmUseful = false),
        Regex("adreno\\s*6[0-9]{2}", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 25f, llmUseful = false),
        Regex("adreno\\s*7[0-9]{2}", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 40f, llmUseful = false),
        Regex("adreno\\s*8[0-9]{2}", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 55f, llmUseful = false),
        Regex("apple\\s*gpu|apple\\s*m[1-4]", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 70f, llmUseful = true),
        // Desktop discrete
        Regex("nvidia|geforce|rtx|gtx|quadro", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 80f, llmUseful = true),
        Regex("radeon|amd\\s*rx|radeon\\s*pro", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 75f, llmUseful = true),
        Regex("intel\\s*(uhd|iris|arc)", RegexOption.IGNORE_CASE) to
            GpuHint(scoreHint = 35f, llmUseful = false),
    )

    fun lookup(gpuName: String?): GpuHint? {
        if (gpuName.isNullOrBlank()) return null
        return entries.firstOrNull { (regex, _) -> regex.containsMatchIn(gpuName) }?.second
    }
}

package com.uzopb.ragg.models

/**
 * Относительная «стоимость» генерации (безразмерная). Больше = медленнее.
 *
 * Формула из плана:
 * `paramB * quantFactor(quantName) * (approxLayers / etalon.approxLayers).coerceAtLeast(0.5)`
 *
 * Для [ModelRole.Embedding] cost не используется (оценка embedMs отдельно).
 */
object ModelCost {

    /** quantFactor: Q8≈1.0, Q5≈0.85, Q4_K≈0.75, Q3≈0.65. */
    fun quantFactor(quantName: String): Float {
        val q = quantName.uppercase()
        return when {
            q.contains("Q8") -> 1.0f
            q.contains("Q6") -> 0.90f
            q.contains("Q5") -> 0.85f
            q.contains("Q4_K") || q.startsWith("Q4") || q.contains("Q4") -> 0.75f
            q.contains("Q3") -> 0.65f
            q.contains("Q2") -> 0.55f
            else -> 0.80f
        }
    }

    /**
     * Стоимость LLM относительно эталона (слои нормируются на эталон).
     * @throws IllegalArgumentException если роль не Llm.
     */
    fun cost(model: ModelArtifact, etalon: ModelArtifact): Float {
        require(model.role == ModelRole.Llm) { "cost() только для LLM" }
        require(etalon.role == ModelRole.Llm) { "эталон должен быть LLM" }
        val layerScale =
            (model.approxLayers.toFloat() / etalon.approxLayers.toFloat()).coerceAtLeast(0.5f)
        return model.paramBillions * quantFactor(model.quantName) * layerScale
    }

    /**
     * Класс weaker / etalon / stronger по порогам ±5% cost.
     */
    fun relativeClass(model: ModelArtifact, etalon: ModelArtifact): RelativeSpeedClass {
        if (model.id == etalon.id) return RelativeSpeedClass.Etalon
        if (model.role != ModelRole.Llm) return RelativeSpeedClass.EtalonTier
        val modelCost = cost(model, etalon)
        val etalonCost = cost(etalon, etalon)
        return when {
            modelCost < etalonCost * 0.95f -> RelativeSpeedClass.Weaker
            modelCost > etalonCost * 1.05f -> RelativeSpeedClass.Stronger
            else -> RelativeSpeedClass.EtalonTier
        }
    }

    /**
     * Оценка tok/s от якоря T: `T * cost(etalon) / cost(model)`.
     */
    fun estTokPerSec(model: ModelArtifact, etalon: ModelArtifact, measuredTokPerSec: Float): Float {
        val cModel = cost(model, etalon)
        val cEtalon = cost(etalon, etalon)
        require(cModel > 0f)
        return measuredTokPerSec * cEtalon / cModel
    }
}

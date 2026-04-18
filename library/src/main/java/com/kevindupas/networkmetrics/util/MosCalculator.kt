package com.kevindupas.networkmetrics.util

import kotlin.math.max
import kotlin.math.min

/**
 * ITU-T G.107 E-model simplified MOS calculation.
 * Returns a MOS value in the range [1.0, 4.5].
 */
internal object MosCalculator {

    fun calculate(latencyMs: Double, jitterMs: Double, lossPercent: Double): Double {
        if (latencyMs <= 0) return 0.0
        val effectiveLatency = latencyMs + jitterMs * 2 + 10
        val r = when {
            effectiveLatency < 160 -> 93.2 - effectiveLatency / 40.0
            else -> 93.2 - effectiveLatency / 120.0 - 10.0
        } - lossPercent * 2.5

        if (r < 0) return 1.0
        if (r > 100) return 4.5

        val mos = 1 + 0.035 * r + r * (r - 60) * (100 - r) * 7e-6
        return min(4.5, max(1.0, mos))
    }
}

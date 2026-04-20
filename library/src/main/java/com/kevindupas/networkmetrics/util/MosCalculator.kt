package com.kevindupas.networkmetrics.util

import kotlin.math.max
import kotlin.math.min

/**
 * ITU-T P.1203 MOS estimation (rescaled G.107 E-model).
 * Returns MOS ∈ [1.0, 5.0] (raw [1.0, 4.5] rescaled via 1 + (raw - 1) * (4 / 3.5)).
 */
internal object MosCalculator {

    fun calculate(latencyMs: Double, jitterMs: Double, lossPercent: Double): Double {
        if (latencyMs <= 0) return 0.0
        val effectiveLatency = latencyMs + jitterMs * 2 + 10
        val r = when {
            effectiveLatency < 160 -> 93.2 - effectiveLatency / 40.0
            else -> 93.2 - effectiveLatency / 120.0 - 10.0
        } - lossPercent * 2.5

        val raw = when {
            r < 0 -> 1.0
            r > 100 -> 4.5
            else -> 1 + 0.035 * r + r * (r - 60) * (100 - r) * 7e-6
        }
        val clamped = min(4.5, max(1.0, raw))
        val scaled = 1 + (clamped - 1) * (4.0 / 3.5)
        return min(5.0, max(1.0, scaled))
    }
}

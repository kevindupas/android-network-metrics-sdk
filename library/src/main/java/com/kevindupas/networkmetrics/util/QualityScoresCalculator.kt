package com.kevindupas.networkmetrics.util

import com.kevindupas.networkmetrics.model.QualityScores
import com.kevindupas.networkmetrics.model.ScoreEntry
import kotlin.math.max
import kotlin.math.min

/**
 * Cloudflare-inspired quality score computation.
 * Scores range 0–100, classified as Poor / Core / Excellent.
 */
internal object QualityScoresCalculator {

    fun calculate(
        downloadMbps: Double,
        latencyMs: Double,
        jitterMs: Double,
        lossPercent: Double,
    ): QualityScores = QualityScores(
        streaming = streamingScore(downloadMbps, latencyMs),
        gaming = gamingScore(latencyMs, jitterMs, lossPercent),
        rtc = rtcScore(latencyMs, jitterMs, lossPercent),
    )

    private fun streamingScore(downloadMbps: Double, latencyMs: Double): ScoreEntry {
        // 4K needs ~25 Mbps, HD ~5 Mbps, SD ~2 Mbps
        val speedScore = when {
            downloadMbps >= 25 -> 100
            downloadMbps >= 10 -> 80
            downloadMbps >= 5  -> 60
            downloadMbps >= 2  -> 40
            else -> 20
        }
        val latScore = when {
            latencyMs <= 20  -> 100
            latencyMs <= 50  -> 80
            latencyMs <= 100 -> 60
            latencyMs <= 200 -> 40
            else -> 20
        }
        val score = clamp((speedScore * 0.7 + latScore * 0.3).toInt())
        return ScoreEntry(score, label(score))
    }

    private fun gamingScore(latencyMs: Double, jitterMs: Double, lossPercent: Double): ScoreEntry {
        val latScore = when {
            latencyMs <= 20  -> 100
            latencyMs <= 40  -> 80
            latencyMs <= 80  -> 60
            latencyMs <= 150 -> 40
            else -> 20
        }
        val jitterScore = when {
            jitterMs <= 5  -> 100
            jitterMs <= 15 -> 80
            jitterMs <= 30 -> 60
            jitterMs <= 60 -> 40
            else -> 20
        }
        val lossScore = when {
            lossPercent <= 0.5 -> 100
            lossPercent <= 1   -> 80
            lossPercent <= 3   -> 50
            lossPercent <= 5   -> 30
            else -> 10
        }
        val score = clamp((latScore * 0.5 + jitterScore * 0.3 + lossScore * 0.2).toInt())
        return ScoreEntry(score, label(score))
    }

    private fun rtcScore(latencyMs: Double, jitterMs: Double, lossPercent: Double): ScoreEntry {
        val latScore = when {
            latencyMs <= 30  -> 100
            latencyMs <= 60  -> 80
            latencyMs <= 100 -> 60
            latencyMs <= 200 -> 40
            else -> 20
        }
        val jitterScore = when {
            jitterMs <= 5  -> 100
            jitterMs <= 20 -> 80
            jitterMs <= 40 -> 60
            jitterMs <= 80 -> 40
            else -> 20
        }
        val lossScore = when {
            lossPercent <= 0.5 -> 100
            lossPercent <= 2   -> 80
            lossPercent <= 5   -> 50
            lossPercent <= 10  -> 30
            else -> 10
        }
        val score = clamp((latScore * 0.4 + jitterScore * 0.35 + lossScore * 0.25).toInt())
        return ScoreEntry(score, label(score))
    }

    private fun label(score: Int): String = when {
        score >= 80 -> "Excellent"
        score >= 50 -> "Core"
        else -> "Poor"
    }

    private fun clamp(v: Int) = max(0, min(100, v))
}

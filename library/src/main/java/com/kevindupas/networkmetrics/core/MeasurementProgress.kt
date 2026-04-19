package com.kevindupas.networkmetrics.core

/**
 * Emitted after each measurement phase completes.
 * result contains the phase JSON (null if phase was skipped or failed).
 */
data class MeasurementProgress(
    val phase: Phase,
    val result: Any?,
) {
    enum class Phase {
        SPEED,
        PACKET_LOSS,
        STREAMING,
        SOCIAL_LATENCY,
        DNS,
        WEB_BROWSING,
        RADIO,
        NETWORK,
        DEVICE,
        GEO,
        COMPLETE,
    }
}

fun interface ProgressCallback {
    fun onProgress(progress: MeasurementProgress)
}

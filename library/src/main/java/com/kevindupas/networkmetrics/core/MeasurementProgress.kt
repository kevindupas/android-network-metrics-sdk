package com.kevindupas.networkmetrics.core

/**
 * Emitted during and after each measurement phase.
 * result contains phase-specific payload:
 *  - SPEED_DOWNLOAD_PROGRESS / SPEED_UPLOAD_PROGRESS → current Mbps (Double)
 *  - End-of-phase events → phase JSON (or null if skipped/failed)
 */
data class MeasurementProgress(
    val phase: Phase,
    val result: Any?,
) {
    enum class Phase {
        SPEED_DOWNLOAD_PROGRESS,
        SPEED_UPLOAD_PROGRESS,
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

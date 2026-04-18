package com.kevindupas.networkmetrics.core

data class NetworkMetricsConfig(
    // Required
    val backendUrl: String,

    // Packet loss server (optional — skipped if blank)
    val udpHost: String = "",
    val udpPort: Int = 5005,
    val tcpPort: Int = 8230,

    // Scheduling — WorkManager minimum is 15 min
    val intervalMs: Long = 30 * 60_000L,

    // Feature flags
    val enableSpeed: Boolean = true,
    val enablePacketLoss: Boolean = true,
    val enableStreaming: Boolean = true,
    val enableSocialLatency: Boolean = true,

    // Speed test tuning — reduce for metered/prepaid networks
    val speedDownloadDurationMs: Long = 8_000L,   // download window
    val speedUploadDurationMs: Long = 6_000L,     // upload window
    val speedThreadCount: Int = 3,                // parallel streams

    // Notification (kept for API compatibility — unused since WorkManager migration)
    val notificationTitle: String = "Network Metrics",
    val notificationText: String = "Collecting network quality data…",
    val notificationIconRes: Int = android.R.drawable.stat_sys_download,

    // Security
    val authHeader: String? = null,
)

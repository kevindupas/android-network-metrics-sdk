package com.kevindupas.networkmetrics.core

import com.kevindupas.networkmetrics.measurement.WebTarget

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
    val enableDns: Boolean = true,
    val enableWebBrowsing: Boolean = true,
    val enableNeighboringCells: Boolean = true,

    // Speed test tuning — reduce for metered/prepaid networks
    val speedDownloadDurationMs: Long = 8_000L,
    val speedUploadDurationMs: Long = 6_000L,
    val speedThreadCount: Int = 3,

    // Web browsing targets — override via remoteConfigUrl or set directly
    // Default targets cover common global services (regulators can override per country)
    val webTargets: List<WebTarget> = listOf(
        WebTarget("Google", "https://www.google.com/"),
        WebTarget("WhatsApp", "https://web.whatsapp.com/"),
        WebTarget("YouTube", "https://www.youtube.com/"),
        WebTarget("Facebook", "https://www.facebook.com/"),
    ),

    // Remote config — if set, SDK fetches targets JSON from this URL each cycle (cached 1h)
    // Expected format: { "targets": [{ "name": "...", "url": "..." }] }
    val remoteConfigUrl: String? = null,

    // Notification (kept for API compatibility — unused since WorkManager migration)
    val notificationTitle: String = "Network Metrics",
    val notificationText: String = "Collecting network quality data…",
    val notificationIconRes: Int = android.R.drawable.stat_sys_download,

    // Security
    val authHeader: String? = null,
)

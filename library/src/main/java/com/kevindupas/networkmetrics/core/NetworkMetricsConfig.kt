package com.kevindupas.networkmetrics.core

/**
 * SDK configuration. Pass to [NetworkMetricsSdk.init].
 *
 * @param backendUrl HTTPS endpoint that receives POST with [NetworkMetricsRecord] JSON.
 * @param udpHost    Host of the UDP/TCP echo server for packet loss measurement.
 * @param udpPort    UDP port (default 5005).
 * @param tcpPort    TCP fallback port (default 8230).
 * @param intervalMs Measurement cycle interval in milliseconds (default 30 min).
 * @param enableSpeed          Include speed test in each cycle.
 * @param enablePacketLoss     Include UDP packet loss test.
 * @param enableStreaming      Include HLS streaming download test.
 * @param enableSocialLatency  Include social media TTFB probes.
 * @param notificationTitle    Title shown in the persistent foreground notification.
 * @param notificationText     Body text of the persistent foreground notification.
 * @param notificationIconRes  Drawable resource ID for the notification icon.
 * @param authHeader           Optional Authorization header value sent with every POST.
 */
data class NetworkMetricsConfig(
    val backendUrl: String,
    val udpHost: String = "",
    val udpPort: Int = 5005,
    val tcpPort: Int = 8230,
    val intervalMs: Long = 30 * 60 * 1000L,
    val enableSpeed: Boolean = true,
    val enablePacketLoss: Boolean = true,
    val enableStreaming: Boolean = true,
    val enableSocialLatency: Boolean = true,
    val notificationTitle: String = "Network Metrics",
    val notificationText: String = "Collecting network quality data…",
    val notificationIconRes: Int = android.R.drawable.stat_sys_download,
    val authHeader: String? = null,
)

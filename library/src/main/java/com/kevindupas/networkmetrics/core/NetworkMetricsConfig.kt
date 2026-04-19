package com.kevindupas.networkmetrics.core

import com.kevindupas.networkmetrics.measurement.DEFAULT_SOCIAL_TARGETS
import com.kevindupas.networkmetrics.measurement.SocialTarget
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
    val webTargets: List<WebTarget> = DEFAULT_WEB_TARGETS,

    // Social latency targets — override via remoteConfigUrl or set directly
    val socialTargets: List<SocialTarget> = DEFAULT_SOCIAL_TARGETS,

    // HLS streaming URL for streaming measurement
    val streamingUrl: String? = null,

    // Remote config — fetched each cycle (cached 1h)
    // Format: { "webTargets": [...], "socialTargets": [...], "streamingUrl": "..." }
    val remoteConfigUrl: String? = null,

    // Notification (kept for API compatibility — unused since WorkManager migration)
    val notificationTitle: String = "Network Metrics",
    val notificationText: String = "Collecting network quality data…",
    val notificationIconRes: Int = android.R.drawable.stat_sys_download,

    // Security
    val authHeader: String? = null,
) {
    companion object {
        @JvmField
        val DEFAULT_WEB_TARGETS: List<WebTarget> = listOf(
            WebTarget("Google", "https://www.google.com/"),
            WebTarget("WhatsApp", "https://web.whatsapp.com/"),
            WebTarget("YouTube", "https://www.youtube.com/"),
            WebTarget("Facebook", "https://www.facebook.com/"),
        )

        /** Java-friendly builder — only backendUrl is required, everything else has sensible defaults. */
        @JvmStatic
        fun builder(backendUrl: String): Builder = Builder(backendUrl)
    }

    class Builder(private val backendUrl: String) {
        private var udpHost = ""
        private var udpPort = 5005
        private var tcpPort = 8230
        private var intervalMs = 30 * 60_000L
        private var enableSpeed = true
        private var enablePacketLoss = true
        private var enableStreaming = true
        private var enableSocialLatency = true
        private var enableDns = true
        private var enableWebBrowsing = true
        private var enableNeighboringCells = true
        private var speedDownloadDurationMs = 8_000L
        private var speedUploadDurationMs = 6_000L
        private var speedThreadCount = 3
        private var webTargets: List<WebTarget> = DEFAULT_WEB_TARGETS
        private var socialTargets: List<SocialTarget> = DEFAULT_SOCIAL_TARGETS
        private var streamingUrl: String? = null
        private var remoteConfigUrl: String? = null
        private var authHeader: String? = null

        fun udpHost(v: String)                   = apply { udpHost = v }
        fun udpPort(v: Int)                      = apply { udpPort = v }
        fun tcpPort(v: Int)                      = apply { tcpPort = v }
        fun intervalMs(v: Long)                  = apply { intervalMs = v }
        fun enableSpeed(v: Boolean)              = apply { enableSpeed = v }
        fun enablePacketLoss(v: Boolean)         = apply { enablePacketLoss = v }
        fun enableStreaming(v: Boolean)          = apply { enableStreaming = v }
        fun enableSocialLatency(v: Boolean)      = apply { enableSocialLatency = v }
        fun enableDns(v: Boolean)                = apply { enableDns = v }
        fun enableWebBrowsing(v: Boolean)        = apply { enableWebBrowsing = v }
        fun enableNeighboringCells(v: Boolean)   = apply { enableNeighboringCells = v }
        fun speedDownloadDurationMs(v: Long)     = apply { speedDownloadDurationMs = v }
        fun speedUploadDurationMs(v: Long)       = apply { speedUploadDurationMs = v }
        fun speedThreadCount(v: Int)             = apply { speedThreadCount = v }
        fun webTargets(v: List<WebTarget>)       = apply { webTargets = v }
        fun socialTargets(v: List<SocialTarget>) = apply { socialTargets = v }
        fun streamingUrl(v: String?)             = apply { streamingUrl = v }
        fun remoteConfigUrl(v: String?)          = apply { remoteConfigUrl = v }
        fun authHeader(v: String?)               = apply { authHeader = v }

        fun build() = NetworkMetricsConfig(
            backendUrl = backendUrl,
            udpHost = udpHost, udpPort = udpPort, tcpPort = tcpPort,
            intervalMs = intervalMs,
            enableSpeed = enableSpeed, enablePacketLoss = enablePacketLoss,
            enableStreaming = enableStreaming, enableSocialLatency = enableSocialLatency,
            enableDns = enableDns, enableWebBrowsing = enableWebBrowsing,
            enableNeighboringCells = enableNeighboringCells,
            speedDownloadDurationMs = speedDownloadDurationMs,
            speedUploadDurationMs = speedUploadDurationMs,
            speedThreadCount = speedThreadCount,
            webTargets = webTargets,
            socialTargets = socialTargets,
            streamingUrl = streamingUrl,
            remoteConfigUrl = remoteConfigUrl,
            authHeader = authHeader,
        )
    }
}

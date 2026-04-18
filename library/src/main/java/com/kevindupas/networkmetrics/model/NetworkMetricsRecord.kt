package com.kevindupas.networkmetrics.model

/**
 * Full measurement record produced by one SDK measurement cycle.
 * Transmitted as-is to the configured backend endpoint via POST.
 */
data class NetworkMetricsRecord(
    val testId: String,
    val timestamp: String,
    val sdkVersion: String,
    val speed: SpeedResult?,
    val udpPacketLoss: UdpResult?,
    val streaming: StreamingResult?,
    val socialLatency: List<SocialLatencyResult>,
    val radio: RadioResult?,
    val network: NetworkResult,
    val geo: GeoResult?,
    val device: DeviceResult,
    val scores: QualityScores?,
    val mos: Double?,
)

data class SpeedResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMs: Double,
    val jitterMs: Double,
    val loadedLatencyMs: Double?,
    val serverName: String?,
    val serverLocation: String?,
)

data class UdpResult(
    val sent: Int,
    val received: Int,
    val lossPercent: Double,
    val method: String,
)

data class StreamingResult(
    val startTimeMs: Long?,
    val rebufferCount: Int,
    val rebufferDurationMs: Long,
    val avgBitrateKbps: Int?,
    val durationMeasuredMs: Long,
    val bytesDownloaded: Long,
    val error: String?,
)

data class SocialLatencyResult(
    val service: String,
    val ttfbMs: Long?,
    val reachable: Boolean,
)

data class RadioResult(
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val rssi: Int?,
    val cqi: Int?,
    val ci: Long?,
    val pci: Int?,
    val tac: Int?,
    val earfcn: Int?,
    val isNrAvailable: Boolean,
    val networkGeneration: String,
    val signalStrengthLevel: String,
    val technology: String,
)

data class NetworkResult(
    val connectionType: String,
    val ip: String?,
    val asn: String?,
    val isp: String?,
    val city: String?,
    val country: String?,
    val countryCode: String?,
    val cfColo: String?,
    val cfServerCity: String?,
    val isLocallyServed: Boolean?,
    val ipVersion: String?,
)

data class GeoResult(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val altitude: Double?,
    val speed: Float?,
    val bearing: Float?,
    val provider: String?,
)

data class DeviceResult(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val simOperatorName: String?,
    val mcc: String?,
    val mnc: String?,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
)

data class QualityScores(
    val streaming: ScoreEntry?,
    val gaming: ScoreEntry?,
    val rtc: ScoreEntry?,
)

data class ScoreEntry(
    val score: Int,
    val label: String,
)

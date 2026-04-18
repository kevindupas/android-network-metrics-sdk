package com.kevindupas.networkmetrics.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.kevindupas.networkmetrics.core.ConfigHolder
import com.kevindupas.networkmetrics.measurement.DeviceMeasurement
import com.kevindupas.networkmetrics.measurement.NetworkContextMeasurement
import com.kevindupas.networkmetrics.measurement.PacketLossMeasurement
import com.kevindupas.networkmetrics.measurement.RadioMeasurement
import com.kevindupas.networkmetrics.measurement.SocialLatencyMeasurement
import com.kevindupas.networkmetrics.measurement.SpeedMeasurement
import com.kevindupas.networkmetrics.measurement.StreamingMeasurement
import com.kevindupas.networkmetrics.model.GeoResult
import com.kevindupas.networkmetrics.model.NetworkMetricsRecord
import com.kevindupas.networkmetrics.util.MosCalculator
import com.kevindupas.networkmetrics.util.QualityScoresCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "NetworkMetricsWorker"

internal class NetworkMetricsWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val config = ConfigHolder.config ?: run {
            Log.e(TAG, "Config missing — skipping cycle")
            return Result.failure()
        }

        Log.d(TAG, "Starting measurement cycle")

        return try {
            val geo = getLastLocation()

            val (speed, social, streaming) = coroutineScope {
                val s  = if (config.enableSpeed) async {
                SpeedMeasurement(
                    downloadDurationMs = config.speedDownloadDurationMs,
                    uploadDurationMs   = config.speedUploadDurationMs,
                    threadCount        = config.speedThreadCount,
                ).measure()
            } else null
                val sl = if (config.enableSocialLatency) async { SocialLatencyMeasurement().measure() } else null
                val st = if (config.enableStreaming) async { StreamingMeasurement().measure() } else null
                Triple(s?.await(), sl?.await() ?: emptyList(), st?.await())
            }

            val udp = if (config.enablePacketLoss && config.udpHost.isNotBlank()) {
                PacketLossMeasurement(config.udpHost, config.udpPort, config.tcpPort).measure()
            } else null

            val radio   = RadioMeasurement(appContext).measure()
            val network = NetworkContextMeasurement().measure()
            val device  = DeviceMeasurement(appContext).measure()

            val loss = udp?.lossPercent ?: 0.0
            val mos  = speed?.let { MosCalculator.calculate(it.latencyMs, it.jitterMs, loss) }
            val scores = speed?.let {
                QualityScoresCalculator.calculate(it.downloadMbps, it.latencyMs, it.jitterMs, loss)
            }

            val record = NetworkMetricsRecord(
                testId = UUID.randomUUID().toString(),
                timestamp = iso8601(),
                sdkVersion = "1.0.0",
                speed = speed,
                udpPacketLoss = udp,
                streaming = streaming,
                socialLatency = social,
                radio = radio,
                network = network,
                geo = geo,
                device = device,
                scores = scores,
                mos = mos,
            )

            postRecord(record, config.backendUrl, config.authHeader)
            Log.d(TAG, "Cycle complete — testId=${record.testId}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cycle failed: ${e.message}")
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): GeoResult? = suspendCancellableCoroutine { cont ->
        LocationServices.getFusedLocationProviderClient(appContext).lastLocation
            .addOnSuccessListener { loc ->
                cont.resume(loc?.let {
                    GeoResult(
                        lat = it.latitude,
                        lon = it.longitude,
                        accuracy = it.accuracy,
                        altitude = if (it.hasAltitude()) it.altitude else null,
                        speed = if (it.hasSpeed()) it.speed else null,
                        bearing = if (it.hasBearing()) it.bearing else null,
                        provider = it.provider,
                    )
                })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    private suspend fun postRecord(record: NetworkMetricsRecord, url: String, authHeader: String?) {
        withContext(Dispatchers.IO) {
            try {
                val body = recordToJson(record).toString()
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(url).post(body)
                    .also { b -> authHeader?.let { b.header("Authorization", it) } }
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) Log.d(TAG, "Posted — testId=${record.testId}")
                    else Log.w(TAG, "Backend ${resp.code} — testId=${record.testId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post: ${e.message}")
            }
        }
    }

    private fun recordToJson(r: NetworkMetricsRecord): JSONObject = JSONObject().apply {
        put("testId", r.testId)
        put("timestamp", r.timestamp)
        put("sdkVersion", r.sdkVersion)
        put("speed", r.speed?.let { s -> JSONObject().apply {
            put("downloadMbps", s.downloadMbps); put("uploadMbps", s.uploadMbps)
            put("latencyMs", s.latencyMs); put("jitterMs", s.jitterMs)
            put("loadedLatencyMs", s.loadedLatencyMs)
            put("serverName", s.serverName); put("serverLocation", s.serverLocation)
        }})
        put("udpPacketLoss", r.udpPacketLoss?.let { u -> JSONObject().apply {
            put("sent", u.sent); put("received", u.received)
            put("lossPercent", u.lossPercent); put("method", u.method)
        }})
        put("streaming", r.streaming?.let { st -> JSONObject().apply {
            put("startTimeMs", st.startTimeMs); put("rebufferCount", st.rebufferCount)
            put("rebufferDurationMs", st.rebufferDurationMs); put("avgBitrateKbps", st.avgBitrateKbps)
            put("durationMeasuredMs", st.durationMeasuredMs); put("bytesDownloaded", st.bytesDownloaded)
            put("error", st.error)
        }})
        put("socialLatency", JSONArray(r.socialLatency.map { sl -> JSONObject().apply {
            put("service", sl.service); put("ttfbMs", sl.ttfbMs); put("reachable", sl.reachable)
        }}))
        put("radio", r.radio?.let { ra -> JSONObject().apply {
            put("rsrp", ra.rsrp); put("rsrq", ra.rsrq); put("sinr", ra.sinr); put("rssi", ra.rssi)
            put("cqi", ra.cqi); put("ci", ra.ci); put("pci", ra.pci)
            put("tac", ra.tac); put("lac", ra.lac); put("earfcn", ra.earfcn)
            put("bandwidth", ra.bandwidth); put("psc", ra.psc)
            put("isNrAvailable", ra.isNrAvailable); put("networkGeneration", ra.networkGeneration)
            put("signalStrengthLevel", ra.signalStrengthLevel); put("technology", ra.technology)
        }})
        put("network", JSONObject().apply {
            put("connectionType", r.network.connectionType); put("ip", r.network.ip)
            put("asn", r.network.asn); put("isp", r.network.isp)
            put("city", r.network.city); put("country", r.network.country)
            put("countryCode", r.network.countryCode); put("cfColo", r.network.cfColo)
            put("cfServerCity", r.network.cfServerCity)
            put("isLocallyServed", r.network.isLocallyServed); put("ipVersion", r.network.ipVersion)
        })
        put("geo", r.geo?.let { g -> JSONObject().apply {
            put("lat", g.lat); put("lon", g.lon); put("accuracy", g.accuracy)
            put("altitude", g.altitude); put("speed", g.speed)
            put("bearing", g.bearing); put("provider", g.provider)
        }})
        put("device", JSONObject().apply {
            put("manufacturer", r.device.manufacturer); put("model", r.device.model)
            put("osVersion", r.device.osVersion); put("sdkInt", r.device.sdkInt)
            put("simOperatorName", r.device.simOperatorName)
            put("mcc", r.device.mcc); put("mnc", r.device.mnc)
            put("batteryLevel", r.device.batteryLevel); put("isCharging", r.device.isCharging)
            put("ramUsedMb", r.device.ramUsedMb); put("cpuLoadPercent", r.device.cpuLoadPercent)
        })
        put("scores", r.scores?.let { sc -> JSONObject().apply {
            put("streaming", sc.streaming?.let { JSONObject().apply { put("score", it.score); put("label", it.label) }})
            put("gaming",    sc.gaming?.let    { JSONObject().apply { put("score", it.score); put("label", it.label) }})
            put("rtc",       sc.rtc?.let       { JSONObject().apply { put("score", it.score); put("label", it.label) }})
        }})
        put("mos", r.mos)
    }

    private fun iso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
}

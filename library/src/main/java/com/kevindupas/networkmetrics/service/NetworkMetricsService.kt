package com.kevindupas.networkmetrics.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private const val TAG = "NetworkMetricsService"
private const val CHANNEL_ID = "network_metrics_channel"
private const val NOTIFICATION_ID = 1001

class NetworkMetricsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var measurementJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: GeoResult? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                lastLocation = GeoResult(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracy = loc.accuracy,
                    altitude = if (loc.hasAltitude()) loc.altitude else null,
                    speed = if (loc.hasSpeed()) loc.speed else null,
                    bearing = if (loc.hasBearing()) loc.bearing else null,
                    provider = loc.provider,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = ConfigHolder.config ?: run {
            Log.e(TAG, "SDK not initialised — call NetworkMetricsSdk.init() before start()")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(config.notificationTitle, config.notificationText))
        startLocationUpdates()
        startMeasurementLoop(config.intervalMs)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Location ---

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .build()
        fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // --- Measurement loop ---

    private fun startMeasurementLoop(intervalMs: Long) {
        measurementJob?.cancel()
        measurementJob = scope.launch {
            while (true) {
                runCycle()
                delay(intervalMs)
            }
        }
    }

    private suspend fun runCycle() {
        val config = ConfigHolder.config ?: return
        Log.d(TAG, "Starting measurement cycle")

        try {
            val speedDeferred    = if (config.enableSpeed) scope.async { SpeedMeasurement().measure() } else null
            val socialDeferred   = if (config.enableSocialLatency) scope.async { SocialLatencyMeasurement().measure() } else null
            val streamingDeferred = if (config.enableStreaming) scope.async { StreamingMeasurement().measure() } else null

            val speed    = speedDeferred?.await()
            val social   = socialDeferred?.await() ?: emptyList()
            val streaming = streamingDeferred?.await()

            val udp = if (config.enablePacketLoss && config.udpHost.isNotBlank()) {
                PacketLossMeasurement(config.udpHost, config.udpPort, config.tcpPort).measure()
            } else null

            val radio   = RadioMeasurement(applicationContext).measure()
            val network = NetworkContextMeasurement().measure()
            val device  = DeviceMeasurement(applicationContext).measure()
            val geo     = lastLocation

            val loss = udp?.lossPercent ?: 0.0
            val mos = speed?.let { MosCalculator.calculate(it.latencyMs, it.jitterMs, loss) }
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
        } catch (e: Exception) {
            Log.e(TAG, "Measurement cycle failed: ${e.message}")
        }
    }

    // --- Backend POST ---

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun postRecord(record: NetworkMetricsRecord, url: String, authHeader: String?) {
        withContext(Dispatchers.IO) {
            try {
                val json = recordToJson(record).toString()
                val body = json.toRequestBody("application/json".toMediaType())
                val reqBuilder = Request.Builder().url(url).post(body)
                authHeader?.let { reqBuilder.header("Authorization", it) }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "Record posted — testId=${record.testId}")
                    } else {
                        Log.w(TAG, "Backend returned ${resp.code} for testId=${record.testId}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post record: ${e.message}")
            }
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Metrics",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background network quality measurement"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(ConfigHolder.config?.notificationIconRes ?: android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    // --- Serialisation ---

    private fun recordToJson(r: NetworkMetricsRecord): JSONObject = JSONObject().apply {
        put("testId", r.testId)
        put("timestamp", r.timestamp)
        put("sdkVersion", r.sdkVersion)
        put("speed", r.speed?.let { s -> JSONObject().apply {
            put("downloadMbps", s.downloadMbps)
            put("uploadMbps", s.uploadMbps)
            put("latencyMs", s.latencyMs)
            put("jitterMs", s.jitterMs)
            put("loadedLatencyMs", s.loadedLatencyMs)
            put("serverName", s.serverName)
            put("serverLocation", s.serverLocation)
        }})
        put("udpPacketLoss", r.udpPacketLoss?.let { u -> JSONObject().apply {
            put("sent", u.sent)
            put("received", u.received)
            put("lossPercent", u.lossPercent)
            put("method", u.method)
        }})
        put("streaming", r.streaming?.let { st -> JSONObject().apply {
            put("startTimeMs", st.startTimeMs)
            put("rebufferCount", st.rebufferCount)
            put("rebufferDurationMs", st.rebufferDurationMs)
            put("avgBitrateKbps", st.avgBitrateKbps)
            put("durationMeasuredMs", st.durationMeasuredMs)
            put("bytesDownloaded", st.bytesDownloaded)
            put("error", st.error)
        }})
        put("socialLatency", JSONArray(r.socialLatency.map { sl -> JSONObject().apply {
            put("service", sl.service)
            put("ttfbMs", sl.ttfbMs)
            put("reachable", sl.reachable)
        }}))
        put("radio", r.radio?.let { ra -> JSONObject().apply {
            put("rsrp", ra.rsrp)
            put("rsrq", ra.rsrq)
            put("sinr", ra.sinr)
            put("rssi", ra.rssi)
            put("cqi", ra.cqi)
            put("ci", ra.ci)
            put("pci", ra.pci)
            put("tac", ra.tac)
            put("earfcn", ra.earfcn)
            put("isNrAvailable", ra.isNrAvailable)
            put("networkGeneration", ra.networkGeneration)
            put("signalStrengthLevel", ra.signalStrengthLevel)
            put("technology", ra.technology)
        }})
        put("network", JSONObject().apply {
            put("connectionType", r.network.connectionType)
            put("ip", r.network.ip)
            put("asn", r.network.asn)
            put("isp", r.network.isp)
            put("city", r.network.city)
            put("country", r.network.country)
            put("countryCode", r.network.countryCode)
            put("cfColo", r.network.cfColo)
            put("cfServerCity", r.network.cfServerCity)
            put("isLocallyServed", r.network.isLocallyServed)
            put("ipVersion", r.network.ipVersion)
        })
        put("geo", r.geo?.let { g -> JSONObject().apply {
            put("lat", g.lat)
            put("lon", g.lon)
            put("accuracy", g.accuracy)
            put("altitude", g.altitude)
            put("speed", g.speed)
            put("bearing", g.bearing)
            put("provider", g.provider)
        }})
        put("device", JSONObject().apply {
            put("manufacturer", r.device.manufacturer)
            put("model", r.device.model)
            put("osVersion", r.device.osVersion)
            put("sdkInt", r.device.sdkInt)
            put("simOperatorName", r.device.simOperatorName)
            put("mcc", r.device.mcc)
            put("mnc", r.device.mnc)
            put("batteryLevel", r.device.batteryLevel)
            put("isCharging", r.device.isCharging)
        })
        put("scores", r.scores?.let { sc -> JSONObject().apply {
            put("streaming", sc.streaming?.let { JSONObject().apply { put("score", it.score); put("label", it.label) }})
            put("gaming", sc.gaming?.let { JSONObject().apply { put("score", it.score); put("label", it.label) }})
            put("rtc", sc.rtc?.let { JSONObject().apply { put("score", it.score); put("label", it.label) }})
        }})
        put("mos", r.mos)
    }

    private fun iso8601(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
}

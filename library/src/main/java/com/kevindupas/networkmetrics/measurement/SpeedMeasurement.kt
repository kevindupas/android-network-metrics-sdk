package com.kevindupas.networkmetrics.measurement

import android.util.Log
import com.kevindupas.networkmetrics.model.SpeedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "SpeedMeasurement"
private const val CF_BASE = "https://speed.cloudflare.com"
private const val PING_COUNT = 8
private const val DL_CHUNK_BYTES = 1 * 1024 * 1024
private const val UL_CHUNK_BYTES = 256 * 1024
private const val PROGRESS_INTERVAL_MS = 400L

internal class SpeedMeasurement(
    private val downloadDurationMs: Long = 8_000L,
    private val uploadDurationMs: Long = 6_000L,
    private val threadCount: Int = 3,
    private val onDownloadProgress: ((Double) -> Unit)? = null,
    private val onUploadProgress: ((Double) -> Unit)? = null,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun measure(): SpeedResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting latency measurement...")
            val (latencyMs, jitterMs) = measureLatencyAndJitter()
            Log.d(TAG, "Latency: ${latencyMs}ms  Jitter: ${jitterMs}ms")

            Log.d(TAG, "Starting download (${downloadDurationMs}ms window, ${threadCount} threads)...")
            val downloadMbps = measureDownload()
            Log.d(TAG, "Download: ${String.format("%.2f", downloadMbps)} Mbps")

            Log.d(TAG, "Starting upload (${uploadDurationMs}ms window)...")
            val uploadMbps = measureUpload()
            Log.d(TAG, "Upload: ${String.format("%.2f", uploadMbps)} Mbps")

            val loadedLatency = measureLoadedLatency()
            val colo = fetchColoLocation()

            SpeedResult(
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                loadedLatencyMs = loadedLatency,
                serverName = "Cloudflare",
                serverLocation = colo,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Speed measurement failed: ${e.message}")
            null
        }
    }

    private fun measureLatencyAndJitter(): Pair<Double, Double> {
        val pings = mutableListOf<Long>()
        repeat(PING_COUNT) {
            val start = System.currentTimeMillis()
            try {
                client.newCall(
                    Request.Builder().url("$CF_BASE/__down?bytes=0").build()
                ).execute().use {}
                pings.add(System.currentTimeMillis() - start)
            } catch (_: Exception) {}
        }
        if (pings.isEmpty()) return Pair(0.0, 0.0)
        val latency = pings.average()
        val jitter = if (pings.size < 2) 0.0
            else (1 until pings.size).map { abs(pings[it] - pings[it - 1]).toDouble() }.average()
        return Pair(latency, jitter)
    }

    private fun measureDownload(): Double {
        val totalBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        val deadline = startTime + downloadDurationMs

        // Progress-emitter thread — samples throughput every PROGRESS_INTERVAL_MS
        val progressThread = onDownloadProgress?.let { cb ->
            Thread {
                var lastBytes = 0L
                var lastTime = startTime
                while (System.currentTimeMillis() < deadline) {
                    try { Thread.sleep(PROGRESS_INTERVAL_MS) } catch (_: InterruptedException) { break }
                    val now = System.currentTimeMillis()
                    val bytes = totalBytes.get()
                    val deltaBytes = bytes - lastBytes
                    val deltaMs = max(now - lastTime, 1L)
                    val instMbps = deltaBytes * 8.0 / deltaMs / 1000.0
                    try { cb(instMbps) } catch (_: Exception) {}
                    lastBytes = bytes
                    lastTime = now
                }
            }.also { it.start() }
        }

        val threads = (1..threadCount).map {
            Thread {
                while (System.currentTimeMillis() < deadline) {
                    try {
                        client.newCall(
                            Request.Builder()
                                .url("$CF_BASE/__down?bytes=$DL_CHUNK_BYTES")
                                .build()
                        ).execute().use { resp ->
                            val source = resp.body?.source() ?: return@use
                            val buf = ByteArray(8192)
                            var read: Int
                            while (source.read(buf).also { read = it } != -1) {
                                totalBytes.addAndGet(read.toLong())
                                if (System.currentTimeMillis() >= deadline) break
                            }
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }.also { it.start() }
        }

        threads.forEach { it.join(downloadDurationMs + 3000) }
        progressThread?.interrupt()
        progressThread?.join(500)
        val elapsed = max(System.currentTimeMillis() - startTime, 1L)
        return totalBytes.get() * 8.0 / elapsed / 1000.0
    }

    private fun measureUpload(): Double {
        val payload = ByteArray(UL_CHUNK_BYTES) { (it % 256).toByte() }
        val totalBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        val deadline = startTime + uploadDurationMs

        val progressThread = onUploadProgress?.let { cb ->
            Thread {
                var lastBytes = 0L
                var lastTime = startTime
                while (System.currentTimeMillis() < deadline) {
                    try { Thread.sleep(PROGRESS_INTERVAL_MS) } catch (_: InterruptedException) { break }
                    val now = System.currentTimeMillis()
                    val bytes = totalBytes.get()
                    val deltaBytes = bytes - lastBytes
                    val deltaMs = max(now - lastTime, 1L)
                    val instMbps = deltaBytes * 8.0 / deltaMs / 1000.0
                    try { cb(instMbps) } catch (_: Exception) {}
                    lastBytes = bytes
                    lastTime = now
                }
            }.also { it.start() }
        }

        val threads = (1..threadCount).map {
            Thread {
                while (System.currentTimeMillis() < deadline) {
                    try {
                        client.newCall(
                            Request.Builder()
                                .url("$CF_BASE/__up")
                                .post(payload.toRequestBody("application/octet-stream".toMediaType()))
                                .build()
                        ).execute().use { resp ->
                            if (resp.isSuccessful) {
                                totalBytes.addAndGet(UL_CHUNK_BYTES.toLong())
                            }
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }.also { it.start() }
        }

        threads.forEach { it.join(uploadDurationMs + 3000) }
        progressThread?.interrupt()
        progressThread?.join(500)
        val elapsed = max(System.currentTimeMillis() - startTime, 1L)
        return totalBytes.get() * 8.0 / elapsed / 1000.0
    }

    private fun measureLoadedLatency(): Double? {
        return try {
            val start = System.currentTimeMillis()
            client.newCall(
                Request.Builder().url("$CF_BASE/__down?bytes=102400").build()
            ).execute().use {}
            (System.currentTimeMillis() - start).toDouble()
        } catch (_: Exception) { null }
    }

    private fun fetchColoLocation(): String? {
        return try {
            client.newCall(
                Request.Builder().url("https://1.1.1.1/cdn-cgi/trace").build()
            ).execute().use { resp ->
                resp.body?.string()
                    ?.split("\n")
                    ?.firstOrNull { it.startsWith("colo=") }
                    ?.removePrefix("colo=")
                    ?.trim()
            }
        } catch (_: Exception) { null }
    }
}

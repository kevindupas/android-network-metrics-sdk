package com.kevindupas.networkmetrics.measurement

import android.util.Log
import com.kevindupas.networkmetrics.model.SpeedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "SpeedMeasurement"
private const val CF_BASE = "https://speed.cloudflare.com"
private const val PING_COUNT = 12
private const val PING_WARMUP = 2
private const val DL_CHUNK_BYTES = 25 * 1024 * 1024
private const val UL_CHUNK_BYTES = 1 * 1024 * 1024
private const val PROGRESS_INTERVAL_MS = 400L

internal class SpeedMeasurement(
    private val downloadDurationMs: Long = 8_000L,
    private val uploadDurationMs: Long = 6_000L,
    private val threadCount: Int = 3,
    private val onDownloadProgress: ((Double) -> Unit)? = null,
    private val onUploadProgress: ((Double) -> Unit)? = null,
) {

    // Shared pool + HTTP/2 → connection reuse across pings and parallel download streams.
    // Without this, every .execute() reopens TCP+TLS → pings balloon on cellular (radio wakeup + handshake).
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()

    suspend fun measure(): SpeedResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting latency measurement...")
            val (latencyMs, jitterMs) = measureLatencyAndJitter()
            Log.d(TAG, "Latency: ${latencyMs}ms  Jitter: ${jitterMs}ms")

            Log.d(TAG, "Starting download (${downloadDurationMs}ms window, ${threadCount} threads)...")
            val (downloadMbps, loadedLatency) = measureDownload()
            Log.d(TAG, "Download: ${String.format("%.2f", downloadMbps)} Mbps  Loaded latency: ${loadedLatency}ms")

            Log.d(TAG, "Starting upload (${uploadDurationMs}ms window)...")
            val uploadMbps = measureUpload()
            Log.d(TAG, "Upload: ${String.format("%.2f", uploadMbps)} Mbps")
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
        val total = PING_WARMUP + PING_COUNT
        repeat(total) { i ->
            val start = System.nanoTime()
            try {
                client.newCall(
                    Request.Builder().url("$CF_BASE/__down?bytes=0").build()
                ).execute().use { resp ->
                    // Fully drain body so OkHttp returns connection to pool for next ping.
                    resp.body?.bytes()
                }
                val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
                if (i >= PING_WARMUP) pings.add(elapsedMs.toLong())
            } catch (_: Exception) {}
        }
        if (pings.isEmpty()) return Pair(0.0, 0.0)

        // Trim 20% worst outliers (cellular jitter spikes distort the mean).
        val sorted = pings.sorted()
        val trimCount = (sorted.size * 0.2).toInt().coerceAtLeast(1)
        val trimmed = sorted.drop(trimCount / 2).dropLast(trimCount - trimCount / 2)
        val sample = if (trimmed.size >= 3) trimmed else sorted

        val mean = sample.average()
        // Jitter = standard deviation of samples (true RFC-3550-style dispersion).
        val variance = sample.map { (it - mean).let { d -> d * d } }.average()
        val jitter = sqrt(variance)
        return Pair(mean, jitter)
    }

    private fun measureDownload(): Pair<Double, Double?> {
        val totalBytes = AtomicLong(0)
        val steadyBytes = AtomicLong(0) // bytes counted after warmup (TCP slow-start excluded)
        val startTime = System.currentTimeMillis()
        val warmupEnd = startTime + 2_000L // skip first 2s for slow-start ramp
        val deadline = startTime + downloadDurationMs
        val loadedPings = mutableListOf<Long>()

        // Ping every ~500ms during steady-state → true loaded latency under load (bufferbloat).
        val loadedPingThread = Thread {
            while (System.currentTimeMillis() < deadline) {
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                if (System.currentTimeMillis() < warmupEnd) continue
                val t0 = System.nanoTime()
                try {
                    client.newCall(
                        Request.Builder().url("$CF_BASE/__down?bytes=0").build()
                    ).execute().use { it.body?.bytes() }
                    synchronized(loadedPings) {
                        loadedPings.add((System.nanoTime() - t0) / 1_000_000L)
                    }
                } catch (_: Exception) {}
            }
        }.also { it.start() }

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
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (source.read(buf).also { read = it } != -1) {
                                val now = System.currentTimeMillis()
                                totalBytes.addAndGet(read.toLong())
                                if (now >= warmupEnd) steadyBytes.addAndGet(read.toLong())
                                if (now >= deadline) break
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
        loadedPingThread.interrupt()
        loadedPingThread.join(500)

        val loadedLatency = synchronized(loadedPings) {
            if (loadedPings.size < 2) null
            else {
                val sorted = loadedPings.sorted()
                // Trim 25% worst spikes, mean of remainder (common bufferbloat methodology).
                val trim = (sorted.size * 0.25).toInt().coerceAtLeast(1)
                sorted.dropLast(trim).average()
            }
        }

        val steadyElapsed = max(System.currentTimeMillis() - warmupEnd, 1L)
        val steady = steadyBytes.get()
        // Fall back to full-window average if warmup never finished (short test).
        val mbps = if (steady > 0 && System.currentTimeMillis() > warmupEnd) {
            steady * 8.0 / steadyElapsed / 1000.0
        } else {
            val elapsed = max(System.currentTimeMillis() - startTime, 1L)
            totalBytes.get() * 8.0 / elapsed / 1000.0
        }
        return Pair(mbps, loadedLatency)
    }

    private fun measureUpload(): Double {
        val payload = ByteArray(UL_CHUNK_BYTES) { (it % 256).toByte() }
        val totalBytes = AtomicLong(0)
        val steadyBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        val warmupEnd = startTime + 2_000L
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
                                val now = System.currentTimeMillis()
                                totalBytes.addAndGet(UL_CHUNK_BYTES.toLong())
                                if (now >= warmupEnd) steadyBytes.addAndGet(UL_CHUNK_BYTES.toLong())
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
        val steadyElapsed = max(System.currentTimeMillis() - warmupEnd, 1L)
        val steady = steadyBytes.get()
        return if (steady > 0 && System.currentTimeMillis() > warmupEnd) {
            steady * 8.0 / steadyElapsed / 1000.0
        } else {
            val elapsed = max(System.currentTimeMillis() - startTime, 1L)
            totalBytes.get() * 8.0 / elapsed / 1000.0
        }
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

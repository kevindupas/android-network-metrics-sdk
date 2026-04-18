package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.SpeedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

private const val CF_BASE = "https://speed.cloudflare.com"
private const val DOWNLOAD_DURATION_MS = 5_000L
private const val UPLOAD_DURATION_MS = 5_000L
private const val PING_COUNT = 10
private const val THREAD_COUNT = 4

internal class SpeedMeasurement {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun measure(): SpeedResult? = withContext(Dispatchers.IO) {
        try {
            val latencyMs = measureLatency()
            val jitterMs = measureJitter()
            val downloadMbps = measureDownload()
            val uploadMbps = measureUpload()
            val loadedLatency = measureLoadedLatency()

            SpeedResult(
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                loadedLatencyMs = loadedLatency,
                serverName = "Cloudflare",
                serverLocation = fetchColoLocation(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun measureLatency(): Double {
        val pings = mutableListOf<Long>()
        repeat(PING_COUNT) {
            val start = System.currentTimeMillis()
            try {
                client.newCall(Request.Builder().url("$CF_BASE/__down?bytes=0").build()).execute().use {}
                pings.add(System.currentTimeMillis() - start)
            } catch (_: Exception) {}
        }
        return if (pings.isEmpty()) 0.0 else pings.average()
    }

    private fun measureJitter(): Double {
        val pings = mutableListOf<Long>()
        repeat(PING_COUNT) {
            val start = System.currentTimeMillis()
            try {
                client.newCall(Request.Builder().url("$CF_BASE/__down?bytes=0").build()).execute().use {}
                pings.add(System.currentTimeMillis() - start)
            } catch (_: Exception) {}
        }
        if (pings.size < 2) return 0.0
        return (1 until pings.size).map { abs(pings[it] - pings[it - 1]).toDouble() }.average()
    }

    private fun measureDownload(): Double {
        val chunkSize = 10 * 1024 * 1024 // 10 MB chunks
        var totalBytes = 0L
        val start = System.currentTimeMillis()
        val threads = (1..THREAD_COUNT).map {
            Thread {
                try {
                    client.newCall(
                        Request.Builder().url("$CF_BASE/__down?bytes=$chunkSize").build()
                    ).execute().use { resp ->
                        val bytes = resp.body?.bytes()?.size?.toLong() ?: 0L
                        synchronized(this) { totalBytes += bytes }
                    }
                } catch (_: Exception) {}
            }.also { it.start() }
        }
        threads.forEach { it.join(DOWNLOAD_DURATION_MS + 2000) }
        val elapsed = max(System.currentTimeMillis() - start, 1L)
        return totalBytes * 8.0 / elapsed / 1000.0 // Mbps
    }

    private fun measureUpload(): Double {
        val chunkSize = 2 * 1024 * 1024 // 2 MB
        val payload = ByteArray(chunkSize) { (it % 256).toByte() }
        var totalBytes = 0L
        val start = System.currentTimeMillis()
        val threads = (1..THREAD_COUNT).map {
            Thread {
                try {
                    client.newCall(
                        Request.Builder()
                            .url("$CF_BASE/__up")
                            .post(payload.toRequestBody("application/octet-stream".toMediaType()))
                            .build()
                    ).execute().use {
                        synchronized(this) { totalBytes += chunkSize }
                    }
                } catch (_: Exception) {}
            }.also { it.start() }
        }
        threads.forEach { it.join(UPLOAD_DURATION_MS + 2000) }
        val elapsed = max(System.currentTimeMillis() - start, 1L)
        return totalBytes * 8.0 / elapsed / 1000.0
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

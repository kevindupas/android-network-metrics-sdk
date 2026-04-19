package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.StreamingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val DEFAULT_HLS_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
private const val MEASURE_DURATION_MS = 20_000L

internal class StreamingMeasurement(
    private val streamingUrl: String? = null,
) {
    private val hlsUrl = streamingUrl ?: DEFAULT_HLS_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun measure(): StreamingResult = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch HLS manifest
            val manifestStart = System.currentTimeMillis()
            val manifestResp = client.newCall(
                Request.Builder().url(hlsUrl).build()
            ).execute()
            val manifest = manifestResp.body?.string() ?: run {
                return@withContext StreamingResult(null, 0, 0, null, 0, 0, "manifest fetch failed")
            }

            // 2. Parse segment URLs from lowest-bitrate playlist
            val segmentUrls = parseSegmentUrls(manifest, hlsUrl)
            if (segmentUrls.isEmpty()) {
                return@withContext StreamingResult(null, 0, 0, null, 0, 0, "no segments found")
            }

            val firstByteMs = System.currentTimeMillis() - manifestStart
            val deadline = System.currentTimeMillis() + MEASURE_DURATION_MS

            var totalBytes = 0L
            var rebufferCount = 0
            var rebufferDurationMs = 0L
            var segmentsDownloaded = 0
            var lastSegmentEnd = System.currentTimeMillis()

            for (segUrl in segmentUrls) {
                if (System.currentTimeMillis() > deadline) break
                val segStart = System.currentTimeMillis()
                try {
                    val bytes = client.newCall(
                        Request.Builder().url(segUrl).build()
                    ).execute().use { resp -> resp.body?.bytes()?.size?.toLong() ?: 0L }

                    totalBytes += bytes
                    segmentsDownloaded++

                    // Gap between expected segment end and actual = rebuffer
                    val gap = segStart - lastSegmentEnd
                    if (gap > 500) {
                        rebufferCount++
                        rebufferDurationMs += gap
                    }
                    lastSegmentEnd = System.currentTimeMillis()
                } catch (_: Exception) {
                    rebufferCount++
                    rebufferDurationMs += 1000
                }
            }

            val elapsed = System.currentTimeMillis() - manifestStart
            val avgBitrateKbps = if (elapsed > 0) (totalBytes * 8 / elapsed).toInt() else null

            StreamingResult(
                startTimeMs = firstByteMs,
                rebufferCount = rebufferCount,
                rebufferDurationMs = rebufferDurationMs,
                avgBitrateKbps = avgBitrateKbps,
                durationMeasuredMs = elapsed,
                bytesDownloaded = totalBytes,
                error = null,
            )
        } catch (e: Exception) {
            StreamingResult(null, 0, 0, null, 0, 0, e.message ?: "unknown error")
        }
    }

    private fun parseSegmentUrls(manifest: String, baseUrl: String): List<String> {
        val base = baseUrl.substringBeforeLast("/") + "/"
        val lines = manifest.lines()

        // Find the lowest-bitrate sub-playlist URL
        var lowestBandwidth = Long.MAX_VALUE
        var playlistUrl: String? = null
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
                if (bw < lowestBandwidth && i + 1 < lines.size && !lines[i + 1].startsWith("#")) {
                    lowestBandwidth = bw
                    playlistUrl = lines[i + 1].trim()
                }
            }
            i++
        }

        if (playlistUrl == null) return emptyList()
        val fullPlaylistUrl = if (playlistUrl.startsWith("http")) playlistUrl else base + playlistUrl

        // Fetch sub-playlist and extract .ts segment URLs
        return try {
            val subManifest = client.newCall(
                Request.Builder().url(fullPlaylistUrl).build()
            ).execute().use { it.body?.string() ?: "" }

            val segBase = fullPlaylistUrl.substringBeforeLast("/") + "/"
            subManifest.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .take(10) // max 10 segments (~15s at typical HLS segment duration)
                .map { if (it.startsWith("http")) it else segBase + it }
        } catch (_: Exception) { emptyList() }
    }
}

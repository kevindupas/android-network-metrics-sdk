package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.SocialLatencyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

val DEFAULT_SOCIAL_TARGETS = listOf(
    SocialTarget("WhatsApp",  "https://web.whatsapp.com/"),
    SocialTarget("Instagram", "https://www.instagram.com/"),
    SocialTarget("YouTube",   "https://www.youtube.com/"),
    SocialTarget("TikTok",    "https://www.tiktok.com/"),
    SocialTarget("X",         "https://x.com/"),
    SocialTarget("Facebook",  "https://www.facebook.com/"),
)

internal class SocialLatencyMeasurement(
    private val targets: List<SocialTarget> = DEFAULT_SOCIAL_TARGETS,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun measure(): List<SocialLatencyResult> = withContext(Dispatchers.IO) {
        targets.map { target ->
            async {
                val start = System.currentTimeMillis()
                try {
                    client.newCall(Request.Builder().url(target.url).head().build()).execute().use { resp ->
                        val ttfb = System.currentTimeMillis() - start
                        SocialLatencyResult(target.service, ttfb, resp.code in 100..599)
                    }
                } catch (_: Exception) {
                    val ttfb = System.currentTimeMillis() - start
                    SocialLatencyResult(target.service, ttfb, false)
                }
            }
        }.awaitAll()
    }
}

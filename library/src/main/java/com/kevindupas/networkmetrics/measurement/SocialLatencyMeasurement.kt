package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.SocialLatencyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val TARGETS = listOf(
    "WhatsApp"  to "https://web.whatsapp.com/",
    "Instagram" to "https://www.instagram.com/",
    "YouTube"   to "https://www.youtube.com/",
    "TikTok"    to "https://www.tiktok.com/",
    "X"         to "https://x.com/",
    "Facebook"  to "https://www.facebook.com/",
)

internal class SocialLatencyMeasurement {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun measure(): List<SocialLatencyResult> = withContext(Dispatchers.IO) {
        TARGETS.map { (service, url) ->
            async {
                val start = System.currentTimeMillis()
                try {
                    client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
                        val ttfb = System.currentTimeMillis() - start
                        SocialLatencyResult(service, ttfb, resp.code in 100..599)
                    }
                } catch (_: Exception) {
                    // Connection refused / timeout still gives us a TTFB indicator
                    val ttfb = System.currentTimeMillis() - start
                    SocialLatencyResult(service, ttfb, false)
                }
            }
        }.awaitAll()
    }
}

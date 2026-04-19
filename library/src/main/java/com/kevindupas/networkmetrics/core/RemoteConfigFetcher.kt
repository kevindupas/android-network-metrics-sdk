package com.kevindupas.networkmetrics.core

import android.util.Log
import com.kevindupas.networkmetrics.measurement.DEFAULT_SOCIAL_TARGETS
import com.kevindupas.networkmetrics.measurement.SocialTarget
import com.kevindupas.networkmetrics.measurement.WebTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "RemoteConfig"
private const val CACHE_TTL_MS = 60 * 60_000L

data class RemoteConfig(
    val webTargets: List<WebTarget>,
    val socialTargets: List<SocialTarget>,
    val streamingUrl: String?,
)

internal object RemoteConfigFetcher {

    private var cached: RemoteConfig? = null
    private var cacheTimestamp = 0L

    suspend fun fetch(
        configUrl: String,
        authHeader: String?,
        defaultWebTargets: List<WebTarget>,
        defaultSocialTargets: List<SocialTarget> = DEFAULT_SOCIAL_TARGETS,
        defaultStreamingUrl: String? = null,
    ): RemoteConfig {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cacheTimestamp < CACHE_TTL_MS) return it }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(configUrl)
                    .also { b -> authHeader?.let { b.header("Authorization", it) } }
                    .build()
                val body = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext defaults(defaultWebTargets, defaultSocialTargets, defaultStreamingUrl)
                    resp.body?.string() ?: return@withContext defaults(defaultWebTargets, defaultSocialTargets, defaultStreamingUrl)
                }
                val root = JSONObject(body)
                val webTargets = parseWebTargets(root.optJSONArray("webTargets") ?: root.optJSONArray("targets"))
                    .ifEmpty { defaultWebTargets }
                val socialTargets = parseSocialTargets(root.optJSONArray("socialTargets"))
                    .ifEmpty { defaultSocialTargets }
                val streamingUrl = root.optString("streamingUrl").ifBlank { null } ?: defaultStreamingUrl

                RemoteConfig(webTargets, socialTargets, streamingUrl).also {
                    cached = it
                    cacheTimestamp = now
                    Log.d(TAG, "Remote config: ${webTargets.size} web, ${socialTargets.size} social, streamingUrl=$streamingUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch remote config: ${e.message}")
                cached ?: defaults(defaultWebTargets, defaultSocialTargets, defaultStreamingUrl)
            }
        }
    }

    // Legacy compat — used by older callers
    suspend fun fetchWebTargets(
        configUrl: String,
        authHeader: String?,
        defaultTargets: List<WebTarget>,
    ): List<WebTarget> = fetch(configUrl, authHeader, defaultTargets).webTargets

    private fun defaults(web: List<WebTarget>, social: List<SocialTarget>, streamingUrl: String?) =
        RemoteConfig(web, social, streamingUrl)

    private fun parseWebTargets(arr: JSONArray?): List<WebTarget> {
        arr ?: return emptyList()
        return try {
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name").ifBlank { null } ?: return@mapNotNull null
                val url  = obj.optString("url").ifBlank { null }  ?: return@mapNotNull null
                WebTarget(name, url)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseSocialTargets(arr: JSONArray?): List<SocialTarget> {
        arr ?: return emptyList()
        return try {
            (0 until arr.length()).mapNotNull { i ->
                val obj     = arr.getJSONObject(i)
                val service = obj.optString("service").ifBlank { null } ?: return@mapNotNull null
                val url     = obj.optString("url").ifBlank { null }     ?: return@mapNotNull null
                SocialTarget(service, url)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun invalidateCache() {
        cached = null
        cacheTimestamp = 0L
    }
}

package com.kevindupas.networkmetrics.core

import android.util.Log
import com.kevindupas.networkmetrics.measurement.WebTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "RemoteConfig"
private const val CACHE_TTL_MS = 60 * 60 * 1_000L // 1 hour

internal object RemoteConfigFetcher {

    private var cachedTargets: List<WebTarget>? = null
    private var cacheTimestamp = 0L

    suspend fun fetchWebTargets(
        configUrl: String,
        authHeader: String?,
        defaultTargets: List<WebTarget>,
    ): List<WebTarget> {
        val now = System.currentTimeMillis()
        if (cachedTargets != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return cachedTargets!!
        }

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
                    if (!resp.isSuccessful) return@withContext defaultTargets
                    resp.body?.string() ?: return@withContext defaultTargets
                }
                val parsed = parseTargets(body)
                if (parsed.isNotEmpty()) {
                    cachedTargets = parsed
                    cacheTimestamp = now
                    Log.d(TAG, "Loaded ${parsed.size} remote web targets")
                    parsed
                } else {
                    defaultTargets
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch remote config: ${e.message}")
                cachedTargets ?: defaultTargets
            }
        }
    }

    private fun parseTargets(json: String): List<WebTarget> {
        return try {
            val root = JSONObject(json)
            val arr: JSONArray = root.optJSONArray("targets") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name").ifBlank { null } ?: return@mapNotNull null
                val url = obj.optString("url").ifBlank { null } ?: return@mapNotNull null
                WebTarget(name, url)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun invalidateCache() {
        cachedTargets = null
        cacheTimestamp = 0L
    }
}

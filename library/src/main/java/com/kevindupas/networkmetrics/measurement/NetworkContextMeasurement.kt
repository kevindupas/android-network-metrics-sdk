package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

internal class NetworkContextMeasurement {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun measure(): NetworkResult = withContext(Dispatchers.IO) {
        val trace = fetchCfTrace()
        val userIp = trace["ip"] ?: ""
        val colo = trace["colo"] ?: ""
        val countryCode = trace["loc"] ?: ""

        var asn: String? = null
        var isp: String? = null
        var city: String? = null
        var country: String? = null

        for ((url, parser) in listOf(
            "https://ipapi.co/$userIp/json/" to { j: JSONObject ->
                if (!j.isNull("asn")) mapOf(
                    "asn" to (j.optString("asn")),
                    "isp" to (j.optString("org")),
                    "city" to (j.optString("city")),
                    "country" to (j.optString("country_name")),
                ) else null
            },
            "https://ipwho.is/$userIp" to { j: JSONObject ->
                val conn = j.optJSONObject("connection")
                if (conn != null) mapOf(
                    "asn" to ("AS" + conn.optString("asn")),
                    "isp" to conn.optString("org"),
                    "city" to j.optString("city"),
                    "country" to j.optString("country"),
                ) else null
            },
        )) {
            try {
                val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() } ?: continue
                val parsed = parser(JSONObject(body)) ?: continue
                asn = parsed["asn"]
                isp = parsed["isp"]
                city = parsed["city"]
                country = parsed["country"]
                break
            } catch (_: Exception) { continue }
        }

        val serverCity = fetchCfServerCity(colo)
        val ipVersion = detectIpVersion()

        NetworkResult(
            connectionType = "cellular",
            ip = userIp.ifBlank { null },
            asn = asn,
            isp = isp,
            city = city,
            country = country,
            countryCode = countryCode.ifBlank { null },
            cfColo = colo.ifBlank { null },
            cfServerCity = serverCity,
            isLocallyServed = if (countryCode.isNotBlank() && serverCity != null) null else null,
            ipVersion = ipVersion,
        )
    }

    private fun fetchCfTrace(): Map<String, String> {
        return try {
            val body = client.newCall(
                Request.Builder().url("https://1.1.1.1/cdn-cgi/trace").build()
            ).execute().use { it.body?.string() } ?: return emptyMap()
            body.lines().mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx < 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }.toMap()
        } catch (_: Exception) { emptyMap() }
    }

    private fun fetchCfServerCity(colo: String): String? {
        if (colo.isBlank()) return null
        return try {
            val json = client.newCall(
                Request.Builder().url("https://speed.cloudflare.com/locations").build()
            ).execute().use { it.body?.string() } ?: return null
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("iata") == colo) return obj.optString("city")
            }
            null
        } catch (_: Exception) { null }
    }

    private fun detectIpVersion(): String {
        var hasV4 = false
        var hasV6 = false
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    when (addrs.nextElement()) {
                        is Inet6Address -> hasV6 = true
                        is Inet4Address -> hasV4 = true
                    }
                }
            }
        } catch (_: Exception) {}
        return when {
            hasV4 && hasV6 -> "dual"
            hasV6 -> "IPv6"
            hasV4 -> "IPv4"
            else -> "unknown"
        }
    }
}

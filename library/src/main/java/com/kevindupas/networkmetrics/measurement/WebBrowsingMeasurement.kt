package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.WebBrowsingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class WebTarget(val name: String, val url: String)
data class SocialTarget(val service: String, val url: String)

internal class WebBrowsingMeasurement(private val targets: List<WebTarget>) {

    suspend fun measure(): List<WebBrowsingResult> = withContext(Dispatchers.IO) {
        targets.map { target -> measureTarget(target) }
    }

    private fun measureTarget(target: WebTarget): WebBrowsingResult {
        val timings = Timings()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .eventListener(timings)
            .followRedirects(true)
            .build()

        val start = System.currentTimeMillis()
        return try {
            val req = Request.Builder().url(target.url).head().build()
            val resp = client.newCall(req).execute()
            val totalMs = System.currentTimeMillis() - start
            resp.close()
            WebBrowsingResult(
                name = target.name,
                url = target.url,
                dnsMs = timings.dnsMs,
                tcpMs = timings.tcpMs,
                tlsMs = timings.tlsMs,
                ttfbMs = timings.ttfbMs,
                totalMs = totalMs,
                httpStatus = resp.code,
                success = resp.isSuccessful || resp.code in 300..399,
                error = null,
            )
        } catch (e: Exception) {
            val totalMs = System.currentTimeMillis() - start
            WebBrowsingResult(
                name = target.name,
                url = target.url,
                dnsMs = timings.dnsMs,
                tcpMs = timings.tcpMs,
                tlsMs = timings.tlsMs,
                ttfbMs = timings.ttfbMs,
                totalMs = totalMs,
                httpStatus = null,
                success = false,
                error = e.message?.take(120),
            )
        }
    }

    private class Timings : EventListener() {
        private var dnsStart = 0L
        private var connectStart = 0L
        private var tlsStart = 0L
        private var requestHeadersEndAt = 0L

        var dnsMs: Long? = null
        var tcpMs: Long? = null
        var tlsMs: Long? = null
        var ttfbMs: Long? = null

        override fun dnsStart(call: Call, domainName: String) { dnsStart = now() }
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
            dnsMs = now() - dnsStart
        }
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            connectStart = now()
        }
        override fun secureConnectStart(call: Call) { tlsStart = now() }
        override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) {
            tlsMs = now() - tlsStart
        }
        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: okhttp3.Protocol?) {
            tcpMs = now() - connectStart - (tlsMs ?: 0L)
        }
        override fun requestHeadersEnd(call: Call, request: okhttp3.Request) {
            requestHeadersEndAt = now()
        }
        override fun responseHeadersStart(call: Call) {
            if (requestHeadersEndAt > 0L) ttfbMs = now() - requestHeadersEndAt
        }
        override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: okhttp3.Protocol?, ioe: IOException) {
            tcpMs = now() - connectStart
        }

        private fun now() = System.currentTimeMillis()
    }
}

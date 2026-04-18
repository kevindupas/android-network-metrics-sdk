package com.kevindupas.networkmetrics.measurement

import com.kevindupas.networkmetrics.model.DnsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

internal class DnsMeasurement(private val host: String = "google.com") {

    suspend fun measure(): DnsResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        return@withContext try {
            val addresses = InetAddress.getAllByName(host)
            val elapsed = System.currentTimeMillis() - start
            DnsResult(
                resolveMs = elapsed,
                host = host,
                resolvedIps = addresses.map { it.hostAddress ?: "" }.filter { it.isNotEmpty() },
                success = true,
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            DnsResult(
                resolveMs = elapsed,
                host = host,
                resolvedIps = emptyList(),
                success = false,
            )
        }
    }
}

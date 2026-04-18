package com.kevindupas.networkmetrics.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.kevindupas.networkmetrics.service.NetworkMetricsService

/**
 * Entry point for the Network Metrics SDK.
 *
 * Usage:
 * ```kotlin
 * NetworkMetricsSdk.init(context, NetworkMetricsConfig(backendUrl = "https://your-backend/metrics"))
 * NetworkMetricsSdk.start(context)
 * ```
 */
object NetworkMetricsSdk {

    private var config: NetworkMetricsConfig? = null

    /**
     * Initialise the SDK with a configuration. Must be called before [start].
     * Safe to call multiple times — subsequent calls update the configuration.
     */
    fun init(context: Context, config: NetworkMetricsConfig) {
        this.config = config
        ConfigHolder.config = config
    }

    /**
     * Start the background measurement service.
     * The service runs as a ForegroundService and survives app termination.
     * Requires FOREGROUND_SERVICE and location permissions to be granted before calling.
     */
    fun start(context: Context) {
        checkNotNull(config) { "NetworkMetricsSdk.init() must be called before start()" }
        val intent = Intent(context, NetworkMetricsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Stop the background measurement service.
     */
    fun stop(context: Context) {
        context.stopService(Intent(context, NetworkMetricsService::class.java))
    }

    /**
     * Returns true if the service is currently configured and running.
     */
    fun isInitialised(): Boolean = config != null
}

internal object ConfigHolder {
    var config: NetworkMetricsConfig? = null
}

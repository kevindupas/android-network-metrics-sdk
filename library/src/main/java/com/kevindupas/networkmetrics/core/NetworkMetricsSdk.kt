package com.kevindupas.networkmetrics.core

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kevindupas.networkmetrics.service.NetworkMetricsWorker
import java.util.concurrent.TimeUnit

private const val TAG = "NetworkMetricsSdk"
private const val WORK_NAME = "network_metrics_periodic"

object NetworkMetricsSdk {

    private var config: NetworkMetricsConfig? = null

    fun init(context: Context, config: NetworkMetricsConfig) {
        this.config = config
        ConfigHolder.config = config
    }

    fun start(context: Context) {
        val cfg = checkNotNull(config) { "NetworkMetricsSdk.init() must be called before start()" }

        // WorkManager minimum is 15 minutes — OS enforced
        val intervalMinutes = (cfg.intervalMs / 60_000L).coerceAtLeast(15)

        val request = PeriodicWorkRequestBuilder<NetworkMetricsWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        Log.d(TAG, "Scheduled every $intervalMinutes min (invisible, no notification)")
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "SDK stopped")
    }

    fun isInitialised(): Boolean = config != null
}

internal object ConfigHolder {
    var config: NetworkMetricsConfig? = null
}

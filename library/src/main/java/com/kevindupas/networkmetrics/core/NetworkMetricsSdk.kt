package com.kevindupas.networkmetrics.core

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kevindupas.networkmetrics.service.NetworkMetricsWorker
import java.util.concurrent.TimeUnit

private const val TAG = "NetworkMetricsSdk"
private const val WORK_NAME = "network_metrics_periodic"
private const val ONE_SHOT_NAME = "network_metrics_one_shot"

const val PREFS_NAME = "network_metrics_sdk"
const val PREF_LAST_RESULT = "last_result_json"
const val PREF_LAST_RESULT_AT = "last_result_at"

object NetworkMetricsSdk {

    private var config: NetworkMetricsConfig? = null

    fun init(context: Context, config: NetworkMetricsConfig) {
        this.config = config
        ConfigHolder.config = config
    }

    fun start(context: Context) {
        val cfg = checkNotNull(config) { "NetworkMetricsSdk.init() must be called before start()" }

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

        Log.d(TAG, "Scheduled every $intervalMinutes min")
    }

    /**
     * Trigger an immediate measurement.
     * @param progressCallback optional — called after each phase with partial results.
     *   Runs on a background coroutine; marshal to main thread yourself if updating UI.
     */
    fun measureNow(context: Context, progressCallback: ProgressCallback? = null) {
        checkNotNull(config) { "NetworkMetricsSdk.init() must be called before measureNow()" }
        ConfigHolder.progressCallback = progressCallback
        val request = OneTimeWorkRequestBuilder<NetworkMetricsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.d(TAG, "One-shot measurement enqueued")
    }

    fun getLastResult(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LAST_RESULT, null)
    }

    fun getLastResultTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(PREF_LAST_RESULT_AT, 0L)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "SDK stopped")
    }

    fun isInitialised(): Boolean = config != null
}

internal object ConfigHolder {
    var config: NetworkMetricsConfig? = null
    var progressCallback: ProgressCallback? = null
}

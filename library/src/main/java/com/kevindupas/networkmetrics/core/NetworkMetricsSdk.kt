package com.kevindupas.networkmetrics.core

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kevindupas.networkmetrics.measurement.DeviceMeasurement
import com.kevindupas.networkmetrics.measurement.RadioMeasurement
import com.kevindupas.networkmetrics.model.DeviceResult
import com.kevindupas.networkmetrics.model.RadioResult
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
    @JvmOverloads
    fun measureNow(
        context: Context,
        progressCallback: ProgressCallback? = null,
        skipSpeed: Boolean = false,
    ) {
        checkNotNull(config) { "NetworkMetricsSdk.init() must be called before measureNow()" }
        ConfigHolder.progressCallback = progressCallback
        val inputData = androidx.work.Data.Builder()
            .putBoolean("skipSpeed", skipSpeed)
            .build()
        val request = OneTimeWorkRequestBuilder<NetworkMetricsWorker>()
            .setInputData(inputData)
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
        Log.d(TAG, "One-shot measurement enqueued (skipSpeed=$skipSpeed)")
    }

    /**
     * Synchronous snapshot — Radio + Device only. Fast (<100ms, no I/O).
     * Use to populate operator/signal info at app launch, before running full measureNow().
     */
    fun getRadioSnapshot(context: Context): RadioSnapshot {
        val rm = RadioMeasurement(context)
        val radio = try { rm.measure() } catch (_: Exception) { null }
        val perSim = try { rm.measurePerSim() } catch (_: Exception) { emptyList() }
        val device = try { DeviceMeasurement(context).measure() } catch (_: Exception) { null }
        return RadioSnapshot(radio, device, perSim)
    }

    private val gnssMeasurement = mutableMapOf<Int, com.kevindupas.networkmetrics.measurement.GnssMeasurement>()

    /**
     * Snapshot of GNSS satellites currently visible (GPS / GLONASS / Galileo / BeiDou / …).
     *
     * Registers a passive [android.location.GnssStatus.Callback] on first call and reuses it.
     * Requires `ACCESS_FINE_LOCATION` at runtime; returns an empty snapshot otherwise.
     */
    fun getGnssSatellites(context: Context): com.kevindupas.networkmetrics.model.GnssSnapshot {
        val key = context.applicationContext.hashCode()
        val gm = gnssMeasurement.getOrPut(key) {
            com.kevindupas.networkmetrics.measurement.GnssMeasurement(context.applicationContext)
        }
        return gm.snapshot()
    }

    private val neighborCellsMeasurement = mutableMapOf<Int, com.kevindupas.networkmetrics.measurement.NeighborCellsMeasurement>()

    /**
     * Snapshot of all visible cells (serving + neighbors), per
     * [android.telephony.TelephonyManager.getAllCellInfo].
     * Requires ACCESS_FINE_LOCATION + READ_PHONE_STATE at runtime; returns
     * an empty list otherwise.
     */
    fun getNeighborCells(context: Context): List<com.kevindupas.networkmetrics.model.NeighborCell> {
        val key = context.applicationContext.hashCode()
        val m = neighborCellsMeasurement.getOrPut(key) {
            com.kevindupas.networkmetrics.measurement.NeighborCellsMeasurement(context.applicationContext)
        }
        return m.snapshot()
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

data class RadioSnapshot(
    val radio: RadioResult?,
    val device: DeviceResult?,
    val radioPerSim: List<com.kevindupas.networkmetrics.model.RadioPerSimResult> = emptyList(),
)

internal object ConfigHolder {
    var config: NetworkMetricsConfig? = null
    var progressCallback: ProgressCallback? = null
}

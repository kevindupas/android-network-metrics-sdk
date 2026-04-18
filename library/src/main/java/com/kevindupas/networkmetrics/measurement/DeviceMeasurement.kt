package com.kevindupas.networkmetrics.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.telephony.TelephonyManager
import com.kevindupas.networkmetrics.model.DeviceResult

internal class DeviceMeasurement(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun measure(): DeviceResult {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        var simOperatorName: String? = null
        var mcc: String? = null
        var mnc: String? = null
        try {
            simOperatorName = tm.simOperatorName?.takeIf { it.isNotBlank() }
            val simOp = tm.simOperator
            if (simOp != null && simOp.length >= 5) {
                mcc = simOp.substring(0, 3)
                mnc = simOp.substring(3)
            }
        } catch (_: Exception) {}

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        }
        val isCharging = batteryIntent?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }

        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val ramUsedMb = (memInfo.totalPss / 1024).takeIf { it > 0 }

        val cpuLoadPercent = try {
            val statBefore = readCpuStat()
            Thread.sleep(200)
            val statAfter = readCpuStat()
            val total = statAfter.first - statBefore.first
            val idle  = statAfter.second - statBefore.second
            if (total > 0) ((total - idle) * 100.0 / total) else null
        } catch (_: Exception) { null }

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> null
            }
        } else null

        return DeviceResult(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            simOperatorName = simOperatorName,
            mcc = mcc,
            mnc = mnc,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            ramUsedMb = ramUsedMb,
            cpuLoadPercent = cpuLoadPercent,
            thermalStatus = thermalStatus,
        )
    }

    private fun readCpuStat(): Pair<Long, Long> {
        val line = java.io.File("/proc/stat").bufferedReader().readLine() ?: return Pair(0L, 0L)
        val vals = line.trim().split("\\s+".toRegex()).drop(1).map { it.toLongOrNull() ?: 0L }
        val total = vals.sum()
        val idle  = vals.getOrElse(3) { 0L }
        return Pair(total, idle)
    }
}

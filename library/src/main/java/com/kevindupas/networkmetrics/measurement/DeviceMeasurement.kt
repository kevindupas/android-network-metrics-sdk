package com.kevindupas.networkmetrics.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
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
        )
    }
}

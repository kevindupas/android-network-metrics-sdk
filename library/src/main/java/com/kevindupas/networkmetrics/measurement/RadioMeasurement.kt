package com.kevindupas.networkmetrics.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.kevindupas.networkmetrics.model.RadioResult

internal class RadioMeasurement(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun measure(): RadioResult {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val connectionType = when {
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI -> "WiFi"
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE -> "cellular"
            else -> "none"
        }

        val signalLevel = signalStrengthLabel(tm.signalStrength)
        val networkGen = detectNetworkGeneration(tm)
        val isRoaming = try { tm.isNetworkRoaming } catch (_: Exception) { false }
        val isVoLte = try { tm.isVoLteAvailable } catch (_: Exception) { false }
        val isVoNr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { tm.isVoNrAvailable } catch (_: Exception) { false }
        } else false

        if (!hasPermissions()) {
            return emptyResult(networkGen, signalLevel, connectionType, isRoaming = isRoaming, isVoLte = isVoLte, isVoNr = isVoNr)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            buildFromCellInfo(tm, networkGen, signalLevel, connectionType, isRoaming, isVoLte, isVoNr)
        } else {
            emptyResult(networkGen, signalLevel, connectionType, isRoaming = isRoaming, isVoLte = isVoLte, isVoNr = isVoNr)
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildFromCellInfo(
        tm: TelephonyManager,
        networkGen: String,
        signalLevel: String,
        tech: String,
        isRoaming: Boolean,
        isVoLte: Boolean,
        isVoNr: Boolean,
    ): RadioResult {
        val cells = try { tm.allCellInfo } catch (_: Exception) { emptyList() }
        val nrMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) detect5GMode(tm) else null
        val isNr = nrMode != null

        for (cell in cells) {
            if (!cell.isRegistered) continue

            when (cell) {
                is CellInfoLte -> {
                    val sig = cell.cellSignalStrength
                    val id: CellIdentityLte = cell.cellIdentity
                    val bw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        id.bandwidth.takeUnless { it == CellInfo.UNAVAILABLE } else null
                    return RadioResult(
                        rsrp = sig.rsrp.takeUnless { it == CellInfo.UNAVAILABLE },
                        rsrq = sig.rsrq.takeUnless { it == CellInfo.UNAVAILABLE },
                        sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sig.rssnr.takeUnless { it == CellInfo.UNAVAILABLE } else null,
                        rssi = sig.rssi.takeUnless { it == CellInfo.UNAVAILABLE },
                        cqi = sig.cqi.takeUnless { it == CellInfo.UNAVAILABLE },
                        ci = id.ci.toLong().takeUnless { it == CellInfo.UNAVAILABLE.toLong() },
                        pci = id.pci.takeUnless { it == CellInfo.UNAVAILABLE },
                        tac = id.tac.takeUnless { it == CellInfo.UNAVAILABLE },
                        lac = null,
                        earfcn = id.earfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                        bandwidth = bw,
                        psc = null,
                        isNrAvailable = isNr,
                        isVoLteAvailable = isVoLte,
                        isVoNrAvailable = isVoNr,
                        isRoaming = isRoaming,
                        nrMode = nrMode,
                        networkGeneration = networkGen,
                        signalStrengthLevel = signalLevel,
                        technology = tech,
                    )
                }
                is CellInfoNr -> {
                    val sig = cell.cellSignalStrength as CellSignalStrengthNr
                    val id = cell.cellIdentity as CellIdentityNr
                    return RadioResult(
                        rsrp = sig.ssRsrp.takeUnless { it == CellInfo.UNAVAILABLE },
                        rsrq = sig.ssRsrq.takeUnless { it == CellInfo.UNAVAILABLE },
                        sinr = sig.ssSinr.takeUnless { it == CellInfo.UNAVAILABLE },
                        rssi = null,
                        cqi = null,
                        ci = id.nci.takeUnless { it == CellInfo.UNAVAILABLE_LONG },
                        pci = id.pci.takeUnless { it == CellInfo.UNAVAILABLE },
                        tac = id.tac.takeUnless { it == CellInfo.UNAVAILABLE },
                        lac = null,
                        earfcn = id.nrarfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                        bandwidth = null,
                        psc = null,
                        isNrAvailable = true,
                        isVoLteAvailable = isVoLte,
                        isVoNrAvailable = isVoNr,
                        isRoaming = isRoaming,
                        nrMode = "SA",
                        networkGeneration = networkGen,
                        signalStrengthLevel = signalLevel,
                        technology = tech,
                    )
                }
                is CellInfoWcdma -> {
                    val id: CellIdentityWcdma = cell.cellIdentity
                    return RadioResult(
                        rsrp = null, rsrq = null, sinr = null,
                        rssi = cell.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                        cqi = null,
                        ci = id.cid.toLong().takeUnless { it == CellInfo.UNAVAILABLE.toLong() },
                        pci = null,
                        tac = null,
                        lac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                        earfcn = id.uarfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                        bandwidth = null,
                        psc = id.psc.takeUnless { it == CellInfo.UNAVAILABLE },
                        isNrAvailable = false,
                        isVoLteAvailable = isVoLte,
                        isVoNrAvailable = isVoNr,
                        isRoaming = isRoaming,
                        nrMode = null,
                        networkGeneration = networkGen,
                        signalStrengthLevel = signalLevel,
                        technology = tech,
                    )
                }
                is CellInfoGsm -> {
                    val id: CellIdentityGsm = cell.cellIdentity
                    return RadioResult(
                        rsrp = null, rsrq = null, sinr = null,
                        rssi = cell.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                        cqi = null,
                        ci = id.cid.toLong().takeUnless { it == CellInfo.UNAVAILABLE.toLong() },
                        pci = null,
                        tac = null,
                        lac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                        earfcn = id.arfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                        bandwidth = null,
                        psc = null,
                        isNrAvailable = false,
                        isVoLteAvailable = isVoLte,
                        isVoNrAvailable = isVoNr,
                        isRoaming = isRoaming,
                        nrMode = null,
                        networkGeneration = networkGen,
                        signalStrengthLevel = signalLevel,
                        technology = tech,
                    )
                }
            }
        }

        return emptyResult(networkGen, signalLevel, tech, isNr, isRoaming, nrMode, isVoLte, isVoNr)
    }

    private fun emptyResult(
        networkGen: String, signalLevel: String, tech: String,
        isNr: Boolean = false, isRoaming: Boolean = false, nrMode: String? = null,
        isVoLte: Boolean = false, isVoNr: Boolean = false,
    ) = RadioResult(
        rsrp = null, rsrq = null, sinr = null, rssi = null, cqi = null,
        ci = null, pci = null, tac = null, lac = null,
        earfcn = null, bandwidth = null, psc = null,
        isNrAvailable = isNr,
        isVoLteAvailable = isVoLte,
        isVoNrAvailable = isVoNr,
        isRoaming = isRoaming,
        nrMode = nrMode,
        networkGeneration = networkGen,
        signalStrengthLevel = signalLevel,
        technology = tech,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private fun detect5GMode(tm: TelephonyManager): String? {
        return try {
            val result = arrayOfNulls<String>(1)
            val lock = Object()
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    result[0] = when (info.overrideNetworkType) {
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "NSA"
                        else -> null
                    }
                    // SA 5G shows as NETWORK_TYPE_NR in dataNetworkType with no override
                    if (result[0] == null && info.networkType == TelephonyManager.NETWORK_TYPE_NR
                        && info.overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE) {
                        result[0] = "SA"
                    }
                    synchronized(lock) { lock.notifyAll() }
                }
            }
            tm.registerTelephonyCallback(context.mainExecutor, cb)
            synchronized(lock) { lock.wait(600) }
            tm.unregisterTelephonyCallback(cb)
            result[0]
        } catch (_: Exception) { null }
    }

    @SuppressLint("MissingPermission")
    private fun detectNetworkGeneration(tm: TelephonyManager): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return when {
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI -> "WiFi"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && detect5GMode(tm) != null) "5G" else "4G"
                    TelephonyManager.NETWORK_TYPE_UMTS,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_GPRS,
                    TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                    else -> "Unknown"
                }
            }
            else -> "Unknown"
        }
    }

    private fun signalStrengthLabel(ss: SignalStrength?): String {
        if (ss == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "UNKNOWN"
        return when (ss.level) {
            0 -> "NONE"; 1 -> "POOR"; 2 -> "MODERATE"; 3 -> "GOOD"; 4 -> "GREAT"
            else -> "UNKNOWN"
        }
    }

    private fun hasPermissions(): Boolean {
        val loc = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val phone = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
        return loc == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                phone == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

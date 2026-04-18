package com.kevindupas.networkmetrics.measurement

import android.annotation.SuppressLint
import android.content.Context
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
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.kevindupas.networkmetrics.model.NeighboringCellResult

internal class NeighboringCellsMeasurement(private val context: Context) {

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun measure(): List<NeighboringCellResult> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cells = try { tm.allCellInfo } catch (_: Exception) { return emptyList() }
        return cells.mapNotNull { cell -> cellToResult(cell) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cellToResult(cell: CellInfo): NeighboringCellResult? = when (cell) {
        is CellInfoLte -> {
            val sig = cell.cellSignalStrength
            val id: CellIdentityLte = cell.cellIdentity
            NeighboringCellResult(
                type = "LTE",
                pci = id.pci.takeUnless { it == CellInfo.UNAVAILABLE },
                ci = id.ci.toLong().takeUnless { it == CellInfo.UNAVAILABLE.toLong() },
                rsrp = sig.rsrp.takeUnless { it == CellInfo.UNAVAILABLE },
                rsrq = sig.rsrq.takeUnless { it == CellInfo.UNAVAILABLE },
                rssi = sig.rssi.takeUnless { it == CellInfo.UNAVAILABLE },
                tac = id.tac.takeUnless { it == CellInfo.UNAVAILABLE },
                lac = null,
                earfcn = id.earfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                isRegistered = cell.isRegistered,
            )
        }
        is CellInfoNr -> {
            val sig = cell.cellSignalStrength as CellSignalStrengthNr
            val id = cell.cellIdentity as CellIdentityNr
            NeighboringCellResult(
                type = "NR",
                pci = id.pci.takeUnless { it == CellInfo.UNAVAILABLE },
                ci = id.nci.takeUnless { it == CellInfo.UNAVAILABLE_LONG },
                rsrp = sig.ssRsrp.takeUnless { it == CellInfo.UNAVAILABLE },
                rsrq = sig.ssRsrq.takeUnless { it == CellInfo.UNAVAILABLE },
                rssi = null,
                tac = id.tac.takeUnless { it == CellInfo.UNAVAILABLE },
                lac = null,
                earfcn = id.nrarfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                isRegistered = cell.isRegistered,
            )
        }
        is CellInfoWcdma -> {
            val id: CellIdentityWcdma = cell.cellIdentity
            NeighboringCellResult(
                type = "WCDMA",
                pci = null,
                ci = id.cid.toLong().takeUnless { it == CellInfo.UNAVAILABLE.toLong() },
                rsrp = null,
                rsrq = null,
                rssi = cell.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                tac = null,
                lac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                earfcn = id.uarfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                isRegistered = cell.isRegistered,
            )
        }
        is CellInfoGsm -> {
            val id: CellIdentityGsm = cell.cellIdentity
            NeighboringCellResult(
                type = "GSM",
                pci = null,
                ci = id.cid.toLong().takeUnless { it == CellInfo.UNAVAILABLE.toLong() },
                rsrp = null,
                rsrq = null,
                rssi = cell.cellSignalStrength.dbm.takeUnless { it == CellInfo.UNAVAILABLE },
                tac = null,
                lac = id.lac.takeUnless { it == CellInfo.UNAVAILABLE },
                earfcn = id.arfcn.takeUnless { it == CellInfo.UNAVAILABLE },
                isRegistered = cell.isRegistered,
            )
        }
        else -> null
    }
}

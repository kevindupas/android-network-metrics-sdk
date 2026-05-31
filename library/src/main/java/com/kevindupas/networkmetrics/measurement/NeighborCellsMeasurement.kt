package com.kevindupas.networkmetrics.measurement

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import com.kevindupas.networkmetrics.model.NeighborCell

class NeighborCellsMeasurement(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun snapshot(): List<NeighborCell> {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return emptyList()

        val all: List<CellInfo> = try {
            tm.allCellInfo ?: emptyList()
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return all.mapNotNull { cell -> mapCell(cell) }
    }

    private fun mapCell(cell: CellInfo): NeighborCell? {
        val ts = cell.timeStamp
        return when (cell) {
            is CellInfoLte -> {
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                NeighborCell(
                    technology = "LTE",
                    isRegistered = cell.isRegistered,
                    mcc = mccOf(id),
                    mnc = mncOf(id),
                    tac = id.tac.takeIfValid(),
                    ci = id.ci.toLong().takeIfValidLong(),
                    nci = null,
                    pci = id.pci.takeIfValid(),
                    psc = null,
                    arfcn = id.earfcn.takeIfValid(),
                    rsrp = ss.rsrp.takeIfValid(),
                    rsrq = ss.rsrq.takeIfValid(),
                    sinr = ss.rssnr.takeIfValid(),
                    asuLevel = ss.asuLevel.takeIfValid(),
                    dbm = ss.dbm.takeIfValid(),
                    timestampNanos = ts,
                )
            }
            else -> {
                if (Build.VERSION.SDK_INT >= 29 && cell is CellInfoNr) {
                    val id = cell.cellIdentity as CellIdentityNr
                    val ss = cell.cellSignalStrength as CellSignalStrengthNr
                    return NeighborCell(
                        technology = "NR",
                        isRegistered = cell.isRegistered,
                        mcc = id.mccString,
                        mnc = id.mncString,
                        tac = id.tac.takeIfValid(),
                        ci = null,
                        nci = id.nci.takeIfValidLong(),
                        pci = id.pci.takeIfValid(),
                        psc = null,
                        arfcn = id.nrarfcn.takeIfValid(),
                        rsrp = ss.ssRsrp.takeIfValid(),
                        rsrq = ss.ssRsrq.takeIfValid(),
                        sinr = ss.ssSinr.takeIfValid(),
                        asuLevel = ss.asuLevel.takeIfValid(),
                        dbm = ss.dbm.takeIfValid(),
                        timestampNanos = ts,
                    )
                }
                if (cell is CellInfoWcdma) {
                    val id = cell.cellIdentity
                    val ss = cell.cellSignalStrength
                    return NeighborCell(
                        technology = "WCDMA",
                        isRegistered = cell.isRegistered,
                        mcc = mccOf(id),
                        mnc = mncOf(id),
                        tac = id.lac.takeIfValid(),
                        ci = id.cid.toLong().takeIfValidLong(),
                        nci = null,
                        pci = null,
                        psc = id.psc.takeIfValid(),
                        arfcn = id.uarfcn.takeIfValid(),
                        rsrp = null,
                        rsrq = null,
                        sinr = null,
                        asuLevel = ss.asuLevel.takeIfValid(),
                        dbm = ss.dbm.takeIfValid(),
                        timestampNanos = ts,
                    )
                }
                if (cell is CellInfoGsm) {
                    val id = cell.cellIdentity
                    val ss = cell.cellSignalStrength
                    return NeighborCell(
                        technology = "GSM",
                        isRegistered = cell.isRegistered,
                        mcc = mccOf(id),
                        mnc = mncOf(id),
                        tac = id.lac.takeIfValid(),
                        ci = id.cid.toLong().takeIfValidLong(),
                        nci = null,
                        pci = null,
                        psc = null,
                        arfcn = id.arfcn.takeIfValid(),
                        rsrp = null,
                        rsrq = null,
                        sinr = null,
                        asuLevel = ss.asuLevel.takeIfValid(),
                        dbm = ss.dbm.takeIfValid(),
                        timestampNanos = ts,
                    )
                }
                null
            }
        }
    }

    private fun mccOf(id: Any): String? = try {
        when (id) {
            is CellIdentityLte   -> id.mccString
            is CellIdentityWcdma -> id.mccString
            is CellIdentityGsm   -> id.mccString
            else -> null
        }
    } catch (_: Exception) { null }

    private fun mncOf(id: Any): String? = try {
        when (id) {
            is CellIdentityLte   -> id.mncString
            is CellIdentityWcdma -> id.mncString
            is CellIdentityGsm   -> id.mncString
            else -> null
        }
    } catch (_: Exception) { null }

    private fun Int.takeIfValid(): Int? = if (this == Int.MAX_VALUE || this == Integer.MIN_VALUE) null else this
    private fun Long.takeIfValidLong(): Long? = if (this == Long.MAX_VALUE || this == Long.MIN_VALUE) null else this
}

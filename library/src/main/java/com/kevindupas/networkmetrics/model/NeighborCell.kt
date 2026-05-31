package com.kevindupas.networkmetrics.model

/**
 * One cell observed by TelephonyManager.getAllCellInfo() — serving (isRegistered=true) or neighbor.
 *
 * Fields that don't apply to a given technology are null (e.g. `nci` only on NR, `psc` only on WCDMA).
 *
 * Note: mcc/mnc may be null on API 26-27 devices where the `*String` getters were not yet
 * available; we don't fall back to the deprecated int getters to keep the implementation simple.
 */
data class NeighborCell(
    /** "LTE", "NR", "WCDMA", "GSM" */
    val technology: String,
    val isRegistered: Boolean,
    val mcc: String?,
    val mnc: String?,
    /** LTE: tac, NR: tac, WCDMA: lac, GSM: lac */
    val tac: Int?,
    /** LTE: ci, WCDMA: cid, GSM: cid */
    val ci: Long?,
    /** NR only: 5G cell identity (64-bit). */
    val nci: Long?,
    /** Physical cell id (LTE/NR). */
    val pci: Int?,
    /** WCDMA primary scrambling code. */
    val psc: Int?,
    /** LTE EARFCN / NR ARFCN / WCDMA UARFCN / GSM ARFCN. */
    val arfcn: Int?,
    /** RSRP (LTE) / ssRsrp (NR) in dBm; WCDMA/GSM use plain dBm. */
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val asuLevel: Int?,
    val dbm: Int?,
    val timestampNanos: Long,
)

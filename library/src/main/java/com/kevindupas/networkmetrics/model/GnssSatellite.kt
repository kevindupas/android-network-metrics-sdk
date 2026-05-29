package com.kevindupas.networkmetrics.model

/** One satellite as observed by the GNSS receiver at a point in time. */
data class GnssSatellite(
    val svid: Int,
    /** GPS, GLO, GAL, BDS, QZS, SBAS, IRN, OTH. */
    val constellation: String,
    /** Azimuth in degrees, 0 = North, clockwise. */
    val azimuth: Float,
    /** Elevation above horizon in degrees (0..90). */
    val elevation: Float,
    /** Carrier-to-noise density in dB-Hz. */
    val cn0DbHz: Float,
    val usedInFix: Boolean,
)

/** Snapshot of currently visible GNSS satellites. */
data class GnssSnapshot(
    val satellites: List<GnssSatellite>,
    val inView: Int,
    val usedInFix: Int,
    val avgCn0DbHz: Double,
)

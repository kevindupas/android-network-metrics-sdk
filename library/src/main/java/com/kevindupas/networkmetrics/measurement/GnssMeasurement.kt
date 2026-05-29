package com.kevindupas.networkmetrics.measurement

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import com.kevindupas.networkmetrics.model.GnssSatellite
import com.kevindupas.networkmetrics.model.GnssSnapshot

/**
 * Reads the latest snapshot of GNSS satellites currently visible to the device.
 *
 * Registers a [GnssStatus.Callback] once on first use and keeps the latest
 * snapshot in memory. Subsequent calls to [snapshot] return that cached value.
 *
 * Requires [Manifest.permission.ACCESS_FINE_LOCATION] at runtime and location
 * services to be enabled. Without permission, [snapshot] returns an empty result.
 */
class GnssMeasurement(private val context: Context) {

    @Volatile private var latest: GnssStatus? = null
    private var registered = false

    @SuppressLint("MissingPermission")
    private fun ensureRegistered() {
        if (registered) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                latest = status
            }
        }
        try {
            lm.registerGnssStatusCallback(cb, null)
            registered = true
        } catch (_: SecurityException) {
        }
    }

    fun snapshot(): GnssSnapshot {
        ensureRegistered()
        val s = latest ?: return GnssSnapshot(emptyList(), 0, 0, 0.0)
        val list = ArrayList<GnssSatellite>(s.satelliteCount)
        var used = 0
        var sum = 0.0
        var n = 0
        for (i in 0 until s.satelliteCount) {
            val cn0 = s.getCn0DbHz(i)
            val isUsed = s.usedInFix(i)
            if (isUsed) used++
            if (cn0 > 0) { sum += cn0; n++ }
            list += GnssSatellite(
                svid = s.getSvid(i),
                constellation = constellationName(s.getConstellationType(i)),
                azimuth = s.getAzimuthDegrees(i),
                elevation = s.getElevationDegrees(i),
                cn0DbHz = cn0,
                usedInFix = isUsed,
            )
        }
        return GnssSnapshot(
            satellites = list,
            inView = list.size,
            usedInFix = used,
            avgCn0DbHz = if (n > 0) sum / n else 0.0,
        )
    }

    private fun constellationName(c: Int): String = when (c) {
        GnssStatus.CONSTELLATION_GPS     -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLO"
        GnssStatus.CONSTELLATION_GALILEO -> "GAL"
        GnssStatus.CONSTELLATION_BEIDOU  -> "BDS"
        GnssStatus.CONSTELLATION_QZSS    -> "QZS"
        GnssStatus.CONSTELLATION_SBAS    -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS   -> "IRN"
        else                             -> "OTH"
    }
}

package com.smartattendance.app.core.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis()
)

data class GpsProximityResult(
    val isWithinRange: Boolean,
    val distanceMeters: Float,
    val allowedRadiusMeters: Float,
    val studentLat: Double,
    val studentLon: Double,
    val targetLat: Double,
    val targetLon: Double,
    val message: String
)

class GpsLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    companion object {
        // IIIT Naya Raipur Academic Block 1 Coordinates (Classrooms & Lecture Halls)
        const val CAMPUS_ACADEMIC_LAT = 21.128456
        const val CAMPUS_ACADEMIC_LON = 81.766184
        const val MAX_CLASSROOM_RADIUS_METERS = 30f // Strict 30m geofence: eliminates hostel 60-70m away!
    }

    /**
     * Compute distance in meters between two coordinates using Android hardware distance calculation
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Verify if the student's current position is within [maxRadiusMeters] (default 30m)
     * of the classroom / teacher's coordinates.
     */
    fun verifyClassroomProximity(
        studentLat: Double,
        studentLon: Double,
        targetLat: Double = CAMPUS_ACADEMIC_LAT,
        targetLon: Double = CAMPUS_ACADEMIC_LON,
        maxRadiusMeters: Float = MAX_CLASSROOM_RADIUS_METERS
    ): GpsProximityResult {
        val distance = calculateDistanceMeters(studentLat, studentLon, targetLat, targetLon)
        val isWithin = distance <= maxRadiusMeters

        val msg = if (isWithin) {
            "Verified inside classroom geofence (${distance.toInt()}m from center, limit ${maxRadiusMeters.toInt()}m)"
        } else {
            "GEOFENCE EXCEEDED: You are ${distance.toInt()}m away (Hostel/Outside detected). Limit is ${maxRadiusMeters.toInt()}m."
        }

        return GpsProximityResult(
            isWithinRange = isWithin,
            distanceMeters = distance,
            allowedRadiusMeters = maxRadiusMeters,
            studentLat = studentLat,
            studentLon = studentLon,
            targetLat = targetLat,
            targetLon = targetLon,
            message = msg
        )
    }

    /**
     * Retrieve the most recent or active location from GPS or Network provider
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): GpsCoordinate? {
        if (locationManager == null) return null

        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var bestLocation: Location? = null

            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            }

            return bestLocation?.let {
                GpsCoordinate(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy,
                    timestampMs = it.time
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Request a fresh single location update with timeout
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GpsCoordinate? = withTimeoutOrNull(2500L) {
        suspendCancellableCoroutine { cont ->
            if (locationManager == null) {
                cont.resume(getLastKnownLocation())
                return@suspendCancellableCoroutine
            }

            try {
                val last = getLastKnownLocation()
                // If we have a very recent fix (< 60 seconds old), use it immediately
                if (last != null && (System.currentTimeMillis() - last.timestampMs) < 60_000L) {
                    cont.resume(last)
                    return@suspendCancellableCoroutine
                }

                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (cont.isActive) {
                            cont.resume(
                                GpsCoordinate(
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    accuracyMeters = loc.accuracy,
                                    timestampMs = loc.time
                                )
                            )
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                // Prefer NETWORK_PROVIDER indoors, fallback to GPS_PROVIDER
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation {
                        try {
                            locationManager.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }
                } else {
                    cont.resume(last)
                }
            } catch (e: Exception) {
                cont.resume(getLastKnownLocation())
            }
        }
    } ?: getLastKnownLocation()
}

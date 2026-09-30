package com.smartattendance.app.core.engine

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class AttendanceStatus {
    PRESENT,
    REVIEW,
    ABSENT
}

data class AttendanceEvaluationResult(
    val coveragePercentage: Double,
    val observedWindows: Int,
    val totalWindows: Int,
    val status: AttendanceStatus,
    val wifiVerified: Boolean = false,
    val bleVerified: Boolean = false,
    val rttDistanceMeters: Double? = null,
    val reviewReason: String? = null
)

data class MultiSensorObservation(
    val timestampMs: Long,
    val isWifiApMatched: Boolean,
    val isBleDetected: Boolean,
    val bleRssi: Int? = null,
    val rttDistanceMeters: Double? = null
)

object AttendanceCalculator {

    const val DEFAULT_PRESENT_THRESHOLD = 0.60 // 60%
    const val DEFAULT_REVIEW_THRESHOLD = 0.20  // 20%
    const val DEFAULT_WINDOW_MINUTES = 3       // 3-minute discrete buckets
    const val DEFAULT_RSSI_THRESHOLD = -80     // -80 dBm
    const val MAX_CLASSROOM_RTT_METERS = 12.0  // 12 meters classroom boundary

    /**
     * Legacy BLE-only presence evaluation.
     */
    fun calculateCoverage(
        observationTimestamps: List<Long>,
        sessionStartMs: Long,
        sessionEndMs: Long,
        windowMinutes: Int = DEFAULT_WINDOW_MINUTES,
        presentThreshold: Double = DEFAULT_PRESENT_THRESHOLD,
        reviewThreshold: Double = DEFAULT_REVIEW_THRESHOLD
    ): AttendanceEvaluationResult {
        val durationMs = max(1000L, sessionEndMs - sessionStartMs)
        val windowSizeMs = windowMinutes * 60 * 1000L
        val totalWindows = max(1, ceil(durationMs.toDouble() / windowSizeMs.toDouble()).toInt())

        val observedBucketSet = mutableSetOf<Int>()
        for (timestamp in observationTimestamps) {
            if (timestamp in sessionStartMs..sessionEndMs) {
                val windowIndex = ((timestamp - sessionStartMs) / windowSizeMs).toInt()
                if (windowIndex in 0 until totalWindows) {
                    observedBucketSet.add(windowIndex)
                }
            }
        }

        val observedCount = observedBucketSet.size
        val rawCoverage = (observedCount.toDouble() / totalWindows.toDouble()) * 100.0
        val coveragePercentage = min(100.0, String.format("%.2f", rawCoverage).toDouble())

        val status = when {
            coveragePercentage >= (presentThreshold * 100.0) -> AttendanceStatus.PRESENT
            coveragePercentage >= (reviewThreshold * 100.0) -> AttendanceStatus.REVIEW
            else -> AttendanceStatus.ABSENT
        }

        return AttendanceEvaluationResult(
            coveragePercentage = coveragePercentage,
            observedWindows = observedCount,
            totalWindows = totalWindows,
            status = status,
            bleVerified = observedCount > 0
        )
    }

    /**
     * Hybrid Multi-Modal Presence Evaluation (Wi-Fi AP BSSID + BLE + Wi-Fi RTT).
     * Matches the user's Multi-Sensor Fusion specification.
     */
    fun calculateHybridCoverage(
        observations: List<MultiSensorObservation>,
        sessionStartMs: Long,
        sessionEndMs: Long,
        hasRoomWifiInfrastructure: Boolean = true,
        windowMinutes: Int = DEFAULT_WINDOW_MINUTES,
        presentThreshold: Double = DEFAULT_PRESENT_THRESHOLD,
        reviewThreshold: Double = DEFAULT_REVIEW_THRESHOLD
    ): AttendanceEvaluationResult {
        val durationMs = max(1000L, sessionEndMs - sessionStartMs)
        val windowSizeMs = windowMinutes * 60 * 1000L
        val totalWindows = max(1, ceil(durationMs.toDouble() / windowSizeMs.toDouble()).toInt())

        val wifiBuckets = mutableSetOf<Int>()
        val bleBuckets = mutableSetOf<Int>()
        val rttSamples = mutableListOf<Double>()

        for (obs in observations) {
            if (obs.timestampMs in sessionStartMs..sessionEndMs) {
                val bucket = ((obs.timestampMs - sessionStartMs) / windowSizeMs).toInt()
                if (bucket in 0 until totalWindows) {
                    if (obs.isWifiApMatched) {
                        wifiBuckets.add(bucket)
                    }
                    if (obs.isBleDetected && (obs.bleRssi == null || obs.bleRssi >= DEFAULT_RSSI_THRESHOLD)) {
                        bleBuckets.add(bucket)
                    }
                    if (obs.rttDistanceMeters != null && obs.rttDistanceMeters <= MAX_CLASSROOM_RTT_METERS) {
                        rttSamples.add(obs.rttDistanceMeters)
                    }
                }
            }
        }

        val wifiRatio = (wifiBuckets.size.toDouble() / totalWindows.toDouble()) * 100.0
        val bleRatio = (bleBuckets.size.toDouble() / totalWindows.toDouble()) * 100.0
        val avgRtt = if (rttSamples.isNotEmpty()) rttSamples.average() else null

        val isWifiVerified = wifiRatio >= 40.0
        val isBleVerified = bleRatio >= 40.0

        // Multi-modal weighted score
        var finalScore: Double
        if (hasRoomWifiInfrastructure) {
            // 50% Wi-Fi AP + 50% BLE
            finalScore = (0.50 * wifiRatio) + (0.50 * bleRatio)
            // RTT bonus (up to +10%) if within classroom radius
            if (avgRtt != null && avgRtt <= 8.0) {
                finalScore = min(100.0, finalScore + 10.0)
            }
        } else {
            // Standalone BLE mode
            finalScore = bleRatio
        }

        finalScore = min(100.0, String.format("%.2f", finalScore).toDouble())

        var reviewReason: String? = null
        val status = when {
            finalScore >= (presentThreshold * 100.0) -> {
                AttendanceStatus.PRESENT
            }
            isWifiVerified && !isBleVerified -> {
                // Connected to classroom AP, but BLE signal absent (e.g. bluetooth turned off)
                reviewReason = "Wi-Fi AP verified (Room A-204), but Bluetooth proximity signal was not detected"
                AttendanceStatus.REVIEW
            }
            !isWifiVerified && isBleVerified -> {
                // BLE detected near teacher, but not connected to Room AP (e.g. student on cellular data)
                reviewReason = "BLE proximity detected, but student was not connected to Classroom Wi-Fi"
                AttendanceStatus.REVIEW
            }
            finalScore >= (reviewThreshold * 100.0) -> {
                reviewReason = "Partial presence coverage (${finalScore}%). Teacher review recommended."
                AttendanceStatus.REVIEW
            }
            else -> {
                AttendanceStatus.ABSENT
            }
        }

        return AttendanceEvaluationResult(
            coveragePercentage = finalScore,
            observedWindows = max(wifiBuckets.size, bleBuckets.size),
            totalWindows = totalWindows,
            status = status,
            wifiVerified = isWifiVerified,
            bleVerified = isBleVerified,
            rttDistanceMeters = avgRtt,
            reviewReason = reviewReason
        )
    }
}

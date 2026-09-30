package com.smartattendance.app

import com.smartattendance.app.core.engine.AttendanceCalculator
import com.smartattendance.app.core.engine.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceCalculatorTest {

    @Test
    fun testStudentWithHighPresenceCoverageIsPresent() {
        val sessionStart = 1759296000_000L
        val sessionEnd = sessionStart + (60 * 60 * 1000L) // 60 minutes lecture

        // 60 minutes with 3-minute windows = 20 total windows
        // Generate observations in 18 out of 20 windows (90% coverage)
        val observations = mutableListOf<Long>()
        val windowMs = 3 * 60 * 1000L
        for (i in 0 until 18) {
            observations.add(sessionStart + (i * windowMs) + 30_000L)
        }

        val result = AttendanceCalculator.calculateCoverage(
            observationTimestamps = observations,
            sessionStartMs = sessionStart,
            sessionEndMs = sessionEnd
        )

        assertEquals(20, result.totalWindows)
        assertEquals(18, result.observedWindows)
        assertEquals(90.0, result.coveragePercentage, 0.01)
        assertEquals(AttendanceStatus.PRESENT, result.status)
    }

    @Test
    fun testStudentWithBorderlinePresenceIsReview() {
        val sessionStart = 1759296000_000L
        val sessionEnd = sessionStart + (60 * 60 * 1000L) // 20 windows total

        // Observed in 8 out of 20 windows = 40% coverage
        val observations = mutableListOf<Long>()
        val windowMs = 3 * 60 * 1000L
        for (i in 0 until 8) {
            observations.add(sessionStart + (i * windowMs) + 15_000L)
        }

        val result = AttendanceCalculator.calculateCoverage(
            observationTimestamps = observations,
            sessionStartMs = sessionStart,
            sessionEndMs = sessionEnd
        )

        assertEquals(40.0, result.coveragePercentage, 0.01)
        assertEquals(AttendanceStatus.REVIEW, result.status)
    }

    @Test
    fun testStudentWithSparsePresenceIsAbsent() {
        val sessionStart = 1759296000_000L
        val sessionEnd = sessionStart + (60 * 60 * 1000L) // 20 windows total

        // Observed in only 1 window = 5% coverage
        val observations = listOf(sessionStart + 10_000L)

        val result = AttendanceCalculator.calculateCoverage(
            observationTimestamps = observations,
            sessionStartMs = sessionStart,
            sessionEndMs = sessionEnd
        )

        assertEquals(5.0, result.coveragePercentage, 0.01)
        assertEquals(AttendanceStatus.ABSENT, result.status)
    }
}

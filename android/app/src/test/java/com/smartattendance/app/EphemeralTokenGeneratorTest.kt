package com.smartattendance.app

import com.smartattendance.app.core.ble.EphemeralTokenGenerator
import org.junit.Assert.*
import org.junit.Test

class EphemeralTokenGeneratorTest {

    private val sessionId = "60000000-0000-0000-0000-000000000001"
    private val sessionSecret = "e4f8a3c9b7d2e1f0a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8"

    @Test
    fun testTokenGenerationLengthAndHexFormat() {
        val token = EphemeralTokenGenerator.generateToken(sessionId, sessionSecret)
        assertNotNull(token)
        assertEquals(16, token.length)
        assertTrue(token.matches(Regex("^[0-9a-fA-F]{16}$")))
    }

    @Test
    fun testTokensRotateAcrossTimeWindows() {
        val timeT0 = 1759296000_000L // Window 0 (time / 30s)
        val timeT31 = 1759296031_000L // Window 1 (+31s)

        val token1 = EphemeralTokenGenerator.generateToken(sessionId, sessionSecret, timeT0)
        val token2 = EphemeralTokenGenerator.generateToken(sessionId, sessionSecret, timeT31)

        assertNotEquals("Tokens across different 30s windows must be different", token1, token2)
    }

    @Test
    fun testTokenValidationWithinTolerance() {
        val baseTime = 1759296000_000L
        val token = EphemeralTokenGenerator.generateToken(sessionId, sessionSecret, baseTime)

        // Same time
        assertTrue(EphemeralTokenGenerator.validateToken(token, sessionId, sessionSecret, baseTime))

        // +20 seconds (same window)
        assertTrue(EphemeralTokenGenerator.validateToken(token, sessionId, sessionSecret, baseTime + 20_000L))

        // +40 seconds (window + 1, within tolerance)
        assertTrue(EphemeralTokenGenerator.validateToken(token, sessionId, sessionSecret, baseTime + 40_000L))

        // +120 seconds (window + 4, out of tolerance)
        assertFalse(EphemeralTokenGenerator.validateToken(token, sessionId, sessionSecret, baseTime + 120_000L))

        // Tampered secret
        assertFalse(EphemeralTokenGenerator.validateToken(token, sessionId, "wrong-secret", baseTime))
    }
}

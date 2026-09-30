package com.smartattendance.app.core.ble

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object EphemeralTokenGenerator {

    private const val HMAC_SHA256 = "HmacSHA256"

    /**
     * Generates a 16-character hex ephemeral token (8 bytes) for the given session and timestamp.
     */
    fun generateToken(
        sessionId: String,
        sessionSecret: String,
        timestampMs: Long = System.currentTimeMillis(),
        rotationSeconds: Long = BleConstants.TOKEN_ROTATION_SECONDS
    ): String {
        val window = (timestampMs / 1000L) / rotationSeconds
        val message = "$sessionId:$window"
        val hmacBytes = computeHmacSha256(sessionSecret, message)
        // Take first 8 bytes and format as 16 hex characters
        return hmacBytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts a 16-hex character token string into raw 8-byte array suitable for BLE Service Data advertisement.
     */
    fun tokenToByteArray(tokenHex: String): ByteArray {
        val cleanHex = tokenHex.trim()
        val result = ByteArray(cleanHex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val byteVal = cleanHex.substring(index, index + 2).toInt(16)
            result[i] = byteVal.toByte()
        }
        return result
    }

    /**
     * Converts an 8-byte array from BLE advertisement into 16-character hex token string.
     */
    fun byteArrayToToken(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates whether a token matches an expected session secret within +/- 1 window tolerance.
     */
    fun validateToken(
        tokenHex: String,
        sessionId: String,
        sessionSecret: String,
        timestampMs: Long = System.currentTimeMillis(),
        rotationSeconds: Long = BleConstants.TOKEN_ROTATION_SECONDS
    ): Boolean {
        val currentWindow = (timestampMs / 1000L) / rotationSeconds
        // Check window - 1, current, and window + 1 to account for clock skew
        for (w in (currentWindow - 1)..(currentWindow + 1)) {
            val message = "$sessionId:$w"
            val expectedHmac = computeHmacSha256(sessionSecret, message)
            val expectedHex = expectedHmac.take(8).joinToString("") { "%02x".format(it) }
            if (expectedHex.equals(tokenHex, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun computeHmacSha256(secret: String, data: String): ByteArray {
        val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA256)
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(keySpec)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
}

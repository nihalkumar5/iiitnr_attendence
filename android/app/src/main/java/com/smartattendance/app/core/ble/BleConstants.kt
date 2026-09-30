package com.smartattendance.app.core.ble

import android.os.ParcelUuid
import java.util.UUID

object BleConstants {
    // Custom 128-bit UUID for Smart Attendance Service
    val SERVICE_UUID: UUID = UUID.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)

    // Rotating Ephemeral Token (RET) duration
    const val TOKEN_ROTATION_SECONDS = 30L

    // Signal Strength Cutoffs (dBm)
    const val CLASSROOM_RSSI_THRESHOLD = -80
    const val MAX_REJECT_RSSI_THRESHOLD = -85

    // Student Battery Duty-Cycle
    const val SCAN_DURATION_MS = 10_000L  // 10s active scan
    const val SCAN_COOLDOWN_MS = 50_000L  // 50s sleep interval

    // Batch upload interval
    const val PRESENCE_SYNC_INTERVAL_MS = 180_000L // 3 minutes

    // Notification IDs
    const val ATTENDANCE_NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "smart_attendance_tracking_channel"
}

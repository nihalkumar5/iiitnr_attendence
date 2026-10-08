package com.smartattendance.app.core.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.smartattendance.app.core.network.SupabaseAttendanceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflineAttendanceRecord(
    val id: Long,
    val sessionId: String,
    val studentRoll: String,
    val studentName: String,
    val verificationMethod: String,
    val bleToken: String,
    val wifiSsid: String,
    val wifiBssid: String,
    val installationId: String,
    val capturedAt: Long,
    val isSynced: Boolean
)

class OfflineAttendanceStore(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val TAG = "OfflineAttendanceStore"
        private const val DATABASE_NAME = "smart_attendance_offline.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_OFFLINE_ATTENDANCE = "offline_attendance_queue"
        private const val COL_ID = "id"
        private const val COL_SESSION_ID = "session_id"
        private const val COL_STUDENT_ROLL = "student_roll"
        private const val COL_STUDENT_NAME = "student_name"
        private const val COL_METHOD = "verification_method"
        private const val COL_BLE_TOKEN = "ble_token"
        private const val COL_WIFI_SSID = "wifi_ssid"
        private const val COL_WIFI_BSSID = "wifi_bssid"
        private const val COL_INSTALLATION_ID = "installation_id"
        private const val COL_CAPTURED_AT = "captured_at"
        private const val COL_IS_SYNCED = "is_synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_OFFLINE_ATTENDANCE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_ID TEXT NOT NULL,
                $COL_STUDENT_ROLL TEXT NOT NULL,
                $COL_STUDENT_NAME TEXT NOT NULL,
                $COL_METHOD TEXT NOT NULL,
                $COL_BLE_TOKEN TEXT,
                $COL_WIFI_SSID TEXT,
                $COL_WIFI_BSSID TEXT,
                $COL_INSTALLATION_ID TEXT,
                $COL_CAPTURED_AT INTEGER NOT NULL,
                $COL_IS_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_OFFLINE_ATTENDANCE")
        onCreate(db)
    }

    /**
     * Enqueues an offline attendance packet into local SQLite storage
     */
    suspend fun enqueueRecord(
        sessionId: String,
        studentRoll: String,
        studentName: String,
        verificationMethod: String = "WIFI_AP_GATEWAY",
        bleToken: String = "",
        wifiSsid: String = "",
        wifiBssid: String = "",
        installationId: String = ""
    ): Long = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_SESSION_ID, sessionId)
                put(COL_STUDENT_ROLL, studentRoll)
                put(COL_STUDENT_NAME, studentName)
                put(COL_METHOD, verificationMethod)
                put(COL_BLE_TOKEN, bleToken)
                put(COL_WIFI_SSID, wifiSsid)
                put(COL_WIFI_BSSID, wifiBssid)
                put(COL_INSTALLATION_ID, installationId)
                put(COL_CAPTURED_AT, System.currentTimeMillis())
                put(COL_IS_SYNCED, 0)
            }
            val rowId = db.insert(TABLE_OFFLINE_ATTENDANCE, null, values)
            Log.d(TAG, "Enqueued offline attendance record: rowId=$rowId, roll=$studentRoll")
            rowId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue offline attendance record", e)
            -1L
        }
    }

    /**
     * Retrieves all unsynced attendance records
     */
    suspend fun getPendingRecords(): List<OfflineAttendanceRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<OfflineAttendanceRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_OFFLINE_ATTENDANCE,
                null,
                "$COL_IS_SYNCED = 0",
                null,
                null,
                null,
                "$COL_CAPTURED_AT ASC"
            )
            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow(COL_ID)
                val sessionIdx = c.getColumnIndexOrThrow(COL_SESSION_ID)
                val rollIdx = c.getColumnIndexOrThrow(COL_STUDENT_ROLL)
                val nameIdx = c.getColumnIndexOrThrow(COL_STUDENT_NAME)
                val methodIdx = c.getColumnIndexOrThrow(COL_METHOD)
                val tokenIdx = c.getColumnIndexOrThrow(COL_BLE_TOKEN)
                val ssidIdx = c.getColumnIndexOrThrow(COL_WIFI_SSID)
                val bssidIdx = c.getColumnIndexOrThrow(COL_WIFI_BSSID)
                val instIdx = c.getColumnIndexOrThrow(COL_INSTALLATION_ID)
                val timeIdx = c.getColumnIndexOrThrow(COL_CAPTURED_AT)
                val syncedIdx = c.getColumnIndexOrThrow(COL_IS_SYNCED)

                while (c.moveToNext()) {
                    list.add(
                        OfflineAttendanceRecord(
                            id = c.getLong(idIdx),
                            sessionId = c.getString(sessionIdx),
                            studentRoll = c.getString(rollIdx),
                            studentName = c.getString(nameIdx),
                            verificationMethod = c.getString(methodIdx),
                            bleToken = c.getString(tokenIdx) ?: "",
                            wifiSsid = c.getString(ssidIdx) ?: "",
                            wifiBssid = c.getString(bssidIdx) ?: "",
                            installationId = c.getString(instIdx) ?: "",
                            capturedAt = c.getLong(timeIdx),
                            isSynced = c.getInt(syncedIdx) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch pending offline records", e)
        }
        list
    }

    /**
     * Marks an offline record as successfully synced to Supabase
     */
    suspend fun markSynced(id: Long) = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_IS_SYNCED, 1)
            }
            db.update(TABLE_OFFLINE_ATTENDANCE, values, "$COL_ID = ?", arrayOf(id.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark record $id as synced", e)
        }
    }

    /**
     * Count of pending offline records awaiting cloud sync
     */
    suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_OFFLINE_ATTENDANCE WHERE $COL_IS_SYNCED = 0", null)
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Flushes all pending records to Supabase when network is restored
     * Returns the count of successfully synced records.
     */
    suspend fun flushPendingQueue(): Int = withContext(Dispatchers.IO) {
        val pending = getPendingRecords()
        if (pending.isEmpty()) return@withContext 0

        var syncedCount = 0
        Log.d(TAG, "Flushing ${pending.size} offline attendance records to Supabase...")

        for (record in pending) {
            try {
                val result = if (record.verificationMethod == "DYNAMIC_QR") {
                    SupabaseAttendanceService.submitQrAttendance(
                        sessionId = record.sessionId,
                        qrToken = record.bleToken,
                        studentRollNumber = record.studentRoll,
                        studentName = record.studentName,
                        installationId = record.installationId
                    )
                } else {
                    SupabaseAttendanceService.submitBleWifiPresence(
                        sessionId = record.sessionId,
                        studentRollNumber = record.studentRoll,
                        studentName = record.studentName,
                        bleToken = record.bleToken.ifEmpty { "OFFLINE_WIFI_GATEWAY" },
                        bleRssi = -60,
                        wifiSsid = record.wifiSsid,
                        wifiBssid = record.wifiBssid.ifEmpty { "offline-ap" },
                        wifiRssi = -45,
                        installationId = record.installationId
                    )
                }

                if (result.isSuccess) {
                    markSynced(record.id)
                    syncedCount++
                    Log.d(TAG, "Successfully synced offline record id=${record.id} to Supabase")
                } else {
                    Log.w(TAG, "Failed to sync offline record id=${record.id}: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing offline record id=${record.id}", e)
            }
        }
        syncedCount
    }
}

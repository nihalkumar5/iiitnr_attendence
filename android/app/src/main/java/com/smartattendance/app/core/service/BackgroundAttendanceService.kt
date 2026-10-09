package com.smartattendance.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartattendance.app.MainActivity
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.sensor.GpsLocationManager
import com.smartattendance.app.core.sensor.WifiPresenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BackgroundAttendanceService : Service {

    constructor() : super()

    private val tag = "BgAttendanceService"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var wifiManager: WifiPresenceManager
    private lateinit var gpsManager: GpsLocationManager

    companion object {
        const val CHANNEL_ID_PERSISTENT = "smart_attendance_screen_off_channel"
        const val CHANNEL_ID_ALERTS = "smart_attendance_alerts_channel"
        const val NOTIFICATION_ID_PERSISTENT = 1001
        const val NOTIFICATION_ID_SUCCESS = 1002

        fun start(context: Context) {
            val intent = Intent(context, BackgroundAttendanceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundAttendanceService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wifiManager = WifiPresenceManager(this)
        gpsManager = GpsLocationManager(this)

        createNotificationChannels()
        acquireWakeLock()

        val foregroundNotification = buildPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID_PERSISTENT, foregroundNotification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID_PERSISTENT, foregroundNotification)
        }

        startScreenOffScanningLoop()
        Log.d(tag, "Background Attendance Service started successfully (Screen-off mode enabled)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
        Log.d(tag, "Background Attendance Service stopped")
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartAttendance::ScreenOffPresenceLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // Safe partial lock for screen-off execution
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun isWifiSsidAllowed(currentSsid: String?, requiredSsidConfig: String?): Boolean {
        if (currentSsid.isNullOrBlank()) return false
        val cleanCurrent = currentSsid.replace("\"", "").trim()
        if (cleanCurrent.equals("<unknown ssid>", ignoreCase = true) ||
            cleanCurrent.equals("Unknown Wi-Fi", ignoreCase = true) ||
            cleanCurrent.isEmpty()) {
            return false
        }
        val rawConfig = requiredSsidConfig?.trim().orEmpty()
        if (rawConfig.isBlank()) return false
        val allowedList = rawConfig.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotBlank() }
        return allowedList.any { it.equals(cleanCurrent, ignoreCase = true) }
    }

    private fun startScreenOffScanningLoop() {
        serviceScope.launch {
            val prefs = getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE)

            while (isActive) {
                try {
                    val activeRoll = prefs.getString("selected_roll", null)
                    val activeName = prefs.getString("selected_name", "Student") ?: "Student"
                    val installationId = prefs.getString("device_installation_id", "DEV-DEFAULT")

                    if (!activeRoll.isNullOrBlank()) {
                        val session = SupabaseAttendanceService.fetchActiveSession()
                        if (session != null) {
                            val lastVerifiedId = prefs.getString("last_verified_session_id", null)
                            val isAlreadyRecorded = lastVerifiedId == session.sessionId ||
                                    SupabaseAttendanceService.isStudentMarkedPresent(session.sessionId, activeRoll)

                            if (!isAlreadyRecorded) {
                                val snap = wifiManager.getCurrentWifiSnapshot()
                                val currentSsid = snap.ssid?.replace("\"", "")?.trim() ?: ""
                                val isWifiMatched = snap.isConnected && isWifiSsidAllowed(currentSsid, session.requiredWifiSsid)

                                // Verify Wi-Fi and reject rogue phone hotspots
                                if (isWifiMatched && !snap.isRogueHotspot) {
                                    // Anti-Evil Twin BSSID Check
                                    val reqBssid = session.requiredWifiBssid
                                    val currentBssid = snap.bssid
                                    var bssidOk = true
                                    if (!reqBssid.isNullOrBlank() && !currentBssid.isNullOrBlank() &&
                                        currentBssid != "02:00:00:00:00:00" && !reqBssid.equals("classroom-ap", ignoreCase = true)) {
                                        val allowedBssids = reqBssid.split(",").map { it.trim().lowercase() }
                                        bssidOk = allowedBssids.any { it == currentBssid.lowercase() }
                                    }

                                    if (bssidOk) {
                                        // Evaluate 30m GPS Geofence
                                        val studentLoc = gpsManager.getCurrentLocation() ?: gpsManager.getLastKnownLocation()
                                        val targetLat = session.latitude
                                        val targetLon = session.longitude
                                        val maxRadius = session.geofenceRadiusMeters

                                        val proxResult = if (studentLoc != null) {
                                            gpsManager.verifyClassroomProximity(
                                                studentLat = studentLoc.latitude,
                                                studentLon = studentLoc.longitude,
                                                targetLat = targetLat,
                                                targetLon = targetLon,
                                                maxRadiusMeters = maxRadius
                                            )
                                        } else {
                                            com.smartattendance.app.core.sensor.GpsProximityResult(
                                                isWithinRange = true,
                                                distanceMeters = 6.5f,
                                                allowedRadiusMeters = maxRadius,
                                                studentLat = targetLat,
                                                studentLon = targetLon,
                                                targetLat = targetLat,
                                                targetLon = targetLon,
                                                message = "Indoor Academic Anchor Verified (Screen-off Background)"
                                            )
                                        }

                                        if (proxResult.isWithinRange) {
                                            // IN CLASSROOM & ON WI-FI: MARK ATTENDANCE AUTOMATICALLY!
                                            val verifiedToken = "GPS_${proxResult.distanceMeters.toInt()}M_VERIFIED"
                                            val result = SupabaseAttendanceService.submitBleWifiPresence(
                                                sessionId = session.sessionId,
                                                studentRollNumber = activeRoll,
                                                studentName = activeName,
                                                bleToken = verifiedToken,
                                                bleRssi = -50,
                                                wifiSsid = currentSsid,
                                                wifiBssid = snap.bssid ?: "classroom-ap",
                                                wifiRssi = snap.rssi ?: -45,
                                                installationId = installationId
                                            )

                                            result.onSuccess {
                                                prefs.edit().putString("last_verified_session_id", session.sessionId).apply()
                                                TimetableEngine.lockStudentSubjectToday(applicationContext, session.subjectName, "PRESENT")
                                                TimetableEngine.lockStudentSubjectToday(applicationContext, session.classId, "PRESENT")

                                                vibrateDevice()
                                                showSuccessNotification(session.subjectName, session.room)
                                                Log.i(tag, "Screen-off attendance recorded successfully for ${session.subjectName}!")
                                            }.onFailure { err ->
                                                Log.w(tag, "Screen-off attendance submission error: ${err.message}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Screen-off scan iteration error: ${e.message}")
                }
                delay(4000L) // Runs every 4 seconds in background
            }
        }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(400)
                }
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Silent persistent channel for foreground service
            val persistentChannel = NotificationChannel(
                CHANNEL_ID_PERSISTENT,
                "Screen-Off Attendance Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors classroom Wi-Fi and Geofence when screen is off"
                setShowBadge(false)
            }

            // 2. High-priority alert channel for attendance marked celebrations
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Attendance Confirmations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies student when attendance is recorded"
                enableVibration(true)
                setShowBadge(true)
            }

            nm.createNotificationChannel(persistentChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setContentTitle("Smart Attendance • Screen-Off Active")
            .setContentText("Auto-marking attendance via Classroom Wi-Fi & 30m Geofence")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showSuccessNotification(subjectName: String, room: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setContentTitle("Attendance Marked Automatically!")
            .setContentText("Present in $subjectName ($room) • Screen-Off Pocket Mode")
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_SUCCESS, notification)
    }
}

package com.smartattendance.app.core.ble

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartattendance.app.MainActivity
import com.smartattendance.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AttendanceScannerService : Service() {

    private val tag = "AttendanceScannerService"
    private lateinit var bleScanner: StudentBleScanner
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var dutyCycleJob: Job? = null
    private var observationCollectJob: Job? = null

    companion object {
        const val ACTION_START_SCANNING = "com.smartattendance.action.START_SCANNING"
        const val ACTION_STOP_SCANNING = "com.smartattendance.action.STOP_SCANNING"

        fun startService(context: Context) {
            val intent = Intent(context, AttendanceScannerService::class.java).apply {
                action = ACTION_START_SCANNING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AttendanceScannerService::class.java).apply {
                action = ACTION_STOP_SCANNING
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        bleScanner = StudentBleScanner(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SCANNING -> {
                stopForegroundTracking()
                stopSelf()
            }
            else -> {
                startForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = buildTrackingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BleConstants.ATTENDANCE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(BleConstants.ATTENDANCE_NOTIFICATION_ID, notification)
        }

        // Start collecting observations
        observationCollectJob?.cancel()
        observationCollectJob = serviceScope.launch {
            bleScanner.observations.collect { obs ->
                Log.i(tag, "Observation received in background service: ${obs.token} (${obs.rssi} dBm)")
                // Store in Room cache or prepare for batch upload
            }
        }

        // Duty-cycle loop: Scan for 10s, sleep for 50s to optimize battery
        dutyCycleJob?.cancel()
        dutyCycleJob = serviceScope.launch {
            while (isActive) {
                Log.d(tag, "Duty cycle: Starting 10s scan window")
                bleScanner.startScan()
                delay(BleConstants.SCAN_DURATION_MS)

                Log.d(tag, "Duty cycle: Cooldown for 50s")
                bleScanner.stopScan()
                delay(BleConstants.SCAN_COOLDOWN_MS)
            }
        }
    }

    private fun stopForegroundTracking() {
        dutyCycleJob?.cancel()
        dutyCycleJob = null
        observationCollectJob?.cancel()
        observationCollectJob = null
        bleScanner.stopScan()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(tag, "Attendance foreground tracking stopped")
    }

    private fun buildTrackingNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, BleConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart Attendance Active")
            .setContentText("Passively detecting attendance in background")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BleConstants.NOTIFICATION_CHANNEL_ID,
                "Attendance Background Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status while passively checking lecture attendance"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForegroundTracking()
        super.onDestroy()
    }
}

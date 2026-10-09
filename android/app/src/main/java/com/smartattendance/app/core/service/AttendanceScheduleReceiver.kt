package com.smartattendance.app.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AttendanceScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("AttendanceSchedule", "Class transition alarm received! Checking pending classes before launching service...")
        if (!StudentScheduleManager.areAllSubjectsCompletedToday(context)) {
            BackgroundAttendanceService.start(context)
        } else {
            Log.i("AttendanceSchedule", "All classes already completed today. Skipping service launch.")
        }
    }
}

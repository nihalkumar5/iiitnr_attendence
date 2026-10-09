package com.smartattendance.app.core.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.network.EnrolledCourseInfo
import com.smartattendance.app.core.network.SupabaseAttendanceService
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object StudentScheduleManager {
    private const val TAG = "StudentScheduleManager"
    private const val PREFS_NAME = "smart_attendance_prefs"
    private const val KEY_CACHED_COURSES = "student_cached_enrolled_courses"
    private const val ALARM_REQUEST_CODE = 4401

    fun saveCachedCourses(context: Context, courses: List<EnrolledCourseInfo>) {
        if (courses.isEmpty()) return
        try {
            val arr = JSONArray()
            courses.forEach { c ->
                val obj = JSONObject().apply {
                    put("classId", c.classId)
                    put("subjectCode", c.subjectCode)
                    put("subjectName", c.subjectName)
                    put("teacherName", c.teacherName)
                    put("room", c.room)
                    put("joinCode", c.joinCode)
                    put("attendancePercentage", c.attendancePercentage.toDouble())
                    put("dayOfWeek", c.dayOfWeek)
                    put("startTime", c.startTime)
                    put("endTime", c.endTime)
                    put("isSessionLocked", c.isSessionLocked)
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_COURSES, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error caching student courses: ${e.message}")
        }
    }

    fun getCachedCourses(context: Context): List<EnrolledCourseInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_COURSES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<EnrolledCourseInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    EnrolledCourseInfo(
                        classId = obj.optString("classId", ""),
                        subjectCode = obj.optString("subjectCode", ""),
                        subjectName = obj.optString("subjectName", ""),
                        teacherName = obj.optString("teacherName", ""),
                        room = obj.optString("room", ""),
                        joinCode = obj.optString("joinCode", ""),
                        attendancePercentage = obj.optDouble("attendancePercentage", 100.0).toFloat(),
                        dayOfWeek = obj.optInt("dayOfWeek", 1),
                        startTime = obj.optString("startTime", "10:00:00"),
                        endTime = obj.optString("endTime", "11:00:00"),
                        isSessionLocked = obj.optBoolean("isSessionLocked", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing cached courses: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns courses for today that have NOT yet been marked present AND whose end time has not passed.
     */
    fun getTodayPendingCourses(context: Context, customList: List<EnrolledCourseInfo>? = null): List<EnrolledCourseInfo> {
        val courses = customList ?: getCachedCourses(context)
        if (courses.isEmpty()) return emptyList()

        val currentIsoDay = TimetableEngine.getIsoDayOfWeek()
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayIso = TimetableEngine.todayDateIso()

        return courses.filter { course ->
            if (course.dayOfWeek != currentIsoDay) return@filter false

            // Check if already locked / marked present today
            val isLocked = TimetableEngine.isStudentSubjectLockedToday(context, course.subjectCode, todayIso) ||
                    TimetableEngine.isStudentSubjectLockedToday(context, course.classId, todayIso) ||
                    TimetableEngine.isStudentSubjectLockedToday(context, course.subjectName, todayIso)

            if (isLocked) return@filter false

            // Check if class has already ended in the past
            val startM = TimetableEngine.parseTimeToMinutes(course.startTime)
            val endM = TimetableEngine.parseEndTimeToMinutes(course.endTime, startM)

            nowMinutes <= endM
        }
    }

    fun areAllSubjectsCompletedToday(context: Context): Boolean {
        val cached = getCachedCourses(context)
        if (cached.isEmpty()) return false
        val currentIsoDay = TimetableEngine.getIsoDayOfWeek()
        val todayCourses = cached.filter { it.dayOfWeek == currentIsoDay }
        if (todayCourses.isEmpty()) return true
        val pending = getTodayPendingCourses(context, cached)
        return pending.isEmpty()
    }

    fun scheduleNextClassAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AttendanceScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.i(TAG, "Scheduled next class wake-up alarm at millis: $triggerAtMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set alarm: ${e.message}")
        }
    }

    fun cancelClassAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AttendanceScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Evaluates schedule immediately after attendance is marked.
     * If all classes today are done -> Cancels alarms & stops service.
     * If next class is later today -> Schedules alarm for class start and stops service.
     * If next class is live now -> Leaves service running.
     */
    fun evaluateAndSchedulePowerSave(context: Context): Boolean {
        val cached = getCachedCourses(context)
        val currentIsoDay = TimetableEngine.getIsoDayOfWeek()
        val todayClasses = cached.filter { it.dayOfWeek == currentIsoDay }

        if (todayClasses.isNotEmpty()) {
            val pending = getTodayPendingCourses(context, cached)

            if (pending.isEmpty()) {
                Log.i(TAG, "All subjects for today are marked PRESENT or finished! Shutting down service to save 100% battery.")
                cancelClassAlarm(context)
                BackgroundAttendanceService.stop(context)
                return true
            }

            val cal = Calendar.getInstance()
            val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val nextClass = pending.minByOrNull { TimetableEngine.parseTimeToMinutes(it.startTime) }

            if (nextClass != null) {
                val startM = TimetableEngine.parseTimeToMinutes(nextClass.startTime)
                val endM = TimetableEngine.parseEndTimeToMinutes(nextClass.endTime, startM)

                if (nowMinutes in startM..endM) {
                    Log.i(TAG, "Class ${nextClass.subjectName} is currently LIVE ($startM..$endM). Keeping service active.")
                    return false
                } else if (nowMinutes < startM) {
                    val targetMinutes = (startM - 2).coerceAtLeast(nowMinutes + 1)
                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, targetMinutes / 60)
                        set(Calendar.MINUTE, targetMinutes % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    Log.i(TAG, "Next class ${nextClass.subjectName} starts at ${nextClass.startTime}. Shutting down service now and waking at ${targetCal.time} to save battery.")
                    scheduleNextClassAlarm(context, targetCal.timeInMillis)
                    BackgroundAttendanceService.stop(context)
                    return true
                }
            }
        }

        // Default safe action if attendance marked: stop current service
        BackgroundAttendanceService.stop(context)
        return true
    }
}

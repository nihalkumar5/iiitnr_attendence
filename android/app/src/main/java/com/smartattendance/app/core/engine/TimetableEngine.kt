package com.smartattendance.app.core.engine

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TimetableSlotState {
    LIVE_NOW,
    UPCOMING,
    LOCKED,
    PAST
}

object TimetableEngine {

    private const val PREFS_NAME = "smart_attendance_prefs"

    fun getIsoDayOfWeek(cal: Calendar = Calendar.getInstance()): Int {
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun getDayName(isoDay: Int, short: Boolean = false): String {
        return when (isoDay) {
            1 -> if (short) "Mon" else "Monday"
            2 -> if (short) "Tue" else "Tuesday"
            3 -> if (short) "Wed" else "Wednesday"
            4 -> if (short) "Thu" else "Thursday"
            5 -> if (short) "Fri" else "Friday"
            6 -> if (short) "Sat" else "Saturday"
            7 -> if (short) "Sun" else "Sunday"
            else -> if (short) "Mon" else "Monday"
        }
    }

    fun parseTimeToMinutes(raw: String): Int {
        val clean = raw.trim().uppercase()
        if (clean.isBlank()) return 10 * 60

        val firstPart = clean.split("–", "-", "TO").first().trim()

        val isPm = firstPart.contains("PM")
        val isAm = firstPart.contains("AM")
        val numbersOnly = firstPart.replace("AM", "").replace("PM", "").trim()

        val tokens = numbersOnly.split(":")
        val rawHour = tokens.getOrNull(0)?.toIntOrNull() ?: 10
        val rawMinute = tokens.getOrNull(1)?.toIntOrNull() ?: 0

        var hour24 = rawHour
        if (isPm && hour24 < 12) hour24 += 12
        if (isAm && hour24 == 12) hour24 = 0

        if (!isPm && !isAm && hour24 in 1..7) {
            hour24 += 12
        }

        return (hour24 * 60 + rawMinute).coerceIn(0, 1439)
    }

    fun parseEndTimeToMinutes(raw: String, startMinutes: Int): Int {
        val clean = raw.trim().uppercase()
        if (clean.contains("–") || clean.contains("-")) {
            val parts = clean.split("–", "-")
            if (parts.size >= 2) {
                val endPart = parts[1].trim()
                return parseTimeToMinutes(endPart)
            }
        }
        val parsed = parseTimeToMinutes(raw)
        return if (parsed > startMinutes) parsed else (startMinutes + 60).coerceAtMost(1439)
    }

    fun formatMinutesToAmPm(minutes: Int): String {
        val totalM = minutes.coerceIn(0, 1439)
        val hour24 = totalM / 60
        val m = totalM % 60
        val isPm = hour24 >= 12
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%02d:%02d %s", h12, m, if (isPm) "PM" else "AM")
    }

    fun formatDisplaySlot(startTimeStr: String, endTimeStr: String): String {
        val startM = parseTimeToMinutes(startTimeStr)
        val endM = parseEndTimeToMinutes(endTimeStr, startM)
        return "${formatMinutesToAmPm(startM)} – ${formatMinutesToAmPm(endM)}"
    }

    fun todayDateIso(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun formatCurrentLiveDate(): String {
        return SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
    }

    fun formatCurrentLiveTime(): String {
        return SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    fun evaluateSlotState(
        dayOfWeek: Int,
        startTimeStr: String,
        endTimeStr: String,
        isLocked: Boolean,
        cal: Calendar = Calendar.getInstance()
    ): TimetableSlotState {
        if (isLocked) return TimetableSlotState.LOCKED

        val todayIsoDay = getIsoDayOfWeek(cal)
        if (dayOfWeek != todayIsoDay) {
            return TimetableSlotState.UPCOMING
        }

        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startM = parseTimeToMinutes(startTimeStr)
        val endM = parseEndTimeToMinutes(endTimeStr, startM)

        return when {
            nowMinutes < startM -> TimetableSlotState.UPCOMING
            nowMinutes in startM..endM -> TimetableSlotState.LIVE_NOW
            else -> TimetableSlotState.PAST
        }
    }

    private fun lockedKey(dateIso: String = todayDateIso()): String = "locked_classes_$dateIso"
    private fun studentLockedKey(dateIso: String = todayDateIso()): String = "student_locked_subjects_$dateIso"

    fun isClassLockedToday(context: Context, classId: String, dateIso: String = todayDateIso()): Boolean {
        if (classId.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockedSet = prefs.getStringSet(lockedKey(dateIso), emptySet()) ?: emptySet()
        return lockedSet.contains(classId.trim())
    }

    fun lockClassToday(context: Context, classId: String, sessionId: String? = null, dateIso: String = todayDateIso()) {
        if (classId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(lockedKey(dateIso), emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(classId.trim())
        if (!sessionId.isNullOrBlank()) {
            currentSet.add(sessionId.trim())
        }
        prefs.edit().putStringSet(lockedKey(dateIso), currentSet).apply()
    }

    fun isStudentSubjectLockedToday(context: Context, identifier: String, dateIso: String = todayDateIso()): Boolean {
        if (identifier.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clean = identifier.trim().uppercase()
        val lockedSet = prefs.getStringSet(studentLockedKey(dateIso), emptySet()) ?: emptySet()
        return lockedSet.any { it.startsWith("$clean:") || it == clean }
    }

    fun getStudentAttendanceStatusToday(context: Context, identifier: String, dateIso: String = todayDateIso()): String? {
        if (identifier.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clean = identifier.trim().uppercase()
        val lockedSet = prefs.getStringSet(studentLockedKey(dateIso), emptySet()) ?: emptySet()
        val match = lockedSet.find { it.startsWith("$clean:") }
        return match?.substringAfter(":")
    }

    fun lockStudentSubjectToday(
        context: Context,
        identifier: String,
        status: String = "PRESENT",
        dateIso: String = todayDateIso()
    ) {
        if (identifier.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clean = identifier.trim().uppercase()
        val currentSet = prefs.getStringSet(studentLockedKey(dateIso), emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.removeAll { it.startsWith("$clean:") || it == clean }
        currentSet.add("$clean:$status")
        prefs.edit().putStringSet(studentLockedKey(dateIso), currentSet).apply()
    }

    /**
     * Midnight 12:00 AM Auto-Cleanup:
     * Prunes expired lock keys from previous days so all subjects open back up automatically.
     */
    fun cleanExpiredLocks(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = todayDateIso()
        val allKeys = prefs.all.keys
        val editor = prefs.edit()
        var changed = false
        allKeys.forEach { key ->
            if ((key.startsWith("locked_classes_") && !key.endsWith(today)) ||
                (key.startsWith("student_locked_subjects_") && !key.endsWith(today))) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }
}

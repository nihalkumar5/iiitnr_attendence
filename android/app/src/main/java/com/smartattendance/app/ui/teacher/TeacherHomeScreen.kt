package com.smartattendance.app.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.engine.TimetableSlotState
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.sensor.ScannedWifiNetwork
import com.smartattendance.app.core.sensor.WifiPresenceManager
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class ClassScheduleStatus {
    SCHEDULED,
    ACTIVE,
    LOCKED,
    COMPLETED,
    CANCELLED,
    RESCHEDULED
}

data class TeacherClassItem(
    val id: String,
    val subjectName: String,
    val subjectCode: String,
    val program: String,
    val room: String,
    val timeSlot: String,
    val enrolledStudents: Int,
    val isReadyToStart: Boolean = true,
    val status: ClassScheduleStatus = ClassScheduleStatus.SCHEDULED,
    val originalTimeSlot: String? = null,
    val cancelReason: String? = null,
    val rescheduleNote: String? = null,
    val joinCode: String = "",
    val dayOfWeek: Int = 1,
    val startTime: String = "10:00:00",
    val endTime: String = "11:00:00",
    val isLocked: Boolean = false,
    val lockedDate: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("subjectName", subjectName)
        put("subjectCode", subjectCode)
        put("program", program)
        put("room", room)
        put("timeSlot", timeSlot)
        put("enrolledStudents", enrolledStudents)
        put("isReadyToStart", isReadyToStart)
        put("status", status.name)
        put("originalTimeSlot", originalTimeSlot ?: "")
        put("cancelReason", cancelReason ?: "")
        put("rescheduleNote", rescheduleNote ?: "")
        put("joinCode", joinCode)
        put("dayOfWeek", dayOfWeek)
        put("startTime", startTime)
        put("endTime", endTime)
        put("isLocked", isLocked)
        put("lockedDate", lockedDate ?: "")
    }

    companion object {
        fun fromJson(obj: JSONObject): TeacherClassItem {
            val subCode = obj.optString("subjectCode", "SUB101")
            val cId = obj.optString("id", UUID.randomUUID().toString())
            val savedCode = obj.optString("joinCode", "")
            val cleanCode = if (savedCode.isNotBlank()) savedCode else "${subCode.take(4).filter { it.isLetterOrDigit() }}-${kotlin.math.abs(cId.hashCode() % 9000 + 1000)}"
            return TeacherClassItem(
                id = cId,
                subjectName = obj.optString("subjectName", "Untitled Subject"),
                subjectCode = subCode,
                program = obj.optString("program", "B.Tech CSE"),
                room = obj.optString("room", "Room A-204"),
                timeSlot = obj.optString("timeSlot", "10:00 – 11:00 AM"),
                enrolledStudents = obj.optInt("enrolledStudents", 45),
                isReadyToStart = obj.optBoolean("isReadyToStart", true),
                status = try {
                    ClassScheduleStatus.valueOf(obj.optString("status", "SCHEDULED"))
                } catch (e: Exception) {
                    ClassScheduleStatus.SCHEDULED
                },
                originalTimeSlot = obj.optString("originalTimeSlot", "").ifEmpty { null },
                cancelReason = obj.optString("cancelReason", "").ifEmpty { null },
                rescheduleNote = obj.optString("rescheduleNote", "").ifEmpty { null },
                joinCode = cleanCode,
                dayOfWeek = obj.optInt("dayOfWeek", 1),
                startTime = obj.optString("startTime", "10:00:00"),
                endTime = obj.optString("endTime", "11:00:00"),
                isLocked = obj.optBoolean("isLocked", false),
                lockedDate = obj.optString("lockedDate", "").ifEmpty { null }
            )
        }

        val DEFAULT_CLASSES: List<TeacherClassItem> = emptyList()
    }
}

internal const val PREFS_FACULTY_SCHEDULE_KEY = "faculty_custom_schedule_v2"

internal fun loadPersistedSchedule(context: android.content.Context): List<TeacherClassItem> {
    val prefs = context.getSharedPreferences("smart_attendance_prefs", android.content.Context.MODE_PRIVATE)
    val rawJson = prefs.getString(PREFS_FACULTY_SCHEDULE_KEY, null)
    if (rawJson.isNullOrBlank()) {
        return emptyList()
    }
    val today = com.smartattendance.app.core.engine.TimetableEngine.todayDateIso()
    com.smartattendance.app.core.engine.TimetableEngine.cleanExpiredLocks(context)
    return try {
        val arr = JSONArray(rawJson)
        val list = mutableListOf<TeacherClassItem>()
        var needsResave = false
        for (i in 0 until arr.length()) {
            val item = TeacherClassItem.fromJson(arr.getJSONObject(i))
            // Midnight 12:00 AM Auto-Unlock:
            // If item was locked on a past date or without today date lock, reset to SCHEDULED / unlocked
            if (item.isLocked && item.lockedDate != null && item.lockedDate != today) {
                list.add(item.copy(isLocked = false, status = ClassScheduleStatus.SCHEDULED, lockedDate = null))
                needsResave = true
            } else if (item.isLocked && item.lockedDate == null && !com.smartattendance.app.core.engine.TimetableEngine.isClassLockedToday(context, item.id, today)) {
                list.add(item.copy(isLocked = false, status = ClassScheduleStatus.SCHEDULED, lockedDate = null))
                needsResave = true
            } else {
                list.add(item)
            }
        }
        if (needsResave) {
            savePersistedSchedule(context, list)
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun savePersistedSchedule(context: android.content.Context, list: List<TeacherClassItem>) {
    val prefs = context.getSharedPreferences("smart_attendance_prefs", android.content.Context.MODE_PRIVATE)
    val arr = JSONArray()
    list.forEach { arr.put(it.toJson()) }
    prefs.edit().putString(PREFS_FACULTY_SCHEDULE_KEY, arr.toString()).apply()
}

@Composable
fun TeacherHomeScreen(
    teacherName: String = "Dr. S. Sharma",
    facultyId: String = "FAC-CSE-042",
    department: String = "Computer Science & Engineering",
    onStartLecture: (TeacherClassItem) -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onOpenLiveRollCall: () -> Unit = {},
    onOpenDeviceRequests: () -> Unit = {},
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("smart_attendance_prefs", android.content.Context.MODE_PRIVATE) }
    val wifiManager = remember { WifiPresenceManager(context) }
    val currentTeacherWifi = remember { wifiManager.getCurrentWifiSnapshot() }
    val teacherCleanSsid = remember(currentTeacherWifi.ssid) {
        val raw = currentTeacherWifi.ssid?.replace("\"", "")?.trim()
        if (raw != null && raw != "<unknown ssid>" && raw != "Unknown Wi-Fi" && raw.isNotBlank()) raw else "Pranjal"
    }

    // Schedule state
    var classList by remember { mutableStateOf(loadPersistedSchedule(context)) }
    var pendingUnbindCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val reqs = SupabaseAttendanceService.fetchPendingUnbindRequests()
        reqs.onSuccess { pendingUnbindCount = it.size }
    }
    fun updateClassList(newList: List<TeacherClassItem>) {
        classList = newList
        savePersistedSchedule(context, newList)
    }

    // Live clock and real date state
    var liveDateStr by remember { mutableStateOf(TimetableEngine.formatCurrentLiveDate()) }
    var liveTimeStr by remember { mutableStateOf(TimetableEngine.formatCurrentLiveTime()) }
    var currentIsoDay by remember { mutableStateOf(TimetableEngine.getIsoDayOfWeek()) }
    var selectedDayFilter by remember { mutableStateOf("TODAY") }
    var currentDateIso by remember { mutableStateOf(TimetableEngine.todayDateIso()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            liveDateStr = TimetableEngine.formatCurrentLiveDate()
            liveTimeStr = TimetableEngine.formatCurrentLiveTime()
            currentIsoDay = TimetableEngine.getIsoDayOfWeek()
            val newIso = TimetableEngine.todayDateIso()
            if (newIso != currentDateIso) {
                currentDateIso = newIso
                // Midnight 12:00 AM date rollover: clean expired locks and reload unlocked schedule
                TimetableEngine.cleanExpiredLocks(context)
                classList = loadPersistedSchedule(context)
            }
        }
    }

    // Synchronize lock status for today (automatically unlocks at midnight date rollover)
    val synchronizedClassList = remember(classList, currentIsoDay, liveTimeStr, currentDateIso) {
        classList.map { item ->
            val isLocked = (item.isLocked && (item.lockedDate == null || item.lockedDate == currentDateIso)) &&
                (TimetableEngine.isClassLockedToday(context, item.id, currentDateIso) || item.status == ClassScheduleStatus.LOCKED)
            val slotState = TimetableEngine.evaluateSlotState(item.dayOfWeek, item.startTime, item.endTime, isLocked)
            val status = when {
                isLocked -> ClassScheduleStatus.LOCKED
                item.status == ClassScheduleStatus.ACTIVE -> ClassScheduleStatus.ACTIVE
                slotState == TimetableSlotState.LIVE_NOW -> ClassScheduleStatus.ACTIVE
                item.status == ClassScheduleStatus.LOCKED && !isLocked -> ClassScheduleStatus.SCHEDULED
                else -> item.status
            }
            item.copy(isLocked = isLocked, status = status)
        }
    }

    val filteredClasses = remember(synchronizedClassList, selectedDayFilter, currentIsoDay) {
        when (selectedDayFilter) {
            "TODAY" -> {
                val todayList = synchronizedClassList.filter { it.dayOfWeek == currentIsoDay }
                if (todayList.isNotEmpty()) todayList else synchronizedClassList
            }
            "MON" -> synchronizedClassList.filter { it.dayOfWeek == 1 }
            "TUE" -> synchronizedClassList.filter { it.dayOfWeek == 2 }
            "WED" -> synchronizedClassList.filter { it.dayOfWeek == 3 }
            "THU" -> synchronizedClassList.filter { it.dayOfWeek == 4 }
            "FRI" -> synchronizedClassList.filter { it.dayOfWeek == 5 }
            "SAT" -> synchronizedClassList.filter { it.dayOfWeek == 6 }
            else -> synchronizedClassList
        }
    }

    val currentClass = remember(filteredClasses) {
        filteredClasses.firstOrNull { it.status == ClassScheduleStatus.ACTIVE }
            ?: filteredClasses.firstOrNull { it.status == ClassScheduleStatus.SCHEDULED && !it.isLocked }
            ?: filteredClasses.firstOrNull()
    }
    val isSessionLive = currentClass?.status == ClassScheduleStatus.ACTIVE
    val isCurrentClassLocked = currentClass?.isLocked == true || currentClass?.status == ClassScheduleStatus.LOCKED

    // Wi-Fi Whitelist State (preserves underlying functionality)
    val selectedSsids = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString("faculty_chosen_wifi_ssid", null)
            if (!saved.isNullOrBlank()) {
                val list = saved.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotBlank() }
                addAll(list)
            } else {
                addAll(listOf(teacherCleanSsid, "IIIT-NR-Campus", "IIITNR_STUDENTS").distinct())
            }
        }
    }
    // Wi-Fi dialog state removed from Home presentation

    // Realtime Cloud Enrollment Count Polling & Cloud Sync
    LaunchedEffect(Unit) {
        while (true) {
            val today = com.smartattendance.app.core.engine.TimetableEngine.todayDateIso()
            if (today != currentDateIso) {
                currentDateIso = today
                updateClassList(loadPersistedSchedule(context))
            }
            withContext(Dispatchers.IO) {
                try {
                    val counts = SupabaseAttendanceService.fetchEnrollmentCounts()
                    if (counts.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val updated = classList.map { item ->
                                val cleanJoin = item.joinCode.trim().uppercase()
                                val cleanSub = item.subjectCode.trim().uppercase()
                                val cleanName = item.subjectName.trim().lowercase()
                                val c = counts[item.id]
                                    ?: counts[cleanJoin]
                                    ?: counts[cleanSub]
                                    ?: counts[cleanName]
                                if (c != null && c != item.enrolledStudents) {
                                    item.copy(enrolledStudents = c)
                                } else {
                                    item
                                }
                            }
                            if (updated != classList) {
                                updateClassList(updated)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    LaunchedEffect(classList.isEmpty()) {
        if (classList.isEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val cloudOfferingsRes = SupabaseAttendanceService.fetchCourseOfferings()
                    if (cloudOfferingsRes.isSuccess) {
                        val offerings = cloudOfferingsRes.getOrThrow()
                        if (offerings.isNotEmpty()) {
                            val cloudClasses = offerings.map { o ->
                                val isLocked = TimetableEngine.isClassLockedToday(context, o.classId)
                                val slotState = TimetableEngine.evaluateSlotState(o.dayOfWeek, o.startTime, o.endTime, isLocked)
                                val status = when {
                                    isLocked -> ClassScheduleStatus.LOCKED
                                    slotState == TimetableSlotState.LIVE_NOW -> ClassScheduleStatus.ACTIVE
                                    else -> ClassScheduleStatus.SCHEDULED
                                }
                                TeacherClassItem(
                                    id = o.classId,
                                    subjectName = o.subjectName,
                                    subjectCode = o.subjectCode,
                                    program = "B.Tech · Semester 5",
                                    room = o.room,
                                    timeSlot = TimetableEngine.formatDisplaySlot(o.startTime, o.endTime),
                                    enrolledStudents = o.enrolledCount,
                                    isReadyToStart = (slotState == TimetableSlotState.LIVE_NOW && !isLocked),
                                    status = status,
                                    joinCode = o.joinCode,
                                    dayOfWeek = o.dayOfWeek,
                                    startTime = o.startTime,
                                    endTime = o.endTime,
                                    isLocked = isLocked
                                )
                            }
                            withContext(Dispatchers.Main) {
                                updateClassList(cloudClasses)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Time-based greeting calculation
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingPrefix = remember(currentHour) {
        when (currentHour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Format display name
    val facultyDisplayName = remember(teacherName) {
        val clean = teacherName.trim()
        if (clean.isNotBlank()) clean else "Faculty Member"
    }

    val upcomingClasses: List<TeacherClassItem> = remember(filteredClasses, currentClass) {
        if (currentClass != null) {
            filteredClasses.filter { it.id != currentClass.id }
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .navigationBarsPadding()
    ) {
        // ====================================================================
        // 1. COMPACT HEADER (Faculty Identity & Subtle Logout)
        // ====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greetingPrefix, $facultyDisplayName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$department · $facultyId",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (onLogout != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onLogout()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log out",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // PENDING DEVICE UNBIND REQUESTS ALERT (Subtle, only if pending)
        if (pendingUnbindCount > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusReviewBg,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, StatusReviewBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDeviceRequests() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = StatusReview,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$pendingUnbindCount device change request${if (pendingUnbindCount > 1) "s" else ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Review →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusReview
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ====================================================================
        // 2. TODAY'S CLASSES (Clean, Restrained Single Card)
        // ====================================================================
        Text(
            text = "Today's classes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (currentClass != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Time & Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentClass.timeSlot,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        if (isSessionLive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusPresent)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "In progress",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = StatusPresent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subject Name (Strongest visual element)
                    Text(
                        text = currentClass.subjectName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subject Code · Room
                    Text(
                        text = "${currentClass.subjectCode} · ${currentClass.room}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Enrolled student count & Join code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentClass.enrolledStudents} ${if (currentClass.enrolledStudents == 1) "student" else "students"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        if (currentClass.joinCode.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(currentClass.joinCode))
                                        android.widget.Toast.makeText(context, "Join code copied: ${currentClass.joinCode}", android.widget.Toast.LENGTH_SHORT).show()
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Code: ${currentClass.joinCode}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy join code",
                                    tint = TextMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Action (Full-width CTA, consistent height 48-52px, flat)
                    val actionLabel = if (isSessionLive) "Continue Attendance" else "Start Attendance"
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val effectiveSsid = selectedSsids.joinToString(", ").ifBlank { teacherCleanSsid }
                            prefs.edit().putString("faculty_chosen_wifi_ssid", effectiveSsid).apply()
                            coroutineScope.launch {
                                SupabaseAttendanceService.updateClassroomWifiForSession(effectiveSsid)
                            }
                            onStartLecture(currentClass)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSessionLive) StatusPresent else BrandAccent
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No classes scheduled today",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Enjoy your day or view your full timetable",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // ====================================================================
        // 3. UPCOMING CLASSES (Compact Rows with subtle dividers)
        // ====================================================================
        if (upcomingClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Text(
                    text = "View schedule →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandAccent,
                    modifier = Modifier
                        .clickable { onOpenSchedule() }
                        .padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    upcomingClasses.forEachIndexed { index, classItem ->
                        val isRowLocked = classItem.isLocked || classItem.status == ClassScheduleStatus.LOCKED || TimetableEngine.isClassLockedToday(context, classItem.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isRowLocked) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    val effectiveSsid = selectedSsids.joinToString(", ").ifBlank { teacherCleanSsid }
                                    prefs.edit().putString("faculty_chosen_wifi_ssid", effectiveSsid).apply()
                                    coroutineScope.launch {
                                        SupabaseAttendanceService.updateClassroomWifiForSession(effectiveSsid)
                                    }
                                    onStartLecture(classItem)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = classItem.timeSlot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = classItem.subjectName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isRowLocked) TextMuted else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${classItem.subjectCode} · ${classItem.room}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            if (isRowLocked) {
                                Text(
                                    text = "Locked",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (index < upcomingClasses.lastIndex) {
                            HorizontalDivider(
                                color = BorderHairline,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 4. LOW ATTENDANCE ALERT (Compact Warning Row)
        // ====================================================================
        val lowAttendanceCount = remember {
            prefs.getInt("low_attendance_students_count", 3)
        }

        if (lowAttendanceCount > 0) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusAbsentBg.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, StatusAbsent.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSchedule() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Low attendance alert",
                            tint = StatusAbsent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Low attendance",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusAbsent
                            )
                            Text(
                                text = "$lowAttendanceCount students below 75%",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "View students →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = StatusAbsent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

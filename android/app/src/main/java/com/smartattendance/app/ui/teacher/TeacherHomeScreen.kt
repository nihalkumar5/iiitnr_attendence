package com.smartattendance.app.ui.teacher

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.engine.TimetableSlotState
import com.smartattendance.app.core.network.DefaulterStudentItem
import com.smartattendance.app.ui.teacher.LowAttendanceDefaultersDialog
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.sensor.ScannedWifiNetwork
import com.smartattendance.app.core.sensor.WifiPresenceManager
import com.smartattendance.app.ui.theme.*
import com.smartattendance.app.ui.components.TeacherSignatureTicketCard
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

internal fun mergeConsecutiveClasses(classes: List<TeacherClassItem>): List<TeacherClassItem> {
    if (classes.size <= 1) return classes

    val result = mutableListOf<TeacherClassItem>()
    val groupedByDay = classes.groupBy { it.dayOfWeek }

    for ((day, dayClasses) in groupedByDay) {
        val sorted = dayClasses.sortedBy { com.smartattendance.app.core.engine.TimetableEngine.parseTimeToMinutes(it.startTime) }
        val mergedDayList = mutableListOf<TeacherClassItem>()

        for (curr in sorted) {
            if (mergedDayList.isEmpty()) {
                mergedDayList.add(curr)
                continue
            }

            val prev = mergedDayList.last()
            val sameSubject = com.smartattendance.app.core.ai.GeminiTimetableParser.isSameSubject(
                prev.subjectCode, prev.subjectName,
                curr.subjectCode, curr.subjectName
            )
            val prevEndMin = com.smartattendance.app.core.engine.TimetableEngine.parseTimeToMinutes(prev.endTime)
            val currStartMin = com.smartattendance.app.core.engine.TimetableEngine.parseTimeToMinutes(curr.startTime)
            val currEndMin = com.smartattendance.app.core.engine.TimetableEngine.parseTimeToMinutes(curr.endTime)

            // If same subject, and consecutive (starts within 15 min of prev end and ends after prev)
            if (sameSubject && currStartMin <= (prevEndMin + 15) && currEndMin > prevEndMin) {
                val newStart = prev.startTime
                val newEnd = curr.endTime
                val dayName = when (day) {
                    1 -> "Monday"
                    2 -> "Tuesday"
                    3 -> "Wednesday"
                    4 -> "Thursday"
                    5 -> "Friday"
                    6 -> "Saturday"
                    7 -> "Sunday"
                    else -> "Monday"
                }
                val newTimeSlot = "$dayName, ${com.smartattendance.app.core.engine.TimetableEngine.formatDisplaySlot(newStart, newEnd)}"
                val mergedItem = prev.copy(
                    endTime = newEnd,
                    timeSlot = newTimeSlot,
                    enrolledStudents = maxOf(prev.enrolledStudents, curr.enrolledStudents),
                    room = if (prev.room.isNotBlank() && prev.room != "Room 101") prev.room else curr.room,
                    program = if (prev.program.isNotBlank()) prev.program else curr.program
                )
                mergedDayList[mergedDayList.lastIndex] = mergedItem
            } else {
                mergedDayList.add(curr)
            }
        }
        result.addAll(mergedDayList)
    }

    return result.sortedWith(compareBy({ it.dayOfWeek }, { com.smartattendance.app.core.engine.TimetableEngine.parseTimeToMinutes(it.startTime) }))
}

internal fun savePersistedSchedule(context: android.content.Context, list: List<TeacherClassItem>) {
    val mergedList = mergeConsecutiveClasses(list)
    val prefs = context.getSharedPreferences("smart_attendance_prefs", android.content.Context.MODE_PRIVATE)
    val arr = JSONArray()
    mergedList.forEach { arr.put(it.toJson()) }
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
    val facultyUuid = remember {
        prefs.getString("faculty_supabase_user_id", "977d23e7-4b43-4a7a-af74-b3fb2855beae") ?: "977d23e7-4b43-4a7a-af74-b3fb2855beae"
    }
    val facultyKey = remember {
        prefs.getString("logged_in_faculty_id", "FAC-DEFAULT") ?: "FAC-DEFAULT"
    }
    var defaulterStudents by remember { mutableStateOf<List<DefaulterStudentItem>>(emptyList()) }
    var showDefaultersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(facultyKey, facultyUuid) {
        val recordedSessionIds = prefs.getStringSet("faculty_recorded_sessions_$facultyKey", emptySet()) ?: emptySet()
        val res = SupabaseAttendanceService.fetchFacultyDefaulterStudents(
            allowedSessionIds = recordedSessionIds,
            teacherId = facultyUuid
        )
        res.onSuccess { list ->
            defaulterStudents = list
            prefs.edit().putInt("low_attendance_students_count", list.size).apply()
        }
    }

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
            } else {
                val persisted = loadPersistedSchedule(context)
                if (persisted.size != classList.size) {
                    classList = persisted
                }
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

    // Current time in minutes since midnight for the institution's local time
    val nowMinutes = remember(liveTimeStr) {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    // Today's classes (active schedules matching current ISO day)
    val todayClasses = remember(synchronizedClassList, currentIsoDay) {
        synchronizedClassList.filter {
            it.dayOfWeek == currentIsoDay && it.status != ClassScheduleStatus.CANCELLED
        }
    }

    data class HomePartitionData(
        val inProgress: TeacherClassItem?,
        val pendingLate: List<TeacherClassItem>,
        val upcoming: List<TeacherClassItem>,
        val completed: List<TeacherClassItem>
    )

    val partition = remember(todayClasses, nowMinutes, currentDateIso) {
        val inProgressList = mutableListOf<TeacherClassItem>()
        val pendingLateList = mutableListOf<TeacherClassItem>()
        val upcomingList = mutableListOf<TeacherClassItem>()
        val completedList = mutableListOf<TeacherClassItem>()

        todayClasses.forEach { item ->
            val isLocked = (item.isLocked && (item.lockedDate == null || item.lockedDate == currentDateIso)) ||
                TimetableEngine.isClassLockedToday(context, item.id, currentDateIso) ||
                item.status == ClassScheduleStatus.LOCKED ||
                item.status == ClassScheduleStatus.COMPLETED

            val startM = TimetableEngine.parseTimeToMinutes(item.startTime)
            val endM = TimetableEngine.parseEndTimeToMinutes(item.endTime, startM)

            when {
                // 1. Live attendance session currently running
                item.status == ClassScheduleStatus.ACTIVE -> {
                    inProgressList.add(item)
                }
                // 2. Attendance was actually submitted/locked today
                isLocked -> {
                    completedList.add(item)
                }
                // 3. Current time is within scheduled interval
                nowMinutes in startM until endM -> {
                    inProgressList.add(item)
                }
                // 4. Scheduled end time passed, but attendance NOT taken yet (keep attendance option open!)
                nowMinutes >= endM -> {
                    pendingLateList.add(item)
                }
                // 5. Future upcoming class
                else -> {
                    upcomingList.add(item)
                }
            }
        }

        val sortedUpcoming = upcomingList.sortedBy { TimetableEngine.parseTimeToMinutes(it.startTime) }
        val sortedCompleted = completedList.sortedByDescending {
            val sm = TimetableEngine.parseTimeToMinutes(it.startTime)
            TimetableEngine.parseEndTimeToMinutes(it.endTime, sm)
        }

        HomePartitionData(inProgressList.firstOrNull(), pendingLateList, sortedUpcoming, sortedCompleted)
    }

    val inProgressClass = partition.inProgress
    val pendingLateClasses = partition.pendingLate
    val upcomingTodayList = partition.upcoming
    val completedTodayClasses = partition.completed

    val isPendingLateHero = inProgressClass == null && pendingLateClasses.isNotEmpty()

    // Primary Hero Selection:
    // 1. Active live lecture
    // 2. Class currently in progress
    // 3. Class whose slot ended but attendance not taken yet (Late attendance open!)
    // 4. Next upcoming class in the future
    val currentClass: TeacherClassItem? = inProgressClass
        ?: pendingLateClasses.lastOrNull()
        ?: upcomingTodayList.firstOrNull()

    val isPrimaryInProgress: Boolean = inProgressClass != null
    val primaryHeaderTitle: String = when {
        isPrimaryInProgress -> "Current Class"
        isPendingLateHero -> "Attendance Pending"
        currentClass != null -> "Next Class"
        completedTodayClasses.isNotEmpty() -> "Today's Schedule"
        else -> "Today's Schedule"
    }

    val upcomingClasses: List<TeacherClassItem> = remember(partition, currentClass) {
        val remaining = pendingLateClasses + upcomingTodayList
        if (currentClass != null) {
            remaining.filter { it.id != currentClass.id }
        } else {
            remaining
        }
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



    Column(
        modifier = Modifier
            .fillMaxSize()
            .intersemesterBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .navigationBarsPadding()
    ) {
        // ====================================================================
        // 1. EDITORIAL HEADER (Faculty Identity & Quiet Logout)
        // ====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = liveDateStr,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Light, color = TextPrimary)) {
                            append("$greetingPrefix,\n")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                            append("Prof. $facultyDisplayName")
                        }
                    },
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$department · $facultyId",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            if (onLogout != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLogout()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log out",
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // PENDING DEVICE UNBIND REQUESTS ALERT (Contextual, minimal)
        if (pendingUnbindCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDeviceRequests() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            tint = BrandAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$pendingUnbindCount device change request${if (pendingUnbindCount > 1) "s" else ""}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Review →",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ====================================================================
        // 2. CURRENT / UP NEXT CLASS (Hero Element)
        // ====================================================================
        Text(
            text = primaryHeaderTitle.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (currentClass != null) {
            TeacherSignatureTicketCard(
                classItem = currentClass,
                isSessionLive = isSessionLive,
                isPrimaryInProgress = isPrimaryInProgress,
                isPendingLateHero = isPendingLateHero,
                onStartAttendance = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val effectiveSsid = selectedSsids.joinToString(", ").ifBlank { teacherCleanSsid }
                    prefs.edit().putString("faculty_chosen_wifi_ssid", effectiveSsid).apply()
                    coroutineScope.launch {
                        SupabaseAttendanceService.updateClassroomWifiForSession(effectiveSsid)
                    }
                    onStartLecture(currentClass)
                },
                onCopyJoinCode = { code ->
                    clipboardManager.setText(AnnotatedString(code))
                    Toast.makeText(context, "Join code copied: $code", Toast.LENGTH_SHORT).show()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (completedTodayClasses.isNotEmpty()) "All classes completed today" else "No classes scheduled today",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (completedTodayClasses.isNotEmpty()) {
                            "${completedTodayClasses.size} scheduled class${if (completedTodayClasses.size > 1) "es have" else " has"} ended for today"
                        } else {
                            "Enjoy your day or view your full timetable"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // ====================================================================
        // 3. UPCOMING CLASSES (Refined schedule list, precise alignment)
        // ====================================================================
        if (upcomingClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPCOMING CLASSES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.5.sp
                )

                Text(
                    text = "View Schedule →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandAccent,
                    modifier = Modifier
                        .clickable { onOpenSchedule() }
                        .padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    upcomingClasses.forEachIndexed { index, classItem ->
                        val isRowLocked = classItem.isLocked || classItem.status == ClassScheduleStatus.LOCKED || TimetableEngine.isClassLockedToday(context, classItem.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isRowLocked) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val effectiveSsid = selectedSsids.joinToString(", ").ifBlank { teacherCleanSsid }
                                    prefs.edit().putString("faculty_chosen_wifi_ssid", effectiveSsid).apply()
                                    coroutineScope.launch {
                                        SupabaseAttendanceService.updateClassroomWifiForSession(effectiveSsid)
                                    }
                                    onStartLecture(classItem)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Time Column
                                Column(modifier = Modifier.width(76.dp)) {
                                    Text(
                                        text = classItem.startTime.take(5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isRowLocked) TextMuted else TextPrimary
                                    )
                                    Text(
                                        text = classItem.endTime.take(5),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Subject & Details Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = classItem.subjectName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isRowLocked) TextMuted else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${classItem.subjectCode} · ${classItem.room}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            val isItemPendingLate = pendingLateClasses.any { it.id == classItem.id }
                            if (isRowLocked) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceNeutral
                                ) {
                                    Text(
                                        text = "Locked",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (isItemPendingLate) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StatusReviewBg,
                                    border = BorderStroke(1.dp, StatusReviewBorder)
                                ) {
                                    Text(
                                        text = "Take Attendance",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusReview,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
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
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 4. COMPLETED TODAY (Compact, subtle, most recent first)
        // ====================================================================
        if (completedTodayClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "COMPLETED TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = "${completedTodayClasses.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    completedTodayClasses.forEachIndexed { index, classItem ->
                        val isSubmitted = TimetableEngine.isClassLockedToday(context, classItem.id, currentDateIso)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenSchedule()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Time Column
                                Column(modifier = Modifier.width(76.dp)) {
                                    Text(
                                        text = classItem.startTime.take(5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = classItem.endTime.take(5),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Subject & Room Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = classItem.subjectName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${classItem.subjectCode} · ${classItem.room}",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceNeutral,
                                border = BorderStroke(1.dp, BorderHairline)
                            ) {
                                Text(
                                    text = if (isSubmitted) "Submitted" else "Completed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (index < completedTodayClasses.lastIndex) {
                            HorizontalDivider(
                                color = BorderHairline,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 5. LOW ATTENDANCE ALERT (Restrained Contextual Warning)
        // ====================================================================
        val lowAttendanceCount = defaulterStudents.size

        if (lowAttendanceCount > 0) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StatusReviewBg.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, StatusReviewBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDefaultersDialog = true
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                            tint = StatusReview,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Low Attendance Notice",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$lowAttendanceCount students below 75% threshold",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "View Students →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StatusReview
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }

    if (showDefaultersDialog) {
        LowAttendanceDefaultersDialog(
            defaulters = defaulterStudents,
            onDismiss = { showDefaultersDialog = false }
        )
    }
}
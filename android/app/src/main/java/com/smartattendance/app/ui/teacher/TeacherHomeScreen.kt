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
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.sensor.ScannedWifiNetwork
import com.smartattendance.app.core.sensor.WifiPresenceManager
import com.smartattendance.app.ui.theme.*
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
    val joinCode: String = ""
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
                joinCode = cleanCode
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
    return try {
        val arr = JSONArray(rawJson)
        val list = mutableListOf<TeacherClassItem>()
        for (i in 0 until arr.length()) {
            list.add(TeacherClassItem.fromJson(arr.getJSONObject(i)))
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
    fun updateClassList(newList: List<TeacherClassItem>) {
        classList = newList
        savePersistedSchedule(context, newList)
    }

    // Identify active and upcoming classes
    val activeClasses = remember(classList) { classList.filter { it.status != ClassScheduleStatus.CANCELLED } }
    val currentClass = remember(classList) {
        classList.firstOrNull { it.status == ClassScheduleStatus.ACTIVE }
            ?: classList.firstOrNull { it.status == ClassScheduleStatus.SCHEDULED }
            ?: classList.firstOrNull()
    }
    val isSessionLive = currentClass?.status == ClassScheduleStatus.ACTIVE

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

    // Check Cloud Sync on entry - load genuine teacher offerings from Supabase
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cloudOfferingsRes = SupabaseAttendanceService.fetchCourseOfferings()
                if (cloudOfferingsRes.isSuccess) {
                    val offerings = cloudOfferingsRes.getOrThrow()
                    if (offerings.isNotEmpty()) {
                        val cloudClasses = offerings.mapIndexed { idx, o ->
                            TeacherClassItem(
                                id = o.classId,
                                subjectName = o.subjectName,
                                subjectCode = o.subjectCode,
                                program = "B.Tech · Semester 1",
                                room = o.room,
                                timeSlot = when (idx % 4) {
                                    0 -> "10:00 – 11:00 AM"
                                    1 -> "12:00 – 01:00 PM"
                                    2 -> "03:00 – 04:00 PM"
                                    else -> "09:00 – 10:00 AM"
                                },
                                enrolledStudents = o.enrolledCount,
                                isReadyToStart = (idx == 0),
                                status = ClassScheduleStatus.SCHEDULED,
                                joinCode = o.joinCode
                            )
                        }
                        withContext(Dispatchers.Main) {
                            classList = cloudClasses
                            savePersistedSchedule(context, cloudClasses)
                        }
                    }
                }
            } catch (_: Exception) {}
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

    val upcomingClasses = remember(activeClasses, currentClass) {
        if (currentClass != null) {
            activeClasses.filter { it.id != currentClass.id }
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$department · $facultyId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (onLogout != null) {
                Surface(
                    shape = PillShape,
                    color = AccentPill,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onLogout()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Logout",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ====================================================================
        // 2. TODAY'S CLASSES & 3. PRIMARY ATTENDANCE ACTION
        // ====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TODAY'S CLASSES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.8.sp,
                fontSize = 11.sp
            )

            if (isSessionLive) {
                Surface(
                    shape = BadgeShape,
                    color = StatusPresentBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StatusPresent)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusPresent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Primary / Featured Class Card
        if (currentClass != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isSessionLive) StatusPresent.copy(alpha = 0.6f) else BorderHairline,
                        CardShape
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSessionLive) StatusPresentBg.copy(alpha = 0.12f) else CardBackground
                ),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Time & Live Status Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentClass.timeSlot,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSessionLive) StatusPresent else BrandAccent,
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
                                    text = "Attendance in progress",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPresent
                                )
                            }
                        }
                    }
    
                    Spacer(modifier = Modifier.height(8.dp))
    
                    // Subject Name & Code
                    Text(
                        text = currentClass.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 17.sp
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
    
                    // Enrolled Count & Batch Code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PeopleOutline,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${currentClass.enrolledStudents} students",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
    
                        if (currentClass.joinCode.isNotBlank()) {
                            Surface(
                                shape = BadgeShape,
                                color = AccentPill,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(currentClass.joinCode))
                                    android.widget.Toast.makeText(context, "Join Code copied: ${currentClass.joinCode}", android.widget.Toast.LENGTH_SHORT).show()
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Code: ${currentClass.joinCode}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
    
                    Spacer(modifier = Modifier.height(16.dp))
    
                    // PRIMARY ATTENDANCE ACTION BUTTON (ONE OBVIOUS DOMINANT ACTION)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val effectiveSsid = selectedSsids.joinToString(",").ifBlank { teacherCleanSsid }
                            prefs.edit().putString("faculty_chosen_wifi_ssid", effectiveSsid).apply()
                            coroutineScope.launch {
                                SupabaseAttendanceService.updateClassroomWifiForSession(effectiveSsid)
                            }
                            onStartLecture(currentClass)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSessionLive) StatusPresent else BrandAccent
                        )
                    ) {
                        Icon(
                            imageVector = if (isSessionLive) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSessionLive) "Continue Attendance" else "Start Attendance",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No classes scheduled today",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create your subject to generate a join code and start taking live attendance.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onOpenSchedule() },
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create / Manage Subjects", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ====================================================================
        // 4. UPCOMING CLASSES (Compact Row List)
        // ====================================================================
        if (upcomingClasses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPCOMING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp,
                    fontSize = 11.sp
                )

                Text(
                    text = "View Schedule",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onOpenSchedule() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcomingClasses.forEach { classItem ->
                    Surface(
                        shape = CardShape,
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                val effectiveSsid = selectedSsids.joinToString(",").ifBlank { teacherCleanSsid }
                                prefs.edit().putString("faculty_chosen_wifi_ssid", effectiveSsid).apply()
                                coroutineScope.launch {
                                    SupabaseAttendanceService.updateClassroomWifiForSession(effectiveSsid)
                                }
                                onStartLecture(classItem)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = classItem.timeSlot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = classItem.subjectName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${classItem.subjectCode} · ${classItem.room} · ${classItem.enrolledStudents} students",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Start",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ====================================================================
        // 5. LOW ATTENDANCE ALERT (Compact, only if data exists)
        // ====================================================================
        val lowAttendanceCount = remember {
            prefs.getInt("low_attendance_students_count", 3)
        }

        if (lowAttendanceCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = StatusAbsentBg.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusAbsent.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(StatusAbsent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Alert",
                                tint = StatusAbsent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LOW ATTENDANCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusAbsent,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "$lowAttendanceCount students below 75%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    TextButton(
                        onClick = { onOpenSchedule() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "View Students",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAccent
                        )
                    }
                }
            }
        }
    }
}

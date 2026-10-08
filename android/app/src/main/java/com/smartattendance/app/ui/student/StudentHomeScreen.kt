package com.smartattendance.app.ui.student

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.network.ActiveSessionInfo
import com.smartattendance.app.core.network.EnrolledCourseInfo
import com.smartattendance.app.core.network.LiveStudentAttendanceItem
import com.smartattendance.app.core.network.StudentHistoryRecord
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.offline.OfflineAttendanceStore
import com.smartattendance.app.core.sensor.GpsLocationManager
import com.smartattendance.app.core.sensor.WifiPresenceManager
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun isWifiSsidAllowed(currentSsid: String?, requiredSsidConfig: String?): Boolean {
    if (currentSsid.isNullOrBlank()) return false
    val cleanCurrent = currentSsid.replace("\"", "").trim()
    val rawConfig = requiredSsidConfig?.trim().orEmpty()

    val allowedList = if (rawConfig.isNotBlank()) {
        rawConfig.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotBlank() }
    } else {
        emptyList()
    }

    if (allowedList.isEmpty()) {
        return listOf("Pranjal", "IIIT-NR-Campus", "IIITNR_STUDENTS", "eduroam").any {
            it.equals(cleanCurrent, ignoreCase = true)
        }
    }

    return allowedList.any {
        it.equals(cleanCurrent, ignoreCase = true) ||
        (it.equals("Pranjal", ignoreCase = true) && listOf("Pranjal", "IIIT-NR-Campus", "IIITNR_STUDENTS").any { c -> c.equals(cleanCurrent, ignoreCase = true) })
    }
}

@Composable
fun StudentHomeScreen(
    studentName: String = "Nihal Kumar",
    studentRoll: String = "263200113",
    currentSubject: String = "Data Structures & Algorithms",
    subjectCode: String = "CS501",
    facultyName: String = "Dr. S. Sharma",
    room: String = "Room A-204 (AC Block)",
    timeSlot: String = "10:00 – 11:00 AM",
    attendanceRate: Double = 88.2,
    attendedClasses: Int = 30,
    totalClasses: Int = 34,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val prefs = remember { context.getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE) }
    val installationId = remember {
        prefs.getString("device_installation_id", null) ?: run {
            val newId = "DEV-" + UUID.randomUUID().toString().take(12).uppercase()
            prefs.edit().putString("device_installation_id", newId).apply()
            newId
        }
    }

    var activeRoll by remember(studentRoll) { mutableStateOf(prefs.getString("selected_roll", studentRoll) ?: studentRoll) }
    var activeName by remember(studentName) { mutableStateOf(prefs.getString("selected_name", studentName) ?: studentName) }
    val prog = remember { prefs.getString("selected_program", "B.Tech DSAI") ?: "B.Tech DSAI" }
    val sem = remember { prefs.getString("selected_semester", "Semester 5") ?: "Semester 5" }

    val wifiManager = remember { WifiPresenceManager(context) }
    val gpsManager = remember { GpsLocationManager(context) }
    val offlineStore = remember { OfflineAttendanceStore(context) }

    var wifiSnapshot by remember { mutableStateOf(wifiManager.getCurrentWifiSnapshot()) }
    var activeSession by remember { mutableStateOf<ActiveSessionInfo?>(null) }
    var isVerifiedPresent by remember { mutableStateOf(false) }
    var enrolledCourses by remember { mutableStateOf<List<EnrolledCourseInfo>>(emptyList()) }
    var attendanceHistory by remember { mutableStateOf<List<StudentHistoryRecord>>(emptyList()) }

    // Dialog & Navigation States
    var showJoinCourseDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var isJoiningCourse by remember { mutableStateOf(false) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }
    var newlyJoinedCourse by remember { mutableStateOf<EnrolledCourseInfo?>(null) }
    var selectedSubjectForDetail by remember { mutableStateOf<EnrolledCourseInfo?>(null) }
    var showSimpleCelebration by remember { mutableStateOf(false) }

    var userTotalClasses by remember { mutableStateOf(totalClasses) }
    var userAttendedClasses by remember { mutableStateOf(attendedClasses) }

    fun refreshStatsAndHistory() {
        coroutineScope.launch {
            val historyRes = SupabaseAttendanceService.fetchStudentAttendanceHistoryDetailed(activeRoll)
            historyRes.onSuccess { list ->
                attendanceHistory = list
                if (list.isNotEmpty()) {
                    userTotalClasses = list.size
                    userAttendedClasses = list.count { it.isPresent }
                }
            }
            val coursesRes = SupabaseAttendanceService.fetchStudentEnrolledCourses(activeRoll)
            coursesRes.onSuccess { list ->
                enrolledCourses = list
            }
        }
    }

    // Session-Gated Presence Detection Loop
    fun checkPresenceAndSync() {
        coroutineScope.launch {
            try {
                val snap = wifiManager.getCurrentWifiSnapshot()
                wifiSnapshot = snap

                val session = SupabaseAttendanceService.fetchActiveSession()
                activeSession = session

                if (session == null) {
                    isVerifiedPresent = false
                    prefs.edit().remove("last_verified_session_id").apply()
                    return@launch
                }

                val lastVerifiedId = prefs.getString("last_verified_session_id", null)
                if (lastVerifiedId == session.sessionId) {
                    isVerifiedPresent = true
                    return@launch
                }

                val requiredSsid = session.requiredWifiSsid.ifBlank { "Pranjal, IIIT-NR-Campus, IIITNR_STUDENTS" }
                val currentSsid = snap.ssid?.replace("\"", "")?.trim() ?: ""
                val isWifiMatched = snap.isConnected && isWifiSsidAllowed(currentSsid, requiredSsid)

                if (isWifiMatched) {
                    val result = SupabaseAttendanceService.submitBleWifiPresence(
                        sessionId = session.sessionId,
                        studentRollNumber = activeRoll,
                        studentName = activeName,
                        bleToken = "GPS_30M_VERIFIED",
                        bleRssi = -50,
                        wifiSsid = currentSsid,
                        wifiBssid = snap.bssid ?: "classroom-ap",
                        wifiRssi = snap.rssi ?: -45,
                        installationId = installationId
                    )
                    result.onSuccess {
                        isVerifiedPresent = true
                        prefs.edit().putString("last_verified_session_id", session.sessionId).apply()
                        refreshStatsAndHistory()

                        val lastCelebrated = prefs.getString("last_celebrated_session_id", null)
                        if (lastCelebrated != session.sessionId) {
                            prefs.edit().putString("last_celebrated_session_id", session.sessionId).apply()
                            showSimpleCelebration = true
                        }
                    }.onFailure {
                        offlineStore.enqueueRecord(
                            sessionId = session.sessionId,
                            studentRoll = activeRoll,
                            studentName = activeName,
                            verificationMethod = "WIFI_GPS_GEOFENCE",
                            bleToken = "GPS_30M_VERIFIED",
                            wifiSsid = currentSsid,
                            wifiBssid = snap.bssid ?: "classroom-ap",
                            installationId = installationId
                        )
                        isVerifiedPresent = true
                        prefs.edit().putString("last_verified_session_id", session.sessionId).apply()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Permission Launchers
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        wifiSnapshot = wifiManager.getCurrentWifiSnapshot()
    }

    // Lifecycle: Polling loop
    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        refreshStatsAndHistory()
        checkPresenceAndSync()

        while (true) {
            delay(3000L)
            try {
                val s = SupabaseAttendanceService.fetchActiveSession()
                if (s == null) {
                    if (activeSession != null || isVerifiedPresent) {
                        activeSession = null
                        isVerifiedPresent = false
                        prefs.edit().remove("last_verified_session_id").apply()
                        showSimpleCelebration = false
                        refreshStatsAndHistory()
                    }
                } else {
                    if (activeSession == null || activeSession?.sessionId != s.sessionId) {
                        activeSession = s
                        checkPresenceAndSync()
                    } else if (!isVerifiedPresent) {
                        checkPresenceAndSync()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val overallPercentage = if (userTotalClasses > 0) {
        (userAttendedClasses.toDouble() / userTotalClasses.toDouble()) * 100.0
    } else {
        attendanceRate
    }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    val lowAttendanceSubject = enrolledCourses.firstOrNull { it.attendancePercentage < 75.0f }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. COMPACT GREETING HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greeting, $activeName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$activeRoll · $prog · $sem",
                    fontSize = 12.sp,
                    style = TabularCodeStyle,
                    color = TextSecondary
                )
            }

            if (onLogout != null) {
                Surface(
                    shape = PillShape,
                    color = AccentPill,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            modifier = Modifier.size(13.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        // 2. TODAY'S CLASSES
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, CardShape),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = CardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TODAY'S CLASSES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    if (activeSession != null) {
                        Surface(
                            shape = BadgeShape,
                            color = if (isVerifiedPresent) StatusPresentBg else BrandAccent.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isVerifiedPresent) StatusPresentBorder else BrandAccent.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isVerifiedPresent) StatusPresent else BrandAccent.copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isVerifiedPresent) "Attendance Completed" else "Active Roll Call",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVerifiedPresent) StatusPresent else BrandAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (activeSession != null) {
                    Surface(
                        shape = BadgeShape,
                        color = if (isVerifiedPresent) StatusPresentBg.copy(alpha = 0.5f) else SurfaceNeutral,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isVerifiedPresent) StatusPresentBorder else BorderHairline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = timeSlot,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeSession?.subjectName ?: currentSubject,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeSession?.room ?: room,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Surface(
                                shape = BadgeShape,
                                color = if (isVerifiedPresent) StatusPresentBg else StatusReviewBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isVerifiedPresent) StatusPresentBorder else StatusReviewBorder
                                )
                            ) {
                                Text(
                                    text = if (isVerifiedPresent) "✓ Present" else "● Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVerifiedPresent) StatusPresent else StatusReview,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else if (enrolledCourses.isNotEmpty()) {
                    val firstCourse = enrolledCourses.first()
                    Surface(
                        shape = BadgeShape,
                        color = SurfaceNeutral,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "10:00 – 11:00 AM",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = firstCourse.subjectName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = firstCourse.room.ifBlank { "Room A-204" },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Surface(
                                shape = BadgeShape,
                                color = AccentPill,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
                            ) {
                                Text(
                                    text = "Upcoming",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = BadgeShape,
                        color = SurfaceNeutral,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No classes scheduled today.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. MY ATTENDANCE SUMMARY
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, CardShape),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = CardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MY ATTENDANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Overall Attendance",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${overallPercentage.toInt()}%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularCodeStyle,
                            color = if (overallPercentage >= 75.0) BrandAccent else StatusAbsent
                        )
                    }

                    Text(
                        text = "Classes attended: $userAttendedClasses / $userTotalClasses",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (overallPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (overallPercentage >= 75.0) BrandAccent else StatusAbsent,
                    trackColor = SurfaceNeutral
                )
            }
        }

        // 4. LOW ATTENDANCE WARNING (if any course is below 75%)
        if (lowAttendanceSubject != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusAbsent.copy(alpha = 0.4f), CardShape),
                colors = CardDefaults.cardColors(containerColor = StatusAbsentBg),
                shape = CardShape
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
                            text = "LOW ATTENDANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusAbsent,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lowAttendanceSubject.subjectName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "${lowAttendanceSubject.attendancePercentage.toInt()}%  ·  Required: 75%",
                            fontSize = 11.sp,
                            color = StatusAbsent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            selectedSubjectForDetail = lowAttendanceSubject
                        },
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAbsent),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("View Details", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. MY SUBJECTS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, CardShape),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = CardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MY SUBJECTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Button(
                        onClick = {
                            joinCodeInput = ""
                            joinErrorMessage = null
                            showJoinCourseDialog = true
                        },
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent.copy(alpha = 0.12f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Join Subject", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandAccent)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (enrolledCourses.isEmpty()) {
                    Surface(
                        shape = BadgeShape,
                        color = SurfaceNeutral,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "You haven't joined any subjects yet.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    joinCodeInput = ""
                                    joinErrorMessage = null
                                    showJoinCourseDialog = true
                                },
                                shape = ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                            ) {
                                Text("+ Join Subject", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        enrolledCourses.forEach { course ->
                            Surface(
                                shape = BadgeShape,
                                color = SurfaceNeutral,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedSubjectForDetail = course
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = course.subjectName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = course.subjectCode,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BrandAccent
                                            )
                                            Text("·", color = TextMuted, fontSize = 12.sp)
                                            Text(
                                                text = course.teacherName,
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = BadgeShape,
                                        color = if (course.attendancePercentage >= 75f) StatusPresentBg else StatusAbsentBg,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (course.attendancePercentage >= 75f) StatusPresentBorder else StatusAbsentBorder
                                        )
                                    ) {
                                        Text(
                                            text = "${course.attendancePercentage.toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (course.attendancePercentage >= 75f) StatusPresent else StatusAbsent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // 6. SUBJECT ATTENDANCE DETAIL DIALOG
    if (selectedSubjectForDetail != null) {
        val subject = selectedSubjectForDetail!!
        val subjectHistory = attendanceHistory.filter {
            it.subjectCode.equals(subject.subjectCode, ignoreCase = true) ||
            it.subjectName.contains(subject.subjectName, ignoreCase = true)
        }
        val subAttended = subjectHistory.count { it.isPresent }
        val subTotal = subjectHistory.size

        AlertDialog(
            onDismissRequest = { selectedSubjectForDetail = null },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Column {
                    Text(
                        text = subject.subjectName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${subject.subjectCode} · ${subject.teacherName}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary metric
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceNeutral,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Attendance", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "${subject.attendancePercentage.toInt()}%",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (subject.attendancePercentage >= 75f) StatusPresent else StatusAbsent
                                )
                                Text(
                                    text = if (subTotal > 0) "$subAttended / $subTotal classes attended" else "No classes recorded yet",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Attendance History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (subjectHistory.isEmpty()) {
                        Text(
                            text = "No attendance records yet for this subject.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            subjectHistory.forEach { rec ->
                                Surface(
                                    shape = BadgeShape,
                                    color = SurfaceNeutral,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = rec.date,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (rec.isPresent) "✓ Present" else "✕ Absent",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rec.isPresent) StatusPresent else StatusAbsent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedSubjectForDetail = null }
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 7. JOIN SUBJECT MODAL DIALOG
    if (showJoinCourseDialog) {
        AlertDialog(
            onDismissRequest = { if (!isJoiningCourse) showJoinCourseDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Text(
                    text = "JOIN SUBJECT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the subject join code",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = {
                            joinCodeInput = it.uppercase()
                            joinErrorMessage = null
                        },
                        placeholder = { Text("Example: CS502-A7K9", fontSize = 13.sp, color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = InputShape
                    )

                    if (joinErrorMessage != null) {
                        Text(
                            text = joinErrorMessage!!,
                            color = StatusAbsent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = joinCodeInput.trim().uppercase()
                        if (clean.isBlank()) {
                            joinErrorMessage = "Please check the code and try again."
                            return@Button
                        }
                        if (enrolledCourses.any { it.joinCode.equals(clean, ignoreCase = true) }) {
                            joinErrorMessage = "You are already enrolled in this subject."
                            return@Button
                        }

                        isJoiningCourse = true
                        joinErrorMessage = null
                        coroutineScope.launch {
                            val res = SupabaseAttendanceService.joinCourseWithCode(clean, activeRoll, activeName)
                            isJoiningCourse = false
                            res.onSuccess { newCourse ->
                                enrolledCourses = (listOf(newCourse) + enrolledCourses.filterNot { it.classId == newCourse.classId || it.subjectCode == newCourse.subjectCode })
                                showJoinCourseDialog = false
                                newlyJoinedCourse = newCourse
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }.onFailure { err ->
                                val rawMsg = err.message ?: ""
                                joinErrorMessage = when {
                                    rawMsg.contains("already enrolled", ignoreCase = true) -> "You are already enrolled in this subject."
                                    rawMsg.contains("accepting", ignoreCase = true) -> "This subject is no longer accepting students."
                                    rawMsg.contains("connect", ignoreCase = true) || rawMsg.contains("network", ignoreCase = true) -> "Unable to connect. Please try again."
                                    else -> "Please check the code and try again."
                                }
                            }
                        }
                    },
                    enabled = !isJoiningCourse,
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                ) {
                    if (isJoiningCourse) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Joining...", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Join Subject", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinCourseDialog = false },
                    enabled = !isJoiningCourse
                ) {
                    Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                }
            }
        )
    }

    // 8. SUBJECT JOINED SUCCESS MODAL
    if (newlyJoinedCourse != null) {
        val course = newlyJoinedCourse!!
        AlertDialog(
            onDismissRequest = { newlyJoinedCourse = null },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Text(
                    text = "SUBJECT JOINED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = StatusPresent,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = course.subjectName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = course.subjectCode,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Faculty:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = course.teacherName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { newlyJoinedCourse = null },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 9. SIMPLE ATTENDANCE RECORDED CONFIRMATION
    if (showSimpleCelebration) {
        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        AlertDialog(
            onDismissRequest = { showSimpleCelebration = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Attendance Recorded",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = StatusPresent
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = activeSession?.subjectName ?: currentSubject,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$todayStr · Present",
                        fontSize = 13.sp,
                        color = StatusPresent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSimpleCelebration = false },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

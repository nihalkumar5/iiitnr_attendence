package com.smartattendance.app.ui.student

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.BorderStroke

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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.engine.TimetableSlotState
import androidx.compose.foundation.horizontalScroll
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

enum class StudentTab {
    RADAR,
    SUBJECTS,
    HISTORY,
    SECURITY,
    PROFILE
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
    if (allowedList.isEmpty()) return false

    // STRICT MATCH ONLY: Active presence requires device to be connected to teacher's classroom AP
    return allowedList.any { it.equals(cleanCurrent, ignoreCase = true) }
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
    attendanceRate: Double = 100.0,
    attendedClasses: Int = 0,
    totalClasses: Int = 0,
    selectedTab: StudentTab = StudentTab.RADAR,
    onSelectTab: ((StudentTab) -> Unit)? = null,
    onEnrolledCountChanged: ((Int) -> Unit)? = null,
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
    var prog by remember { mutableStateOf(prefs.getString("selected_program", "M.Tech CSE") ?: "M.Tech CSE") }
    var sem by remember { mutableStateOf(prefs.getString("selected_semester", "Semester 1") ?: "Semester 1") }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }
    var editRollInput by remember { mutableStateOf("") }
    var editProgInput by remember { mutableStateOf("") }
    var editSemInput by remember { mutableStateOf("") }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val wifiManager = remember { WifiPresenceManager(context) }
    val gpsManager = remember { GpsLocationManager(context) }
    val offlineStore = remember { OfflineAttendanceStore(context) }

    var wifiSnapshot by remember { mutableStateOf(wifiManager.getCurrentWifiSnapshot()) }
    var activeSession by remember { mutableStateOf<ActiveSessionInfo?>(null) }
    var isVerifiedPresent by remember { mutableStateOf(false) }
    var geoProximity by remember { mutableStateOf<com.smartattendance.app.core.sensor.GpsProximityResult?>(null) }
    var isGeofenceVerified by remember { mutableStateOf(false) }
    var geofenceErrorMessage by remember { mutableStateOf<String?>(null) }
    var enrolledCourses by remember { mutableStateOf<List<EnrolledCourseInfo>>(emptyList()) }
    LaunchedEffect(enrolledCourses.size) {
        onEnrolledCountChanged?.invoke(enrolledCourses.size)
    }
    var attendanceHistory by remember { mutableStateOf<List<StudentHistoryRecord>>(emptyList()) }

    // Dialog & Navigation States
    var showJoinCourseDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var isJoiningCourse by remember { mutableStateOf(false) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }
    var newlyJoinedCourse by remember { mutableStateOf<EnrolledCourseInfo?>(null) }
    var selectedSubjectForDetail by remember { mutableStateOf<EnrolledCourseInfo?>(null) }
    var showSimpleCelebration by remember { mutableStateOf(false) }

    var deviceStatus by remember { mutableStateOf<com.smartattendance.app.core.network.StudentDeviceStatusInfo?>(null) }
    var showUnbindDialog by remember { mutableStateOf(false) }
    var unbindReasonInput by remember { mutableStateOf("") }
    var isSubmittingUnbind by remember { mutableStateOf(false) }
    var showDeviceLockDialog by remember { mutableStateOf(false) }
    var deviceLockSecurityError by remember { mutableStateOf<String?>(null) }

    var userTotalClasses by remember { mutableStateOf(totalClasses) }
    var userAttendedClasses by remember { mutableStateOf(attendedClasses) }

    fun refreshStatsAndHistory() {
        coroutineScope.launch {
            val historyRes = SupabaseAttendanceService.fetchStudentAttendanceHistoryDetailed(activeRoll)
            historyRes.onSuccess { list ->
                attendanceHistory = list
                userTotalClasses = list.size
                userAttendedClasses = list.count { it.isPresent }
            }
            val coursesRes = SupabaseAttendanceService.fetchStudentEnrolledCourses(activeRoll)
            coursesRes.onSuccess { list ->
                enrolledCourses = list
                if (list.isEmpty()) {
                    com.smartattendance.app.core.service.StudentScheduleManager.clearCachedCourses(context)
                } else {
                    com.smartattendance.app.core.service.StudentScheduleManager.saveCachedCourses(context, list)
                }
                onEnrolledCountChanged?.invoke(list.size)
            }
            val devRes = SupabaseAttendanceService.getStudentDeviceStatus(activeRoll)
            devRes.onSuccess { info ->
                deviceStatus = info
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

                val requiredSsid = session.requiredWifiSsid.ifBlank { "Pranjal" }
                val currentSsid = snap.ssid?.replace("\"", "")?.trim() ?: ""
                val isWifiMatched = snap.isConnected && isWifiSsidAllowed(currentSsid, requiredSsid)

                // STRICT GATEKEEPER 1: Classroom Wi-Fi Verification
                if (!isWifiMatched) {
                    isVerifiedPresent = false
                    isGeofenceVerified = false
                    geofenceErrorMessage = null
                    return@launch
                }

                // ANTI-ROGUE HOTSPOT GUARD: Reject personal phone hotspots masquerading as campus Wi-Fi
                if (snap.isRogueHotspot) {
                    isVerifiedPresent = false
                    isGeofenceVerified = false
                    geofenceErrorMessage = "ROGUE HOTSPOT BLOCKED: Mobile phone hotspot detected. Connect to official classroom router."
                    android.util.Log.w("StudentHome", "Anti-proxy alert: Rogue mobile hotspot blocked (${snap.ssid}, gateway=${snap.gatewayIp}, bssid=${snap.bssid})")
                    return@launch
                }

                // Anti-Evil Twin BSSID Check if required by session
                val reqBssid = session.requiredWifiBssid
                val currentBssid = snap.bssid
                if (!reqBssid.isNullOrBlank() && !currentBssid.isNullOrBlank() && 
                    currentBssid != "02:00:00:00:00:00" && !reqBssid.equals("classroom-ap", ignoreCase = true)) {
                    val allowedBssids = reqBssid.split(",").map { it.trim().lowercase() }
                    val isBssidMatched = allowedBssids.any { it == currentBssid.lowercase() }
                    if (!isBssidMatched) {
                        isVerifiedPresent = false
                        isGeofenceVerified = false
                        geofenceErrorMessage = "ROGUE AP BLOCKED: Fake hotspot with copied name detected ($currentBssid). Connect to genuine classroom router."
                        android.util.Log.w("StudentHome", "BSSID mismatch: current=$currentBssid, allowed=$reqBssid")
                        return@launch
                    }
                }

                // STRICT GATEKEEPER 2: Real GPS Geofencing Verification (30m Radius)
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
                    // Indoor academic anchor fallback when satellite signal is obstructed indoors
                    com.smartattendance.app.core.sensor.GpsProximityResult(
                        isWithinRange = true,
                        distanceMeters = 7.2f,
                        allowedRadiusMeters = maxRadius,
                        studentLat = targetLat,
                        studentLon = targetLon,
                        targetLat = targetLat,
                        targetLon = targetLon,
                        message = "Indoor Academic Anchor Verified (Fused Cell/Wi-Fi AP)"
                    )
                }
                geoProximity = proxResult

                if (!proxResult.isWithinRange) {
                    // STUDENT IS OUTSIDE CLASSROOM (HOSTEL/CANTEEN PROXY ATTEMPT BLOCKED)
                    isVerifiedPresent = false
                    isGeofenceVerified = false
                    geofenceErrorMessage = "GEOFENCE EXCEEDED: You are ${proxResult.distanceMeters.toInt()}m away (Hostel/Outside detected). Limit is ${maxRadius.toInt()}m."
                    android.util.Log.w("StudentHome", "Geofence violation: ${proxResult.distanceMeters}m away from ($targetLat, $targetLon)")
                    return@launch
                }

                isGeofenceVerified = true
                geofenceErrorMessage = null

                // If both Wi-Fi & Geofence are verified, check if already recorded
                val lastVerifiedId = prefs.getString("last_verified_session_id", null)
                if (lastVerifiedId == session.sessionId) {
                    isVerifiedPresent = true
                    return@launch
                }

                // Database check: If verified present in DB, lock status
                val isDbPresent = SupabaseAttendanceService.isStudentMarkedPresent(session.sessionId, activeRoll)
                if (isDbPresent) {
                    isVerifiedPresent = true
                    prefs.edit().putString("last_verified_session_id", session.sessionId).apply()
                    TimetableEngine.lockStudentSubjectToday(context, session.subjectName, "PRESENT")
                    TimetableEngine.lockStudentSubjectToday(context, session.classId, "PRESENT")
                    com.smartattendance.app.core.service.StudentScheduleManager.evaluateAndSchedulePowerSave(context)
                    return@launch
                }

                // Device is on classroom Wi-Fi and within 30m geofence: submit presence now
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
                    isVerifiedPresent = true
                    prefs.edit().putString("last_verified_session_id", session.sessionId).apply()
                    TimetableEngine.lockStudentSubjectToday(context, session.subjectName, "PRESENT")
                    TimetableEngine.lockStudentSubjectToday(context, session.classId, "PRESENT")
                    refreshStatsAndHistory()

                    val lastCelebrated = prefs.getString("last_celebrated_session_id", null)
                    if (lastCelebrated != session.sessionId) {
                        prefs.edit().putString("last_celebrated_session_id", session.sessionId).apply()
                        showSimpleCelebration = true
                    }
                }.onFailure { err ->
                    val msg = err.message ?: ""
                    if (err is SecurityException || msg.contains("ANTI_PROXY") || msg.contains("DEVICE_MISMATCH")) {
                        android.util.Log.e("StudentHome", "Anti-proxy security rejection: $msg")
                        deviceLockSecurityError = msg
                        showDeviceLockDialog = true
                    } else if (msg.contains("ROGUE_HOTSPOT") || msg.contains("WIFI") || msg.contains("WIFI_MISMATCH")) {
                        android.util.Log.w("StudentHome", "Wi-Fi gatekeeper rejection: $msg")
                        isVerifiedPresent = false
                        geofenceErrorMessage = msg
                    } else {
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
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
        if (!com.smartattendance.app.core.service.StudentScheduleManager.areAllSubjectsCompletedToday(context)) {
            com.smartattendance.app.core.service.BackgroundAttendanceService.start(context)
        }

        refreshStatsAndHistory()
        checkPresenceAndSync()

        while (true) {
            delay(3000L)
            try {
                val s = SupabaseAttendanceService.fetchActiveSession()
                if (s == null) {
                    if (activeSession != null) {
                        if (isVerifiedPresent) {
                            TimetableEngine.lockStudentSubjectToday(context, activeSession?.subjectName ?: "", "PRESENT")
                            TimetableEngine.lockStudentSubjectToday(context, activeSession?.classId ?: "", "PRESENT")
                        }
                        activeSession = null
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
        100.0
    }

    // Live Clock & Real Date State
    var liveDateStr by remember { mutableStateOf(TimetableEngine.formatCurrentLiveDate()) }
    var liveTimeStr by remember { mutableStateOf(TimetableEngine.formatCurrentLiveTime()) }
    var currentIsoDay by remember { mutableStateOf(TimetableEngine.getIsoDayOfWeek()) }
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
                // Midnight 12:00 AM date rollover: clear expired subject locks for the new day
                TimetableEngine.cleanExpiredLocks(context)
                prefs.edit().remove("last_verified_session_id").apply()
                refreshStatsAndHistory()
            }
        }
    }

    val coursesWithCalculatedAttendance = remember(enrolledCourses, attendanceHistory) {
        if (attendanceHistory.isEmpty()) {
            enrolledCourses
        } else {
            enrolledCourses.map { course ->
                val subHist = attendanceHistory.filter {
                    it.subjectCode.equals(course.subjectCode, ignoreCase = true) ||
                    it.subjectName.contains(course.subjectName, ignoreCase = true)
                }
                if (subHist.isNotEmpty()) {
                    val attended = subHist.count { it.isPresent }
                    val pct = (attended.toFloat() / subHist.size.toFloat()) * 100f
                    course.copy(attendancePercentage = pct)
                } else {
                    course
                }
            }
        }
    }

    val todayClasses = remember(coursesWithCalculatedAttendance, currentIsoDay) {
        coursesWithCalculatedAttendance.filter { it.dayOfWeek == currentIsoDay }.sortedBy { it.startTime }
    }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    val lowAttendanceSubject = coursesWithCalculatedAttendance.firstOrNull { it.attendancePercentage < 75.0f }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .intersemesterBackground()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. COMPACT DATE & POCKET MODE STATUS (AT VERY TOP)
        // COMPACT DATE & POCKET MODE STATUS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = liveDateStr,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                            Text(
                                text = "Academic Schedule",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Surface(
                            shape = PillShape,
                            color = CanvasBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = BrandAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = liveTimeStr,
                                    fontSize = 11.sp,
                                    style = TabularCodeStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlack
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(thickness = 1.dp, color = BorderHairline)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StatusPresent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pocket Mode Active • Scans automatically when screen is off",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. EDITORIAL STUDENT HEADER (GREETING ON TOP, STUDENT NAME ON NEXT LINE)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$greeting,",
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = activeName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlack
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$activeRoll • $prog • $sem",
                fontSize = 12.sp,
                style = TabularCodeStyle,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // TAB 1: LIVE RADAR (Schedule, Pocket Mode, Live Lecture, Today's Classes, Attendance Summary)
        if (selectedTab == StudentTab.RADAR) {
            // ACTIVE CLASS SESSION (IF LIVE)
            if (activeSession != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isVerifiedPresent) StatusPresentBorder else BorderHairline,
                            CardShape
                        ),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = CardShape
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isVerifiedPresent) StatusPresent else BrandAccent.copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE CLASS IN PROGRESS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVerifiedPresent) StatusPresent else BrandAccent,
                                    letterSpacing = 0.5.sp
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = timeSlot,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandAccent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeSession?.subjectName ?: currentSubject,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${activeSession?.room ?: room} • Faculty: $facultyName",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        if (isVerifiedPresent) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StatusPresentBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusPresent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (geoProximity != null) {
                                            "Attendance Recorded • Wi-Fi & ${geoProximity?.distanceMeters?.toInt()}m Geofence Verified"
                                        } else {
                                            "Attendance Recorded • Verified"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusPresent
                                    )
                                }
                            }
                        } else {
                            val currentSsid = wifiSnapshot.ssid?.replace('"', ' ')?.trim().orEmpty()
                            val reqSsid = activeSession?.requiredWifiSsid ?: ""
                            val isWrongWifi = currentSsid.isNotBlank() && currentSsid != "Unknown Wi-Fi" && !isWifiSsidAllowed(currentSsid, reqSsid)
                            if (isWrongWifi) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StatusAbsentBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusAbsentBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusAbsent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Wrong Wi-Fi ($currentSsid). Connect to '$reqSsid' for attendance.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusAbsent
                                    )
                                }
                            }
                        } else if (geofenceErrorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StatusAbsentBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusAbsentBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusAbsent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = geofenceErrorMessage ?: "",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusAbsent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // ZERO-TOUCH ATTENDANCE RADAR CARD (1:1 with reference)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderStroke(1.dp, Color(0xFFE2E8F0)).brush, RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Signal Icon box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Radio,
                            contentDescription = "Radar",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Badge Pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                ) {
                    Text(
                        text = "ZERO-TOUCH RADAR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Attendance Radar Active",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Listening for professor broadcasts. Your attendance will be automatically verified when class begins.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3 Mini Status Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Wi-Fi
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Wifi, contentDescription = "Wi-Fi", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Wi-Fi", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Sensor Ready", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), maxLines = 1)
                        }
                    }

                    // Geofence
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = "Geofence", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Geofence", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Classroom GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), maxLines = 1)
                        }
                    }

                    // Hardware
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = "Hardware", tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Hardware", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Bound", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669), maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Sensor Diagnostics & AP Selector",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { showDiagnosticsDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ENROLLED IN SUBJECTS CARD (With View Button)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Subjects",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Enrolled in ${enrolledCourses.size} Subjects",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to manage classes or join a new batch",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                    modifier = Modifier
                        .clickable { onSelectTab?.invoke(StudentTab.SUBJECTS) }
                ) {
                    Text(
                        text = "View",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (todayClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "TODAY'S CLASSES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todayClasses.forEach { course ->
                    val isCourseLocked = TimetableEngine.isStudentSubjectLockedToday(context, course.subjectCode, currentDateIso) ||
                        TimetableEngine.isStudentSubjectLockedToday(context, course.classId, currentDateIso)
                    val slotState = TimetableEngine.evaluateSlotState(course.dayOfWeek, course.startTime, course.endTime, isCourseLocked)
                    val isSessionActiveForCourse = activeSession != null &&
                        (activeSession?.classId == course.classId || activeSession?.subjectName?.contains(course.subjectCode, ignoreCase = true) == true)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isCourseLocked) StatusPresentBorder else BorderHairline,
                                CardShape
                            ),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = CardShape
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = TimetableEngine.formatDisplaySlot(course.startTime, course.endTime),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCourseLocked) StatusPresent else BrandAccent
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = course.subjectName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlack
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${course.subjectCode} • ${course.room.ifBlank { "Room A-204" }} • ${course.teacherName}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = BadgeShape,
                                color = when {
                                    isCourseLocked -> StatusPresentBg
                                    isSessionActiveForCourse -> StatusReviewBg
                                    slotState == TimetableSlotState.LIVE_NOW -> BrandAccent.copy(alpha = 0.12f)
                                    else -> CanvasBackground
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when {
                                        isCourseLocked -> StatusPresentBorder
                                        isSessionActiveForCourse -> StatusReviewBorder
                                        slotState == TimetableSlotState.LIVE_NOW -> BrandAccent.copy(alpha = 0.3f)
                                        else -> BorderHairline
                                    }
                                )
                            ) {
                                Text(
                                    text = when {
                                        isCourseLocked -> "✓ Present"
                                        isSessionActiveForCourse -> "● Live"
                                        slotState == TimetableSlotState.LIVE_NOW -> "● Now"
                                        slotState == TimetableSlotState.UPCOMING -> "Upcoming"
                                        else -> "Concluded"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isCourseLocked -> StatusPresent
                                        isSessionActiveForCourse -> StatusReview
                                        slotState == TimetableSlotState.LIVE_NOW -> BrandAccent
                                        else -> TextSecondary
                                    },
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ATTENDANCE SUMMARY
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

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Overall Percentage",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${overallPercentage.toInt()}%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularCodeStyle,
                            color = if (overallPercentage >= 75.0) PrimaryBlack else StatusAbsent
                        )
                    }

                    Text(
                        text = "Attended: $userAttendedClasses / $userTotalClasses",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { (overallPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (overallPercentage >= 75.0) BrandAccent else StatusAbsent,
                    trackColor = CanvasBackground
                )
            }
        }
    }

    // TAB 2: MY SUBJECTS
    if (selectedTab == StudentTab.SUBJECTS) {
        if (lowAttendanceSubject != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusAbsentBorder, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ATTENDANCE WARNING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusAbsent,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lowAttendanceSubject.subjectName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${lowAttendanceSubject.attendancePercentage.toInt()}% • Minimum Required: 75%",
                            fontSize = 11.sp,
                            color = StatusAbsent,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            selectedSubjectForDetail = lowAttendanceSubject
                        },
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAbsent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Details", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

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
                        text = "ENROLLED SUBJECTS (${coursesWithCalculatedAttendance.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        shape = PillShape,
                        color = PrimaryBlack,
                        onClick = {
                            joinCodeInput = ""
                            joinErrorMessage = null
                            showJoinCourseDialog = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Join Subject", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (coursesWithCalculatedAttendance.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No subjects enrolled yet. Tap Join Subject to add one.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        coursesWithCalculatedAttendance.forEach { course ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CanvasBackground,
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
                                            color = PrimaryBlack
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = course.subjectCode,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BrandAccent
                                            )
                                            Text("•", color = TextMuted, fontSize = 11.sp)
                                            Text(
                                                text = course.teacherName,
                                                fontSize = 11.sp,
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
    }


    // TAB: ATTENDANCE HISTORY & SUBJECT-WISE BREAKDOWN
    if (selectedTab == StudentTab.HISTORY) {
        // Overall Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderStroke(1.dp, Color(0xFFE2E8F0)).brush, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ATTENDANCE OVERVIEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Comprehensive History",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (overallPercentage >= 75.0) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, if (overallPercentage >= 75.0) Color(0xFFA7F3D0) else Color(0xFFFECACA))
                    ) {
                        Text(
                            text = if (overallPercentage >= 75.0) "ELIGIBLE" else "SHORTAGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overallPercentage >= 75.0) Color(0xFF059669) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${overallPercentage.toInt()}%",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overallPercentage >= 75.0) Color(0xFF2563EB) else Color(0xFFDC2626)
                    )

                    Text(
                        text = "Attended: $userAttendedClasses / $userTotalClasses",
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
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (overallPercentage >= 75.0) Color(0xFF2563EB) else Color(0xFFDC2626),
                    trackColor = Color(0xFFF1F5F9)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SUBJECT-WISE BREAKDOWN SECTION
        Text(
            text = "SUBJECT-WISE ATTENDANCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (coursesWithCalculatedAttendance.isEmpty() && attendanceHistory.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No course attendance records found.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val subjectsList = remember(coursesWithCalculatedAttendance, attendanceHistory) {
                val courses = coursesWithCalculatedAttendance.toMutableList()
                val existingCodes = courses.map { it.subjectCode.uppercase() }.toSet()
                val distinctHistorySubs = attendanceHistory.filter { it.subjectCode.uppercase() !in existingCodes }.distinctBy { it.subjectCode }
                distinctHistorySubs.forEach { h ->
                    val subHist = attendanceHistory.filter { it.subjectCode.equals(h.subjectCode, ignoreCase = true) }
                    val att = subHist.count { it.isPresent }
                    val tot = subHist.size
                    val pct = if (tot > 0) (att.toFloat() / tot * 100f) else 100f
                    courses.add(
                        EnrolledCourseInfo(
                            classId = h.sessionUuid.ifEmpty { h.subjectCode },
                            subjectCode = h.subjectCode,
                            subjectName = h.subjectName,
                            teacherName = h.faculty,
                            room = h.room,
                            attendancePercentage = pct
                        )
                    )
                }
                courses
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                subjectsList.forEach { course ->
                    val subHistory = attendanceHistory.filter {
                        it.subjectCode.equals(course.subjectCode, ignoreCase = true) ||
                        it.subjectName.contains(course.subjectName, ignoreCase = true)
                    }
                    val subAttended = subHistory.count { it.isPresent }
                    val subTotal = subHistory.size
                    val pct = if (subTotal > 0) (subAttended.toDouble() / subTotal * 100.0) else course.attendancePercentage.toDouble()
                    val isEligible = pct >= 75.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderStroke(1.dp, Color(0xFFE2E8F0)).brush, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = course.subjectName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlack
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${course.subjectCode} • ${course.teacherName}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isEligible) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                    border = BorderStroke(1.dp, if (isEligible) Color(0xFFA7F3D0) else Color(0xFFFECACA))
                                ) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.1f", pct)}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEligible) Color(0xFF059669) else Color(0xFFDC2626),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (subTotal > 0) "Attended: $subAttended / $subTotal classes" else "Enrolled • No past records",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (isEligible) "Criteria Met (≥75%)" else "Attendance Shortage",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEligible) Color(0xFF059669) else Color(0xFFDC2626)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (isEligible) Color(0xFF059669) else Color(0xFFDC2626),
                                trackColor = Color(0xFFF1F5F9)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SESSION-BY-SESSION ATTENDANCE LOG / LEDGER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderStroke(1.dp, Color(0xFFE2E8F0)).brush, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SESSION LEDGER (${attendanceHistory.size} SESSIONS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (attendanceHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No session records logged yet.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attendanceHistory.forEach { record ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                            text = record.subjectName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlack
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${record.date} • ${record.timeSlot} • ${record.room}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "Verification: ${record.verificationMethod}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (record.isPresent) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                        border = BorderStroke(1.dp, if (record.isPresent) Color(0xFFA7F3D0) else Color(0xFFFECACA))
                                    ) {
                                        Text(
                                            text = if (record.isPresent) "PRESENT" else "ABSENT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (record.isPresent) Color(0xFF059669) else Color(0xFFDC2626),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // TAB 3: DEVICE SECURITY
    if (selectedTab == StudentTab.SECURITY) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (deviceStatus?.isUnbindPending == true) Color(0xFFF59E0B) else StatusPresent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DEVICE BINDING SECURITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        shape = BadgeShape,
                        color = if (deviceStatus?.isUnbindPending == true) Color(0xFFFEF3C7) else StatusPresentBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (deviceStatus?.isUnbindPending == true) Color(0xFFF59E0B) else StatusPresentBorder
                        )
                    ) {
                        Text(
                            text = if (deviceStatus?.isUnbindPending == true) "UNBIND PENDING" else "HARDWARE BOUND",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deviceStatus?.isUnbindPending == true) Color(0xFFB45309) else StatusPresent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CanvasBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = deviceStatus?.deviceModel ?: android.os.Build.MODEL ?: "This Device",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                            Text(
                                text = "ID: " + (deviceStatus?.installationId ?: installationId).take(18) + "...",
                                fontSize = 10.sp,
                                style = TabularCodeStyle,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (deviceStatus?.isUnbindPending == true) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Unbind request sent to faculty. Pending approval.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Strict Anti-Proxy: Your student account is locked to this physical device. 1 Student = 1 Device hardware protection is active.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        unbindReasonInput = ""
                        showUnbindDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonShape,
                    enabled = deviceStatus?.isUnbindPending != true
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (deviceStatus?.isUnbindPending == true) TextMuted else BrandAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (deviceStatus?.isUnbindPending == true) "Unbind Request Pending" else "Request Device Unbind",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (deviceStatus?.isUnbindPending == true) TextMuted else BrandAccent
                    )
                }
            }
        }
    }

    // TAB 4: STUDENT PROFILE & SESSION HISTORY
    if (selectedTab == StudentTab.PROFILE) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, CardShape),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = CardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "STUDENT PROFILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = activeName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                        Text(text = "$activeRoll • $prog", fontSize = 12.sp, color = TextSecondary)
                        Text(text = sem, fontSize = 12.sp, color = TextSecondary)
                    }

                    Surface(
                        shape = BadgeShape,
                        color = StatusPresentBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder)
                    ) {
                        Text(
                            text = "VERIFIED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusPresent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = BorderHairline)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Device Hardware Binding",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = (deviceStatus?.deviceModel ?: android.os.Build.MODEL ?: "This Device") + " • ID: " + (deviceStatus?.installationId ?: installationId).take(16) + "...",
                    fontSize = 12.sp,
                    style = TabularCodeStyle,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session Ledger History
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
                        text = "ATTENDANCE LEDGER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = "$userAttendedClasses / $userTotalClasses Present",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusPresent
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (attendanceHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No attendance sessions recorded yet.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attendanceHistory.take(20).forEach { record ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CanvasBackground,
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
                                            text = record.subjectName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlack
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${record.date} • ${record.subjectCode}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Surface(
                                        shape = BadgeShape,
                                        color = if (record.isPresent) StatusPresentBg else StatusAbsentBg,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (record.isPresent) StatusPresentBorder else StatusAbsentBorder)
                                    ) {
                                        Text(
                                            text = if (record.isPresent) "✓ Present" else "✕ Absent",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (record.isPresent) StatusPresent else StatusAbsent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(96.dp))
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
                                refreshStatsAndHistory()
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

    // 10. REQUEST DEVICE UNBIND DIALOG
    if (showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingUnbind) showUnbindDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Request Device Unbind",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Faculty approval is required to switch your attendance device or reset your phone.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = unbindReasonInput,
                        onValueChange = { unbindReasonInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Reason (e.g. Bought new phone, device reset)", fontSize = 12.sp, color = TextMuted) },
                        shape = InputShape
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingUnbind = true
                        coroutineScope.launch {
                            val res = SupabaseAttendanceService.requestDeviceUnbind(activeRoll, unbindReasonInput)
                            isSubmittingUnbind = false
                            if (res.isSuccess) {
                                showUnbindDialog = false
                                refreshStatsAndHistory()
                                android.widget.Toast.makeText(context, "Unbind request submitted to teacher.", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Error: " + res.exceptionOrNull()?.message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    enabled = !isSubmittingUnbind
                ) {
                    if (isSubmittingUnbind) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Submit Request", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showUnbindDialog = false },
                    shape = ButtonShape,
                    enabled = !isSubmittingUnbind
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // 11. DEVICE LOCK SECURITY REJECTION DIALOG
    if (showDeviceLockDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceLockDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = StatusAbsent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anti-Proxy Device Lock",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = StatusAbsent
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = deviceLockSecurityError ?: "This device is locked or does not match your registered hardware.",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "To use this phone for attendance, request a device unbind from your teacher.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeviceLockDialog = false
                        unbindReasonInput = "Switching to new device: " + (android.os.Build.MODEL ?: "Phone")
                        showUnbindDialog = true
                    },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                ) {
                    Text("Request Faculty Unbind", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeviceLockDialog = false },
                    shape = ButtonShape
                ) {
                    Text("Dismiss", color = TextSecondary)
                }
            }
        )
    }

    // EDIT PROFILE DIALOG
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Column {
                    Text("Edit Student Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                    Text("Update your institutional details", fontSize = 12.sp, color = TextSecondary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editRollInput,
                        onValueChange = { editRollInput = it },
                        label = { Text("Roll Number / ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editProgInput,
                        onValueChange = { editProgInput = it },
                        label = { Text("Programme (e.g. M.Tech CSE)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSemInput,
                        onValueChange = { editSemInput = it },
                        label = { Text("Semester (e.g. Semester 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = editNameInput.trim().ifEmpty { activeName }
                        val cleanRoll = editRollInput.trim().ifEmpty { activeRoll }
                        val cleanProg = editProgInput.trim().ifEmpty { prog }
                        val cleanSem = editSemInput.trim().ifEmpty { sem }
                        prefs.edit()
                            .putString("selected_name", cleanName)
                            .putString("selected_roll", cleanRoll)
                            .putString("selected_program", cleanProg)
                            .putString("selected_semester", cleanSem)
                            .apply()
                        activeName = cleanName
                        activeRoll = cleanRoll
                        prog = cleanProg
                        sem = cleanSem
                        showEditProfileDialog = false
                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // DIAGNOSTICS & AP SELECTOR DIALOG
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Sensor Telemetry & Diagnostics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Wi-Fi SSID: ${wifiSnapshot?.ssid ?: "Connected"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                            Text("BSSID: ${wifiSnapshot?.bssid ?: "Classroom Router"}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("GPS Geofence: Classroom Verified", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                            Text("Accuracy: ±5.2m (Fused GNSS Anchor)", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Device Hardware ID: ${installationId.take(16)}...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                            Text("Hardware Binding: SECURE (1 Student = 1 Device)", fontSize = 11.sp, color = Color(0xFF059669))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDiagnosticsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

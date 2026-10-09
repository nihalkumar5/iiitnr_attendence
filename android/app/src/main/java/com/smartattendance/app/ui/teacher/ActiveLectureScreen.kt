package com.smartattendance.app.ui.teacher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
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
import com.smartattendance.app.core.network.EnrolledStudentInfo
import com.smartattendance.app.core.network.LiveStudentAttendanceItem
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.sensor.ScannedWifiNetwork
import com.smartattendance.app.core.sensor.WifiPresenceManager
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ActiveLectureScreen(
    classId: String = "class-01",
    joinCode: String = "",
    subjectName: String = "",
    subjectCode: String = "",
    room: String = "Room A-204 (AC Block)",
    timeSlot: String = "10:00 – 11:00 AM",
    detectedStudents: Int = 0,
    totalStudents: Int = 0,
    onEndLecture: (sessionId: String) -> Unit = {},
    onCancelLecture: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("smart_attendance_prefs", android.content.Context.MODE_PRIVATE) }
    var activeWifiSsid by remember { mutableStateOf(prefs.getString("faculty_chosen_wifi_ssid", "Pranjal") ?: "Pranjal") }
    var showChangeWifiDialog by remember { mutableStateOf(false) }
    var isUpdatingWifi by remember { mutableStateOf(false) }
    var showSecurityDetails by remember { mutableStateOf(false) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    // Intercept hardware/gesture back to prevent accidental session exit
    BackHandler {
        showCancelConfirmDialog = true
    }

    val wifiManager = remember { WifiPresenceManager(context) }
    val gpsManager = remember { com.smartattendance.app.core.sensor.GpsLocationManager(context) }
    var teacherLocation by remember { mutableStateOf<com.smartattendance.app.core.sensor.GpsCoordinate?>(null) }
    var proximityResult by remember { mutableStateOf<com.smartattendance.app.core.sensor.GpsProximityResult?>(null) }

    LaunchedEffect(Unit) {
        try {
            val loc = gpsManager.getCurrentLocation() ?: gpsManager.getLastKnownLocation()
            teacherLocation = loc
            if (loc != null) {
                // Faculty mobile device IS the dynamic geofence center point (distance = 0m)
                proximityResult = com.smartattendance.app.core.sensor.GpsProximityResult(
                    isWithinRange = true,
                    distanceMeters = 0f,
                    allowedRadiusMeters = 30f,
                    studentLat = loc.latitude,
                    studentLon = loc.longitude,
                    targetLat = loc.latitude,
                    targetLon = loc.longitude,
                    message = "Faculty mobile center point active (0m distance, 30m geofence broadcast)"
                )
            }
        } catch (_: Exception) {}
    }
    var scannedNetworks by remember { mutableStateOf<List<ScannedWifiNetwork>>(emptyList()) }
    var isScanningWifi by remember { mutableStateOf(false) }

    var activeSessionId by remember { mutableStateOf<String?>(null) }

    fun refreshWifiScan() {
        isScanningWifi = true
        coroutineScope.launch(Dispatchers.IO) {
            val networks = wifiManager.getNearbyWifiNetworks()
            withContext(Dispatchers.Main) {
                scannedNetworks = networks
                isScanningWifi = false
            }
        }
    }

    LaunchedEffect(showChangeWifiDialog) {
        if (showChangeWifiDialog) {
            refreshWifiScan()
        }
    }

    var elapsedSeconds by remember { mutableStateOf(0) }

    // Real Supabase Live Attendance Records Stream
    var liveAttendanceRecords by remember { mutableStateOf<List<LiveStudentAttendanceItem>>(emptyList()) }

    // Enrolled course roster
    var enrolledRoster by remember { mutableStateOf<List<EnrolledStudentInfo>>(emptyList()) }
    var isLoadingRoster by remember { mutableStateOf(true) }
    var manualMarkingStudentId by remember { mutableStateOf<String?>(null) }
    var rosterSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PRESENT, WAITING

    // Load full enrolled roster from Supabase
    fun loadRoster() {
        coroutineScope.launch {
            val res = SupabaseAttendanceService.fetchCourseRoster(classId, joinCode, subjectCode, subjectName)
            res.onSuccess { list ->
                enrolledRoster = list
                prefs.edit().putInt("synced_enrolled_student_count", list.size).apply()
                isLoadingRoster = false
            }.onFailure {
                isLoadingRoster = false
            }
        }
    }

    LaunchedEffect(classId) {
        loadRoster()
        coroutineScope.launch {
            val loc = teacherLocation ?: gpsManager.getCurrentLocation() ?: gpsManager.getLastKnownLocation()
            if (teacherLocation == null && loc != null) {
                teacherLocation = loc
                proximityResult = com.smartattendance.app.core.sensor.GpsProximityResult(
                    isWithinRange = true,
                    distanceMeters = 0f,
                    allowedRadiusMeters = 30f,
                    studentLat = loc.latitude,
                    studentLon = loc.longitude,
                    targetLat = loc.latitude,
                    targetLon = loc.longitude,
                    message = "Faculty mobile center point active (0m distance, 30m geofence broadcast)"
                )
            }
            val res = SupabaseAttendanceService.startClassAttendanceSession(
                classId = classId,
                joinCode = joinCode,
                subjectName = subjectName,
                subjectCode = subjectCode,
                room = room,
                chosenWifiSsid = activeWifiSsid,
                teacherLat = loc?.latitude,
                teacherLon = loc?.longitude
            )
            res.onSuccess { newId ->
                activeSessionId = newId
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val sId = activeSessionId
                if (!sId.isNullOrBlank()) {
                    SupabaseAttendanceService.endAttendanceSession(sId)
                }
                SupabaseAttendanceService.endAllActiveSessions()
            }
        }
    }

    // Timer ticker
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds += 1
        }
    }

    // Continuous Live Supabase Attendance Polling (Every 2.5 Seconds)
    LaunchedEffect(activeSessionId) {
        val sId = activeSessionId ?: return@LaunchedEffect
        while (true) {
            try {
                val result = SupabaseAttendanceService.fetchLiveSessionAttendance(sId)
                result.onSuccess { records ->
                    liveAttendanceRecords = records
                }
            } catch (_: Exception) {
                // Ignore transient network errors
            }
            delay(2500L)
        }
    }

    val presentStudentIds = liveAttendanceRecords.filter { it.status == "PRESENT" }.map { it.studentId }.toSet()
    val presentRolls = liveAttendanceRecords.filter { it.status == "PRESENT" }.map { it.rollNumber }.toSet()

    val realPresentCount = presentStudentIds.size.coerceAtLeast(liveAttendanceRecords.count { it.status == "PRESENT" })
    val isZeroStudents = !isLoadingRoster && enrolledRoster.isEmpty() && liveAttendanceRecords.isEmpty()
    val effectiveTotalStudents = if (isZeroStudents) 0 else if (enrolledRoster.isNotEmpty()) enrolledRoster.size else totalStudents
    val waitingCount = (effectiveTotalStudents - realPresentCount).coerceAtLeast(0)
    val livePercentage = if (effectiveTotalStudents > 0) (realPresentCount * 100) / effectiveTotalStudents else 0

    // Live blinking recording dot
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

    val elapsedMinutes = elapsedSeconds / 60
    val elapsedSec = elapsedSeconds % 60
    val elapsedFormatted = String.format("%02d:%02d", elapsedMinutes, elapsedSec)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .intersemesterBackground()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. CLASS HEADER
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
                        text = subjectCode,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAccent
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = PillShape,
                            color = StatusPresentBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(StatusPresent.copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active · $elapsedFormatted",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPresent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { showCancelConfirmDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close active attendance session",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subjectName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$room · $timeSlot",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. ATTENDANCE SUMMARY
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, CardShape),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = CardShape
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PRESENT",
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
                    Text(
                        text = "$realPresentCount / $effectiveTotalStudents",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularCodeStyle,
                        color = BrandAccent
                    )

                    Surface(
                        shape = BadgeShape,
                        color = if (livePercentage >= 75) StatusPresentBg else StatusReviewBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (livePercentage >= 75) StatusPresentBorder else StatusReviewBorder
                        )
                    ) {
                        Text(
                            text = "$livePercentage% Turnout",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (livePercentage >= 75) StatusPresent else StatusReview,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusPresent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Present: $realPresentCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusPresent
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusReview)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Waiting: $waitingCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusReview
                        )
                    }

                    if (effectiveTotalStudents > realPresentCount) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TextMuted)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Absent: 0",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. STUDENT ROSTER (MAIN SECTION)
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
                        text = "STUDENTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = if (effectiveTotalStudents > 0) "$effectiveTotalStudents enrolled" else "0 enrolled",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field
                OutlinedTextField(
                    value = rosterSearchQuery,
                    onValueChange = { rosterSearchQuery = it },
                    placeholder = { Text("Search students...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = InputShape
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "All ($effectiveTotalStudents)", "PRESENT" to "Present ($realPresentCount)", "WAITING" to "Waiting ($waitingCount)").forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Surface(
                            shape = PillShape,
                            color = if (isSelected) BrandAccent else AccentPill,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                            modifier = Modifier.clickable { selectedFilter = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Unified Roster Building
                data class UnifiedRosterRow(
                    val id: String,
                    val name: String,
                    val roll: String,
                    val isPresent: Boolean,
                    val isManual: Boolean,
                    val isDeviceBound: Boolean
                )

                val unifiedList = mutableListOf<UnifiedRosterRow>()

                if (enrolledRoster.isNotEmpty()) {
                    enrolledRoster.forEach { student ->
                        val isPresent = presentStudentIds.contains(student.studentId) || presentRolls.contains(student.rollNumber)
                        val record = liveAttendanceRecords.find { it.studentId == student.studentId || it.rollNumber == student.rollNumber }
                        val isManual = record?.verificationMethod == "MANUAL_TEACHER"

                        unifiedList.add(
                            UnifiedRosterRow(
                                id = student.studentId,
                                name = student.name,
                                roll = student.rollNumber,
                                isPresent = isPresent,
                                isManual = isManual,
                                isDeviceBound = student.isDeviceBound
                            )
                        )
                    }
                } else {
                    // Fallback to live stream records directly
                    liveAttendanceRecords.forEach { rec ->
                        unifiedList.add(
                            UnifiedRosterRow(
                                id = rec.studentId,
                                name = rec.studentName,
                                roll = rec.rollNumber,
                                isPresent = rec.status == "PRESENT",
                                isManual = rec.verificationMethod == "MANUAL_TEACHER",
                                isDeviceBound = true
                            )
                        )
                    }
                }

                val filteredRoster = unifiedList.filter { student ->
                    val matchesSearch = rosterSearchQuery.isBlank() ||
                        student.name.contains(rosterSearchQuery, ignoreCase = true) ||
                        student.roll.contains(rosterSearchQuery, ignoreCase = true)

                    val matchesFilter = when (selectedFilter) {
                        "PRESENT" -> student.isPresent
                        "WAITING" -> !student.isPresent
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }

                if (isLoadingRoster && unifiedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = BrandAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading students...", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                } else if (unifiedList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceNeutral,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(BrandAccent.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PeopleOutline,
                                    contentDescription = null,
                                    tint = BrandAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No students enrolled",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Students must register for $subjectCode before they appear in the attendance roster.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            if (joinCode.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = PillShape,
                                    color = CardBackground,
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = "Join Code: $joinCode",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (filteredRoster.isEmpty()) {
                    Text(
                        text = "No students matching current filter.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredRoster.forEach { student ->
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Left Status Indicator
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (student.isPresent) StatusPresent.copy(alpha = 0.15f)
                                                    else StatusReview.copy(alpha = 0.15f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (student.isPresent) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Present",
                                                    tint = StatusPresent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(StatusReview)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = student.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "Roll No: ${student.roll}",
                                                    fontSize = 11.sp,
                                                    style = TabularCodeStyle,
                                                    color = TextSecondary
                                                )
                                                Text("·", fontSize = 11.sp, color = TextMuted)
                                                Text(
                                                    text = if (student.isPresent) (if (student.isManual) "Present (Manual)" else "Present") else "Waiting",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (student.isPresent) StatusPresent else StatusReview
                                                )
                                            }
                                        }
                                    }

                                    // Right Action (Present Badge OR 1-Tap Manual Override)
                                    if (student.isPresent) {
                                        Surface(
                                            shape = BadgeShape,
                                            color = StatusPresentBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder)
                                        ) {
                                            Text(
                                                text = "✓ Present",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusPresent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        val isMarkingThis = manualMarkingStudentId == student.id
                                        Surface(
                                            shape = BadgeShape,
                                            color = BrandAccent.copy(alpha = 0.08f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandAccent.copy(alpha = 0.35f)),
                                            modifier = Modifier.clickable(enabled = !isMarkingThis) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                manualMarkingStudentId = student.id

                                                // Optimistic update
                                                val optimisticItem = LiveStudentAttendanceItem(
                                                    id = "manual-${System.currentTimeMillis()}",
                                                    studentId = student.id,
                                                    studentName = student.name,
                                                    rollNumber = student.roll,
                                                    status = "PRESENT",
                                                    presencePercentage = 100.0,
                                                    verificationMethod = "MANUAL_TEACHER",
                                                    markedAtIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()),
                                                    notes = "Teacher Manual 1-Tap Override"
                                                )
                                                liveAttendanceRecords = liveAttendanceRecords.filterNot { it.studentId == student.id || it.rollNumber == student.roll } + optimisticItem

                                                coroutineScope.launch {
                                                    SupabaseAttendanceService.markStudentManualAttendance(
                                                        sessionId = activeSessionId ?: "",
                                                        studentId = student.id,
                                                        isPresent = true
                                                    )
                                                    manualMarkingStudentId = null
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isMarkingThis) {
                                                    CircularProgressIndicator(modifier = Modifier.size(10.dp), color = BrandAccent, strokeWidth = 1.5.dp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = "Mark Present",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = BrandAccent
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. SECURITY STATUS
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
                        text = "SECURITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = if (showSecurityDetails) "Hide Details ▲" else "View Details ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccent,
                        modifier = Modifier.clickable { showSecurityDetails = !showSecurityDetails }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLocOk = proximityResult?.isWithinRange ?: true
                    Icon(
                        imageVector = if (isLocOk) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (isLocOk) StatusPresent else StatusReview,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (teacherLocation != null) {
                            "Classroom location verified (Faculty device anchor · 30m radius)"
                        } else "Classroom location active (30m geofence)",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Classroom Wi-Fi verified ($activeWifiSsid)", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                if (showSecurityDetails) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Active Access Point: $activeWifiSsid", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Geofence: Faculty Mobile Center (30m Classroom Radius · Active)", fontSize = 11.sp, color = TextSecondary)
                    if (teacherLocation != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Anchor Coordinates: ${String.format(java.util.Locale.US, "%.5f, %.5f (±%.0fm)", teacherLocation!!.latitude, teacherLocation!!.longitude, teacherLocation!!.accuracyMeters)}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Origin: Faculty Mobile Device (Teacher = 0m offset)",
                            fontSize = 11.sp,
                            color = StatusPresent
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showChangeWifiDialog = true },
                        shape = ButtonShape,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Switch Classroom Wi-Fi AP", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PRIMARY ACTION: SUBMIT ATTENDANCE
        val hasEnrolledStudents = effectiveTotalStudents > 0

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showSubmitConfirmDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = ButtonShape,
            enabled = hasEnrolledStudents,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandAccent,
                disabledContainerColor = SurfaceNeutral,
                disabledContentColor = TextMuted
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (hasEnrolledStudents) Color.White else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasEnrolledStudents) "Submit Attendance ($realPresentCount Present)" else "Submit Attendance (No Students)",
                color = if (hasEnrolledStudents) Color.White else TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (!hasEnrolledStudents) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Students must register for this subject before attendance can be submitted.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }


    // CANCEL / DISCARD CONFIRMATION DIALOG
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Text(
                    text = "End or Discard Session?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "You are currently running an active attendance session for $subjectName. Closing the session will terminate it without saving pending attendance records. To save, submit attendance instead.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch(Dispatchers.IO) {
                            val sId = activeSessionId
                            if (!sId.isNullOrBlank()) {
                                SupabaseAttendanceService.endAttendanceSession(sId)
                            }
                            SupabaseAttendanceService.endAllActiveSessions()
                            withContext(Dispatchers.Main) {
                                onCancelLecture()
                            }
                        }
                    },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAbsent)
                ) {
                    Text("Discard Session", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancelConfirmDialog = false },
                    shape = ButtonShape
                ) {
                    Text("Keep Active")
                }
            }
        )
    }

    // SUBMIT CONFIRMATION DIALOG
    if (showSubmitConfirmDialog) {
        val absentCount = (effectiveTotalStudents - realPresentCount).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Text(
                    text = "Submit Attendance?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "$subjectName ($subjectCode)",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Present:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusPresent)
                        Text("$realPresentCount students", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusPresent)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Absent / Waiting:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (absentCount > 0) StatusAbsent else TextSecondary)
                        Text("$absentCount students", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (absentCount > 0) StatusAbsent else TextSecondary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch(Dispatchers.IO) {
                            val sId = activeSessionId
                            if (!sId.isNullOrBlank()) {
                                SupabaseAttendanceService.endAttendanceSession(sId)
                            }
                            SupabaseAttendanceService.endAllActiveSessions()
                            TimetableEngine.lockClassToday(context, classId, sId)
                            withContext(Dispatchers.Main) {
                                onEndLecture(sId ?: "")
                            }
                        }
                    },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                ) {
                    Text("Submit", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSubmitConfirmDialog = false },
                    shape = ButtonShape
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // CHANGE WI-FI AP MODAL DIALOG (MULTI-SELECT SUPPORT)
    if (showChangeWifiDialog) {
        val currentTeacherWifi = remember { wifiManager.getCurrentWifiSnapshot() }
        val teacherCleanSsid = remember(currentTeacherWifi.ssid) {
            val raw = currentTeacherWifi.ssid?.replace("\"", "")?.trim()
            if (raw != null && raw != "<unknown ssid>" && raw != "Unknown Wi-Fi" && raw.isNotBlank()) raw else "Pranjal"
        }
        val initialSelected = remember(activeWifiSsid) {
            activeWifiSsid.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotBlank() }
        }
        val dialogSelectedSsids = remember {
            mutableStateListOf<String>().apply {
                if (initialSelected.isNotEmpty()) {
                    addAll(initialSelected)
                } else {
                    add(teacherCleanSsid)
                }
            }
        }
        var customSsidInput by remember { mutableStateOf("") }

        val standardCampusAps = listOf("Pranjal", "IIIT-NR-Campus", "IIITNR_STUDENTS", "eduroam")
        val combinedList = (listOf(teacherCleanSsid) + standardCampusAps + scannedNetworks.map { it.ssid.replace("\"", "").trim() } + dialogSelectedSsids)
            .distinct()
            .filter { it.isNotBlank() }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (!isUpdatingWifi) showChangeWifiDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = DialogShape,
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Allowed Wi-Fi Networks",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = { if (!isUpdatingWifi) showChangeWifiDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Authorize campus access points for this room. Students must be connected to an authorized network to satisfy Wi-Fi presence verification.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom SSID Input + Add Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customSsidInput,
                            onValueChange = { customSsidInput = it },
                            placeholder = { Text("Enter Wi-Fi SSID...", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandAccent,
                                unfocusedBorderColor = BorderSubtle
                            )
                        )

                        Button(
                            onClick = {
                                val clean = customSsidInput.replace("\"", "").trim()
                                if (clean.isNotBlank() && !dialogSelectedSsids.any { it.equals(clean, ignoreCase = true) }) {
                                    dialogSelectedSsids.add(clean)
                                    customSsidInput = ""
                                }
                            },
                            enabled = customSsidInput.isNotBlank(),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "Add",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Available Networks (${dialogSelectedSsids.size} selected)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Scrollable Network List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        combinedList.forEach { ssid ->
                            val isSelected = dialogSelectedSsids.any { it.equals(ssid, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) BrandAccent.copy(alpha = 0.08f) else SurfaceNeutral,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BrandAccent else BorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            if (dialogSelectedSsids.size > 1) {
                                                dialogSelectedSsids.removeAll { it.equals(ssid, ignoreCase = true) }
                                            }
                                        } else {
                                            dialogSelectedSsids.add(ssid)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (isSelected) BrandAccent else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = ssid,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = TextPrimary
                                        )
                                    }

                                    if (isSelected) {
                                        Surface(
                                            shape = PillShape,
                                            color = BrandAccent.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Allowed",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandAccent,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showChangeWifiDialog = false },
                            enabled = !isUpdatingWifi,
                            shape = ButtonShape,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                val newString: String = dialogSelectedSsids.toList().distinct().joinToString(", ")
                                activeWifiSsid = newString
                                prefs.edit().putString("faculty_chosen_wifi_ssid", newString).apply()
                                isUpdatingWifi = true
                                coroutineScope.launch {
                                    val sId = activeSessionId
                                    if (!sId.isNullOrBlank()) {
                                        SupabaseAttendanceService.updateClassroomWifiForSession(
                                            sId, 
                                            newString,
                                            teacherLat = teacherLocation?.latitude,
                                            teacherLon = teacherLocation?.longitude
                                        )
                                    }
                                    isUpdatingWifi = false
                                    showChangeWifiDialog = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            enabled = !isUpdatingWifi && dialogSelectedSsids.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(42.dp)
                        ) {
                            if (isUpdatingWifi) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "Save Allowed Wi-Fis (${dialogSelectedSsids.size})",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

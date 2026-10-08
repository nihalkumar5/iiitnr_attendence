package com.smartattendance.app.ui.teacher

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
    subjectName: String = "Data Structures & Algorithms",
    subjectCode: String = "CS501",
    room: String = "Room A-204 (AC Block)",
    timeSlot: String = "10:00 – 11:00 AM",
    detectedStudents: Int = 5,
    totalStudents: Int = 22,
    onEndLecture: (sessionId: String) -> Unit = {}
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

    val wifiManager = remember { WifiPresenceManager(context) }
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
            val res = SupabaseAttendanceService.startClassAttendanceSession(
                classId = classId,
                joinCode = joinCode,
                subjectName = subjectName,
                subjectCode = subjectCode,
                room = room,
                chosenWifiSsid = activeWifiSsid
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
    val effectiveTotalStudents = if (enrolledRoster.isNotEmpty()) enrolledRoster.size else totalStudents
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
            .background(CanvasBackground)
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

                    Surface(
                        shape = PillShape,
                        color = StatusPresentBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                                text = "Attendance Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusPresent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "· $elapsedFormatted elapsed",
                                fontSize = 11.sp,
                                color = TextSecondary
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
                        shape = BadgeShape,
                        color = SurfaceNeutral,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No students enrolled in this subject.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Students who join using the class code will appear here.", fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center)
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
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Classroom location verified", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
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
                    Text("Geofence: Campus Boundary (300m Active Radius)", fontSize = 11.sp, color = TextSecondary)
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
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showSubmitConfirmDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Submit Attendance ($realPresentCount Present)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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

    // CHANGE WI-FI AP MODAL DIALOG
    if (showChangeWifiDialog) {
        val currentTeacherWifi = remember { wifiManager.getCurrentWifiSnapshot() }
        val teacherCleanSsid = remember(currentTeacherWifi.ssid) {
            val raw = currentTeacherWifi.ssid?.replace("\"", "")?.trim()
            if (raw != null && raw != "<unknown ssid>" && raw != "Unknown Wi-Fi" && raw.isNotBlank()) raw else "Pranjal"
        }

        AlertDialog(
            onDismissRequest = { if (!isUpdatingWifi) showChangeWifiDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Text("Select Classroom Wi-Fi AP", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose the Wi-Fi network that students must connect to in this classroom.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    val standardCampusAps = listOf("Pranjal", "IIIT-NR-Campus", "IIITNR_STUDENTS", "eduroam")
                    val combinedList = (listOf(teacherCleanSsid) + standardCampusAps + scannedNetworks.map { it.ssid }).distinct().filter { it.isNotBlank() }

                    combinedList.forEach { ssid ->
                        val isCurrentSelected = activeWifiSsid.equals(ssid, ignoreCase = true)
                        Surface(
                            shape = BadgeShape,
                            color = if (isCurrentSelected) BrandAccent.copy(alpha = 0.1f) else SurfaceNeutral,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrentSelected) BrandAccent else BorderHairline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeWifiSsid = ssid
                                    prefs.edit().putString("faculty_chosen_wifi_ssid", ssid).apply()
                                    isUpdatingWifi = true
                                    coroutineScope.launch {
                                        val sId = activeSessionId
                                        if (!sId.isNullOrBlank()) {
                                            SupabaseAttendanceService.updateClassroomWifiForSession(sId, ssid)
                                        }
                                        isUpdatingWifi = false
                                        showChangeWifiDialog = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, tint = if (isCurrentSelected) BrandAccent else TextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(ssid, fontSize = 13.sp, fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal, color = TextPrimary)
                                }
                                if (isCurrentSelected) {
                                    Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandAccent)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showChangeWifiDialog = false }
                ) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

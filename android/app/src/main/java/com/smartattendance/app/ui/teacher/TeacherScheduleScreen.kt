package com.smartattendance.app.ui.teacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun TeacherScheduleScreen(
    teacherName: String = "Dr. S. Sharma",
    facultyId: String = "FAC-CSE-042",
    department: String = "Computer Science & Engineering",
    onStartLecture: (TeacherClassItem) -> Unit = {},
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var classList by remember { mutableStateOf(loadPersistedSchedule(context)) }
    fun updateClassList(newList: List<TeacherClassItem>) {
        classList = newList
        savePersistedSchedule(context, newList)
    }

    var selectedClassForDetail by remember { mutableStateOf<TeacherClassItem?>(null) }
    var subjectTab by remember { mutableStateOf("Overview") }
    var rosterList by remember { mutableStateOf<List<EnrolledStudentInfo>>(emptyList()) }
    var rosterSearchQuery by remember { mutableStateOf("") }
    var isLoadingRoster by remember { mutableStateOf(false) }

    // Dialog States for Create Subject
    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAiTimetableDialog by remember { mutableStateOf(false) }
    var subjectNameInput by remember { mutableStateOf("") }
    var subjectCodeInput by remember { mutableStateOf("") }
    var programInput by remember { mutableStateOf("B.Tech DSAI") }
    var semesterInput by remember { mutableStateOf("Semester 5") }
    var sectionInput by remember { mutableStateOf("Section A") }
    var roomInput by remember { mutableStateOf("Room A-302") }
    var timeSlotInput by remember { mutableStateOf("10:00 – 11:00 AM") }
    var selectedDayOfWeek by remember { mutableStateOf("Monday") }
    val currentIsoDay = remember { TimetableEngine.getIsoDayOfWeek() }
    val todayDayCode = remember(currentIsoDay) {
        when (currentIsoDay) {
            1 -> "MON"
            2 -> "TUE"
            3 -> "WED"
            4 -> "THU"
            5 -> "FRI"
            6 -> "SAT"
            else -> "MON"
        }
    }
    var selectedDayFilter by remember { mutableStateOf(todayDayCode) }
    var isCreatingSubject by remember { mutableStateOf(false) }
    var addClassError by remember { mutableStateOf<String?>(null) }
    var createdClassSuccess by remember { mutableStateOf<TeacherClassItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeletingSubject by remember { mutableStateOf(false) }

    fun copyToClipboard(text: String, label: String = "Join Code") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(context, "📋 $label '$text' copied!", Toast.LENGTH_SHORT).show()
    }

    fun shareJoinCode(subjectName: String, subjectCode: String, joinCode: String, program: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val msg = "Join " + subjectName + " (" + subjectCode + ") for " + program + " on Smart Attendance App!\n\nStudent Join Code: " + joinCode
            putExtra(Intent.EXTRA_TEXT, msg)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Join Code via"))
    }
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    val counts = SupabaseAttendanceService.fetchEnrollmentCounts()
                    if (counts.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val updated = classList.map { item ->
                                val cleanJoin = item.joinCode.trim().uppercase()
                                val cleanSub = item.subjectCode.trim().uppercase()
                                val cleanName = item.subjectName.trim().lowercase()
                                val prefix = cleanSub.substringBefore("-")
                                val c = counts[item.id]
                                    ?: counts[cleanJoin]
                                    ?: counts[cleanSub]
                                    ?: counts[cleanName]
                                    ?: counts[prefix]
                                if (c != null && c != item.enrolledStudents) {
                                    item.copy(enrolledStudents = c)
                                } else {
                                    item
                                }
                            }
                            if (updated != classList) {
                                updateClassList(updated)
                                val currentDetail = selectedClassForDetail
                                if (currentDetail != null) {
                                    val matched = updated.firstOrNull { it.id == currentDetail.id || it.joinCode == currentDetail.joinCode }
                                    if (matched != null) {
                                        selectedClassForDetail = matched
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    LaunchedEffect(selectedClassForDetail?.id) {
        val current = selectedClassForDetail
        if (current != null) {
            var firstLoad = true
            while (true) {
                if (firstLoad) isLoadingRoster = true
                val res = SupabaseAttendanceService.fetchCourseRoster(
                    current.id,
                    current.joinCode,
                    current.subjectCode,
                    current.subjectName
                )
                if (firstLoad) isLoadingRoster = false
                firstLoad = false
                if (res.isSuccess) {
                    val list = res.getOrThrow()
                    rosterList = list
                    // Synchronize class list count with actual roster size so inside and outside match
                    if (list.size != current.enrolledStudents) {
                        val updated = classList.map {
                            if (it.id == current.id || (it.joinCode.isNotBlank() && it.joinCode == current.joinCode)) {
                                it.copy(enrolledStudents = list.size)
                            } else it
                        }
                        if (updated != classList) {
                            updateClassList(updated)
                            selectedClassForDetail = current.copy(enrolledStudents = list.size)
                        }
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // VIEW 1: SUBJECT DETAIL SCREEN (When a subject is selected)
        if (selectedClassForDetail != null) {
            val currentClass = selectedClassForDetail!!

            // BACK HEADER WITH TITLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedClassForDetail = null
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Subjects",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentClass.subjectName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${currentClass.subjectCode} · ${currentClass.program}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PROMINENT JOIN CODE CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BrandAccent.copy(alpha = 0.3f), CardShape),
                colors = CardDefaults.cardColors(containerColor = BrandAccent.copy(alpha = 0.05f)),
                shape = CardShape
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STUDENT JOIN CODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAccent,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = currentClass.joinCode,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            style = TabularCodeStyle,
                            color = BrandAccent,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(currentClass.joinCode, "Join Code")
                            },
                            shape = ButtonShape,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Join Code", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                shareJoinCode(currentClass.subjectName, currentClass.subjectCode, currentClass.joinCode, currentClass.program)
                            },
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Join Code", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TAB NAVIGATION BAR (Overview | Students | Attendance | Settings)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Overview", "Students", "Attendance", "Settings").forEach { tab ->
                    val isSelected = subjectTab == tab
                    Surface(
                        shape = PillShape,
                        color = if (isSelected) BrandAccent else AccentPill,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) BrandAccent else BorderHairline
                        ),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            subjectTab = tab
                        }
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TAB CONTENTS
            when (subjectTab) {
                "Overview" -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, CardShape),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = CardShape
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "SUBJECT OVERVIEW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )

                            DetailRow(label = "Faculty", value = teacherName)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Program", value = currentClass.program)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Room / Venue", value = currentClass.room)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Schedule", value = currentClass.timeSlot)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            val displayCount = if (rosterList.isNotEmpty()) rosterList.size else currentClass.enrolledStudents
                            DetailRow(label = "Enrolled Students", value = "$displayCount ${if (displayCount == 1) "student" else "students"}")
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Join Code", value = currentClass.joinCode)
                        }
                    }
                }

                "Students" -> {
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
                                val tabDisplayCount = if (rosterList.isNotEmpty()) rosterList.size else currentClass.enrolledStudents
                                Text(
                                    text = "$tabDisplayCount ${if (tabDisplayCount == 1) "Student" else "Students"}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (rosterList.isNotEmpty()) {
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

                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            val filteredRoster = rosterList.filter {
                                it.name.contains(rosterSearchQuery, ignoreCase = true) ||
                                it.rollNumber.contains(rosterSearchQuery, ignoreCase = true)
                            }

                            if (isLoadingRoster) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = BrandAccent, modifier = Modifier.size(24.dp))
                                }
                            } else if (rosterList.isEmpty()) {
                                Surface(
                                    shape = BadgeShape,
                                    color = SurfaceNeutral,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "STUDENTS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "No students have joined yet.",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Share the subject code with your students.",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                copyToClipboard(currentClass.joinCode, "Join Code")
                                            },
                                            shape = ButtonShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Join Code", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else if (filteredRoster.isEmpty()) {
                                Text(
                                    text = "No students matching '$rosterSearchQuery'",
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = student.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Roll No: ${student.rollNumber}",
                                                        fontSize = 11.sp,
                                                        style = TabularCodeStyle,
                                                        color = TextSecondary
                                                    )
                                                }

                                                if (student.isDeviceBound) {
                                                    Surface(
                                                        shape = BadgeShape,
                                                        color = StatusPresentBg,
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresent.copy(alpha = 0.3f))
                                                    ) {
                                                        Text(
                                                            text = "Enrolled",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = StatusPresent,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

                "Attendance" -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, CardShape),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = CardShape
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "ATTENDANCE SESSION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Start a live roll call for ${currentClass.subjectName} in ${currentClass.room}.",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartLecture(currentClass)
                                },
                                shape = ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Attendance Session", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                "Settings" -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, CardShape),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = CardShape
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "SUBJECT CONFIGURATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            DetailRow(label = "Subject Name", value = currentClass.subjectName)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Official Code", value = currentClass.subjectCode)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Program & Branch", value = currentClass.program)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Assigned Venue", value = currentClass.room)
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Status", value = "Active")
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeleteDialog = true
                                },
                                shape = ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFDC2626)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete This Subject", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // VIEW 2: MY SUBJECTS LIST SCREEN (Clean, Single Page Header, No Outer Card)
            val activeClasses = classList.filter { it.status != ClassScheduleStatus.CANCELLED }
            val filteredSubjectClasses = when (selectedDayFilter) {
                "MON" -> activeClasses.filter { it.dayOfWeek == 1 }
                "TUE" -> activeClasses.filter { it.dayOfWeek == 2 }
                "WED" -> activeClasses.filter { it.dayOfWeek == 3 }
                "THU" -> activeClasses.filter { it.dayOfWeek == 4 }
                "FRI" -> activeClasses.filter { it.dayOfWeek == 5 }
                "SAT" -> activeClasses.filter { it.dayOfWeek == 6 }
                else -> activeClasses.filter { it.dayOfWeek == currentIsoDay }
            }

            // 1. CLEAN SINGLE PAGE HEADER (Full-width hierarchy, zero collision)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "My Subjects",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    maxLines = 1,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${activeClasses.size} ${if (activeClasses.size == 1) "active subject" else "active subjects"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. ACTION ROW (Full width, balanced, distinct visual hierarchy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        subjectNameInput = ""
                        subjectCodeInput = ""
                        roomInput = "Room A-302"
                        addClassError = null
                        showAddClassDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Subject",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAiTimetableDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, BorderSubtle),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Timetable",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. DAY FILTER ROW (Weekly days Mon-Sat, today automatically marked and selected)
            val dayTabs = listOf(
                "MON" to (if (currentIsoDay == 1) "Mon (Today)" else "Mon"),
                "TUE" to (if (currentIsoDay == 2) "Tue (Today)" else "Tue"),
                "WED" to (if (currentIsoDay == 3) "Wed (Today)" else "Wed"),
                "THU" to (if (currentIsoDay == 4) "Thu (Today)" else "Thu"),
                "FRI" to (if (currentIsoDay == 5) "Fri (Today)" else "Fri"),
                "SAT" to (if (currentIsoDay == 6) "Sat (Today)" else "Sat")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dayTabs.forEach { (code, label) ->
                    val isSelected = selectedDayFilter == code
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) BrandAccent.copy(alpha = 0.08f) else CardBackground,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) BrandAccent else BorderSubtle
                        ),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedDayFilter = code
                        }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) BrandAccent else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. SUBJECT ITEMS LIST (Refined, lightweight list on CanvasBackground)
            if (filteredSubjectClasses.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (activeClasses.isEmpty()) "No subjects created yet" else "No subjects on this day",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeClasses.isEmpty()) "Create your first subject to start taking attendance" else "Select another day to view scheduled subjects",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (activeClasses.isEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    subjectNameInput = ""
                                    subjectCodeInput = ""
                                    roomInput = "Room A-302"
                                    addClassError = null
                                    showAddClassDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Subject", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredSubjectClasses.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardBackground,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedClassForDetail = item
                                    subjectTab = "Overview"
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Line 1: Subject Name (dominant) & Student count (secondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = item.subjectName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f, fill = false),
                                        lineHeight = 21.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${item.enrolledStudents} ${if (item.enrolledStudents == 1) "student" else "students"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Line 2: Subject code
                                Text(
                                    text = item.subjectCode,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandAccent
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                // Line 3: Program · Semester · Section
                                Text(
                                    text = item.program,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Line 4: Room & Subtle Chevron
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.room,
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "View Details",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(84.dp))
        }

                // DIALOG: DELETE SUBJECT CONFIRMATION
        if (showDeleteDialog && selectedClassForDetail != null) {
            val target = selectedClassForDetail!!
            AlertDialog(
                onDismissRequest = { if (!isDeletingSubject) showDeleteDialog = false },
                shape = DialogShape,
                containerColor = CardBackground,
                title = {
                    Text(
                        text = "Delete " + target.subjectName + "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "This will permanently delete join code " + target.joinCode + " and remove this subject from your schedule.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isDeletingSubject = true
                                SupabaseAttendanceService.deleteCourse(target.id, target.joinCode)
                                val updated = classList.filter { it.id != target.id && !it.joinCode.equals(target.joinCode, ignoreCase = true) }
                                updateClassList(updated)
                                selectedClassForDetail = null
                                showDeleteDialog = false
                                isDeletingSubject = false
                                Toast.makeText(context, "Subject deleted successfully", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFDC2626)),
                        enabled = !isDeletingSubject
                    ) {
                        Text(if (isDeletingSubject) "Deleting..." else "Yes, Delete", color = androidx.compose.ui.graphics.Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false },
                        enabled = !isDeletingSubject
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // DIALOG: CREATE SUBJECT MODAL FORM
        if (showAddClassDialog) {
            val programs = listOf("B.Tech DSAI", "B.Tech CSE", "B.Tech ECE", "M.Tech AI", "PhD CSE")
            val semesters = listOf("Semester 1", "Semester 2", "Semester 3", "Semester 4", "Semester 5", "Semester 6", "Semester 7", "Semester 8")
            val sections = listOf("Section A", "Section B", "Section C", "All Sections")
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val timeSlots = listOf("09:00 – 10:00 AM", "10:00 – 11:00 AM", "11:15 – 12:15 PM", "01:15 – 02:15 PM", "02:15 – 03:15 PM", "03:30 – 04:30 PM")

            AlertDialog(
                onDismissRequest = { if (!isCreatingSubject) showAddClassDialog = false },
                shape = DialogShape,
                containerColor = CardBackground,
                title = {
                    Text(
                        text = "CREATE SUBJECT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Subject Name
                        Text("Subject Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        OutlinedTextField(
                            value = subjectNameInput,
                            onValueChange = { subjectNameInput = it },
                            placeholder = { Text("e.g. Machine Learning", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = InputShape
                        )

                        // Official Subject Code
                        Text("Subject Code", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        OutlinedTextField(
                            value = subjectCodeInput,
                            onValueChange = { subjectCodeInput = it.uppercase() },
                            placeholder = { Text("e.g. CS502", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = InputShape
                        )

                        // Program / Branch
                        Text("Program / Branch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            programs.forEach { p ->
                                val sel = programInput == p
                                Surface(
                                    shape = PillShape,
                                    color = if (sel) BrandAccent else AccentPill,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { programInput = p }
                                ) {
                                    Text(p, fontSize = 11.sp, color = if (sel) Color.White else TextPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }

                        // Semester
                        Text("Semester", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            semesters.forEach { s ->
                                val sel = semesterInput == s
                                Surface(
                                    shape = PillShape,
                                    color = if (sel) BrandAccent else AccentPill,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { semesterInput = s }
                                ) {
                                    Text(s, fontSize = 11.sp, color = if (sel) Color.White else TextPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }

                        // Section
                        Text("Section", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sections.forEach { sec ->
                                val sel = sectionInput == sec
                                Surface(
                                    shape = PillShape,
                                    color = if (sel) BrandAccent else AccentPill,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { sectionInput = sec }
                                ) {
                                    Text(sec, fontSize = 11.sp, color = if (sel) Color.White else TextPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }

                        // Room / Venue
                        Text("Room", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        OutlinedTextField(
                            value = roomInput,
                            onValueChange = { roomInput = it },
                            placeholder = { Text("e.g. Room A-302", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = InputShape
                        )

                        // Schedule / Time Slot
                        Text("Schedule", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            days.forEach { d ->
                                val sel = selectedDayOfWeek == d
                                Surface(
                                    shape = PillShape,
                                    color = if (sel) BrandAccent else AccentPill,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { selectedDayOfWeek = d }
                                ) {
                                    Text(d, fontSize = 11.sp, color = if (sel) Color.White else TextPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            timeSlots.forEach { t ->
                                val sel = timeSlotInput == t
                                Surface(
                                    shape = PillShape,
                                    color = if (sel) BrandAccent else AccentPill,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { timeSlotInput = t }
                                ) {
                                    Text(t, fontSize = 11.sp, color = if (sel) Color.White else TextPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }

                        if (addClassError != null) {
                            Text(text = addClassError!!, color = StatusAbsent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanName = subjectNameInput.trim()
                            val cleanCode = subjectCodeInput.trim().uppercase()
                            val cleanRoom = roomInput.trim().ifBlank { "Room A-302" }

                            if (cleanName.isBlank() || cleanCode.isBlank()) {
                                addClassError = "Please enter both Subject Name and Code."
                                return@Button
                            }

                            val fullProgramString = "$programInput · $semesterInput · $sectionInput"
                            val generatedJoinCode = "${cleanCode.take(4).filter { it.isLetterOrDigit() }}-${UUID.randomUUID().toString().take(4).uppercase()}"

                            val dayOfWeekInt = when (selectedDayOfWeek) {
                                "Monday" -> 1
                                "Tuesday" -> 2
                                "Wednesday" -> 3
                                "Thursday" -> 4
                                "Friday" -> 5
                                "Saturday" -> 6
                                "Sunday" -> 7
                                else -> 1
                            }
                            val startMinutes = TimetableEngine.parseTimeToMinutes(timeSlotInput)
                            val endMinutes = TimetableEngine.parseEndTimeToMinutes(timeSlotInput, startMinutes)
                            val startIsoTime = String.format(java.util.Locale.US, "%02d:%02d:00", startMinutes / 60, startMinutes % 60)
                            val endIsoTime = String.format(java.util.Locale.US, "%02d:%02d:00", endMinutes / 60, endMinutes % 60)

                            val newClass = TeacherClassItem(
                                id = UUID.randomUUID().toString(),
                                subjectName = cleanName,
                                subjectCode = cleanCode,
                                program = fullProgramString,
                                room = cleanRoom,
                                timeSlot = "$selectedDayOfWeek, ${TimetableEngine.formatDisplaySlot(startIsoTime, endIsoTime)}",
                                enrolledStudents = 0,
                                isReadyToStart = true,
                                status = ClassScheduleStatus.SCHEDULED,
                                joinCode = generatedJoinCode,
                                dayOfWeek = dayOfWeekInt,
                                startTime = startIsoTime,
                                endTime = endIsoTime
                            )

                            isCreatingSubject = true
                            updateClassList(classList + newClass)

                            coroutineScope.launch {
                                val res = SupabaseAttendanceService.createCourse(
                                    teacherName = teacherName,
                                    subjectName = cleanName,
                                    subjectCode = cleanCode,
                                    room = cleanRoom,
                                    program = fullProgramString,
                                    timeSlot = timeSlotInput.trim(),
                                    customJoinCode = newClass.joinCode,
                                    dayOfWeek = dayOfWeekInt,
                                    startTime = startIsoTime,
                                    endTime = endIsoTime
                                )
                                isCreatingSubject = false
                                showAddClassDialog = false
                                val remoteId = res.getOrNull()?.optString("id")
                                val finalClass = if (!remoteId.isNullOrBlank()) newClass.copy(id = remoteId) else newClass
                                val updated = classList.map { if (it.joinCode == newClass.joinCode) finalClass else it }
                                updateClassList(updated)
                                createdClassSuccess = finalClass
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        enabled = !isCreatingSubject,
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                    ) {
                        if (isCreatingSubject) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Creating...", fontSize = 12.sp, color = Color.White)
                        } else {
                            Text("Create Subject", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddClassDialog = false },
                        enabled = !isCreatingSubject
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        // DIALOG: SUBJECT CREATED SUCCESS MODAL
        if (createdClassSuccess != null) {
            val successItem = createdClassSuccess!!
            AlertDialog(
                onDismissRequest = { createdClassSuccess = null },
                shape = DialogShape,
                containerColor = CardBackground,
                title = {
                    Text(
                        text = "SUBJECT CREATED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = StatusPresent,
                        letterSpacing = 0.5.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = successItem.subjectName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = successItem.subjectCode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandAccent
                        )
                        Text(
                            text = successItem.program + " · " + successItem.room,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandAccent.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandAccent.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "STUDENT JOIN CODE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandAccent,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = successItem.joinCode,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TabularCodeStyle,
                                    color = BrandAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Students can use this code to join the subject.",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(successItem.joinCode, "Join Code")
                            },
                            shape = ButtonShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Code", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                shareJoinCode(successItem.subjectName, successItem.subjectCode, successItem.joinCode, successItem.program)
                            },
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        TextButton(
                            onClick = { createdClassSuccess = null }
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        if (showAiTimetableDialog) {
            AiTimetableImportDialog(
                teacherName = teacherName,
                onDismiss = { showAiTimetableDialog = false },
                onImportComplete = { newClasses ->
                    val mergedClasses = mergeConsecutiveClasses(newClasses)
                    updateClassList(mergedClasses)
                    showAiTimetableDialog = false
                    Toast.makeText(context, "✨ Timetable updated (${mergedClasses.size} lectures scheduled)!", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}


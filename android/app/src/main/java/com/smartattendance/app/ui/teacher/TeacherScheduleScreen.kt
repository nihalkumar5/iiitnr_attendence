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
import androidx.compose.ui.text.style.TextOverflow
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

    // Edit Subject Dialog State
    var showEditClassDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<TeacherClassItem?>(null) }
    var editSubjectName by remember { mutableStateOf("") }
    var editSubjectCode by remember { mutableStateOf("") }
    var editProgram by remember { mutableStateOf("") }
    var editRoom by remember { mutableStateOf("") }
    var editTimeSlot by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("10:00:00") }
    var editEndTime by remember { mutableStateOf("11:00:00") }
    var editDayOfWeek by remember { mutableStateOf(1) }
    var editIsPractical by remember { mutableStateOf(false) }
    var isSavingEdit by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }

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
            .intersemesterBackground()
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
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
                            DetailRow(label = "Course Type", value = if (currentClass.isPractical) "🔬 Practical / Lab" else "📖 Theory Lecture")
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            DetailRow(label = "Status", value = "Active")
                            HorizontalDivider(color = BorderHairline, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editingClass = currentClass
                                    editSubjectName = currentClass.subjectName.replace("\r", " ").replace("\n", " ").trim()
                                    editSubjectCode = currentClass.subjectCode.trim()
                                    editProgram = currentClass.program.replace("\r", " ").replace("\n", " ").trim()
                                    editRoom = currentClass.room.trim()
                                    editTimeSlot = currentClass.timeSlot.trim()
                                    editStartTime = currentClass.startTime.trim()
                                    editEndTime = currentClass.endTime.trim()
                                    editDayOfWeek = currentClass.dayOfWeek
                                    editIsPractical = currentClass.isPractical
                                    editError = null
                                    showEditClassDialog = true
                                },
                                shape = ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Subject Details", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
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
                    fontWeight = FontWeight.Normal,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
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
                        val cleanName = item.subjectName.replace("\r", " ").replace("\n", " ").trim()
                        val cleanProgram = item.program.replace("\r", " ").replace("\n", " ").trim().ifBlank { "M.Tech I Semester DSAI" }
                        val cleanSlot = item.timeSlot.replace("\r", " ").replace("\n", " ").trim()
                        val cleanRoom = item.room.replace("\r", " ").replace("\n", " ").trim().ifBlank { "Room 319" }
                        val cleanCode = item.subjectCode.trim().ifBlank { "CS301" }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                // Row 1: Title (weight 1f, single line ellipsis) + Format Pill + Edit Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = cleanName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isPractical) Color(0xFFF3E8FF) else Color(0xFFEFF6FF),
                                            border = BorderStroke(0.5.dp, if (item.isPractical) Color(0xFFD8B4FE) else Color(0xFFBFDBFE))
                                        ) {
                                            Text(
                                                text = if (item.isPractical) "PRACTICAL" else "THEORY",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isPractical) Color(0xFF7E22CE) else Color(0xFF1D4ED8),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            editingClass = item
                                            editSubjectName = cleanName
                                            editSubjectCode = cleanCode
                                            editProgram = cleanProgram
                                            editRoom = cleanRoom
                                            editTimeSlot = cleanSlot
                                            editStartTime = item.startTime.trim()
                                            editEndTime = item.endTime.trim()
                                            editDayOfWeek = item.dayOfWeek
                                            editIsPractical = item.isPractical
                                            editError = null
                                            showEditClassDialog = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = SurfaceNeutral,
                                        border = BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Class",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Edit",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }

                                // Row 2: Subject code · Room
                                Text(
                                    text = "$cleanCode · $cleanRoom",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandAccent
                                )

                                // Row 3: Program / Semester (single line)
                                Text(
                                    text = cleanProgram,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Row 4: Time Slot & Enrolled Count
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = cleanSlot,
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    val studentCountLabel = if (item.enrolledStudents == 1) "1 student" else "${item.enrolledStudents} students"
                                    Text(
                                        text = studentCountLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
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

        // DIALOG: DETAILED EDIT SUBJECT (Minimal, Premium Editorial UX/UI)
        if (showEditClassDialog && editingClass != null) {
            val target = editingClass!!
            AlertDialog(
                onDismissRequest = { if (!isSavingEdit) showEditClassDialog = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(Color(0xFFE2E8F0), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "COURSE SPECIFICATION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Edit Subject Details",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = BorderStroke(0.5.dp, Color(0xFFBFDBFE))
                                ) {
                                    Text(
                                        text = "Join Code: ${target.joinCode}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                onClick = { if (!isSavingEdit) showEditClassDialog = false },
                                shape = CircleShape,
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Course Format: Sleek Segmented Switcher (Theory vs Practical / Lab)
                        Column {
                            Text(
                                text = "Course Format",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Option 1: Theory Lecture
                                    Surface(
                                        onClick = { editIsPractical = false },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (!editIsPractical) Color.White else Color.Transparent,
                                        border = if (!editIsPractical) BorderStroke(1.dp, Color(0xFFE2E8F0)) else null,
                                        shadowElevation = if (!editIsPractical) 1.dp else 0.dp,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Theory Lecture",
                                                fontSize = 12.sp,
                                                fontWeight = if (!editIsPractical) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!editIsPractical) Color(0xFF0F172A) else Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    // Option 2: Practical / Lab
                                    Surface(
                                        onClick = { editIsPractical = true },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (editIsPractical) Color.White else Color.Transparent,
                                        border = if (editIsPractical) BorderStroke(1.dp, Color(0xFFE2E8F0)) else null,
                                        shadowElevation = if (editIsPractical) 1.dp else 0.dp,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Practical / Lab",
                                                fontSize = 12.sp,
                                                fontWeight = if (editIsPractical) FontWeight.Bold else FontWeight.Medium,
                                                color = if (editIsPractical) Color(0xFF7E22CE) else Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Subject Name
                        Column {
                            Text(
                                text = "Subject Name *",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            OutlinedTextField(
                                value = editSubjectName,
                                onValueChange = { editSubjectName = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFF8FAFC),
                                    focusedBorderColor = Color(0xFF0F172A),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                )
                            )
                        }

                        // 3. Subject Code & Room / Venue Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Subject Code *",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                OutlinedTextField(
                                    value = editSubjectCode,
                                    onValueChange = { editSubjectCode = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedBorderColor = Color(0xFF0F172A),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Room / Venue",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                OutlinedTextField(
                                    value = editRoom,
                                    onValueChange = { editRoom = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedBorderColor = Color(0xFF0F172A),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )
                            }
                        }

                        // 4. Scheduled Day of Week
                        Column {
                            Text(
                                text = "Scheduled Day",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val days = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                days.forEach { (dInt, dName) ->
                                    val isSelected = editDayOfWeek == dInt
                                    Surface(
                                        onClick = {
                                            editDayOfWeek = dInt
                                            val dayFull = when (dInt) {
                                                1 -> "Monday"
                                                2 -> "Tuesday"
                                                3 -> "Wednesday"
                                                4 -> "Thursday"
                                                5 -> "Friday"
                                                6 -> "Saturday"
                                                else -> "Monday"
                                            }
                                            editTimeSlot = "$dayFull, ${com.smartattendance.app.core.engine.TimetableEngine.formatDisplaySlot(editStartTime, editEndTime)}"
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                        border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = dName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF475569),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Time Schedule & Quick University Presets
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start Time",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    OutlinedTextField(
                                        value = editStartTime,
                                        onValueChange = {
                                            editStartTime = it
                                            val dayFull = when (editDayOfWeek) {
                                                1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Monday"
                                            }
                                            editTimeSlot = "$dayFull, ${com.smartattendance.app.core.engine.TimetableEngine.formatDisplaySlot(it, editEndTime)}"
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF0F172A),
                                            unfocusedBorderColor = Color(0xFFE2E8F0)
                                        )
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End Time",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    OutlinedTextField(
                                        value = editEndTime,
                                        onValueChange = {
                                            editEndTime = it
                                            val dayFull = when (editDayOfWeek) {
                                                1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Monday"
                                            }
                                            editTimeSlot = "$dayFull, ${com.smartattendance.app.core.engine.TimetableEngine.formatDisplaySlot(editStartTime, it)}"
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF0F172A),
                                            unfocusedBorderColor = Color(0xFFE2E8F0)
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Slot Presets:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val presets = listOf(
                                ("09:00" to "10:55") to "09:00 - 10:55",
                                ("11:00" to "12:55") to "11:00 - 12:55",
                                ("14:00" to "15:55") to "02:00 - 03:55",
                                ("16:00" to "17:55") to "04:00 - 05:55"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEach { (times, label) ->
                                    Surface(
                                        onClick = {
                                            editStartTime = "${times.first}:00"
                                            editEndTime = "${times.second}:00"
                                            val dayFull = when (editDayOfWeek) {
                                                1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Monday"
                                            }
                                            editTimeSlot = "$dayFull, ${com.smartattendance.app.core.engine.TimetableEngine.formatDisplaySlot(editStartTime, editEndTime)}"
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF334155),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Program / Batch
                        Column {
                            Text(
                                text = "Program / Batch",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            OutlinedTextField(
                                value = editProgram,
                                onValueChange = { editProgram = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFF8FAFC),
                                    focusedBorderColor = Color(0xFF0F172A),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                )
                            )
                        }

                        if (editError != null) {
                            Text(editError!!, color = Color(0xFFDC2626), fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editSubjectName.isBlank() || editSubjectCode.isBlank()) {
                                editError = "Subject name and code are required."
                                return@Button
                            }
                            isSavingEdit = true
                            val updatedItem = target.copy(
                                subjectName = editSubjectName.replace("\r", " ").replace("\n", " ").trim(),
                                subjectCode = editSubjectCode.trim().uppercase(),
                                room = editRoom.trim().ifBlank { "Room 319" },
                                program = editProgram.replace("\r", " ").replace("\n", " ").trim().ifBlank { "M.Tech I Semester DSAI" },
                                timeSlot = editTimeSlot.trim().ifBlank { "10:00 – 11:00 AM" },
                                startTime = editStartTime.trim().ifBlank { "10:00:00" },
                                endTime = editEndTime.trim().ifBlank { "11:00:00" },
                                dayOfWeek = editDayOfWeek,
                                isPractical = editIsPractical
                            )
                            val updatedList = classList.map {
                                if (it.id == target.id || (it.joinCode.isNotBlank() && it.joinCode == target.joinCode)) updatedItem else it
                            }
                            updateClassList(updatedList)
                            if (selectedClassForDetail?.id == target.id) {
                                selectedClassForDetail = updatedItem
                            }
                            coroutineScope.launch {
                                SupabaseAttendanceService.updateCourse(
                                    classId = target.id,
                                    subjectName = updatedItem.subjectName,
                                    subjectCode = updatedItem.subjectCode,
                                    room = updatedItem.room,
                                    program = updatedItem.program,
                                    timeSlot = updatedItem.timeSlot,
                                    dayOfWeek = updatedItem.dayOfWeek,
                                    startTime = updatedItem.startTime,
                                    endTime = updatedItem.endTime,
                                    joinCode = target.joinCode,
                                    isPractical = updatedItem.isPractical
                                )
                                isSavingEdit = false
                                showEditClassDialog = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "✅ Subject updated successfully!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSavingEdit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text(if (isSavingEdit) "Saving..." else "Save Changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showEditClassDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSavingEdit,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF8FAFC)),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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


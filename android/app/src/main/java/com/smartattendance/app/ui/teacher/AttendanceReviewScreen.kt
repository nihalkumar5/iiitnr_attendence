package com.smartattendance.app.ui.teacher

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.smartattendance.app.core.engine.AttendanceStatus
import com.smartattendance.app.core.network.FinalRollCallRecord
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudentReviewItem(
    val id: String,
    val studentId: String = "",
    val name: String,
    val rollNumber: String,
    val presenceCoverage: Int,
    val verificationSource: String,
    var status: AttendanceStatus
)

@Composable
fun AttendanceReviewScreen(
    classId: String = "",
    joinCode: String = "",
    sessionId: String = "",
    subjectName: String = "Data Structures & Algorithms",
    subjectCode: String = "CS501",
    room: String = "Room A-204 (AC Block)",
    onSubmitSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val studentList = remember { mutableStateListOf<StudentReviewItem>() }

    var selectedFilter by remember { mutableStateOf("All") }
    var showCommitSuccessDialog by remember { mutableStateOf(false) }
    var isSubmittingCloud by remember { mutableStateOf(false) }
    var isLoadingData by remember { mutableStateOf(true) }
    var activeSessionUuid by remember { mutableStateOf(sessionId) }

    // Load real final attendance records and roster from Supabase for this lecture session
    LaunchedEffect(classId, joinCode, sessionId) {
        isLoadingData = true

        // 1. Resolve session ID if not passed directly
        val effectiveSessionId = if (sessionId.isNotBlank()) {
            sessionId
        } else {
            SupabaseAttendanceService.resolveLatestSessionForClass(
                classId = classId,
                joinCode = joinCode,
                subjectCode = subjectCode,
                subjectName = subjectName
            ) ?: ""
        }
        activeSessionUuid = effectiveSessionId

        // 2. Fetch enrolled course roster
        val rosterRes = SupabaseAttendanceService.fetchCourseRoster(
            classId = classId,
            joinCode = joinCode,
            subjectCode = subjectCode,
            subjectName = subjectName
        )
        val roster = rosterRes.getOrNull() ?: emptyList()

        // 3. Fetch live recorded attendance for this session
        val liveRecords = if (effectiveSessionId.isNotBlank()) {
            SupabaseAttendanceService.fetchLiveSessionAttendance(effectiveSessionId).getOrNull() ?: emptyList()
        } else {
            emptyList()
        }

        studentList.clear()
        val addedRolls = mutableSetOf<String>()

        // 4. Merge roster with live records
        for (enrolled in roster) {
            val liveMatch = liveRecords.find {
                (it.studentId.isNotBlank() && it.studentId == enrolled.studentId) ||
                it.rollNumber.equals(enrolled.rollNumber, ignoreCase = true)
            }

            val status = when (liveMatch?.status) {
                "PRESENT" -> AttendanceStatus.PRESENT
                "REVIEW" -> AttendanceStatus.REVIEW
                else -> AttendanceStatus.ABSENT
            }

            val coverage = liveMatch?.presencePercentage?.toInt() ?: if (status == AttendanceStatus.PRESENT) 100 else 0
            val source = when (liveMatch?.verificationMethod) {
                "QR_FALLBACK" -> "Dynamic QR Scan"
                "WIFI_AUTO" -> "Classroom Wi-Fi AP"
                "MANUAL_TEACHER" -> "Teacher Override"
                null -> if (status == AttendanceStatus.PRESENT) "Wi-Fi / BLE Presence" else "Absent (Not Detected)"
                else -> liveMatch.verificationMethod
            }

            studentList.add(
                StudentReviewItem(
                    id = enrolled.studentId.ifBlank { enrolled.rollNumber },
                    studentId = enrolled.studentId,
                    name = enrolled.name,
                    rollNumber = enrolled.rollNumber,
                    presenceCoverage = coverage,
                    verificationSource = source,
                    status = status
                )
            )
            addedRolls.add(enrolled.rollNumber.uppercase())
        }

        // Add any extra students from live attendance not in roster
        for (live in liveRecords) {
            if (!addedRolls.contains(live.rollNumber.uppercase())) {
                val status = when (live.status) {
                    "PRESENT" -> AttendanceStatus.PRESENT
                    "REVIEW" -> AttendanceStatus.REVIEW
                    else -> AttendanceStatus.ABSENT
                }
                studentList.add(
                    StudentReviewItem(
                        id = live.id,
                        studentId = live.studentId,
                        name = live.studentName,
                        rollNumber = live.rollNumber,
                        presenceCoverage = live.presencePercentage.toInt(),
                        verificationSource = if (live.verificationMethod == "QR_FALLBACK") "Dynamic QR Scan" else "Classroom Wi-Fi AP",
                        status = status
                    )
                )
                addedRolls.add(live.rollNumber.uppercase())
            }
        }

        isLoadingData = false
    }

    val presentCount = studentList.count { it.status == AttendanceStatus.PRESENT }
    val reviewCount = studentList.count { it.status == AttendanceStatus.REVIEW }
    val absentCount = studentList.count { it.status == AttendanceStatus.ABSENT }

    val filterOptions = listOf(
        "All (${studentList.size})",
        "Present ($presentCount)",
        "Review ($reviewCount)",
        "Absent ($absentCount)"
    )

    val filteredList = when {
        selectedFilter.startsWith("Present") -> studentList.filter { it.status == AttendanceStatus.PRESENT }
        selectedFilter.startsWith("Review") -> studentList.filter { it.status == AttendanceStatus.REVIEW }
        selectedFilter.startsWith("Absent") -> studentList.filter { it.status == AttendanceStatus.ABSENT }
        else -> studentList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // TOP HEADER: Title & Export CSV on opposite sides, preventing wrapping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "FINAL ROLL CALL AUDIT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Attendance Review",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Surface(
                shape = PillShape,
                color = AccentPill,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    shareRollCallCsv(context, subjectCode, subjectName, studentList)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export CSV",
                        tint = BrandAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Export CSV",
                        color = BrandAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // SUBTITLE INFO CHIP
        Surface(
            shape = BadgeShape,
            color = SurfaceNeutral,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
        ) {
            Text(
                text = "$subjectCode · $room",
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SUMMARY METRIC BADGES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CardShape,
                color = StatusPresentBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "$presentCount",
                        fontWeight = FontWeight.Bold,
                        style = TabularCodeStyle,
                        fontSize = 24.sp,
                        color = StatusPresent
                    )
                    Text("Present", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                shape = CardShape,
                color = StatusReviewBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusReviewBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "$reviewCount",
                        fontWeight = FontWeight.Bold,
                        style = TabularCodeStyle,
                        fontSize = 24.sp,
                        color = StatusReview
                    )
                    Text("Review", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                shape = CardShape,
                color = StatusAbsentBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusAbsentBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "$absentCount",
                        fontWeight = FontWeight.Bold,
                        style = TabularCodeStyle,
                        fontSize = 24.sp,
                        color = StatusAbsent
                    )
                    Text("Absent", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FILTER TABS
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filterOptions) { filter ->
                val isSelected = (selectedFilter.startsWith("All") && filter.startsWith("All")) ||
                        (selectedFilter.startsWith("Present") && filter.startsWith("Present")) ||
                        (selectedFilter.startsWith("Review") && filter.startsWith("Review")) ||
                        (selectedFilter.startsWith("Absent") && filter.startsWith("Absent"))

                Surface(
                    shape = PillShape,
                    color = if (isSelected) BrandAccent.copy(alpha = 0.08f) else SurfaceNeutral,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) BrandAccent else BorderHairline
                    ),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedFilter = filter
                    }
                ) {
                    Text(
                        text = filter,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) BrandAccent else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoadingData) {
            Surface(
                shape = CardShape,
                color = SurfaceNeutral,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = BrandAccent,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading Live Roll Call Audit...",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (filteredList.isEmpty()) {
            Surface(
                shape = CardShape,
                color = SurfaceNeutral,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PeopleOutline,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Attendance Records Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Students marking presence in this session will appear here in real time.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // STUDENT ROSTER LIST
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.id }) { student ->
                    val studentIndex = studentList.indexOfFirst { it.id == student.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, CardShape),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = CardShape
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                // Status Icon
                                val (bgColor, iconTint, iconVec) = when (student.status) {
                                    AttendanceStatus.PRESENT -> Triple(StatusPresentBg, StatusPresent, Icons.Default.Check)
                                    AttendanceStatus.REVIEW -> Triple(StatusReviewBg, StatusReview, Icons.Default.QuestionMark)
                                    AttendanceStatus.ABSENT -> Triple(StatusAbsentBg, StatusAbsent, Icons.Default.Close)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(bgColor)
                                        .border(1.dp, iconTint.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = student.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = student.rollNumber,
                                        fontSize = 12.sp,
                                        style = TabularCodeStyle,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "Via: ${student.verificationSource}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Toggle Pill: tap to cycle (Present -> Absent -> Review)
                            Surface(
                                shape = BadgeShape,
                                color = when (student.status) {
                                    AttendanceStatus.PRESENT -> StatusPresentBg
                                    AttendanceStatus.REVIEW -> StatusReviewBg
                                    AttendanceStatus.ABSENT -> StatusAbsentBg
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (student.status) {
                                        AttendanceStatus.PRESENT -> StatusPresentBorder
                                        AttendanceStatus.REVIEW -> StatusReviewBorder
                                        AttendanceStatus.ABSENT -> StatusAbsentBorder
                                    }
                                ),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (studentIndex >= 0) {
                                        val nextStatus = when (student.status) {
                                            AttendanceStatus.PRESENT -> AttendanceStatus.ABSENT
                                            AttendanceStatus.ABSENT -> AttendanceStatus.REVIEW
                                            AttendanceStatus.REVIEW -> AttendanceStatus.PRESENT
                                        }
                                        val nextCoverage = when (nextStatus) {
                                            AttendanceStatus.PRESENT -> 100
                                            AttendanceStatus.REVIEW -> 50
                                            AttendanceStatus.ABSENT -> 0
                                        }
                                        studentList[studentIndex] = student.copy(
                                            status = nextStatus,
                                            presenceCoverage = nextCoverage,
                                            verificationSource = "Faculty Manual Audit"
                                        )
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${student.presenceCoverage}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        style = TabularCodeStyle,
                                        color = when (student.status) {
                                            AttendanceStatus.PRESENT -> StatusPresent
                                            AttendanceStatus.REVIEW -> StatusReview
                                            AttendanceStatus.ABSENT -> StatusAbsent
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = student.status.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when (student.status) {
                                            AttendanceStatus.PRESENT -> StatusPresent
                                            AttendanceStatus.REVIEW -> StatusReview
                                            AttendanceStatus.ABSENT -> StatusAbsent
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FINAL SUBMISSION BUTTON
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isSubmittingCloud = true
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val finalRecords = studentList.map { s ->
                        FinalRollCallRecord(
                            studentId = s.studentId,
                            rollNumber = s.rollNumber,
                            status = when (s.status) {
                                AttendanceStatus.PRESENT -> "PRESENT"
                                AttendanceStatus.REVIEW -> "REVIEW"
                                AttendanceStatus.ABSENT -> "ABSENT"
                            },
                            presencePercentage = if (s.status == AttendanceStatus.PRESENT) 100.0 else if (s.status == AttendanceStatus.REVIEW) 50.0 else 0.0,
                            verificationMethod = if (s.status == AttendanceStatus.PRESENT) "MANUAL_TEACHER" else "MANUAL_TEACHER"
                        )
                    }
                    val targetId = activeSessionUuid.ifBlank {
                        SupabaseAttendanceService.resolveLatestSessionForClass(
                            classId = classId,
                            joinCode = joinCode,
                            subjectCode = subjectCode,
                            subjectName = subjectName
                        ) ?: ""
                    }
                    if (targetId.isNotBlank()) {
                        SupabaseAttendanceService.commitFinalAttendanceRollCall(targetId, finalRecords)
                        com.smartattendance.app.core.engine.TimetableEngine.lockClassToday(context, classId, targetId)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isSubmittingCloud = false
                        showCommitSuccessDialog = true
                    }
                }
            },
            enabled = !isSubmittingCloud,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
        ) {
            if (isSubmittingCloud) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Syncing to Supabase...",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Commit & Sign to Supabase ($presentCount Present)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }

    // COMMIT CONFIRMATION MODAL
    if (showCommitSuccessDialog) {
        Dialog(onDismissRequest = {
            showCommitSuccessDialog = false
            onSubmitSuccess()
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, DialogShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = DialogShape
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(StatusPresentBg)
                            .border(1.5.dp, StatusPresentBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StatusPresent,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Attendance Committed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Session for $subjectCode has been audited and permanently recorded in Supabase with $presentCount Present and $absentCount Absent.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCommitSuccessDialog = false
                            onSubmitSuccess()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                        shape = ButtonShape
                    ) {
                        Text("Return to Home", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shareRollCallCsv(context, subjectCode, subjectName, studentList)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = ButtonShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Official CSV Sheet", color = BrandAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun shareRollCallCsv(
    context: Context,
    subjectCode: String,
    subjectName: String,
    studentList: List<StudentReviewItem>
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
    val filename = "RollCall_" + subjectCode + "_" + dateStr + ".csv"
    val header = "Roll Number,Student Name,Presence Coverage %,Status,Verification Method" + "\n"
    val rows = studentList.joinToString("\n") {
        s -> s.rollNumber + "," + s.name + "," + s.presenceCoverage + "%," + s.status.name + "," + s.verificationSource
    }
    val present = studentList.count { it.status == AttendanceStatus.PRESENT }
    val absent = studentList.count { it.status == AttendanceStatus.ABSENT }
    val headerInfo = "IIIT NAYA RAIPUR - OFFICIAL ATTENDANCE RECORD" + "\n" + "Course: " + subjectName + " (" + subjectCode + ")" + "\n" + "Generated: " + dateStr + "\n" + "Total Students: " + studentList.size + " | Present: " + present + " | Absent: " + absent + "\n\n"
    val content = headerInfo + header + rows

    try {
        val cacheFile = File(context.cacheDir, filename)
        cacheFile.writeText(content)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Roll Call: " + subjectCode + " (" + dateStr + ")")
            val summary = "Official Attendance Sheet for " + subjectName + " (" + subjectCode + ")" + "\n" + "Total Present: " + present + "/" + studentList.size
            putExtra(Intent.EXTRA_TEXT, summary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Roll Call via..."))
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Roll Call: " + subjectCode)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Roll Call via..."))
    }
}

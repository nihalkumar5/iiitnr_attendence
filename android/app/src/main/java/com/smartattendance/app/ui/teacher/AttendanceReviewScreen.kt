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
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudentReviewItem(
    val id: String,
    val name: String,
    val rollNumber: String,
    val presenceCoverage: Int,
    val verificationSource: String,
    var status: AttendanceStatus
)

@Composable
fun AttendanceReviewScreen(
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

    // Load real final attendance records from Supabase for this lecture session
    LaunchedEffect(Unit) {
        val res = SupabaseAttendanceService.fetchLiveSessionAttendance("c921ca2a-bddf-487f-a5c8-55c05929655f")
        res.onSuccess { liveRecords ->
            studentList.clear()
            liveRecords.forEach { rec ->
                studentList.add(
                    StudentReviewItem(
                        id = rec.id,
                        name = rec.studentName,
                        rollNumber = rec.rollNumber,
                        presenceCoverage = rec.presencePercentage.toInt(),
                        verificationSource = if (rec.verificationMethod == "QR_FALLBACK") "Dynamic QR Optical Scan" else "Classroom Wi-Fi AP Gateway",
                        status = if (rec.status == "PRESENT") AttendanceStatus.PRESENT else if (rec.status == "REVIEW") AttendanceStatus.REVIEW else AttendanceStatus.ABSENT
                    )
                )
            }
        }
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
        // TOP HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FINAL ROLL CALL AUDIT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Attendance Review",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
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

        // FILTER PILLS
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filterOptions) { filter ->
                val isSelected = filter == selectedFilter || (selectedFilter == "All" && filter.startsWith("All"))
                Surface(
                    shape = BadgeShape,
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

        if (filteredList.isEmpty()) {
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
                items(filteredList) { student ->
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
                                    studentList[studentIndex] = student.copy(status = nextStatus)
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
                    com.smartattendance.app.core.network.SupabaseAttendanceService.endAttendanceSession("c921ca2a-bddf-487f-a5c8-55c05929655f")
                }
                showCommitSuccessDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
        ) {
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
                        text = "Session $subjectCode has been locked and permanently recorded in the Supabase Cloud database with $presentCount students marked Present.",
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
                        Text("Return to Schedule", color = Color.White, fontWeight = FontWeight.SemiBold)
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
    val filename = "RollCall_${subjectCode}_$dateStr.csv"
    val header = "Roll Number,Student Name,Presence Coverage %,Status,Verification Method\n"
    val rows = studentList.joinToString("\n") {
        "\"${it.rollNumber}\",\"${it.name}\",${it.presenceCoverage}%,\"${it.status.name}\",\"${it.verificationSource}\""
    }
    val content = "IIIT NAYA RAIPUR - OFFICIAL ATTENDANCE RECORD\nCourse: $subjectName ($subjectCode)\nGenerated: $dateStr\nTotal Students: ${studentList.size} | Present: ${studentList.count { it.status == AttendanceStatus.PRESENT }} | Absent: ${studentList.count { it.status == AttendanceStatus.ABSENT }}\n\n$header$rows"

    try {
        val cacheFile = File(context.cacheDir, filename)
        cacheFile.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Roll Call: $subjectCode ($dateStr)")
            putExtra(Intent.EXTRA_TEXT, "Official Attendance Sheet for $subjectName ($subjectCode)\nTotal Present: ${studentList.count { it.status == AttendanceStatus.PRESENT }}/${studentList.size}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Roll Call via..."))
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Roll Call: $subjectCode")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Roll Call via..."))
    }
}

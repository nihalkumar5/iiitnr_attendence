package com.smartattendance.app.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.engine.AttendanceStatus
import com.smartattendance.app.ui.theme.*

data class StudentReviewItem(
    val id: String,
    val name: String,
    val rollNumber: String,
    val presenceCoverage: Int,
    var status: AttendanceStatus
)

@Composable
fun AttendanceReviewScreen(
    subjectName: String = "Data Structures",
    onSubmitSuccess: () -> Unit = {}
) {
    val studentList = remember {
        mutableStateListOf(
            StudentReviewItem("st-1", "Rahul Kumar", "26DSAI001", 94, AttendanceStatus.PRESENT),
            StudentReviewItem("st-2", "Aman Singh", "26DSAI002", 82, AttendanceStatus.PRESENT),
            StudentReviewItem("st-3", "Priya Sharma", "26DSAI003", 48, AttendanceStatus.REVIEW),
            StudentReviewItem("st-4", "Karan Verma", "26DSAI004", 0, AttendanceStatus.ABSENT)
        )
    }

    var showQrDialog by remember { mutableStateOf(false) }

    val presentCount = studentList.count { it.status == AttendanceStatus.PRESENT }
    val reviewCount = studentList.count { it.status == AttendanceStatus.REVIEW }
    val absentCount = studentList.count { it.status == AttendanceStatus.ABSENT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(20.dp)
    ) {
        Text("Attendance Review", style = MaterialTheme.typography.headlineLarge)
        Text(subjectName, style = MaterialTheme.typography.bodyMedium, color = SecondaryGray)

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Metric Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusPresent.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("$presentCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = StatusPresent)
                    Text("Present", fontSize = 12.sp, color = StatusPresent)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusReview.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("$reviewCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = StatusReview)
                    Text("Review", fontSize = 12.sp, color = StatusReview)
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusAbsent.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("$absentCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = StatusAbsent)
                    Text("Absent", fontSize = 12.sp, color = StatusAbsent)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Student List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(studentList.size) { index ->
                val student = studentList[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status Icon Indicator
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (student.status) {
                                            AttendanceStatus.PRESENT -> StatusPresent.copy(alpha = 0.15f)
                                            AttendanceStatus.REVIEW -> StatusReview.copy(alpha = 0.15f)
                                            AttendanceStatus.ABSENT -> StatusAbsent.copy(alpha = 0.15f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (student.status) {
                                        AttendanceStatus.PRESENT -> Icons.Default.Check
                                        AttendanceStatus.REVIEW -> Icons.Default.QuestionMark
                                        AttendanceStatus.ABSENT -> Icons.Default.Close
                                    },
                                    contentDescription = null,
                                    tint = when (student.status) {
                                        AttendanceStatus.PRESENT -> StatusPresent
                                        AttendanceStatus.REVIEW -> StatusReview
                                        AttendanceStatus.ABSENT -> StatusAbsent
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(student.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(student.rollNumber, fontSize = 12.sp, color = SecondaryGray)
                            }
                        }

                        // Tap to cycle status (Present -> Absent -> Review)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentPill,
                            modifier = Modifier.clickable {
                                studentList[index] = student.copy(
                                    status = when (student.status) {
                                        AttendanceStatus.PRESENT -> AttendanceStatus.ABSENT
                                        AttendanceStatus.ABSENT -> AttendanceStatus.REVIEW
                                        AttendanceStatus.REVIEW -> AttendanceStatus.PRESENT
                                    }
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${student.presenceCoverage}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = PrimaryBlack
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = student.status.name,
                                    fontSize = 11.sp,
                                    color = SecondaryGray
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic QR Fallback trigger
        OutlinedButton(
            onClick = { showQrDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Dynamic QR Fallback", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Submit Button
        Button(
            onClick = onSubmitSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
        ) {
            Text("Submit Attendance", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Close", color = PrimaryBlack)
                }
            },
            title = { Text("Dynamic QR Fallback", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Ask students requiring review to scan this screen. Refreshes every 15s.",
                        fontSize = 13.sp,
                        color = SecondaryGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.size(180.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryBlack
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("QR CODE", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }
}

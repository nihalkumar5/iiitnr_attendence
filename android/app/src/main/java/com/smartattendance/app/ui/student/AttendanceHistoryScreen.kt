package com.smartattendance.app.ui.student

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartattendance.app.core.network.StudentHistoryRecord
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import java.util.Locale

typealias DetailedHistoryItem = StudentHistoryRecord

@Composable
fun AttendanceHistoryScreen(
    studentRoll: String = "263200113"
) {
    val haptic = LocalHapticFeedback.current
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedRecordForReceipt by remember { mutableStateOf<DetailedHistoryItem?>(null) }
    var historyRecords by remember { mutableStateOf<List<DetailedHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(studentRoll) {
        isLoading = true
        val res = SupabaseAttendanceService.fetchStudentAttendanceHistoryDetailed(studentRoll)
        res.onSuccess {
            historyRecords = it
        }
        isLoading = false
    }

    val totalCount = historyRecords.size
    val attendedCount = historyRecords.count { it.isPresent }
    val missedCount = totalCount - attendedCount
    val percentage = if (totalCount > 0) (attendedCount.toDouble() / totalCount * 100.0) else 0.0

    val uniqueSubjects = remember(historyRecords) {
        historyRecords.map { it.subjectCode }.distinct().filter { it.isNotBlank() }
    }
    val filterOptions = listOf("All") + uniqueSubjects

    val filteredRecords = if (selectedFilter == "All") {
        historyRecords
    } else {
        historyRecords.filter { it.subjectCode == selectedFilter }
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
                    text = "ATTENDANCE LEDGER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Session History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Surface(
                shape = PillShape,
                color = if (percentage >= 75.0 || totalCount == 0) StatusPresentBg else StatusAbsentBg,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (percentage >= 75.0 || totalCount == 0) StatusPresentBorder else StatusAbsentBorder
                )
            ) {
                Text(
                    text = if (totalCount > 0) "${String.format(Locale.US, "%.1f", percentage)}% OVERALL" else "0.0% OVERALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = TabularCodeStyle,
                    color = if (percentage >= 75.0 || totalCount == 0) StatusPresent else StatusAbsent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // STATS METRIC SUMMARY CARDS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Total Lectures", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, style = TabularCodeStyle, color = TextPrimary)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Attended", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$attendedCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, style = TabularCodeStyle, color = StatusPresent)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Missed", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$missedCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, style = TabularCodeStyle, color = StatusAbsent)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FILTER PILLS
        if (filterOptions.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    val isSelected = filter == selectedFilter
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredRecords.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isLoading) "Loading Attendance Records..." else "No Attendance Records Yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isLoading) "Checking Supabase cloud ledger..." else "When you attend a live lecture via classroom Wi-Fi or 30m GPS geofence, your verified records will appear here.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // CHRONOLOGICAL ATTENDANCE SESSION LIST
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredRecords) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderHairline, CardShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedRecordForReceipt = item
                        },
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
                                Surface(
                                    shape = BadgeShape,
                                    color = SurfaceNeutral,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
                                ) {
                                    Text(
                                        text = item.subjectCode,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.date,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            // Present / Absent Badge
                            Surface(
                                shape = BadgeShape,
                                color = if (item.isPresent) StatusPresentBg else StatusAbsentBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (item.isPresent) StatusPresentBorder else StatusAbsentBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isPresent) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (item.isPresent) StatusPresent else StatusAbsent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (item.isPresent) "PRESENT" else "ABSENT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isPresent) StatusPresent else StatusAbsent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.subjectName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "${item.faculty} · ${item.room} · ${item.timeSlot}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Verification Tag
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Via: ${item.verificationMethod}",
                                fontSize = 11.sp,
                                color = if (item.isPresent) StatusPresent else TextMuted,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "View Pass >",
                                fontSize = 11.sp,
                                color = BrandAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

    // DIGITAL ATTENDANCE RECEIPT DIALOG
    selectedRecordForReceipt?.let { record ->
        Dialog(onDismissRequest = { selectedRecordForReceipt = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, DialogShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = DialogShape
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OFFICIAL ATTENDANCE RECEIPT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAccent
                        )
                        IconButton(
                            onClick = { selectedRecordForReceipt = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = record.subjectName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${record.subjectCode} · ${record.faculty}",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date & Time", fontSize = 12.sp, color = TextSecondary)
                            Text("${record.date}, ${record.timeSlot}", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Location", fontSize = 12.sp, color = TextSecondary)
                            Text(record.room, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Status", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                if (record.isPresent) "100% VERIFIED PRESENT" else "ABSENT",
                                fontSize = 12.sp,
                                color = if (record.isPresent) StatusPresent else StatusAbsent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Method", fontSize = 12.sp, color = TextSecondary)
                            Text(record.verificationMethod, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Hardware Telemetry Trace:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = record.hardwareDetail,
                            style = TabularCodeStyle,
                            fontSize = 10.sp,
                            color = StatusPresent
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Supabase Session UUID:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = record.sessionUuid,
                            style = TabularCodeStyle,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedRecordForReceipt = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                        shape = ButtonShape
                    ) {
                        Text("Done", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

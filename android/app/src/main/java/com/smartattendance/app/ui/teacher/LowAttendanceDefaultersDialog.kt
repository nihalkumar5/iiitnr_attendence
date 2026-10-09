package com.smartattendance.app.ui.teacher

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.network.DefaulterStudentItem
import com.smartattendance.app.ui.theme.*

@Composable
fun LowAttendanceDefaultersDialog(
    defaulters: List<DefaulterStudentItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedSubjectFilter by remember { mutableStateOf("All") }

    val distinctSubjects = remember(defaulters) {
        listOf("All") + defaulters.map { it.subjectCode }.distinct().filter { it.isNotBlank() }
    }

    val filteredList = remember(defaulters, searchQuery, selectedSubjectFilter) {
        defaulters.filter { item ->
            val matchSubject = selectedSubjectFilter == "All" || item.subjectCode.equals(selectedSubjectFilter, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                item.rollNumber.contains(searchQuery.trim(), ignoreCase = true) ||
                item.name.contains(searchQuery.trim(), ignoreCase = true) ||
                item.subjectName.contains(searchQuery.trim(), ignoreCase = true)
            matchSubject && matchQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = CardBackground,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(StatusReviewBg)
                                .border(1.dp, StatusReviewBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = StatusReview,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Low Attendance Audit",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Below 75% Academic Threshold",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
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
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search roll no. or name...", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.7f))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceNeutral,
                        unfocusedContainerColor = SurfaceNeutral,
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderHairline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                // Subject Filter Chips
                if (distinctSubjects.size > 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(distinctSubjects) { sub ->
                            val isSelected = selectedSubjectFilter == sub
                            val count = if (sub == "All") defaulters.size else defaulters.count { it.subjectCode.equals(sub, true) }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) BrandAccent.copy(alpha = 0.1f) else SurfaceNeutral,
                                border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedSubjectFilter = sub
                                }
                            ) {
                                Text(
                                    text = "$sub ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) BrandAccent else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Defaulter Count Summary Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} of ${defaulters.size} students flagged",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusAbsent
                    )
                    Text(
                        text = "Min required: 75%",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Student List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No students match \"$searchQuery\"" else "No students below 75% in this category.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { "${it.rollNumber}_${it.subjectCode}" }) { student ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardBackground,
                                border = BorderStroke(1.dp, BorderHairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = student.rollNumber,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    style = TabularCodeStyle,
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = SurfaceNeutral,
                                                    border = BorderStroke(0.5.dp, BorderHairline)
                                                ) {
                                                    Text(
                                                        text = student.subjectCode,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = student.name,
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        // Attendance Percentage Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = StatusAbsentBg,
                                            border = BorderStroke(1.dp, StatusAbsentBorder)
                                        ) {
                                            Text(
                                                text = "${student.percentage.toInt()}%",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                style = TabularCodeStyle,
                                                color = StatusAbsent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Progress bar toward 75%
                                    LinearProgressIndicator(
                                        progress = { (student.percentage / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = StatusAbsent,
                                        trackColor = SurfaceNeutral
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Attended ${student.attendedCount} of ${student.totalCount} classes",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )

                                        val needed = student.classesNeededFor75
                                        Text(
                                            text = if (needed > 0) "Needs $needed consecutive classes" else "Warning active",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = StatusReview
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Copy List Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val summary = buildDefaultersExportText(filteredList)
                            clipboardManager.setText(AnnotatedString(summary))
                            Toast.makeText(context, "Defaulters list copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Share Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shareDefaultersList(context, filteredList)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    )
}

private fun buildDefaultersExportText(list: List<DefaulterStudentItem>): String {
    val sb = StringBuilder()
    sb.appendLine("IIIT-NR Smart Attendance — Low Attendance Notice (<75%)")
    sb.appendLine("Total Flagged Students: ${list.size}")
    sb.appendLine("--------------------------------------------------")
    list.forEachIndexed { i, s ->
        val needed = s.classesNeededFor75
        val recoverMsg = if (needed > 0) "Needs $needed classes to reach 75%" else "Below threshold"
        sb.appendLine("${i + 1}. [${s.rollNumber}] ${s.name} — ${s.subjectCode} (${s.percentage.toInt()}%: ${s.attendedCount}/${s.totalCount}) | $recoverMsg")
    }
    sb.appendLine("--------------------------------------------------")
    sb.appendLine("Generated via Smart Attendance System")
    return sb.toString()
}

private fun shareDefaultersList(context: Context, list: List<DefaulterStudentItem>) {
    val text = buildDefaultersExportText(list)
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "IIIT-NR Low Attendance Defaulters List")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Defaulters List")
    context.startActivity(shareIntent)
}

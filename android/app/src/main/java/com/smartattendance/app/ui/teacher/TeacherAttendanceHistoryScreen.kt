package com.smartattendance.app.ui.teacher

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.EventNote
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.smartattendance.app.core.network.LiveStudentAttendanceItem
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.network.TeacherSessionHistoryRecord
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun TeacherAttendanceHistoryScreen(
    facultyName: String = "Dr. Faculty",
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var historySessions by remember { mutableStateOf<List<TeacherSessionHistoryRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedSessionForDetails by remember { mutableStateOf<TeacherSessionHistoryRecord?>(null) }

    fun refreshHistory() {
        isLoading = true
        coroutineScope.launch {
            val res = SupabaseAttendanceService.fetchTeacherAttendanceHistory()
            res.onSuccess {
                historySessions = it
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    val totalSessions = historySessions.size
    val totalPresences = historySessions.sumOf { it.totalPresent }
    val uniqueSubjects = remember(historySessions) {
        listOf("All") + historySessions.map { it.subjectCode }.distinct().filter { it.isNotBlank() }
    }

    val filteredSessions = remember(historySessions, selectedFilter) {
        if (selectedFilter == "All") historySessions
        else historySessions.filter { it.subjectCode.equals(selectedFilter, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 1. TOP HEADER (Compact, minimal, matching design system)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Session Records",
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Attendance history",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    refreshHistory()
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Sync",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. SUMMARY STATISTICS (Two compact, equal-width summary panels)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "$totalSessions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularCodeStyle,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Total Lectures",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "$totalPresences",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularCodeStyle,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Total Present",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. SUBJECT FILTERS (Constrained horizontal scrolling row, no page overflow)
        if (uniqueSubjects.size > 2) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(uniqueSubjects) { code ->
                    val isSelected = selectedFilter == code
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) BrandAccent.copy(alpha = 0.08f) else CardBackground,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) BrandAccent else BorderSubtle
                        ),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFilter = code
                        }
                    ) {
                        Text(
                            text = code,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) BrandAccent else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 4. SESSION HISTORY LIST
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = BrandAccent,
                    strokeWidth = 2.5.dp
                )
            }
        } else if (filteredSessions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
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
                        imageVector = Icons.AutoMirrored.Filled.EventNote,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Stored Attendance Sessions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Conducted lecture sessions and committed roll call audits will appear here.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                items(filteredSessions, key = { it.sessionId }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedSessionForDetails = session
                            },
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Line 1: Subject Name + Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = session.subjectName.replaceFirstChar { it.uppercase() },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                val isLive = session.status == "ACTIVE"
                                val statusBg = if (isLive) StatusReviewBg else StatusPresentBg
                                val statusBorder = if (isLive) StatusReviewBorder else StatusPresentBorder
                                val statusColor = if (isLive) StatusReview else StatusPresent
                                val statusText = if (isLive) "LIVE" else "AUDITED"

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusBg,
                                    border = BorderStroke(1.dp, statusBorder)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Line 2: Subject Code · Room
                            Text(
                                text = "${session.subjectCode} · ${session.room}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            // Line 3: Timestamp
                            Text(
                                text = formatIsoDate(session.startTime),
                                fontSize = 12.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Line 4: Present count + Export CSV
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${session.totalPresent} Present",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusPresent
                                    )
                                    if (session.totalAbsent > 0) {
                                        Text(
                                            text = "· ${session.totalAbsent} Absent",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            shareSessionCsv(context, session)
                                        }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export CSV",
                                        tint = BrandAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Export CSV",
                                        fontSize = 12.sp,
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

    // 5. SESSION AUDIT DETAIL MODAL
    if (selectedSessionForDetails != null) {
        val sess = selectedSessionForDetails!!
        Dialog(onDismissRequest = { selectedSessionForDetails = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .border(1.dp, BorderSubtle, DialogShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = DialogShape
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = sess.subjectCode + " · " + sess.room,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandAccent
                            )
                            Text(
                                text = sess.subjectName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        IconButton(onClick = { selectedSessionForDetails = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatIsoDate(sess.startTime),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusPresentBg,
                            border = BorderStroke(1.dp, StatusPresentBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${sess.totalPresent}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StatusPresent)
                                Text("Present", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusAbsentBg,
                            border = BorderStroke(1.dp, StatusAbsentBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${sess.totalAbsent}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StatusAbsent)
                                Text("Absent", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "STUDENT ROLL CALL AUDIT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (sess.records.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No individual student records for this session.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sess.records) { rec ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceNeutral,
                                    border = BorderStroke(1.dp, BorderHairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(if (rec.status == "PRESENT") StatusPresentBg else StatusAbsentBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (rec.status == "PRESENT") Icons.Default.Check else Icons.Default.Close,
                                                    contentDescription = null,
                                                    tint = if (rec.status == "PRESENT") StatusPresent else StatusAbsent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = rec.studentName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = rec.rollNumber + " · " + rec.verificationMethod,
                                                    fontSize = 10.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (rec.status == "PRESENT") StatusPresentBg else StatusAbsentBg
                                        ) {
                                            Text(
                                                text = rec.status,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (rec.status == "PRESENT") StatusPresent else StatusAbsent,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            shareSessionCsv(context, sess)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Official CSV Sheet", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun formatIsoDate(isoString: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val clean = isoString.take(19)
        val date = parser.parse(clean) ?: Date()
        val formatter = SimpleDateFormat("EEE, MMM d, yyyy · hh:mm a", Locale.getDefault())
        formatter.format(date)
    } catch (_: Exception) {
        isoString.take(10)
    }
}

private fun shareSessionCsv(
    context: Context,
    session: TeacherSessionHistoryRecord
) {
    val nl = System.lineSeparator()
    val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
    val filename = "RollCall_" + session.subjectCode + "_" + dateStr + ".csv"
    val header = "Roll Number,Student Name,Presence Coverage %,Status,Verification Method" + nl
    val rows = session.records.joinToString(nl) { s ->
        s.rollNumber + "," + s.studentName + "," + s.presencePercentage + "%," + s.status + "," + s.verificationMethod
    }
    val content = "IIIT NAYA RAIPUR - OFFICIAL ATTENDANCE RECORD" + nl +
            "Course: " + session.subjectName + " (" + session.subjectCode + ")" + nl +
            "Session Date: " + formatIsoDate(session.startTime) + nl +
            "Total Present: " + session.totalPresent + " | Absent: " + session.totalAbsent + nl + nl +
            header + rows

    try {
        val cacheFile = File(context.cacheDir, filename)
        cacheFile.writeText(content)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Roll Call: " + session.subjectCode)
            val summary = "Official Attendance Sheet for " + session.subjectName + " (" + session.subjectCode + ")" + nl +
                    "Total Present: " + session.totalPresent
            putExtra(Intent.EXTRA_TEXT, summary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Roll Call via..."))
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Roll Call: " + session.subjectCode)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "Share Roll Call via..."))
    }
}

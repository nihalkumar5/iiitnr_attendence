package com.smartattendance.app.ui.teacher

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Faculty Live Attendance Screen — Contextual Class Selection
 *
 * Minimal, Apple-quality interface focused strictly on active roll call
 * and contextually eligible classes for the current day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoActiveLectureScreen(
    onGoToSchedule: () -> Unit,
    onStartClass: (TeacherClassItem) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showClassSelectionSheet by remember { mutableStateOf(false) }

    // Schedule data
    val persistedClasses = remember { loadPersistedSchedule(context) }
    val currentIsoDay = remember { TimetableEngine.getIsoDayOfWeek() }
    val currentDateIso = remember { TimetableEngine.todayDateIso() }
    val currentDayName = remember { TimetableEngine.getDayName(currentIsoDay) }

    // Check if faculty already has an active attendance session in Supabase
    LaunchedEffect(Unit) {
        try {
            val activeSession = SupabaseAttendanceService.fetchActiveSession()
            if (activeSession != null) {
                // Resume existing active session immediately as primary content
                val matched = persistedClasses.find { it.id == activeSession.classId }
                    ?: TeacherClassItem(
                        id = activeSession.classId,
                        subjectName = activeSession.subjectName.substringBefore(" (").trim(),
                        subjectCode = if (activeSession.subjectName.contains("(") && activeSession.subjectName.contains(")"))
                            activeSession.subjectName.substringAfter("(").substringBefore(")") else "SUB101",
                        program = "B.Tech CSE",
                        room = activeSession.room,
                        timeSlot = "Active Session",
                        enrolledStudents = 45,
                        status = ClassScheduleStatus.ACTIVE
                    )
                onStartClass(matched)
                return@LaunchedEffect
            }
        } catch (_: Exception) {
            // Ignore offline/network issues; fall back to local schedule
        }
    }

    // Contextually eligible classes:
    // 1. Scheduled for current local date (dayOfWeek == currentIsoDay)
    // 2. Not cancelled
    // 3. Not already completed or locked today
    // 4. Distinct and sorted by start time
    val eligibleClasses = remember(persistedClasses, currentIsoDay, currentDateIso) {
        persistedClasses
            .filter { item ->
                val isToday = item.dayOfWeek == currentIsoDay
                val isNotCancelled = item.status != ClassScheduleStatus.CANCELLED
                val isLocked = (item.isLocked && (item.lockedDate == null || item.lockedDate == currentDateIso)) ||
                    TimetableEngine.isClassLockedToday(context, item.id, currentDateIso) ||
                    item.status == ClassScheduleStatus.LOCKED ||
                    item.status == ClassScheduleStatus.COMPLETED

                isToday && isNotCancelled && !isLocked
            }
            .distinctBy { it.id }
            .sortedBy { TimetableEngine.parseTimeToMinutes(it.startTime) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "LIVE ATTENDANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAccent,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Live Attendance",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Real-time lecture attendance & verification",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // Compact Empty State Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Status Eyebrow
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TextMuted)
                            )
                            Text(
                                text = "LIVE ATTENDANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.6.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "No active attendance",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = (-0.2).sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Start a session for an eligible class to monitor students in real time.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showClassSelectionSheet = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                        ) {
                            Text(
                                text = "Choose a Class",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Quick Status Footer / Helper Info
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currentDayName • ${eligibleClasses.size} eligible today",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Text(
                        text = "View Schedule",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onGoToSchedule()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Modal Class Selection Sheet
        if (showClassSelectionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showClassSelectionSheet = false },
                containerColor = CardBackground,
                shape = SheetShape,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(BorderSubtle)
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    // Sheet Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Select Class",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$currentDayName’s eligible lectures",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = { showClassSelectionSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (eligibleClasses.isEmpty()) {
                        // Empty State for Eligible Classes
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = CanvasBackground,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No eligible classes right now.",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "All scheduled lectures for today are completed or none remain.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = {
                                        showClassSelectionSheet = false
                                        onGoToSchedule()
                                    },
                                    shape = ButtonShape,
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandAccent),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "View Schedule",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        // List of Eligible Classes
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(eligibleClasses, key = { it.id }) { item ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = CanvasBackground,
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp)
                                        ) {
                                            Text(
                                                text = item.subjectName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${item.subjectCode} • ${item.room}",
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.timeSlot,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = BrandAccent
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showClassSelectionSheet = false
                                                onStartClass(item)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Start",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
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
}

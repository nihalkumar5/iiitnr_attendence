package com.smartattendance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.smartattendance.app.ui.theme.TabularCodeStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.ui.teacher.TeacherClassItem

private val CardDarkBackground = Color(0xFF0B0F19)
private val CardBorderColor = Color(0xFF1E293B)
private val IndigoAccent = Color(0xFF818CF8)
private val IndigoSoft = Color(0xFF1E1B4B)
private val TextSubtle = Color(0xFF64748B)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun TeacherSignatureTicketCard(
    classItem: TeacherClassItem,
    isSessionLive: Boolean,
    isPrimaryInProgress: Boolean,
    isPendingLateHero: Boolean,
    onStartAttendance: () -> Unit,
    onCopyJoinCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)

    val eyebrowText = when {
        isSessionLive -> "LIVE SESSION"
        isPrimaryInProgress -> "IN PROGRESS"
        isPendingLateHero -> "READY TO START"
        else -> "READY TO START"
    }

    val actionButtonText = if (isSessionLive) "Continue Live Attendance" else "Start Attendance"
    val actionButtonColor = if (isSessionLive) Color(0xFF10B981) else Color(0xFF2563EB)

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE) }

    // Pulsing live indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulse"
    )

    // Minimal live timer calculation
    var elapsedSeconds by remember(isSessionLive, classItem.id) { mutableLongStateOf(0L) }

    LaunchedEffect(isSessionLive, classItem.id) {
        if (isSessionLive) {
            val sessionKey = "session_start_epoch_${classItem.id}"
            var startEpoch = prefs.getLong(sessionKey, 0L)
            val nowMs = System.currentTimeMillis()

            if (startEpoch <= 0L || (nowMs - startEpoch) > 4 * 3600 * 1000L) {
                startEpoch = nowMs
                prefs.edit().putLong(sessionKey, startEpoch).apply()
            }

            while (true) {
                val current = System.currentTimeMillis()
                elapsedSeconds = ((current - startEpoch) / 1000L).coerceAtLeast(0L)
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    val timerFormatted = remember(elapsedSeconds) {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        if (mins >= 60) {
            val hrs = mins / 60
            val remMins = mins % 60
            String.format("%02d:%02d:%02d", hrs, remMins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardDarkBackground)
            .border(1.dp, CardBorderColor, cardShape)
            .clickable { onStartAttendance() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // ====================================================================
            // 1. TOP HEADER: STATUS BADGE (LEFT) + TIME PILL (RIGHT)
            // ====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSessionLive) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF064E3B).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color(0xFF10B981).copy(alpha = dotAlpha), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE · $timerFormatted",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                style = TabularCodeStyle,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = IndigoSoft.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(IndigoAccent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = eyebrowText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC7D2FE),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Time slot chip on right (Unique single appearance, no repetition!)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = classItem.timeSlot.takeIf { it.isNotBlank() } ?: "Scheduled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====================================================================
            // 2. SUBJECT TITLE
            // ====================================================================
            Text(
                text = classItem.subjectName.ifBlank { "Upcoming Class" },
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ====================================================================
            // 3. SUBJECT CODE & ROOM
            // ====================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E1B4B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4338CA).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = classItem.subjectCode,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC),
                        style = TabularCodeStyle,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val roomName = classItem.room.takeIf { it.isNotBlank() } ?: "Room 319"
                Text(
                    text = roomName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====================================================================
            // 4. DIVIDER & SUPPORTING INFO (Enrolled count + Join Code)
            // ====================================================================
            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${classItem.enrolledStudents} students enrolled",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Normal
                )

                if (classItem.joinCode.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable { onCopyJoinCode(classItem.joinCode) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Code: ${classItem.joinCode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1),
                            style = TabularCodeStyle
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====================================================================
            // 5. PRIMARY ACTION BUTTON
            // ====================================================================
            Button(
                onClick = onStartAttendance,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionButtonColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = if (isSessionLive) Icons.Default.QrCodeScanner else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = actionButtonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

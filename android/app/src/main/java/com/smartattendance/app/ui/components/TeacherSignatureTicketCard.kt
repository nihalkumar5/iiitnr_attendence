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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.ui.teacher.TeacherClassItem

/**
 * Custom Signature Ticket Shape with inward semicircular cut-outs (notches) on left and right edges.
 */
class TicketShape(
    val cornerRadius: Dp = 20.dp,
    val notchRadius: Dp = 13.dp,
    val notchOffsetFromBottom: Dp = 72.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cornerPx = with(density) { cornerRadius.toPx() }
        val notchPx = with(density) { notchRadius.toPx() }
        val notchOffsetPx = with(density) { notchOffsetFromBottom.toPx() }
        val notchCenterY = size.height - notchOffsetPx

        val path = Path().apply {
            moveTo(cornerPx, 0f)
            lineTo(size.width - cornerPx, 0f)
            arcTo(
                rect = Rect(size.width - 2 * cornerPx, 0f, size.width, 2 * cornerPx),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(size.width, notchCenterY - notchPx)
            arcTo(
                rect = Rect(size.width - notchPx, notchCenterY - notchPx, size.width + notchPx, notchCenterY + notchPx),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(size.width, size.height - cornerPx)
            arcTo(
                rect = Rect(size.width - 2 * cornerPx, size.height - 2 * cornerPx, size.width, size.height),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(cornerPx, size.height)
            arcTo(
                rect = Rect(0f, size.height - 2 * cornerPx, 2 * cornerPx, size.height),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(0f, notchCenterY + notchPx)
            arcTo(
                rect = Rect(-notchPx, notchCenterY - notchPx, notchPx, notchCenterY + notchPx),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(0f, cornerPx)
            arcTo(
                rect = Rect(0f, 0f, 2 * cornerPx, 2 * cornerPx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            close()
        }

        return Outline.Generic(path)
    }
}

private val TicketBackground = Color(0xFF0A0E17)
private val TicketBorderColor = Color(0xFF1E293B)
private val LavenderAccent = Color(0xFFC4B5FD)
private val LavenderSoft = Color(0xFF1E1B4B)
private val TicketTextMuted = Color(0xFF94A3B8)
private val TicketTextSubtle = Color(0xFF64748B)

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
    val ticketShape = TicketShape(
        cornerRadius = 20.dp,
        notchRadius = 13.dp,
        notchOffsetFromBottom = 72.dp
    )

    val eyebrowText = when {
        isSessionLive -> "LIVE SESSION"
        isPrimaryInProgress -> "IN PROGRESS"
        else -> "TODAY'S LECTURE"
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
            .clip(ticketShape)
            .background(TicketBackground)
            .border(1.dp, TicketBorderColor, ticketShape)
            .drawBehind {
                val notchOffsetPx = 72.dp.toPx()
                val notchPx = 13.dp.toPx()
                val notchCenterY = size.height - notchOffsetPx
                val strokeWidth = 1.dp.toPx()

                val pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(12f, 10f),
                    0f
                )

                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(notchPx + 8.dp.toPx(), notchCenterY),
                    end = Offset(size.width - notchPx - 8.dp.toPx(), notchCenterY),
                    strokeWidth = strokeWidth,
                    pathEffect = pathEffect
                )
            }
            .clickable { onStartAttendance() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            // ====================================================================
            // 1. EYEBROW ROW: STATUS TAG (NO DUPLICATE) & TIME BADGE
            // ====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(14.dp)
                            .background(Color(0xFF818CF8), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = eyebrowText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LavenderAccent,
                        letterSpacing = 2.sp
                    )

                    if (isSessionLive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981).copy(alpha = dotAlpha), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "LIVE NOW · $timerFormatted",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TabularCodeStyle,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }

                if (!isSessionLive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = LavenderSoft,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LavenderAccent.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = classItem.timeSlot.takeIf { it.isNotBlank() } ?: "Scheduled",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LavenderAccent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ====================================================================
            // 2. LARGE EXPRESSIVE SUBJECT TITLE
            // ====================================================================
            Text(
                text = classItem.subjectName.ifBlank { "Upcoming Class" },
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ====================================================================
            // 3. SECONDARY METADATA: [Subject Code] · [Room]
            // ====================================================================
            val roomName = classItem.room.takeIf { it.isNotBlank() } ?: "Room A-204"
            Text(
                text = "${classItem.subjectCode} · $roomName",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LavenderAccent.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ====================================================================
            // 4. SUPPORTING INFO: Enrolled Count + Join Code
            // ====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${classItem.enrolledStudents} students enrolled",
                    fontSize = 12.sp,
                    color = TicketTextMuted,
                    fontWeight = FontWeight.Normal
                )

                if (classItem.joinCode.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onCopyJoinCode(classItem.joinCode) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Code: ${classItem.joinCode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TicketTextSubtle
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = TicketTextSubtle,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // Whitespace to clear the perforated line and notches
            Spacer(modifier = Modifier.height(24.dp))

            // ====================================================================
            // 5. TICKET STUB ACTION BUTTON
            // ====================================================================
            Button(
                onClick = onStartAttendance,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
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

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
import com.smartattendance.app.ui.teacher.ClassScheduleStatus
import com.smartattendance.app.ui.teacher.TeacherClassItem

/**
 * Custom Ticket Shape with semicircular inward cut-outs (notches) on left and right edges.
 */
class TicketShape(
    val cornerRadius: Dp = 18.dp,
    val notchRadius: Dp = 12.dp,
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
private val LavenderSoft = Color(0x33C4B5FD)
private val TicketTextWhite = Color(0xFFF8FAFC)
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
        isPendingLateHero -> "PENDING ATTENDANCE"
        else -> "NEXT CLASS"
    }

    val actionButtonText = if (isSessionLive) "Continue Live Attendance" else "Start Attendance"
    val actionButtonColor = if (isSessionLive) Color(0xFF10B981) else Color(0xFF2563EB)

    // Selective typographic contrast: lightweight body with selective bold keyword
    val annotatedTitle = remember(classItem.subjectName) {
        val trimmed = classItem.subjectName.trim()
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        buildAnnotatedString {
            if (words.size > 1) {
                val prefix = words.dropLast(1).joinToString(" ") + " "
                val keyWord = words.last()
                withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Color(0xFFF1F5F9))) {
                    append(prefix)
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                    append(keyWord)
                }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White)) {
                    append(trimmed)
                }
            }
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 14.dp, start = 20.dp, end = 20.dp)
        ) {
            // ====================================================================
            // 1. EYEBROW + STATUS PILL
            // ====================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Restrained Lavender Vertical Rule
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(13.dp)
                            .background(LavenderAccent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = eyebrowText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LavenderAccent,
                        letterSpacing = 1.5.sp
                    )
                }

                if (isSessionLive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x2610B981),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "LIVE NOW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                } else {
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
            // 2. LARGE EXPRESSIVE SUBJECT TITLE (Deliberate Editorial Composition)
            // ====================================================================
            val titleWords = remember(classItem.subjectName) {
                classItem.subjectName.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (titleWords.size >= 2) {
                    val mid = if (titleWords.size == 2) 1 else (titleWords.size + 1) / 2
                    val line1 = titleWords.take(mid).joinToString(" ")
                    val line2 = titleWords.drop(mid).joinToString(" ")

                    val fs1 = if (line1.length > 16) 23.sp else 26.sp
                    val fs2 = if (line2.length > 16) 22.sp else 25.sp

                    Text(
                        text = line1,
                        fontSize = fs1,
                        lineHeight = (fs1.value * 1.15f).sp,
                        fontWeight = FontWeight.ExtraLight,
                        color = Color(0xFFE2E8F0),
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = line2,
                        fontSize = fs2,
                        lineHeight = (fs2.value * 1.15f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val singleText = classItem.subjectName.ifBlank { "Upcoming Class" }
                    val fs = if (singleText.length > 16) 23.sp else 26.sp
                    Text(
                        text = singleText,
                        fontSize = fs,
                        lineHeight = (fs.value * 1.15f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtle restrained lavender underline decorative detail
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(2.5.dp)
                    .background(LavenderAccent.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ====================================================================
            // 3. SECONDARY METADATA: [Day & Time] + [Subject Code] · [Room]
            // ====================================================================
            val roomName = classItem.room.takeIf { it.isNotBlank() } ?: "Room A-204"
            Text(
                text = "${classItem.subjectCode} · $roomName",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LavenderAccent.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = classItem.timeSlot,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TicketTextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ====================================================================
            // 4. SUPPORTING INFO: Enrolled Count + Join Code (Understated)
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

            // Generous whitespace to clear the perforated line and notches
            Spacer(modifier = Modifier.height(24.dp))

            // ====================================================================
            // 5. SINGLE PRIMARY ACTION AREA (No duplicate button)
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

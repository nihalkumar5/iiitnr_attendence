package com.smartattendance.app.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.ui.theme.*

@Composable
fun NoActiveLectureScreen(
    onGoToSchedule: () -> Unit,
    onStartClass: (TeacherClassItem) -> Unit
) {
    val context = LocalContext.current
    val persistedClasses = remember { loadPersistedSchedule(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Column {
                Text(
                    text = "LIVE ROLL CALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandAccent,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live Attendance",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Empty Status Hero Card
        item {
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(BrandAccent.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SensorsOff,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Active Lecture in Progress",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "You haven't started an attendance session yet. Start roll call for one of your scheduled subjects to activate live student verification.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onGoToSchedule,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Schedule & Classes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Quick Launch List if classes exist
        if (persistedClasses.isNotEmpty()) {
            item {
                Text(
                    text = "OR START A CLASS DIRECTLY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
            }

            items(persistedClasses) { item ->
                val isLocked = TimetableEngine.isClassLockedToday(context, item.id)
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.timeSlot,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandAccent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subjectName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${item.subjectCode} · ${item.room}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        if (isLocked) {
                            Surface(
                                shape = BadgeShape,
                                color = StatusPresentBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusPresentBorder)
                            ) {
                                Text(
                                    text = "Completed 🔒",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPresent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = { onStartClass(item) },
                                shape = ButtonShape,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Start",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
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

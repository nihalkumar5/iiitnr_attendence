package com.smartattendance.app.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.ui.theme.*

@Composable
fun StudentHomeScreen(
    studentName: String = "Rahul",
    currentSubject: String = "Data Structures",
    room: String = "A-204",
    timeSlot: String = "10:00 – 11:00",
    attendanceRate: Double = 82.4,
    attendedClasses: Int = 28,
    totalClasses: Int = 34
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(24.dp)
    ) {
        Text("Good morning,", style = MaterialTheme.typography.bodyLarge, color = SecondaryGray)
        Text(studentName, style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "TODAY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SecondaryGray
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Active Class Card (Minimal, Zero-friction indicator)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentSubject, style = MaterialTheme.typography.titleLarge)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPill
                    ) {
                        Text(
                            text = room,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(timeSlot, style = MaterialTheme.typography.bodyMedium)

                Divider(modifier = Modifier.padding(vertical = 16.dp), color = BorderSubtle)

                Text("Attendance", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(StatusPresent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StatusPresent,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Automatically tracked in background",
                        fontWeight = FontWeight.SemiBold,
                        color = StatusPresent,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "SEMESTER ATTENDANCE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SecondaryGray
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "$attendanceRate%",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Classes attended: $attendedClasses / $totalClasses",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (attendedClasses.toFloat() / totalClasses.toFloat()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrimaryBlack,
                    trackColor = AccentPill
                )
            }
        }
    }
}

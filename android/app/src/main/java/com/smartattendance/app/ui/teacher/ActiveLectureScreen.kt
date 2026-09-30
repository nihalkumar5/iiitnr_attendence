package com.smartattendance.app.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun ActiveLectureScreen(
    subjectName: String = "Data Structures",
    room: String = "A-204",
    timeSlot: String = "10:00 – 11:00",
    detectedStudents: Int = 47,
    totalStudents: Int = 50,
    onEndLecture: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentPill
                ) {
                    Text(
                        text = room,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Text(timeSlot, color = SecondaryGray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = subjectName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StatusPresent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lecture in progress · BLE Broadcaster Active",
                    color = StatusPresent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Metric Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "STUDENTS DETECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryGray,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$detectedStudents",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                        Text(
                            text = " / $totalStudents",
                            fontSize = 20.sp,
                            color = SecondaryGray,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Presence collection timeline",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { detectedStudents.toFloat() / totalStudents.toFloat() },
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

        // Single primary CTA
        Button(
            onClick = onEndLecture,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
        ) {
            Text(
                text = "End Lecture & Take Attendance",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

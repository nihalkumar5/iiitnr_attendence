package com.smartattendance.app.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.ui.theme.*

data class TeacherClassItem(
    val id: String,
    val subjectName: String,
    val subjectCode: String,
    val program: String,
    val room: String,
    val timeSlot: String,
    val isReadyToStart: Boolean
)

@Composable
fun TeacherHomeScreen(
    teacherName: String = "Dr. Sharma",
    onStartLecture: (TeacherClassItem) -> Unit = {}
) {
    val classes = listOf(
        TeacherClassItem(
            id = "class-01",
            subjectName = "Data Structures & Algorithms",
            subjectCode = "CS501",
            program = "M.Tech DSAI · Sem 1",
            room = "Room A-204",
            timeSlot = "10:00 – 11:00",
            isReadyToStart = true
        ),
        TeacherClassItem(
            id = "class-02",
            subjectName = "Machine Learning",
            subjectCode = "CS502",
            program = "M.Tech DSAI · Sem 1",
            room = "Room A-302",
            timeSlot = "12:00 – 13:00",
            isReadyToStart = false
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(20.dp)
    ) {
        Text("Good morning,", style = MaterialTheme.typography.bodyLarge, color = SecondaryGray)
        Text(teacherName, style = MaterialTheme.typography.headlineLarge)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "TODAY'S SCHEDULE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SecondaryGray
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(classes.size) { index ->
                val item = classes[index]
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
                            Text(
                                text = item.timeSlot,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = PrimaryBlack
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentPill
                            ) {
                                Text(
                                    text = item.room,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = PrimaryBlack
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(item.subjectName, style = MaterialTheme.typography.titleLarge)
                        Text(item.program, style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.height(20.dp))

                        if (item.isReadyToStart) {
                            Button(
                                onClick = { onStartLecture(item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Lecture", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                enabled = false
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upcoming", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

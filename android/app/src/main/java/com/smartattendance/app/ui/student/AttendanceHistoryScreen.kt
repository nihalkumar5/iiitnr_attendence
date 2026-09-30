package com.smartattendance.app.ui.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

data class SubjectAttendanceStat(
    val name: String,
    val percentage: Int
)

data class HistoryItem(
    val date: String,
    val subject: String,
    val isPresent: Boolean
)

@Composable
fun AttendanceHistoryScreen() {
    val subjects = listOf(
        SubjectAttendanceStat("Data Structures", 91),
        SubjectAttendanceStat("Machine Learning", 84),
        SubjectAttendanceStat("DBMS", 76),
        SubjectAttendanceStat("Networks", 88)
    )

    val history = listOf(
        HistoryItem("September 30", "Data Structures", true),
        HistoryItem("September 30", "DBMS", true),
        HistoryItem("September 29", "Machine Learning", true),
        HistoryItem("September 28", "Networks", true)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(20.dp)
    ) {
        Text("Attendance History", style = MaterialTheme.typography.headlineLarge)
        Text("Overall: 82.4%", style = MaterialTheme.typography.bodyMedium, color = SecondaryGray)

        Spacer(modifier = Modifier.height(20.dp))

        Text("THIS SEMESTER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                subjects.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sub.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("${sub.percentage}%", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryBlack)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("RECENT LOGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history.size) { index ->
                val log = history[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(log.subject, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(log.date, fontSize = 12.sp, color = SecondaryGray)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(StatusPresent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(12.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Present", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = StatusPresent)
                        }
                    }
                }
            }
        }
    }
}

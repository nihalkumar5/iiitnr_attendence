package com.smartattendance.app.ui.teacher

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartattendance.app.core.network.EnrolledStudentInfo
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CourseRosterDialog(
    classItem: TeacherClassItem,
    onDismiss: () -> Unit,
    onOpenImport: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var rosterStudents by remember { mutableStateOf<List<EnrolledStudentInfo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Add Student Modal State
    var showAddStudentModal by remember { mutableStateOf(false) }
    var newStudentRoll by remember { mutableStateOf("") }
    var newStudentName by remember { mutableStateOf("") }
    var newStudentEmail by remember { mutableStateOf("") }
    var isEnrolling by remember { mutableStateOf(false) }
    var enrollError by remember { mutableStateOf<String?>(null) }

    // Delete Student State
    var studentToDelete by remember { mutableStateOf<EnrolledStudentInfo?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    fun refreshRoster() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val res = SupabaseAttendanceService.fetchCourseRoster(classItem.id)
            isLoading = false
            if (res.isSuccess) {
                rosterStudents = res.getOrDefault(emptyList())
            } else {
                errorMessage = res.exceptionOrNull()?.message ?: "Could not load roster."
            }
        }
    }

    LaunchedEffect(classItem.id) {
        refreshRoster()
    }

    val filteredList = remember(searchQuery, rosterStudents) {
        if (searchQuery.isBlank()) rosterStudents
        else rosterStudents.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.rollNumber.contains(searchQuery, ignoreCase = true) ||
            it.email.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = CanvasBackground,
            border = BorderStroke(1.dp, BorderHairline),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentPill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.School, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = classItem.subjectCode,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = PillShape,
                                    color = AccentPill,
                                    border = BorderStroke(1.dp, BorderHairline)
                                ) {
                                    Text(
                                        text = "${rosterStudents.size} Enrolled",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandAccent
                                    )
                                }
                            }
                            Text(
                                text = classItem.subjectName,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar: Search + Add Student + Batch Import
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search name or roll...", fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandAccent,
                            unfocusedBorderColor = BorderHairline,
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground
                        )
                    )

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            newStudentRoll = ""
                            newStudentName = ""
                            newStudentEmail = ""
                            enrollError = null
                            showAddStudentModal = true
                        },
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                            onOpenImport()
                        },
                        shape = ButtonShape,
                        border = BorderStroke(1.dp, BorderHairline),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Batch", fontSize = 12.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = BrandAccent)
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(errorMessage ?: "", color = StatusAbsent, fontSize = 13.sp)
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.GroupOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No registered students found.", fontSize = 14.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap '+ Add' to enroll student or 'Batch' to import roster.", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.studentId }) { student ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CardBackground,
                                border = BorderStroke(1.dp, BorderHairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (student.isDeviceBound) StatusPresentBg else SurfaceNeutral),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = student.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (student.isDeviceBound) StatusPresent else TextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = student.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = PillShape,
                                                color = SurfaceNeutral
                                            ) {
                                                Text(
                                                    text = student.rollNumber,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandAccent
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = student.email.ifBlank { "${student.rollNumber}@student.iiitnr.edu.in" },
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    // Remove Student Button
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            studentToDelete = student
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Student",
                                            tint = StatusAbsent.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Footer Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentPill,
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Only enrolled students can mark attendance in ${classItem.subjectCode} lectures.",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog: Add Single Student
    if (showAddStudentModal) {
        AlertDialog(
            onDismissRequest = { if (!isEnrolling) showAddStudentModal = false },
            title = {
                Text(
                    text = "Add Student to ${classItem.subjectCode}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (enrollError != null) {
                        Text(enrollError ?: "", color = StatusAbsent, fontSize = 12.sp)
                    }

                    Text("Student Roll Number", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    OutlinedTextField(
                        value = newStudentRoll,
                        onValueChange = {
                            newStudentRoll = it
                            if (newStudentEmail.isBlank()) {
                                newStudentEmail = "${it.trim().lowercase()}@student.iiitnr.edu.in"
                            }
                        },
                        placeholder = { Text("e.g. 263200114", fontSize = 12.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = InputShape
                    )

                    Text("Student Full Name", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    OutlinedTextField(
                        value = newStudentName,
                        onValueChange = { newStudentName = it },
                        placeholder = { Text("e.g. Priya Sharma", fontSize = 12.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = InputShape
                    )

                    Text("Student Email Address", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    OutlinedTextField(
                        value = newStudentEmail,
                        onValueChange = { newStudentEmail = it },
                        placeholder = { Text("e.g. 263200114@student.iiitnr.edu.in", fontSize = 12.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = InputShape
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val roll = newStudentRoll.trim()
                        val name = newStudentName.trim()
                        val email = if (newStudentEmail.isNotBlank()) newStudentEmail.trim().lowercase() else "${roll.lowercase()}@student.iiitnr.edu.in"

                        if (roll.isBlank() || name.isBlank()) {
                            enrollError = "Roll Number and Name are required."
                            return@Button
                        }

                        isEnrolling = true
                        enrollError = null
                        coroutineScope.launch {
                            val res = SupabaseAttendanceService.enrollSingleStudentInCourse(
                                classId = classItem.id,
                                rollNumber = roll,
                                name = name,
                                email = email
                            )
                            isEnrolling = false
                            if (res.isSuccess) {
                                showAddStudentModal = false
                                refreshRoster()
                            } else {
                                enrollError = res.exceptionOrNull()?.message ?: "Failed to add student."
                            }
                        }
                    },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    enabled = !isEnrolling
                ) {
                    if (isEnrolling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adding...", color = Color.White)
                    } else {
                        Text("Add Student", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStudentModal = false }, enabled = !isEnrolling) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Modal Dialog: Confirm Remove Student
    if (studentToDelete != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) studentToDelete = null },
            title = {
                Text("Remove Student from Course?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text("Are you sure you want to remove ${studentToDelete?.name} (${studentToDelete?.rollNumber}) from ${classItem.subjectCode}?", fontSize = 13.sp, color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val st = studentToDelete ?: return@Button
                        isDeleting = true
                        coroutineScope.launch {
                            SupabaseAttendanceService.removeStudentFromCourse(classItem.id, st.studentId)
                            isDeleting = false
                            studentToDelete = null
                            refreshRoster()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAbsent),
                    shape = ButtonShape,
                    enabled = !isDeleting
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }, enabled = !isDeleting) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

package com.smartattendance.app.ui.teacher

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartattendance.app.core.network.CourseOfferingOption
import com.smartattendance.app.core.network.ParsedRosterStudent
import com.smartattendance.app.core.network.RosterImportResult
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.roster.RosterFileParser
import com.smartattendance.app.ui.theme.*
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch

@Composable
fun RosterImportDialog(
    initialClassId: String? = null,
    onDismiss: () -> Unit,
    onEnrollmentComplete: (RosterImportResult) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var availableCourses by remember { mutableStateOf<List<CourseOfferingOption>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseOfferingOption?>(null) }

    LaunchedEffect(Unit) {
        val res = SupabaseAttendanceService.fetchCourseOfferings()
        if (res.isSuccess) {
            val courses = res.getOrDefault(emptyList())
            availableCourses = courses
            selectedCourse = courses.find { it.classId == initialClassId } ?: courses.firstOrNull()
        }
    }

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var parsedStudents by remember { mutableStateOf<List<ParsedRosterStudent>>(emptyList()) }
    var isProcessingFile by remember { mutableStateOf(false) }
    var isEnrollingCloud by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<RosterImportResult?>(null) }

    var pasteTextMode by remember { mutableStateOf(false) }
    var manualPastedText by remember { mutableStateOf("") }

    // File picker for .xlsx, .xls, .csv, .pdf, .txt
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingFile = true
            errorMessage = null
            importResult = null

            // Query file display name
            var fileName = "roster_file"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx) ?: fileName
                    }
                }
            } catch (_: Exception) {}

            selectedFileName = fileName

            coroutineScope.launch {
                try {
                    val result = RosterFileParser.parseRosterUri(context, uri, fileName)
                    if (result.isEmpty()) {
                        errorMessage = "Could not find student rows in '$fileName'. Please ensure it has Roll Number and Name columns."
                    } else {
                        parsedStudents = result
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing file: ${e.message}"
                } finally {
                    isProcessingFile = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isEnrollingCloud && !isProcessingFile) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, BorderHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentPill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Import Student Roster",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Excel (.xlsx) · CSV · PDF Roster",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isEnrollingCloud && !isProcessingFile
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Error message banner
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusAbsentBg,
                        border = BorderStroke(1.dp, StatusAbsent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusAbsent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = errorMessage ?: "", fontSize = 12.sp, color = StatusAbsent)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Success Result Banner
                if (importResult != null) {
                    val res = importResult!!
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusPresentBg,
                        border = BorderStroke(1.dp, StatusPresent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Roster Successfully Registered in Cloud!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPresent
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Total Students: ${res.totalParsed} | Enrolled New: ${res.successfullyEnrolled} | Existing/Updated: ${res.skippedOrExisting}",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🔒 All students are authorized. When they open their phones, their accounts will be recognized and their phone hardware will be locked.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Target Course for Enrollment
                if (availableCourses.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardBackground,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enroll Students Into Course:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableCourses.take(3).forEach { course ->
                                    val isSelected = selectedCourse?.classId == course.classId
                                    Surface(
                                        shape = PillShape,
                                        color = if (isSelected) AccentPill else SurfaceNeutral,
                                        border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedCourse = course
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(course.subjectCode, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) BrandAccent else TextPrimary)
                                            Text(
                                                text = if (course.subjectName.length > 14) course.subjectName.take(14) + ".." else course.subjectName,
                                                fontSize = 9.sp,
                                                color = TextMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // File Chooser Buttons & Quick Demo Roster
                if (importResult == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                filePickerLauncher.launch("*/*")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            enabled = !isProcessingFile && !isEnrollingCloud
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select File (.xlsx / .pdf)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Load Demo 2026 Batch roster
                                val demoBatch = listOf(
                                    ParsedRosterStudent("26DSAI001-1055", "Rahul Kumar", "rahul@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("26DSAI002-1055", "Aman Singh", "aman@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("26DSAI003-1055", "Priya Sharma", "priya@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("26DSAI004-1055", "Karan Verma", "karan@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("263200113", "Nihal Kumar", "nihal26@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("263200114", "Ananya Mishra", "ananya@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("263200115", "Rohit Patel", "rohit@student.iiitnr.edu.in", 1),
                                    ParsedRosterStudent("263200116", "Sneha Rao", "sneha@student.iiitnr.edu.in", 1)
                                )
                                selectedFileName = "IIITNR_DSAI_Batch_2026.xlsx (Sample)"
                                parsedStudents = demoBatch
                                errorMessage = null
                            },
                            modifier = Modifier.height(44.dp),
                            shape = ButtonShape,
                            enabled = !isProcessingFile && !isEnrollingCloud
                        ) {
                            Text("🧪 Sample Roster", fontSize = 11.sp, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Text paste toggle link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { pasteTextMode = !pasteTextMode },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (pasteTextMode) "Hide Text Input" else "or Paste Roster / Roll List Manually",
                                fontSize = 11.sp,
                                color = BrandAccent
                            )
                        }
                    }

                    if (pasteTextMode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualPastedText,
                            onValueChange = {
                                manualPastedText = it
                                if (it.isNotBlank()) {
                                    val parsed = RosterFileParser.parseFromPlainText(it)
                                    parsedStudents = parsed
                                    selectedFileName = "Pasted Roster (${parsed.size} students)"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            placeholder = {
                                Text("Paste lines from Excel or PDF:\n26DSAI001, Rahul Kumar, rahul@iiitnr.edu.in\n26DSAI002, Aman Singh", fontSize = 11.sp, color = TextMuted)
                            },
                            textStyle = TabularCodeStyle.copy(fontSize = 11.sp),
                            shape = InputShape
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Selected File Badge & Count
                if (selectedFileName != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceNeutral,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedFileName ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            Surface(
                                shape = BadgeShape,
                                color = AccentPill,
                                border = BorderStroke(1.dp, BorderHairline)
                            ) {
                                Text(
                                    text = "${parsedStudents.size} Students",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Parsed Students Table Preview
                if (parsedStudents.isNotEmpty()) {
                    Text(
                        text = "PREVIEW OF STUDENTS TO ENROLL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceNeutral)
                    ) {
                        items(parsedStudents) { student ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = student.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = student.email,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CardBackground,
                                    border = BorderStroke(1.dp, BorderHairline)
                                ) {
                                    Text(
                                        text = student.rollNumber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TabularCodeStyle,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = BorderHairline.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                } else if (importResult == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                            .background(SurfaceNeutral),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select an Excel, CSV, or PDF file to preview roster",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = ButtonShape,
                        enabled = !isEnrollingCloud && !isProcessingFile
                    ) {
                        Text(if (importResult != null) "Close" else "Cancel", color = TextPrimary)
                    }

                    if (importResult == null) {
                        Button(
                            onClick = {
                                if (parsedStudents.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isEnrollingCloud = true
                                    errorMessage = null

                                    coroutineScope.launch {
                                        val res = SupabaseAttendanceService.enrollStudentsFromRoster(parsedStudents, selectedCourse?.classId)
                                        isEnrollingCloud = false
                                        if (res.isSuccess) {
                                            val report = res.getOrThrow()
                                            importResult = report
                                            onEnrollmentComplete(report)
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.message ?: "Enrollment failed."
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            enabled = parsedStudents.isNotEmpty() && !isEnrollingCloud && !isProcessingFile
                        ) {
                            if (isEnrollingCloud) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enrolling ${parsedStudents.size} Students...", fontSize = 12.sp, color = Color.White)
                            } else {
                                Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pre-Register ${parsedStudents.size} Students", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.smartattendance.app.ui.teacher

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartattendance.app.core.ai.GeminiTimetableParser
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiTimetableImportDialog(
    teacherName: String = "Dr. S. Sharma",
    onDismiss: () -> Unit,
    onImportComplete: (List<TeacherClassItem>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var activeTab by remember { mutableStateOf(0) } // 0: Image Upload, 1: Text Paste
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var pastedText by remember { mutableStateOf("") }

    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var parsedLectures by remember { mutableStateOf<List<GeminiTimetableParser.ParsedLecture>?>(null) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    var isImportingCloud by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            errorMessage = null
            var name = "timetable_image"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
            selectedImageName = name
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isAnalyzing && !isImportingCloud) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            color = CardBackground,
            tonalElevation = 8.dp
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BrandAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI Timetable Scanner",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Gemini AI Vision & Extraction",
                                fontSize = 12.sp,
                                color = BrandAccent
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showApiKeyDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API Key",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isAnalyzing && !isImportingCloud,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // VIEW: RESULTS PREVIEW
                if (parsedLectures != null) {
                    val lectures = parsedLectures!!
                    Text(
                        text = "Extracted ${lectures.size} Lecture Slots",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Review extracted classes and select which ones to add to your schedule.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(lectures.indices.toList()) { index ->
                            val lecture = lectures[index]
                            val isSelected = selectedIndices.contains(index)
                            val dayLabel = when (lecture.dayOfWeek) {
                                1 -> "Monday"
                                2 -> "Tuesday"
                                3 -> "Wednesday"
                                4 -> "Thursday"
                                5 -> "Friday"
                                6 -> "Saturday"
                                7 -> "Sunday"
                                else -> "Mon"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isSelected) {
                                            selectedIndices.remove(index)
                                        } else {
                                            selectedIndices.add(index)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) BrandAccent.copy(alpha = 0.08f) else CanvasBackground
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) BrandAccent.copy(alpha = 0.6f) else BorderSubtle
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedIndices.add(index) else selectedIndices.remove(index)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = BrandAccent,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = lecture.subjectName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                color = BrandAccent.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = lecture.subjectCode,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = BrandAccent,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "📅 $dayLabel",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "⏰ ${lecture.startTime.take(5)} - ${lecture.endTime.take(5)}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "📍 ${lecture.room}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        Text(
                                            text = "🎓 ${lecture.program}",
                                            fontSize = 11.sp,
                                            color = TextSecondary.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isImportingCloud) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving schedule & syncing courses...", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    parsedLectures = null
                                    selectedIndices.clear()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Text("Scan Another", color = TextSecondary, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    val chosen = selectedIndices.map { lectures[it] }
                                    if (chosen.isEmpty()) {
                                        Toast.makeText(context, "Select at least 1 class to import", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isImportingCloud = true
                                    coroutineScope.launch {
                                        val newItems = mutableListOf<TeacherClassItem>()
                                        withContext(Dispatchers.IO) {
                                            for (lec in chosen) {
                                                val item = lec.toTeacherClassItem()
                                                newItems.add(item)
                                                try {
                                                    // Sync course to Supabase so students can join & attend
                                                    SupabaseAttendanceService.createCourse(
                                                        teacherName = teacherName,
                                                        subjectName = item.subjectName,
                                                        subjectCode = item.subjectCode,
                                                        room = item.room,
                                                        program = item.program,
                                                        timeSlot = item.timeSlot,
                                                        customJoinCode = item.joinCode,
                                                        dayOfWeek = item.dayOfWeek,
                                                        startTime = item.startTime,
                                                        endTime = item.endTime
                                                    )
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        isImportingCloud = false
                                        onImportComplete(newItems)
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Import ${selectedIndices.size} Classes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                } else {
                    // VIEW: UPLOAD / INPUT CONTROLS

                    // Tabs
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = SurfaceNeutral,
                        contentColor = TextPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Upload Image", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Paste Text", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeTab == 0) {
                        // Image Upload Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, if (selectedImageUri != null) BrandAccent else BorderSubtle, RoundedCornerShape(14.dp))
                                .background(CanvasBackground)
                                .clickable {
                                    imagePickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedImageUri != null) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = if (selectedImageUri != null) BrandAccent else TextSecondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = selectedImageName ?: "Tap to choose Timetable Image / Photo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedImageUri != null) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = "Supports PNG, JPEG, Camera Photos & Screenshots",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        // Text Paste Box
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            placeholder = {
                                Text(
                                    "Paste timetable details or schedule here...\n\nExample:\nMonday: 10:00 - 11:00 AM CS301 Operating Systems (Room 302)\nTuesday: 02:00 - 03:00 PM CS304 DBMS (Lab 1)",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CanvasBackground,
                                unfocusedContainerColor = CanvasBackground,
                                focusedBorderColor = BrandAccent,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = StatusAbsentBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StatusAbsentBorder)
                        ) {
                            Text(
                                text = "⚠️ $errorMessage",
                                color = StatusAbsent,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // ACTION BUTTON
                    if (isAnalyzing) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = BrandAccent,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gemini AI is analyzing timetable & extracting slots...",
                                fontSize = 13.sp,
                                color = BrandAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                errorMessage = null
                                if (activeTab == 0) {
                                    if (selectedImageUri == null) {
                                        errorMessage = "Please select a timetable image first."
                                        return@Button
                                    }
                                    isAnalyzing = true
                                    coroutineScope.launch {
                                        val res = GeminiTimetableParser.parseFromUri(context, selectedImageUri!!)
                                        isAnalyzing = false
                                        if (res.isSuccess) {
                                            val list = res.getOrThrow()
                                            parsedLectures = list
                                            selectedIndices.clear()
                                            selectedIndices.addAll(list.indices)
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.message ?: "AI Parsing failed"
                                        }
                                    }
                                } else {
                                    if (pastedText.isBlank()) {
                                        errorMessage = "Please paste timetable text first."
                                        return@Button
                                    }
                                    isAnalyzing = true
                                    coroutineScope.launch {
                                        val res = GeminiTimetableParser.parseFromText(context, pastedText)
                                        isAnalyzing = false
                                        if (res.isSuccess) {
                                            val list = res.getOrThrow()
                                            parsedLectures = list
                                            selectedIndices.clear()
                                            selectedIndices.addAll(list.indices)
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.message ?: "AI Parsing failed"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Analyze Timetable with AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal to Rotate / Change API Key anytime
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(GeminiTimetableParser.getApiKey(context)) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "You can rotate or update your Gemini API key anytime.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempKey.isNotBlank()) {
                            GeminiTimetableParser.setApiKey(context, tempKey.trim())
                            Toast.makeText(context, "API Key updated!", Toast.LENGTH_SHORT).show()
                        }
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardBackground
        )
    }
}

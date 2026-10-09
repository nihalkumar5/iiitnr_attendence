package com.smartattendance.app.ui.teacher

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.network.DeviceUnbindRequestItem
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TeacherDeviceRequestsScreen(
    facultyName: String = "Dr. Faculty",
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<DeviceUnbindRequestItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var actionInProgressId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    fun refreshRequests() {
        isLoading = true
        coroutineScope.launch {
            val res = SupabaseAttendanceService.fetchPendingUnbindRequests()
            isLoading = false
            res.onSuccess {
                requests = it
            }.onFailure {
                Toast.makeText(context, "Failed to load requests: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshRequests()
    }

    val filtered = remember(requests, searchQuery) {
        if (searchQuery.isBlank()) requests
        else requests.filter {
            it.rollNumber.contains(searchQuery, ignoreCase = true) ||
            it.studentName.contains(searchQuery, ignoreCase = true) ||
            it.deviceModel.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .intersemesterBackground()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 1. HEADER (Editorial hierarchy, quiet refresh, no logout)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Device Management",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Device authorization and security",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    refreshRequests()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh device requests",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. PENDING REQUESTS SUMMARY
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pending requests",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${requests.size} awaiting approval",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (requests.isNotEmpty()) StatusReviewBg else StatusPresentBg,
                    border = BorderStroke(
                        1.dp,
                        if (requests.isNotEmpty()) StatusReviewBorder else StatusPresentBorder
                    )
                ) {
                    Text(
                        text = if (requests.isNotEmpty()) "Action required" else "All secure",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (requests.isNotEmpty()) StatusReview else StatusPresent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. SEARCH BAR
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = TextPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search by student name or roll number...",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. REQUESTS LIST OR COMPACT EMPTY STATE
        if (isLoading && requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BrandAccent,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
            }
        } else if (filtered.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StatusPresentBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = StatusPresent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching requests" else "All devices are secure",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try searching with a different name or roll number" else "No pending phone-change or unbind requests.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(filtered, key = { it.id }) { req ->
                    val isActing = actionInProgressId == req.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CardBackground,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header row: Student Name & Roll Number, and Request Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.studentName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = req.rollNumber,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StatusReviewBg,
                                    border = BorderStroke(1.dp, StatusReviewBorder)
                                ) {
                                    Text(
                                        text = "Unbind Request",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusReview,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Details Box: Device Model & Reason
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CanvasBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Device: ${req.deviceModel}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )
                                    }

                                    if (req.reason.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Reason: \"${req.reason}\"",
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons: Reject (Secondary) & Approve Unbind (Primary Green)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actionInProgressId = req.id
                                        coroutineScope.launch {
                                            val rejectRes = SupabaseAttendanceService.rejectDeviceUnbind(req.id, req.deviceModel)
                                            actionInProgressId = null
                                            if (rejectRes.isSuccess) {
                                                Toast.makeText(context, "Request rejected for ${req.rollNumber}", Toast.LENGTH_SHORT).show()
                                                refreshRequests()
                                            } else {
                                                Toast.makeText(context, "Error: ${rejectRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CardBackground,
                                        contentColor = TextSecondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    enabled = !isActing
                                ) {
                                    Text(
                                        text = "Reject",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actionInProgressId = req.id
                                        coroutineScope.launch {
                                            val approveRes = SupabaseAttendanceService.approveDeviceUnbind(req.id)
                                            actionInProgressId = null
                                            if (approveRes.isSuccess) {
                                                Toast.makeText(context, "✓ Unbind approved for ${req.studentName} (${req.rollNumber})! Student can now bind new phone.", Toast.LENGTH_LONG).show()
                                                refreshRequests()
                                            } else {
                                                Toast.makeText(context, "Error: ${approveRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusPresent),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    enabled = !isActing
                                ) {
                                    if (isActing) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Approve Unbind",
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
    }
}

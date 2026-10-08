package com.smartattendance.app.ui.teacher

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.smartattendance.app.core.network.DeviceUnbindRequestItem
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
            .background(CanvasBackground)
            .padding(16.dp)
    ) {
        // 1. TOP HEADER BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Anti-Proxy Hardware Authorization",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        refreshRequests()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = BrandAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onLogout != null) {
                    Surface(
                        shape = PillShape,
                        color = AccentPill,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLogout()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Logout",
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Logout",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. OVERVIEW METRICS BANNER
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
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PENDING UNBIND REQUESTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (requests.isNotEmpty()) StatusAbsent else TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${requests.size} Student${if (requests.size != 1) "s" else ""} Awaiting Approval",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Approving allows the student to register their new phone.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Surface(
                    shape = BadgeShape,
                    color = if (requests.isNotEmpty()) StatusAbsent.copy(alpha = 0.12f) else StatusPresent.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (requests.isNotEmpty()) StatusAbsent else StatusPresent
                    )
                ) {
                    Text(
                        text = if (requests.isNotEmpty()) "ACTION NEEDED" else "ALL LOCKED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (requests.isNotEmpty()) StatusAbsent else StatusPresent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by roll number, student name...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = InputShape
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. LIST OF REQUESTS
        if (isLoading && requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandAccent, modifier = Modifier.size(32.dp))
            }
        } else if (filtered.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = StatusPresent.copy(alpha = 0.12f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = StatusPresent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No Matching Requests" else "All Student Devices Are Secured",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No pending unbind requests match \"$searchQuery\""
                            else "No pending phone change or unbind requests right now. 1 Student = 1 Device hardware lock is active.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { req ->
                    val isActing = actionInProgressId == req.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderHairline, CardShape),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = CardShape
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = BrandAccent.copy(alpha = 0.12f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = req.studentName.take(2).uppercase(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandAccent
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = req.studentName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = BadgeShape,
                                                color = SurfaceNeutral,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
                                            ) {
                                                Text(
                                                    text = req.rollNumber,
                                                    fontSize = 10.sp,
                                                    style = TabularCodeStyle,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    shape = BadgeShape,
                                    color = Color(0xFFFEF3C7),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Text(
                                        text = "UNBIND REQUEST",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Device & Reason Details
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceNeutral,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Bound Device: ${req.deviceModel}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Reason: \"${req.reason}\"",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action Buttons
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
                                    modifier = Modifier.weight(1f),
                                    shape = ButtonShape,
                                    enabled = !isActing
                                ) {
                                    Text("Reject", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
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
                                    modifier = Modifier.weight(1.5f),
                                    shape = ButtonShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusPresent),
                                    enabled = !isActing
                                ) {
                                    if (isActing) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Approve Unbind", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
package com.smartattendance.app.ui.prototype

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.ble.StudentBleScanner
import com.smartattendance.app.core.ble.TeacherBleAdvertiser
import com.smartattendance.app.core.engine.AttendanceCalculator
import com.smartattendance.app.core.engine.AttendanceStatus
import com.smartattendance.app.core.engine.MultiSensorObservation
import com.smartattendance.app.core.sensor.WifiPresenceManager
import com.smartattendance.app.core.sensor.WifiRttRangingManager
import com.smartattendance.app.ui.theme.*

enum class PrototypeMode {
    HYBRID_SENSOR_STUDIO,
    PHONE_A_TEACHER_BLE,
    PHONE_B_STUDENT_BLE
}

data class SimulatedStudentRow(
    val id: String,
    val name: String,
    val hasWifi: Boolean,
    val hasBle: Boolean,
    val rttDistanceM: Double?,
    val presenceCoverage: Int,
    val result: AttendanceStatus,
    val note: String
)

@Composable
fun BlePrototypeScreen() {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(PrototypeMode.HYBRID_SENSOR_STUDIO) }

    // Hardware Managers
    val teacherAdvertiser = remember { TeacherBleAdvertiser(context) }
    val teacherState by teacherAdvertiser.state.collectAsState()

    val studentScanner = remember { StudentBleScanner(context) }
    val studentState by studentScanner.state.collectAsState()

    val wifiManager = remember { WifiPresenceManager(context) }
    val rttManager = remember { WifiRttRangingManager(context) }

    val currentWifiSnapshot = remember { wifiManager.getCurrentWifiSnapshot() }
    val isRttSupported = remember { rttManager.isRttSupported() }

    // Interactive Multi-Sensor Simulation State
    var simWifiConnected by remember { mutableStateOf(true) }
    var simBleDetected by remember { mutableStateOf(true) }
    var simRttSupported by remember { mutableStateOf(true) }
    var simRttDistance by remember { mutableStateOf(4.5f) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hybrid Presence Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Wi-Fi AP (BSSID) + BLE Proximity + Wi-Fi RTT (802.11mc)",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-Mode Segmented Controller
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AccentPill,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { selectedMode = PrototypeMode.HYBRID_SENSOR_STUDIO },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMode == PrototypeMode.HYBRID_SENSOR_STUDIO) PrimaryBlack else AccentPill,
                        contentColor = if (selectedMode == PrototypeMode.HYBRID_SENSOR_STUDIO) SurfaceWhite else PrimaryBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    elevation = null
                ) {
                    Text("Hybrid Fusion", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { selectedMode = PrototypeMode.PHONE_A_TEACHER_BLE },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMode == PrototypeMode.PHONE_A_TEACHER_BLE) PrimaryBlack else AccentPill,
                        contentColor = if (selectedMode == PrototypeMode.PHONE_A_TEACHER_BLE) SurfaceWhite else PrimaryBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    elevation = null
                ) {
                    Text("Phone A (Tx)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { selectedMode = PrototypeMode.PHONE_B_STUDENT_BLE },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMode == PrototypeMode.PHONE_B_STUDENT_BLE) PrimaryBlack else AccentPill,
                        contentColor = if (selectedMode == PrototypeMode.PHONE_B_STUDENT_BLE) SurfaceWhite else PrimaryBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    elevation = null
                ) {
                    Text("Phone B (Rx)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedMode == PrototypeMode.HYBRID_SENSOR_STUDIO) {
            // =================================================================
            // 1. HARDWARE SENSOR STATUS TIERS
            // =================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("ACTIVE MULTI-MODAL SENSORS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Tier 1: Wi-Fi AP BSSID
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = if (currentWifiSnapshot.isConnected) StatusPresent else SecondaryGray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Tier 1: Wi-Fi AP (BSSID)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = if (currentWifiSnapshot.bssid != null) "Connected: ${currentWifiSnapshot.bssid} (${currentWifiSnapshot.ssid})" else "Target: AP-204 (a4:2b:b0:12:34:56)",
                                    fontSize = 11.sp,
                                    color = SecondaryGray
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = StatusPresent.copy(alpha = 0.12f)) {
                            Text("Active", color = StatusPresent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = BorderSubtle)

                    // Tier 2: BLE Proximity Beacon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Tier 2: BLE Ephemeral Token", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("0xFD5A Service · Rotating HMAC (30s)", fontSize = 11.sp, color = SecondaryGray)
                            }
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = StatusPresent.copy(alpha = 0.12f)) {
                            Text("Active", color = StatusPresent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = BorderSubtle)

                    // Tier 3: Wi-Fi RTT 802.11mc
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NearMe, contentDescription = null, tint = if (isRttSupported) StatusPresent else StatusReview, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Tier 3: Wi-Fi RTT (802.11mc)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = if (isRttSupported) "Supported on this device (Sub-meter ToF)" else "Optional high-precision ranging signal",
                                    fontSize = 11.sp,
                                    color = SecondaryGray
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = if (isRttSupported) StatusPresent.copy(alpha = 0.12f) else AccentPill) {
                            Text(
                                text = if (isRttSupported) "Supported" else "Optional",
                                color = if (isRttSupported) StatusPresent else PrimaryBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =================================================================
            // 2. MULTI-SENSOR PRESENCE MATRIX (As designed in user request)
            // =================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("STUDENT MULTI-SENSOR PRESENCE MATRIX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
                    Text("Room A-204 · 10:00 – 11:00 Lecture", fontSize = 12.sp, color = SecondaryGray)

                    Spacer(modifier = Modifier.height(16.dp))

                    val matrixRows = listOf(
                        SimulatedStudentRow("s1", "Rahul Kumar (A)", hasWifi = true, hasBle = true, rttDistanceM = 4.2, presenceCoverage = 92, result = AttendanceStatus.PRESENT, note = "Wi-Fi AP + BLE confirmed"),
                        SimulatedStudentRow("s2", "Aman Singh (B)", hasWifi = true, hasBle = true, rttDistanceM = 6.8, presenceCoverage = 71, result = AttendanceStatus.PRESENT, note = "Continuous presence verified"),
                        SimulatedStudentRow("s3", "Priya Sharma (C)", hasWifi = true, hasBle = false, rttDistanceM = 5.0, presenceCoverage = 80, result = AttendanceStatus.REVIEW, note = "Wi-Fi AP verified, BT disabled"),
                        SimulatedStudentRow("s4", "Vikram Patel (D)", hasWifi = true, hasBle = true, rttDistanceM = 14.5, presenceCoverage = 12, result = AttendanceStatus.REVIEW, note = "Brief presence (12% coverage)"),
                        SimulatedStudentRow("s5", "Karan Verma (E)", hasWifi = false, hasBle = false, rttDistanceM = null, presenceCoverage = 0, result = AttendanceStatus.ABSENT, note = "No classroom evidence (Cafeteria)")
                    )

                    matrixRows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(row.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(row.note, fontSize = 11.sp, color = SecondaryGray)
                            }

                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                // Wi-Fi icon
                                Icon(
                                    imageVector = if (row.hasWifi) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (row.hasWifi) StatusPresent else StatusAbsent,
                                    modifier = Modifier.size(16.dp)
                                )
                                // BLE icon
                                Icon(
                                    imageVector = if (row.hasBle) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (row.hasBle) StatusPresent else StatusAbsent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("${row.presenceCoverage}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (row.result) {
                                    AttendanceStatus.PRESENT -> StatusPresent.copy(alpha = 0.12f)
                                    AttendanceStatus.REVIEW -> StatusReview.copy(alpha = 0.12f)
                                    AttendanceStatus.ABSENT -> StatusAbsent.copy(alpha = 0.12f)
                                },
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = row.result.name,
                                    color = when (row.result) {
                                        AttendanceStatus.PRESENT -> StatusPresent
                                        AttendanceStatus.REVIEW -> StatusReview
                                        AttendanceStatus.ABSENT -> StatusAbsent
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (index < matrixRows.size - 1) {
                            Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =================================================================
            // 3. INTERACTIVE SENSOR SIMULATION CONTROLS
            // =================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("LIVE SENSOR SIMULATOR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Connected to Room AP (BSSID)", fontSize = 14.sp)
                        Switch(checked = simWifiConnected, onCheckedChange = { simWifiConnected = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Classroom BLE Signal Detected", fontSize = 14.sp)
                        Switch(checked = simBleDetected, onCheckedChange = { simBleDetected = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wi-Fi RTT Distance Ranging", fontSize = 14.sp)
                        Switch(checked = simRttSupported, onCheckedChange = { simRttSupported = it })
                    }

                    if (simRttSupported) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Simulated Distance: ${String.format("%.1f", simRttDistance)} meters", fontSize = 12.sp, color = SecondaryGray)
                        Slider(
                            value = simRttDistance,
                            onValueChange = { simRttDistance = it },
                            valueRange = 1f..25f,
                            steps = 24
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = BorderSubtle)

                    // Compute Interactive Result
                    val evaluatedResult = remember(simWifiConnected, simBleDetected, simRttSupported, simRttDistance) {
                        val now = System.currentTimeMillis()
                        val simulatedObservations = mutableListOf<MultiSensorObservation>()
                        // Simulate 18 active 3-minute windows
                        for (i in 0 until 18) {
                            simulatedObservations.add(
                                MultiSensorObservation(
                                    timestampMs = now - ((18 - i) * 180 * 1000L),
                                    isWifiApMatched = simWifiConnected,
                                    isBleDetected = simBleDetected,
                                    bleRssi = if (simBleDetected) -65 else -95,
                                    rttDistanceMeters = if (simRttSupported) simRttDistance.toDouble() else null
                                )
                            )
                        }

                        AttendanceCalculator.calculateHybridCoverage(
                            observations = simulatedObservations,
                            sessionStartMs = now - (60 * 60 * 1000L),
                            sessionEndMs = now,
                            hasRoomWifiInfrastructure = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Consolidated Presence Score:", fontSize = 12.sp, color = SecondaryGray)
                            Text("${evaluatedResult.coveragePercentage}%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (evaluatedResult.status) {
                                AttendanceStatus.PRESENT -> StatusPresent.copy(alpha = 0.15f)
                                AttendanceStatus.REVIEW -> StatusReview.copy(alpha = 0.15f)
                                AttendanceStatus.ABSENT -> StatusAbsent.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = evaluatedResult.status.name,
                                fontWeight = FontWeight.Bold,
                                color = when (evaluatedResult.status) {
                                    AttendanceStatus.PRESENT -> StatusPresent
                                    AttendanceStatus.REVIEW -> StatusReview
                                    AttendanceStatus.ABSENT -> StatusAbsent
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    evaluatedResult.reviewReason?.let { reason ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: $reason",
                            fontSize = 11.sp,
                            color = StatusReview,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if (selectedMode == PrototypeMode.PHONE_A_TEACHER_BLE) {
            // PHONE A TEACHER BLE BROADCASTER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("PHONE A — TEACHER BLE BROADCASTER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (teacherState.isAdvertising) "🟢 BROADCASTING LIVE TOKEN" else "⚪ BROADCASTER IDLE",
                        fontWeight = FontWeight.Bold,
                        color = if (teacherState.isAdvertising) StatusPresent else SecondaryGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Token: ${teacherState.currentToken ?: "Not Started"}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!teacherState.isAdvertising) {
                        Button(
                            onClick = { teacherAdvertiser.startAdvertising("60000000-0000-0000-0000-000000000001", "e4f8a3c9b7d2e1f0a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
                        ) {
                            Text("Start BLE Broadcast")
                        }
                    } else {
                        Button(
                            onClick = { teacherAdvertiser.stopAdvertising() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAbsent)
                        ) {
                            Text("Stop BLE Broadcast")
                        }
                    }
                }
            }
        } else {
            // PHONE B STUDENT BLE RECEIVER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("PHONE B — STUDENT PASSIVE SCANNER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SecondaryGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (studentState.isScanning) "🟢 SCANNER ACTIVE" else "⚪ SCANNER IDLE",
                        fontWeight = FontWeight.Bold,
                        color = if (studentState.isScanning) StatusPresent else SecondaryGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Detected Token: ${studentState.lastDetectedToken ?: "None"}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("RSSI: ${studentState.lastRssi ?: "--"} dBm", fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!studentState.isScanning) {
                        Button(
                            onClick = { studentScanner.startScan() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
                        ) {
                            Text("Start Passive Scan")
                        }
                    } else {
                        Button(
                            onClick = { studentScanner.stopScan() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack)
                        ) {
                            Text("Stop Scanning")
                        }
                    }
                }
            }
        }
    }
}

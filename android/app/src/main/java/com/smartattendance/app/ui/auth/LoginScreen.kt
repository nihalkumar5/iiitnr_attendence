package com.smartattendance.app.ui.auth

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.core.auth.GoogleAuthManager
import com.smartattendance.app.core.auth.GoogleUserProfile
import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.ui.theme.*
import kotlinx.coroutines.launch

enum class LoginPortal {
    STUDENT,
    FACULTY
}

@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF4285F4)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginStudent: (name: String, roll: String) -> Unit,
    onLoginFaculty: (name: String, facultyId: String, department: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE) }

    // Persistent hardware installation key for anti-proxy device binding
    val installationId = remember {
        prefs.getString("device_installation_id", null) ?: run {
            val newId = "DEV-" + java.util.UUID.randomUUID().toString().take(12).uppercase()
            prefs.edit().putString("device_installation_id", newId).apply()
            newId
        }
    }

    var selectedPortal by remember { mutableStateOf(LoginPortal.STUDENT) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var securityWarning by remember { mutableStateOf<String?>(null) }

    // Post-Login Profile Completion States (for students)
    var showProfileCompletion by remember { mutableStateOf(false) }
    var pendingGoogleProfile by remember { mutableStateOf<GoogleUserProfile?>(null) }
    var profileFullName by remember { mutableStateOf("") }
    var profileRollNumber by remember { mutableStateOf("") }
    var profileProgram by remember { mutableStateOf("B.Tech DSAI") }
    var profileSemester by remember { mutableStateOf("Semester 5") }
    var profileSection by remember { mutableStateOf("Section A") }

    val haptic = LocalHapticFeedback.current

    val programOptions = listOf("B.Tech DSAI", "B.Tech CSE", "B.Tech ECE", "M.Tech CSE")
    val semesterOptions = listOf("Semester 1", "Semester 2", "Semester 3", "Semester 4", "Semester 5", "Semester 6", "Semester 7", "Semester 8")
    val sectionOptions = listOf("Section A", "Section B", "Section C")

    // Google Sign-In Result Launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val parseRes = GoogleAuthManager.parseSignInResult(result.data, allowAnyDomain = true)

            parseRes.onSuccess { profile ->
                isLoading = true
                errorMessage = null
                securityWarning = null

                coroutineScope.launch {
                    if (selectedPortal == LoginPortal.STUDENT) {
                        // Check if student profile is already completed in database
                        val checkRes = SupabaseAttendanceService.checkStudentGoogleAuth(
                            googleProfile = profile,
                            installationId = installationId
                        )
                        isLoading = false

                        checkRes.onSuccess { check ->
                            if (check.isProfileComplete && check.authResult != null) {
                                // Profile already complete! Log in immediately
                                val student = check.authResult
                                prefs.edit()
                                    .putString("logged_in_role", "STUDENT")
                                    .putString("selected_name", student.name)
                                    .putString("selected_roll", student.rollNumber)
                                    .putString("selected_email", profile.email)
                                    .putString("student_id", student.studentId)
                                    .putString("user_id", student.userId)
                                    .putString("device_id", student.deviceId)
                                    .putBoolean("is_device_bound", true)
                                    .putBoolean("is_profile_completed", true)
                                    .apply()

                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLoginStudent(student.name, student.rollNumber)
                            } else {
                                // First time / incomplete profile -> Prompt student to complete profile!
                                pendingGoogleProfile = profile
                                profileFullName = check.displayName.ifBlank { profile.displayName }
                                profileRollNumber = check.existingRoll ?: ""
                                if (check.existingSemester != null) {
                                    profileSemester = "Semester ${check.existingSemester}"
                                }
                                showProfileCompletion = true
                            }
                        }.onFailure { err ->
                            if (err is SecurityException) {
                                securityWarning = err.message
                            } else {
                                errorMessage = err.message ?: "Google authentication check failed."
                            }
                        }
                    } else {
                        // Faculty Google Sign-In
                        val authResult = SupabaseAttendanceService.authenticateFacultyWithGoogle(
                            googleProfile = profile,
                            allowAnyDomainForTesting = true
                        )
                        isLoading = false

                        authResult.onSuccess { faculty ->
                            prefs.edit()
                                .putString("logged_in_role", "TEACHER")
                                .putString("logged_in_faculty_name", faculty.name)
                                .putString("logged_in_faculty_id", faculty.employeeId)
                                .putString("logged_in_faculty_dept", faculty.department)
                                .putString("logged_in_faculty_uuid", faculty.teacherId)
                                .apply()

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLoginFaculty(faculty.name, faculty.employeeId, faculty.department)
                        }.onFailure { err ->
                            if (err is SecurityException) {
                                securityWarning = err.message
                            } else {
                                errorMessage = err.message ?: "Faculty authentication failed."
                            }
                        }
                    }
                }
            }.onFailure { ex ->
                if (ex is SecurityException) {
                    securityWarning = ex.message
                } else {
                    errorMessage = ex.message ?: "Google Sign-In failed."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ============================================================
        // MODE 1: POST-LOGIN PROFILE COMPLETION VIEW
        // ============================================================
        if (showProfileCompletion && pendingGoogleProfile != null) {
            val googleProf = pendingGoogleProfile!!

            Surface(
                shape = PillShape,
                color = SurfaceNeutral,
                border = BorderStroke(1.dp, BorderHairline),
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable {
                        showProfileCompletion = false
                        pendingGoogleProfile = null
                        errorMessage = null
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Switch Account",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Header
            Text(
                text = "Complete Your Student Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Link your official roll number and academic batch with your Google account.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Verified Google Account Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoogleLogoIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = googleProf.displayName.ifBlank { "Student Account" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = googleProf.email,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = BadgeShape,
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Connected",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Error display
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusAbsentBg,
                    border = BorderStroke(1.dp, StatusAbsent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Error", tint = StatusAbsent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMessage ?: "", fontSize = 12.sp, color = StatusAbsent)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Profile Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Full Name
                    Text("Full Name", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = profileFullName,
                        onValueChange = { profileFullName = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Nihal Kumar", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        shape = InputShape
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // College Roll Number
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("College Roll Number", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(" *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAbsent)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = profileRollNumber,
                        onValueChange = { profileRollNumber = it.uppercase(); errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 263200113 or BT23DSAI001", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = InputShape
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Branch / Program
                    Text("Branch / Program", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        programOptions.forEach { prog ->
                            val isSelected = profileProgram == prog
                            Surface(
                                shape = PillShape,
                                color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else SurfaceNeutral,
                                border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                modifier = Modifier.clickable { profileProgram = prog }
                            ) {
                                Text(
                                    text = prog,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) BrandAccent else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Semester
                    Text("Semester", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        semesterOptions.forEach { sem ->
                            val isSelected = profileSemester == sem
                            Surface(
                                shape = PillShape,
                                color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else SurfaceNeutral,
                                border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                modifier = Modifier.clickable { profileSemester = sem }
                            ) {
                                Text(
                                    text = sem,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) BrandAccent else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Section
                    Text("Section", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sectionOptions.forEach { sec ->
                            val isSelected = profileSection == sec
                            Surface(
                                shape = PillShape,
                                color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else SurfaceNeutral,
                                border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { profileSection = sec }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sec,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) BrandAccent else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Submit Profile CTA Button
                    Button(
                        onClick = {
                            val cleanName = profileFullName.trim()
                            val cleanRoll = profileRollNumber.trim().uppercase()

                            if (cleanName.isBlank()) {
                                errorMessage = "Please enter your full name."
                                return@Button
                            }
                            if (cleanRoll.isBlank()) {
                                errorMessage = "Please enter your official college roll number."
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            val semNum = profileSemester.replace("Semester ", "").toIntOrNull() ?: 5

                            coroutineScope.launch {
                                val completeRes = SupabaseAttendanceService.completeStudentProfile(
                                    googleProfile = googleProf,
                                    fullName = cleanName,
                                    rollNumber = cleanRoll,
                                    programName = profileProgram,
                                    semester = semNum,
                                    sectionName = profileSection,
                                    installationId = installationId
                                )
                                isLoading = false

                                completeRes.onSuccess { student ->
                                    prefs.edit()
                                        .putString("logged_in_role", "STUDENT")
                                        .putString("selected_name", cleanName)
                                        .putString("selected_roll", cleanRoll)
                                        .putString("selected_email", googleProf.email)
                                        .putString("selected_program", profileProgram)
                                        .putString("selected_semester", profileSemester)
                                        .putString("selected_section", profileSection)
                                        .putString("student_id", student.studentId)
                                        .putString("user_id", student.userId)
                                        .putString("device_id", student.deviceId)
                                        .putBoolean("is_device_bound", true)
                                        .putBoolean("is_profile_completed", true)
                                        .apply()

                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLoginStudent(cleanName, cleanRoll)
                                }.onFailure { err ->
                                    if (err is SecurityException) {
                                        securityWarning = err.message
                                    } else {
                                        errorMessage = err.message ?: "Failed to save student profile."
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving Profile...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Save Profile & Enter Portal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

        } else {
            // ============================================================
            // MODE 2: CLEAN GOOGLE-ONLY LOGIN SCREEN
            // ============================================================

            // Institution Brand Logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BrandAccent.copy(alpha = 0.12f))
                    .border(1.5.dp, BrandAccent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "IIIT-NR Logo",
                    tint = BrandAccent,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "IIIT NAYA RAIPUR",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = TextPrimary
            )

            Text(
                text = "Digital Attendance & Cryptographic Presence System",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Role Selector: Student Portal vs Faculty Console
            Surface(
                shape = PillShape,
                color = SurfaceNeutral,
                border = BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    val isStudent = selectedPortal == LoginPortal.STUDENT
                    Surface(
                        shape = PillShape,
                        color = if (isStudent) CardBackground else Color.Transparent,
                        border = if (isStudent) BorderStroke(1.dp, BorderHairline) else null,
                        shadowElevation = if (isStudent) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedPortal = LoginPortal.STUDENT
                                errorMessage = null
                                securityWarning = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                tint = if (isStudent) BrandAccent else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Student Portal",
                                fontSize = 12.sp,
                                fontWeight = if (isStudent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isStudent) TextPrimary else TextSecondary
                            )
                        }
                    }

                    val isFaculty = selectedPortal == LoginPortal.FACULTY
                    Surface(
                        shape = PillShape,
                        color = if (isFaculty) CardBackground else Color.Transparent,
                        border = if (isFaculty) BorderStroke(1.dp, BorderHairline) else null,
                        shadowElevation = if (isFaculty) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedPortal = LoginPortal.FACULTY
                                errorMessage = null
                                securityWarning = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = if (isFaculty) BrandAccent else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Faculty Console",
                                fontSize = 12.sp,
                                fontWeight = if (isFaculty) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFaculty) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security Violation Alert Banner
            if (securityWarning != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.5.dp, StatusAbsent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.GppBad,
                            contentDescription = "Security Alert",
                            tint = StatusAbsent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CAMPUS SECURITY VIOLATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = StatusAbsent,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = securityWarning ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Error Alert Banner
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusAbsentBg,
                    border = BorderStroke(1.dp, StatusAbsent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Error", tint = StatusAbsent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = errorMessage ?: "", fontSize = 12.sp, color = StatusAbsent, lineHeight = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Main Google-Only Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderHairline, CardShape),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = if (selectedPortal == LoginPortal.STUDENT) "STUDENT ACCESS" else "FACULTY ACCESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAccent,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (selectedPortal == LoginPortal.STUDENT)
                            "Sign in with your Google account to complete attendance, verify presence, and manage your courses."
                        else
                            "Sign in with your Google account to create live encrypted sessions and review student attendance.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Single Primary Action: Continue with Google
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                errorMessage = null
                                securityWarning = null
                                try {
                                    val client = GoogleAuthManager.getGoogleSignInClient(
                                        context = context,
                                        enforceHostedDomain = false
                                    )
                                    googleLauncher.launch(client.signInIntent)
                                } catch (e: Exception) {
                                    errorMessage = "Google Sign-In failed to launch: ${e.message}"
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF4285F4)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Authenticating with Google...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2937)
                                )
                            } else {
                                GoogleLogoIcon()
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Continue with Google",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1F2937)
                                    )
                                    Text(
                                        text = if (selectedPortal == LoginPortal.STUDENT)
                                            "New students will complete profile next"
                                        else
                                            "Official faculty Google authentication",
                                        fontSize = 10.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "IIIT-NR Cryptographic Attendance System · v2.6",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

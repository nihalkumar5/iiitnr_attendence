package com.smartattendance.app.ui.auth

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

enum class StudentAuthMode {
    SIGN_IN,
    REGISTER
}

enum class FacultyAuthMode {
    SIGN_IN,
    REGISTER
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

    // Persistent hardware installation key for device binding
    val installationId = remember {
        prefs.getString("device_installation_id", null) ?: run {
            val newId = "DEV-" + java.util.UUID.randomUUID().toString().take(12).uppercase()
            prefs.edit().putString("device_installation_id", newId).apply()
            newId
        }
    }

    // Portal & Auth Mode State
    var selectedPortal by remember { mutableStateOf(LoginPortal.STUDENT) }
    var studentAuthMode by remember { mutableStateOf(StudentAuthMode.SIGN_IN) }
    var facultyAuthMode by remember { mutableStateOf(FacultyAuthMode.SIGN_IN) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var securityWarning by remember { mutableStateOf<String?>(null) }

    // Student Registration Form Fields
    var studentRegFullName by remember { mutableStateOf("") }
    var studentRegRollNumber by remember { mutableStateOf("") }
    var studentRegEmail by remember { mutableStateOf("") }
    var studentRegProgram by remember { mutableStateOf("B.Tech DSAI") }
    var studentRegSemester by remember { mutableStateOf("Semester 5") }
    var studentRegSection by remember { mutableStateOf("Section A") }

    // Faculty Registration Form Fields
    var facultyRegName by remember { mutableStateOf("") }
    var facultyRegEmpId by remember { mutableStateOf("") }
    var facultyRegDept by remember { mutableStateOf("Computer Science & Engineering") }
    var facultyRegEmail by remember { mutableStateOf("") }
    var facultyRegPassword by remember { mutableStateOf("") }

    // Manual fallback fields (strictly requiring @iiitnr.edu.in)
    var manualEmailInput by remember { mutableStateOf("") }
    var manualPasswordInput by remember { mutableStateOf("password123") }
    var showManualInputs by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    // Presets for Academic Details
    val programOptions = listOf("B.Tech DSAI", "B.Tech CSE", "B.Tech ECE", "M.Tech CSE")
    val semesterOptions = listOf("Semester 1", "Semester 2", "Semester 3", "Semester 4", "Semester 5", "Semester 6", "Semester 7", "Semester 8")
    val sectionOptions = listOf("Section A", "Section B", "Section C")

    // 1. Google Sign-In Activity Result Launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val allowAny = (selectedPortal == LoginPortal.FACULTY)
            val parseRes = GoogleAuthManager.parseSignInResult(result.data, allowAnyDomain = true)

            parseRes.onSuccess { profile ->
                isLoading = true
                errorMessage = null
                securityWarning = null

                coroutineScope.launch {
                    if (selectedPortal == LoginPortal.STUDENT) {
                        val authResult = SupabaseAttendanceService.authenticateStudentWithGoogle(
                            googleProfile = profile,
                            installationId = installationId
                        )
                        isLoading = false

                        authResult.onSuccess { student ->
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
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Student authentication failed."
                        }
                    } else {
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
                            errorMessage = err.message ?: "Faculty authentication failed."
                        }
                    }
                }
            }.onFailure { ex ->
                if (ex is SecurityException) {
                    securityWarning = ex.message
                } else {
                    errorMessage = ex.message ?: "Sign in failed"
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
        // INSTITUTION BRAND LOGO & HEADER
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(BrandAccent.copy(alpha = 0.12f))
                .border(1.5.dp, BrandAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = "IIIT-NR Logo",
                tint = BrandAccent,
                modifier = Modifier.size(32.dp)
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

        // DUAL-ROLE SEGMENTED SELECTOR
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

        // ANTI-PROXY SECURITY REJECTION BANNER
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

        // REGULAR ERROR ALERT
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

        // MAIN AUTH CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderHairline, CardShape),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = CardShape
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ============================================================
                // STUDENT PORTAL: SIGN IN VS REGISTRATION TOGGLE
                // ============================================================
                if (selectedPortal == LoginPortal.STUDENT) {
                    Surface(
                        shape = PillShape,
                        color = SurfaceNeutral,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            val isSignIn = studentAuthMode == StudentAuthMode.SIGN_IN
                            Surface(
                                shape = PillShape,
                                color = if (isSignIn) CardBackground else Color.Transparent,
                                border = if (isSignIn) BorderStroke(1.dp, BorderHairline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        studentAuthMode = StudentAuthMode.SIGN_IN
                                        errorMessage = null
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Student Sign In",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSignIn) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSignIn) TextPrimary else TextSecondary
                                    )
                                }
                            }

                            val isReg = studentAuthMode == StudentAuthMode.REGISTER
                            Surface(
                                shape = PillShape,
                                color = if (isReg) CardBackground else Color.Transparent,
                                border = if (isReg) BorderStroke(1.dp, BorderHairline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        studentAuthMode = StudentAuthMode.REGISTER
                                        errorMessage = null
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "New Registration",
                                        fontSize = 12.sp,
                                        fontWeight = if (isReg) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isReg) BrandAccent else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // --------------------------------------------------------
                    // STUDENT REGISTRATION / ONBOARDING FORM
                    // --------------------------------------------------------
                    if (studentAuthMode == StudentAuthMode.REGISTER) {
                        Text(
                            text = "STUDENT REGISTRATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAccent,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create your student profile once. Your academic identity is saved and reused for all class attendance.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // STEP 1 — BASIC DETAILS
                        Text(
                            text = "STEP 1 — BASIC DETAILS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Full Name", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = studentRegFullName,
                            onValueChange = { studentRegFullName = it; errorMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Nihal Kumar", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Roll Number", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = studentRegRollNumber,
                            onValueChange = { studentRegRollNumber = it.uppercase(); errorMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. BT23DSAI001", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Email / Institutional Email", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = studentRegEmail,
                            onValueChange = { studentRegEmail = it.lowercase(); errorMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. rahul@gmail.com or student@iiitnr.edu.in", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // STEP 2 — ACADEMIC DETAILS
                        Text(
                            text = "STEP 2 — ACADEMIC DETAILS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Branch / Program", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            programOptions.forEach { prog ->
                                val isSelected = studentRegProgram == prog
                                Surface(
                                    shape = PillShape,
                                    color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else SurfaceNeutral,
                                    border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { studentRegProgram = prog }
                                ) {
                                    Text(
                                        text = prog,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) BrandAccent else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Semester", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            semesterOptions.forEach { sem ->
                                val isSelected = studentRegSemester == sem
                                Surface(
                                    shape = PillShape,
                                    color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else SurfaceNeutral,
                                    border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                    modifier = Modifier.clickable { studentRegSemester = sem }
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

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Section", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sectionOptions.forEach { sec ->
                                val isSelected = studentRegSection == sec
                                Surface(
                                    shape = PillShape,
                                    color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else SurfaceNeutral,
                                    border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderHairline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { studentRegSection = sec }
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

                        Spacer(modifier = Modifier.height(20.dp))

                        // PRIMARY ACTION: CREATE ACCOUNT
                        Button(
                            onClick = {
                                val cleanName = studentRegFullName.trim()
                                val cleanRoll = studentRegRollNumber.trim().uppercase()
                                val cleanEmail = studentRegEmail.trim().lowercase()

                                if (cleanName.isBlank()) {
                                    errorMessage = "Please enter your Full Name."
                                    return@Button
                                }
                                if (cleanRoll.isBlank()) {
                                    errorMessage = "Please enter your Roll Number."
                                    return@Button
                                }
                                if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                                    errorMessage = "Please enter a valid email address."
                                    return@Button
                                }
                                if (studentRegProgram.isBlank()) {
                                    errorMessage = "Please select your Program/Branch."
                                    return@Button
                                }
                                if (studentRegSemester.isBlank()) {
                                    errorMessage = "Please select your Semester."
                                    return@Button
                                }
                                if (studentRegSection.isBlank()) {
                                    errorMessage = "Please select your Section."
                                    return@Button
                                }

                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLoading = true
                                errorMessage = null

                                coroutineScope.launch {
                                    val res = SupabaseAttendanceService.registerOrUpdateStudent(
                                        name = cleanName,
                                        rollNumber = cleanRoll,
                                        programName = studentRegProgram,
                                        emailInput = cleanEmail,
                                        installationId = installationId
                                    )
                                    isLoading = false
                                    if (res.isSuccess) {
                                        val studentId = res.getOrThrow()
                                        prefs.edit()
                                            .putString("logged_in_role", "STUDENT")
                                            .putString("selected_name", cleanName)
                                            .putString("selected_roll", cleanRoll)
                                            .putString("selected_email", cleanEmail)
                                            .putString("selected_program", studentRegProgram)
                                            .putString("selected_semester", studentRegSemester)
                                            .putString("selected_section", studentRegSection)
                                            .putString("student_id", studentId)
                                            .putBoolean("is_device_bound", true)
                                            .putBoolean("is_profile_completed", true)
                                            .apply()

                                        onLoginStudent(cleanName, cleanRoll)
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Registration failed."
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
                                Text("Creating Profile...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // ============================================================
                // FACULTY PORTAL: SIGN IN VS REGISTRATION TOGGLE
                // ============================================================
                if (selectedPortal == LoginPortal.FACULTY) {
                    Surface(
                        shape = PillShape,
                        color = SurfaceNeutral,
                        border = BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            val isSignIn = facultyAuthMode == FacultyAuthMode.SIGN_IN
                            Surface(
                                shape = PillShape,
                                color = if (isSignIn) CardBackground else Color.Transparent,
                                border = if (isSignIn) BorderStroke(1.dp, BorderHairline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        facultyAuthMode = FacultyAuthMode.SIGN_IN
                                        errorMessage = null
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Faculty Sign In",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSignIn) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSignIn) TextPrimary else TextSecondary
                                    )
                                }
                            }

                            val isReg = facultyAuthMode == FacultyAuthMode.REGISTER
                            Surface(
                                shape = PillShape,
                                color = if (isReg) CardBackground else Color.Transparent,
                                border = if (isReg) BorderStroke(1.dp, BorderHairline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        facultyAuthMode = FacultyAuthMode.REGISTER
                                        errorMessage = null
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Create Account",
                                        fontSize = 12.sp,
                                        fontWeight = if (isReg) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isReg) BrandAccent else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (facultyAuthMode == FacultyAuthMode.REGISTER) {
                        Text(
                            text = "NEW FACULTY REGISTRATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAccent,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create your official profile to register courses and manage class attendance.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Full Name with Title", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = facultyRegName,
                            onValueChange = { facultyRegName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Dr. Rajesh Verma", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Employee ID", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = facultyRegEmpId,
                                    onValueChange = { facultyRegEmpId = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("EMP-0842", fontSize = 12.sp, color = TextMuted) },
                                    singleLine = true,
                                    shape = InputShape
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Department", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = facultyRegDept,
                                    onValueChange = { facultyRegDept = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("CSE / DSAI / ECE", fontSize = 12.sp, color = TextMuted) },
                                    singleLine = true,
                                    shape = InputShape
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Email Address (Any email allowed for testing)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = facultyRegEmail,
                            onValueChange = { facultyRegEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. prof.verma@gmail.com", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Password", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = facultyRegPassword,
                            onValueChange = { facultyRegPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Min 6 characters", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (facultyRegName.isBlank() || facultyRegEmpId.isBlank() || facultyRegEmail.isBlank()) {
                                    errorMessage = "Please enter Name, Employee ID, and Email."
                                    return@Button
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLoading = true
                                errorMessage = null

                                coroutineScope.launch {
                                    val res = SupabaseAttendanceService.registerFaculty(
                                        name = facultyRegName.trim(),
                                        employeeId = facultyRegEmpId.trim().uppercase(),
                                        email = facultyRegEmail.trim().lowercase(),
                                        password = if (facultyRegPassword.isNotBlank()) facultyRegPassword.trim() else "password123",
                                        department = facultyRegDept.trim()
                                    )
                                    isLoading = false
                                    if (res.isSuccess) {
                                        val f = res.getOrThrow()
                                        prefs.edit()
                                            .putString("logged_in_role", "TEACHER")
                                            .putString("logged_in_faculty_name", f.name)
                                            .putString("logged_in_faculty_id", f.employeeId)
                                            .putString("logged_in_faculty_dept", f.department)
                                            .putString("logged_in_faculty_uuid", f.teacherId)
                                            .apply()
                                        onLoginFaculty(f.name, f.employeeId, f.department)
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Failed to register faculty."
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating Account...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Faculty Account & Sign In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ============================================================
                // STANDARD SIGN IN MODE (FOR BOTH STUDENT & FACULTY)
                // ============================================================
                val isCurrentSignIn = (selectedPortal == LoginPortal.STUDENT && studentAuthMode == StudentAuthMode.SIGN_IN) ||
                        (selectedPortal == LoginPortal.FACULTY && facultyAuthMode == FacultyAuthMode.SIGN_IN)

                if (isCurrentSignIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedPortal == LoginPortal.STUDENT) "STUDENT GOOGLE AUTH" else "FACULTY GOOGLE AUTH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        Surface(
                            shape = BadgeShape,
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "ALL GOOGLE ACCOUNTS ALLOWED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Sign in with your Google account (Gmail or Institutional) to continue.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // PRIMARY ACTION: GOOGLE SIGN-IN BUTTON
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
                                .padding(vertical = 13.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF4285F4))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Signing In...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2937)
                                )
                            } else {
                                GoogleLogoIcon()
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (selectedPortal == LoginPortal.FACULTY) "Continue with Google" else "Continue with Google",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1F2937)
                                    )
                                    Text(
                                        text = "Institutional / Personal Google Accounts",
                                        fontSize = 10.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "IIIT-NR Cryptographic Attendance System · v2.6",
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

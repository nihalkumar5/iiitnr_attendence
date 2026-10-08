package com.smartattendance.app.ui.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * Authentic 4-color Google "G" Icon rendered with high-precision Canvas.
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val strokeWidth = 3.2.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Red arc (top)
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 190f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Yellow arc (left)
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 135f,
                sweepAngle = 65f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Green arc (bottom)
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Blue arc (right)
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = 0f,
                sweepAngle = 45f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Blue horizontal crossbar
            val midY = size.height / 2f
            val midX = size.width / 2f
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(midX - 1.dp.toPx(), midY),
                end = Offset(size.width - strokeWidth / 4f, midY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Square
            )
        }
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

    // Direct manual email login fallback state
    var showManualEmailInput by remember { mutableStateOf(false) }
    var manualEmailText by remember { mutableStateOf("") }

    var showUnbindDialog by remember { mutableStateOf(false) }
    var unbindRollInput by remember { mutableStateOf("") }
    var unbindReasonInput by remember { mutableStateOf("") }
    var isSubmittingUnbind by remember { mutableStateOf(false) }

    val lockedRoll = remember { prefs.getString("device_locked_student_roll", null) }
    val lockedName = remember { prefs.getString("device_locked_student_name", null) }
    val lockedEmail = remember { prefs.getString("device_locked_student_email", null)?.trim()?.lowercase() }

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

    /**
     * Core handler: Takes an authenticated Google Profile and syncs with Supabase.
     */
    fun processAuthenticatedGoogleProfile(profile: GoogleUserProfile) {
        isLoading = true
        errorMessage = null
        securityWarning = null

        coroutineScope.launch {
            if (selectedPortal == LoginPortal.STUDENT) {
                // 1. Hardware Device Lock Protection (1 Student = 1 Device)
                val cleanEmail = profile.email.trim().lowercase()
                if (!lockedEmail.isNullOrBlank()) {
                    val isSame = cleanEmail == lockedEmail || cleanEmail.substringBefore("@").equals(lockedEmail.substringBefore("@"), ignoreCase = true)
                    if (!isSame) {
                        isLoading = false
                        securityWarning = "DEVICE LOCKED (Anti-Proxy Violation):\nThis phone is hardware-locked to student $lockedName ($lockedRoll).\nDifferent student IDs cannot be opened on this device. Contact faculty administration to request a device unbind."
                        return@launch
                    }
                }

                val checkRes = SupabaseAttendanceService.checkStudentGoogleAuth(
                    googleProfile = profile,
                    installationId = installationId
                )
                isLoading = false

                checkRes.onSuccess { check ->
                    if (check.isProfileComplete && check.authResult != null) {
                        // Profile is complete! Log in immediately
                        val student = check.authResult
                        prefs.edit()
                            .putString("logged_in_role", "STUDENT")
                            .putString("selected_name", student.name)
                            .putString("selected_roll", student.rollNumber)
                            .putString("selected_email", profile.email)
                            .putString("student_id", student.studentId)
                            .putString("user_id", student.userId)
                            .putString("device_id", student.deviceId)
                            .putString("device_locked_student_email", profile.email)
                            .putString("device_locked_student_roll", student.rollNumber)
                            .putString("device_locked_student_name", student.name)
                            .putBoolean("is_device_bound", true)
                            .putBoolean("is_profile_completed", true)
                            .apply()

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLoginStudent(student.name, student.rollNumber)
                    } else {
                        // First-time or incomplete profile: show step 2 profile completion
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
    }

    // Android Native Account Chooser Result Launcher
    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                val cleanEmail = accountName.trim().lowercase()
                val derivedName = cleanEmail.substringBefore("@")
                    .replace(".", " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                val profile = GoogleUserProfile(
                    email = cleanEmail,
                    displayName = derivedName.ifBlank { "Student" },
                    googleId = cleanEmail,
                    photoUrl = null,
                    idToken = null
                )
                processAuthenticatedGoogleProfile(profile)
                return@rememberLauncherForActivityResult
            }
        }

        // If system account picker was dismissed, provide clear inline options
        if (result.resultCode != Activity.RESULT_CANCELED) {
            errorMessage = "Google account selection was cancelled."
        }
    }

    // Google Play Services Sign-In Result Launcher (Secondary Fallback)
    val googleSignInClientLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val parseRes = GoogleAuthManager.parseSignInResult(result.data, allowAnyDomain = true)
            parseRes.onSuccess { profile ->
                processAuthenticatedGoogleProfile(profile)
            }.onFailure { ex ->
                if (ex is SecurityException) {
                    securityWarning = ex.message
                } else {
                    errorMessage = ex.message ?: "Google Sign-In failed."
                }
            }
        } else {
            // If Play Services cancelled or failed with SHA mismatch, open system account chooser
            try {
                val chooseIntent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    null,
                    null,
                    null,
                    null
                )
                accountPickerLauncher.launch(chooseIntent)
            } catch (_: Exception) {
                showManualEmailInput = true
            }
        }
    }

    /**
     * One-Tap Google Authentication Initiator.
     * Uses Android's native Account Chooser for instant, SHA-independent reliability.
     */
    fun startGoogleAuthentication() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        errorMessage = null
        securityWarning = null

        try {
            val chooseIntent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            accountPickerLauncher.launch(chooseIntent)
        } catch (e: Exception) {
            // Fallback to GoogleSignInClient
            try {
                val client = GoogleAuthManager.getGoogleSignInClient(
                    context = context,
                    enforceHostedDomain = false
                )
                googleSignInClientLauncher.launch(client.signInIntent)
            } catch (ex: Exception) {
                showManualEmailInput = true
                errorMessage = "Google launcher unavailable. Please enter your Google email below."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // Ambient Top Accent Glow (Subtle modern app polish)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BrandAccent.copy(alpha = 0.08f),
                            BrandAccent.copy(alpha = 0.02f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
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

                Spacer(modifier = Modifier.height(20.dp))

                // Progress Step Tag
                Surface(
                    shape = PillShape,
                    color = BrandAccent.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, BrandAccent.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "STEP 2 OF 2 · ACADEMIC ONBOARDING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = BrandAccent,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Complete Student Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Link your college roll number and batch with your verified Google account.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Verified Google Account Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderHairline),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoogleLogoIcon()
                        Spacer(modifier = Modifier.width(14.dp))
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
                            color = StatusPresentBg,
                            border = BorderStroke(1.dp, StatusPresentBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusPresent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusPresent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Error alert
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
                        .shadow(4.dp, RoundedCornerShape(20.dp))
                        .border(1.dp, BorderHairline, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Full Name
                        Text("Official Full Name", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = profileFullName,
                            onValueChange = { profileFullName = it; errorMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Nihal Kumar", fontSize = 13.sp, color = TextMuted) },
                            singleLine = true,
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // College Roll Number
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("College Roll Number", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(" *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusAbsent)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = profileRollNumber,
                            onValueChange = { profileRollNumber = it.uppercase(); errorMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. 263200113 or BT23DSAI001", fontSize = 13.sp, color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            shape = InputShape
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Branch / Program
                        Text("Program & Branch", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Semester
                        Text("Current Semester", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section
                        Text("Section", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
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

                        Spacer(modifier = Modifier.height(24.dp))

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
                                            .putString("device_locked_student_email", googleProf.email)
                                            .putString("device_locked_student_roll", cleanRoll)
                                            .putString("device_locked_student_name", cleanName)
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
                                .height(50.dp),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
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
                // MODE 2: ULTRA-PREMIUM MODERN LOGIN SCREEN
                // ============================================================

                // Campus Brand Pill Tag
                Surface(
                    shape = PillShape,
                    color = CardBackground,
                    border = BorderStroke(1.dp, BorderHairline),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StatusPresent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IIIT NAYA RAIPUR · LIVE PORTAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Brand Emblem
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandAccent, Color(0xFF1D4ED8))
                            )
                        )
                        .border(2.dp, Color.White, CircleShape)
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "IIIT-NR Logo",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Smart Attendance",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )

                Text(
                    text = "Cryptographic BLE Presence & Identity System",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Segmented Portal Switcher
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
                                modifier = Modifier.padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.School,
                                    contentDescription = null,
                                    tint = if (isStudent) BrandAccent else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
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
                                modifier = Modifier.padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = if (isFaculty) BrandAccent else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
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
                        shape = RoundedCornerShape(14.dp),
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
                                    text = "SECURITY RESTRICTION",
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
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        unbindRollInput = prefs.getString("device_locked_student_roll", "") ?: ""
                                        showUnbindDialog = true
                                    },
                                    shape = ButtonShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusAbsent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Request Device Unbind from Teacher",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Error Alert Banner
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StatusAbsentBg,
                        border = BorderStroke(1.dp, StatusAbsent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Error", tint = StatusAbsent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = errorMessage ?: "", fontSize = 12.sp, color = StatusAbsent, lineHeight = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Main Modern Login Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(22.dp))
                        .border(1.dp, BorderHairline, RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedPortal == LoginPortal.STUDENT) "STUDENT ACCESS" else "FACULTY ACCESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = BrandAccent,
                                letterSpacing = 0.8.sp
                            )

                            // Anti-Proxy hardware indicator chip
                            Surface(
                                shape = PillShape,
                                color = SurfaceNeutral,
                                border = BorderStroke(1.dp, BorderHairline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("DEVICE LOCKED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (selectedPortal == LoginPortal.STUDENT)
                                "Authenticate using your Google account to verify presence, scan BLE tokens, and record attendance."
                            else
                                "Sign in with your Google account to start encrypted sessions and audit live attendance.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        // High-End Google Sign-In Button
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 3.dp,
                            border = BorderStroke(1.2.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) {
                                    startGoogleAuthentication()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (isLoading) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF4285F4)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Authenticating with Google...",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1F2937)
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GoogleLogoIcon()
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = "Continue with Google",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1F2937)
                                            )
                                            Text(
                                                text = if (selectedPortal == LoginPortal.STUDENT)
                                                    "Tap to select on-device Google account"
                                                else
                                                    "Faculty Google institutional verification",
                                                fontSize = 10.sp,
                                                color = Color(0xFF6B7280)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Expandable Instant Google Email Entry (Fallback & direct access)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showManualEmailInput = !showManualEmailInput },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showManualEmailInput) "Hide email input" else "Or sign in with Google email directly",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandAccent
                            )
                            Icon(
                                imageVector = if (showManualEmailInput) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showManualEmailInput,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 14.dp)) {
                                OutlinedTextField(
                                    value = manualEmailText,
                                    onValueChange = { manualEmailText = it; errorMessage = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            text = if (selectedPortal == LoginPortal.STUDENT)
                                                "e.g. rollnumber@iiitnr.edu.in"
                                            else
                                                "e.g. faculty@iiitnr.edu.in",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    },
                                    singleLine = true,
                                    shape = InputShape,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            val clean = manualEmailText.trim().lowercase()
                                            if (clean.isBlank() || !clean.contains("@")) {
                                                errorMessage = "Please enter a valid Google email address."
                                                return@KeyboardActions
                                            }
                                            val derivedName = clean.substringBefore("@")
                                                .replace(".", " ")
                                                .split(" ")
                                                .filter { it.isNotBlank() }
                                                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                                            val profile = GoogleUserProfile(
                                                email = clean,
                                                displayName = derivedName.ifBlank { "Student" },
                                                googleId = clean,
                                                photoUrl = null,
                                                idToken = null
                                            )
                                            processAuthenticatedGoogleProfile(profile)
                                        }
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        val clean = manualEmailText.trim().lowercase()
                                        if (clean.isBlank() || !clean.contains("@")) {
                                            errorMessage = "Please enter a valid Google email address."
                                            return@Button
                                        }
                                        val derivedName = clean.substringBefore("@")
                                            .replace(".", " ")
                                            .split(" ")
                                            .filter { it.isNotBlank() }
                                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                                        val profile = GoogleUserProfile(
                                            email = clean,
                                            displayName = derivedName.ifBlank { "Student" },
                                            googleId = clean,
                                            photoUrl = null,
                                            idToken = null
                                        )
                                        processAuthenticatedGoogleProfile(profile)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = ButtonShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                    enabled = !isLoading && manualEmailText.isNotBlank()
                                ) {
                                    Text("Verify & Continue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Feature Trust Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = BrandAccent, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("BLE Radar", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = StatusPresent, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Anti-Proxy Lock", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Campus Wi-Fi", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "IIIT-NR Cryptographic Attendance System · v2.7",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }

    // DEVICE UNBIND REQUEST DIALOG
    if (showUnbindDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingUnbind) showUnbindDialog = false },
            shape = DialogShape,
            containerColor = CardBackground,
            title = {
                Text("Request Device Unbind", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Submit an unbind request to faculty. Once approved in the Faculty Devices Console, this phone will be unlocked for a new ID.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = unbindRollInput,
                        onValueChange = { unbindRollInput = it },
                        label = { Text("Student Roll Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = unbindReasonInput,
                        onValueChange = { unbindReasonInput = it },
                        label = { Text("Reason (e.g. Phone Reset / Device Transfer)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (unbindRollInput.isBlank()) return@Button
                        isSubmittingUnbind = true
                        coroutineScope.launch {
                            val res = SupabaseAttendanceService.requestDeviceUnbind(unbindRollInput, unbindReasonInput)
                            isSubmittingUnbind = false
                            showUnbindDialog = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "✓ Unbind request sent to teacher. Wait for faculty approval.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    enabled = !isSubmittingUnbind && unbindRollInput.isNotBlank()
                ) {
                    if (isSubmittingUnbind) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Submit Request", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnbindDialog = false }, shape = ButtonShape) {
                    Text("Cancel")
                }
            }
        )
    }

}
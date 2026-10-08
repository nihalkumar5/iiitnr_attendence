package com.smartattendance.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.ui.auth.LoginScreen
import com.smartattendance.app.ui.student.AttendanceHistoryScreen
import com.smartattendance.app.ui.student.StudentHomeScreen
import com.smartattendance.app.ui.teacher.ActiveLectureScreen
import com.smartattendance.app.ui.teacher.AttendanceReviewScreen
import com.smartattendance.app.ui.teacher.TeacherHomeScreen
import com.smartattendance.app.ui.teacher.TeacherAttendanceHistoryScreen
import com.smartattendance.app.ui.teacher.TeacherScheduleScreen
import com.smartattendance.app.ui.theme.*

enum class UserRole {
    NONE,
    STUDENT,
    TEACHER
}

enum class StudentTab {
    ATTENDANCE,
    HISTORY
}

enum class TeacherTab {
    HOME,
    SCHEDULE,
    ACTIVE_ROLL_CALL,
    RECORDS,
    DEVICES
}

enum class TeacherSubStep {
    HOME,
    ACTIVE_LECTURE,
    REVIEW
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartAttendanceTheme {
                val context = LocalContext.current
                val prefs = remember {
                    context.getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE)
                }

                val initialRoleStr = remember { prefs.getString("logged_in_role", null) }
                val initialRole = when (initialRoleStr) {
                    "STUDENT" -> UserRole.STUDENT
                    "TEACHER" -> UserRole.TEACHER
                    else -> UserRole.NONE
                }

                var currentRole by remember { mutableStateOf(initialRole) }
                var studentName by remember {
                    mutableStateOf(prefs.getString("selected_name", "Nihal Kumar") ?: "Nihal Kumar")
                }
                var studentRoll by remember {
                    mutableStateOf(prefs.getString("selected_roll", "263200113") ?: "263200113")
                }

                var facultyName by remember {
                    mutableStateOf(prefs.getString("logged_in_faculty_name", "Dr. S. Sharma") ?: "Dr. S. Sharma")
                }
                var facultyId by remember {
                    mutableStateOf(prefs.getString("logged_in_faculty_id", "FAC-CSE-042") ?: "FAC-CSE-042")
                }
                var facultyDept by remember {
                    mutableStateOf(
                        prefs.getString("logged_in_faculty_dept", "Computer Science & Engineering")
                            ?: "Computer Science & Engineering"
                    )
                }

                // Student Tab Navigation State
                var currentStudentTab by remember { mutableStateOf(StudentTab.ATTENDANCE) }

                // Teacher Tab Navigation State
                var currentTeacherTab by remember { mutableStateOf(TeacherTab.HOME) }
                var teacherStep by remember { mutableStateOf(TeacherSubStep.HOME) }
                var activeLectureClass by remember { mutableStateOf<com.smartattendance.app.ui.teacher.TeacherClassItem?>(null) }
                var activeLectureSessionId by remember { mutableStateOf("") }

                val haptic = LocalHapticFeedback.current

                fun logout() {
                    if (currentRole == UserRole.TEACHER) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            com.smartattendance.app.core.network.SupabaseAttendanceService.endAllActiveSessions()
                        }
                    }
                    activeLectureClass = null
                    activeLectureSessionId = ""
                    currentTeacherTab = TeacherTab.HOME
                    teacherStep = TeacherSubStep.HOME
                    prefs.edit().putString("logged_in_role", "").apply()
                    currentRole = UserRole.NONE
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CanvasBackground)
                ) {
                    Crossfade(
                        targetState = currentRole,
                        animationSpec = tween(durationMillis = 200),
                        label = "roleCrossfade"
                    ) { role ->
                        when (role) {
                            UserRole.NONE -> {
                                // 1. AUTHENTICATION PORTAL (STUDENT & FACULTY SEPARATED)
                                LoginScreen(
                                    onLoginStudent = { name, roll ->
                                        prefs.edit()
                                            .putString("logged_in_role", "STUDENT")
                                            .putString("selected_name", name)
                                            .putString("selected_roll", roll)
                                            .apply()
                                        studentName = name
                                        studentRoll = roll
                                        currentStudentTab = StudentTab.ATTENDANCE
                                        currentRole = UserRole.STUDENT
                                    },
                                    onLoginFaculty = { name, id, dept ->
                                        prefs.edit()
                                            .putString("logged_in_role", "TEACHER")
                                            .putString("logged_in_faculty_name", name)
                                            .putString("logged_in_faculty_id", id)
                                            .putString("logged_in_faculty_dept", dept)
                                            .apply()
                                        facultyName = name
                                        facultyId = id
                                        facultyDept = dept
                                        currentTeacherTab = TeacherTab.HOME
                                        teacherStep = TeacherSubStep.HOME
                                        currentRole = UserRole.TEACHER
                                    }
                                )
                            }

                            UserRole.STUDENT -> {
                                // 2. STUDENT DEDICATED DASHBOARD (NO TEACHER TABS/CONTROLS)
                                Scaffold(
                                    bottomBar = {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.98f),
                                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                            shadowElevation = 14.dp,
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.8f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsPadding()
                                                    .padding(vertical = 8.dp, horizontal = 36.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AppllamaTabItem(
                                                    selected = currentStudentTab == StudentTab.ATTENDANCE,
                                                    label = "Attendance",
                                                    selectedIcon = Icons.Filled.Person,
                                                    unselectedIcon = Icons.Outlined.Person,
                                                    onClick = {
                                                        if (currentStudentTab != StudentTab.ATTENDANCE) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentStudentTab = StudentTab.ATTENDANCE
                                                        }
                                                    }
                                                )

                                                AppllamaTabItem(
                                                    selected = currentStudentTab == StudentTab.HISTORY,
                                                    label = "History",
                                                    selectedIcon = Icons.Filled.Assessment,
                                                    unselectedIcon = Icons.Outlined.Assessment,
                                                    onClick = {
                                                        if (currentStudentTab != StudentTab.HISTORY) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentStudentTab = StudentTab.HISTORY
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(CanvasBackground)
                                            .padding(innerPadding)
                                    ) {
                                        Crossfade(
                                            targetState = currentStudentTab,
                                            animationSpec = tween(durationMillis = 180),
                                            label = "studentTabCrossfade"
                                        ) { tab ->
                                            when (tab) {
                                                StudentTab.ATTENDANCE -> {
                                                    StudentHomeScreen(
                                                        studentName = studentName,
                                                        studentRoll = studentRoll,
                                                        onLogout = { logout() }
                                                    )
                                                }
                                                StudentTab.HISTORY -> {
                                                    AttendanceHistoryScreen(studentRoll = studentRoll)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            UserRole.TEACHER -> {
                                // 3. FACULTY DEDICATED CONSOLE (NO STUDENT SELF-MARKING TABS)
                                Scaffold(
                                    bottomBar = {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.98f),
                                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                            shadowElevation = 14.dp,
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.8f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsPadding()
                                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceAround,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AppllamaTabItem(
                                                    selected = currentTeacherTab == TeacherTab.HOME,
                                                    label = "Home",
                                                    selectedIcon = Icons.Filled.Home,
                                                    unselectedIcon = Icons.Outlined.Home,
                                                    onClick = {
                                                        if (currentTeacherTab != TeacherTab.HOME) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentTeacherTab = TeacherTab.HOME
                                                        }
                                                    }
                                                )

                                                AppllamaTabItem(
                                                    selected = currentTeacherTab == TeacherTab.SCHEDULE,
                                                    label = "Schedule",
                                                    selectedIcon = Icons.Filled.School,
                                                    unselectedIcon = Icons.Outlined.School,
                                                    onClick = {
                                                        if (currentTeacherTab != TeacherTab.SCHEDULE) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentTeacherTab = TeacherTab.SCHEDULE
                                                        }
                                                    }
                                                )

                                                AppllamaTabItem(
                                                    selected = currentTeacherTab == TeacherTab.ACTIVE_ROLL_CALL,
                                                    label = "Live Lecture",
                                                    selectedIcon = Icons.Filled.Radio,
                                                    unselectedIcon = Icons.Outlined.Radio,
                                                    isSpecialCenterTab = true,
                                                    badgeText = if (activeLectureClass != null) "●" else null,
                                                    onClick = {
                                                        if (currentTeacherTab != TeacherTab.ACTIVE_ROLL_CALL) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentTeacherTab = TeacherTab.ACTIVE_ROLL_CALL
                                                        }
                                                    }
                                                )

                                                AppllamaTabItem(
                                                    selected = currentTeacherTab == TeacherTab.RECORDS,
                                                    label = "Records",
                                                    selectedIcon = Icons.AutoMirrored.Filled.FactCheck,
                                                    unselectedIcon = Icons.AutoMirrored.Outlined.FactCheck,
                                                    onClick = {
                                                        if (currentTeacherTab != TeacherTab.RECORDS) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentTeacherTab = TeacherTab.RECORDS
                                                        }
                                                    }
                                                )

                                                AppllamaTabItem(
                                                    selected = currentTeacherTab == TeacherTab.DEVICES,
                                                    label = "Devices",
                                                    selectedIcon = Icons.Filled.Lock,
                                                    unselectedIcon = Icons.Outlined.Lock,
                                                    onClick = {
                                                        if (currentTeacherTab != TeacherTab.DEVICES) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentTeacherTab = TeacherTab.DEVICES
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(CanvasBackground)
                                            .padding(innerPadding)
                                    ) {

                                        Crossfade(
                                            targetState = currentTeacherTab,
                                            animationSpec = tween(durationMillis = 180),
                                            label = "teacherTabCrossfade"
                                        ) { tab ->
                                            when (tab) {
                                                TeacherTab.HOME -> {
                                                    TeacherHomeScreen(
                                                        teacherName = facultyName,
                                                        facultyId = facultyId,
                                                        department = facultyDept,
                                                        onStartLecture = { selectedClass ->
                                                            activeLectureClass = selectedClass
                                                            currentTeacherTab = TeacherTab.ACTIVE_ROLL_CALL
                                                            teacherStep = TeacherSubStep.ACTIVE_LECTURE
                                                        },
                                                        onOpenSchedule = {
                                                            currentTeacherTab = TeacherTab.SCHEDULE
                                                        },
                                                        onOpenLiveRollCall = {
                                                            currentTeacherTab = TeacherTab.ACTIVE_ROLL_CALL
                                                            teacherStep = TeacherSubStep.ACTIVE_LECTURE
                                                        },
                                                        onOpenDeviceRequests = {
                                                            currentTeacherTab = TeacherTab.DEVICES
                                                        },
                                                        onLogout = { logout() }
                                                    )
                                                }
                                                TeacherTab.SCHEDULE -> {
                                                    TeacherScheduleScreen(
                                                        teacherName = facultyName,
                                                        facultyId = facultyId,
                                                        department = facultyDept,
                                                        onStartLecture = { selectedClass ->
                                                            activeLectureClass = selectedClass
                                                            currentTeacherTab = TeacherTab.ACTIVE_ROLL_CALL
                                                            teacherStep = TeacherSubStep.ACTIVE_LECTURE
                                                        },
                                                        onLogout = { logout() }
                                                    )
                                                }
                                                TeacherTab.ACTIVE_ROLL_CALL -> {
                                                    val currentActive = activeLectureClass
                                                    if (currentActive == null) {
                                                        com.smartattendance.app.ui.teacher.NoActiveLectureScreen(
                                                            onGoToSchedule = {
                                                                currentTeacherTab = TeacherTab.SCHEDULE
                                                            },
                                                            onStartClass = { selectedClass ->
                                                                activeLectureClass = selectedClass
                                                                teacherStep = TeacherSubStep.ACTIVE_LECTURE
                                                            }
                                                        )
                                                    } else {
                                                        when (teacherStep) {
                                                            TeacherSubStep.HOME,
                                                            TeacherSubStep.ACTIVE_LECTURE -> {
                                                                 ActiveLectureScreen(
                                                                    classId = currentActive.id,
                                                                    joinCode = currentActive.joinCode,
                                                                    subjectName = currentActive.subjectName,
                                                                    subjectCode = currentActive.subjectCode,
                                                                    room = currentActive.room,
                                                                    timeSlot = currentActive.timeSlot,
                                                                    totalStudents = currentActive.enrolledStudents,
                                                                    onEndLecture = { endedSessionId ->
                                                                        activeLectureSessionId = endedSessionId
                                                                        teacherStep = TeacherSubStep.REVIEW
                                                                    },
                                                                    onCancelLecture = {
                                                                        activeLectureClass = null
                                                                        activeLectureSessionId = ""
                                                                        teacherStep = TeacherSubStep.HOME
                                                                        currentTeacherTab = TeacherTab.HOME
                                                                    }
                                                                 )
                                                            }
                                                            TeacherSubStep.REVIEW -> {
                                                                AttendanceReviewScreen(
                                                                    classId = currentActive.id,
                                                                    joinCode = currentActive.joinCode,
                                                                    sessionId = activeLectureSessionId,
                                                                    subjectName = currentActive.subjectName,
                                                                    subjectCode = currentActive.subjectCode,
                                                                    room = currentActive.room,
                                                                    onSubmitSuccess = {
                                                                        com.smartattendance.app.core.engine.TimetableEngine.lockClassToday(
                                                                            context,
                                                                            currentActive.id,
                                                                            activeLectureSessionId
                                                                        )
                                                                        val currentSaved = com.smartattendance.app.ui.teacher.loadPersistedSchedule(context)
                                                                        val updated = currentSaved.map { item ->
                                                                            if (item.id == currentActive.id || (item.joinCode.isNotBlank() && item.joinCode == currentActive.joinCode)) {
                                                                                item.copy(status = com.smartattendance.app.ui.teacher.ClassScheduleStatus.LOCKED, isLocked = true)
                                                                            } else item
                                                                        }
                                                                        com.smartattendance.app.ui.teacher.savePersistedSchedule(context, updated)
                                                                        activeLectureClass = null
                                                                        activeLectureSessionId = ""
                                                                        teacherStep = TeacherSubStep.HOME
                                                                        currentTeacherTab = TeacherTab.HOME
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                TeacherTab.RECORDS -> {
                                                    TeacherAttendanceHistoryScreen(
                                                        facultyName = facultyName,
                                                        onLogout = { logout() }
                                                    )
                                                }
                                                TeacherTab.DEVICES -> {
                                                    com.smartattendance.app.ui.teacher.TeacherDeviceRequestsScreen(
                                                        facultyName = facultyName,
                                                        onLogout = { logout() }
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
        }
    }
}

@Composable
private fun AppllamaTabItem(
    selected: Boolean,
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    badgeText: String? = null,
    isSpecialCenterTab: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val animatedColor by animateColorAsState(
        targetValue = if (selected) BrandAccent else Color(0xFF64748B),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabColor"
    )

    val activePillBg by animateColorAsState(
        targetValue = when {
            selected && isSpecialCenterTab -> BrandAccent.copy(alpha = 0.14f)
            selected -> BrandAccent.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "tabBg"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tabScale"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "indicatorWidth"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(activePillBg)
                .padding(horizontal = if (isSpecialCenterTab) 12.dp else 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = animatedColor,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
            )

            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(StatusPresent)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = animatedColor,
            letterSpacing = (-0.2).sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Active glowing underline pill
        Box(
            modifier = Modifier
                .height(2.5.dp)
                .width(indicatorWidth)
                .clip(CircleShape)
                .background(if (selected) BrandAccent else Color.Transparent)
        )
    }
}

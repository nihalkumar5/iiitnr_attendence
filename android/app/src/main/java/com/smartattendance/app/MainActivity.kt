package com.smartattendance.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.School
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
    ACTIVE_ROLL_CALL
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

                val haptic = LocalHapticFeedback.current

                fun logout() {
                    if (currentRole == UserRole.TEACHER) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            com.smartattendance.app.core.network.SupabaseAttendanceService.endAllActiveSessions()
                        }
                    }
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
                                            color = CardBackground,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = BorderHairline,
                                                    shape = SheetShape
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsPadding()
                                                    .padding(vertical = 8.dp, horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.SpaceAround,
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
                                            color = CardBackground,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = BorderHairline,
                                                    shape = SheetShape
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsPadding()
                                                    .padding(vertical = 8.dp, horizontal = 24.dp),
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
                                                    selectedIcon = Icons.Filled.Assessment,
                                                    unselectedIcon = Icons.Outlined.Assessment,
                                                    onClick = {
                                                        if (currentTeacherTab != TeacherTab.ACTIVE_ROLL_CALL) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            currentTeacherTab = TeacherTab.ACTIVE_ROLL_CALL
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
                                        var activeLectureClass by remember { mutableStateOf<com.smartattendance.app.ui.teacher.TeacherClassItem?>(null) }

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
                                                    when (teacherStep) {
                                                        TeacherSubStep.HOME,
                                                        TeacherSubStep.ACTIVE_LECTURE -> {
                                                             ActiveLectureScreen(
                                                                classId = activeLectureClass?.id ?: "class-01",
                                                                subjectName = activeLectureClass?.subjectName ?: "Data Structures & Algorithms",
                                                                subjectCode = activeLectureClass?.subjectCode ?: "CS501",
                                                                room = activeLectureClass?.room ?: "Room A-204 (AC Block)",
                                                                timeSlot = activeLectureClass?.timeSlot ?: "10:00 – 11:00 AM",
                                                                totalStudents = activeLectureClass?.enrolledStudents ?: prefs.getInt("synced_enrolled_student_count", 22),
                                                                onEndLecture = {
                                                                    teacherStep = TeacherSubStep.REVIEW
                                                                }
                                                             )
                                                        }
                                                        TeacherSubStep.REVIEW -> {
                                                            AttendanceReviewScreen(
                                                                subjectName = activeLectureClass?.subjectName ?: "Data Structures & Algorithms",
                                                                subjectCode = activeLectureClass?.subjectCode ?: "CS501",
                                                                room = activeLectureClass?.room ?: "Room A-204 (AC Block)",
                                                                onSubmitSuccess = {
                                                                    teacherStep = TeacherSubStep.HOME
                                                                    currentTeacherTab = TeacherTab.HOME
                                                                }
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
    }
}

@Composable
private fun AppllamaTabItem(
    selected: Boolean,
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = if (selected) BrandAccent else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) BrandAccent else TextSecondary,
            letterSpacing = 0.1.sp
        )
    }
}

package com.smartattendance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.smartattendance.app.ui.prototype.BlePrototypeScreen
import com.smartattendance.app.ui.student.AttendanceHistoryScreen
import com.smartattendance.app.ui.student.StudentHomeScreen
import com.smartattendance.app.ui.teacher.ActiveLectureScreen
import com.smartattendance.app.ui.teacher.AttendanceReviewScreen
import com.smartattendance.app.ui.teacher.TeacherHomeScreen
import com.smartattendance.app.ui.theme.BorderSubtle
import com.smartattendance.app.ui.theme.CanvasBackground
import com.smartattendance.app.ui.theme.PrimaryBlack
import com.smartattendance.app.ui.theme.SmartAttendanceTheme

enum class MainTab {
    PROTOTYPE,
    TEACHER_FLOW,
    STUDENT_FLOW
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
                var currentTab by remember { mutableStateOf(MainTab.PROTOTYPE) }
                var teacherStep by remember { mutableStateOf(TeacherSubStep.HOME) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = CanvasBackground,
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            NavigationBarItem(
                                selected = currentTab == MainTab.PROTOTYPE,
                                onClick = { currentTab = MainTab.PROTOTYPE },
                                icon = { Icon(Icons.Default.Radio, contentDescription = null) },
                                label = { Text("BLE Prototype") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = BorderSubtle)
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.TEACHER_FLOW,
                                onClick = { currentTab = MainTab.TEACHER_FLOW },
                                icon = { Icon(Icons.Default.School, contentDescription = null) },
                                label = { Text("Teacher App") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = BorderSubtle)
                            )
                            NavigationBarItem(
                                selected = currentTab == MainTab.STUDENT_FLOW,
                                onClick = { currentTab = MainTab.STUDENT_FLOW },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                                label = { Text("Student App") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = BorderSubtle)
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            MainTab.PROTOTYPE -> {
                                BlePrototypeScreen()
                            }
                            MainTab.TEACHER_FLOW -> {
                                when (teacherStep) {
                                    TeacherSubStep.HOME -> {
                                        TeacherHomeScreen(
                                            onStartLecture = { teacherStep = TeacherSubStep.ACTIVE_LECTURE }
                                        )
                                    }
                                    TeacherSubStep.ACTIVE_LECTURE -> {
                                        ActiveLectureScreen(
                                            onEndLecture = { teacherStep = TeacherSubStep.REVIEW }
                                        )
                                    }
                                    TeacherSubStep.REVIEW -> {
                                        AttendanceReviewScreen(
                                            onSubmitSuccess = { teacherStep = TeacherSubStep.HOME }
                                        )
                                    }
                                }
                            }
                            MainTab.STUDENT_FLOW -> {
                                StudentHomeScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

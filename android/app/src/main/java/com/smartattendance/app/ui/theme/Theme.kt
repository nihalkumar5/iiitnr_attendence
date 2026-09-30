package com.smartattendance.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlack,
    onPrimary = SurfaceWhite,
    secondary = SecondaryGray,
    background = CanvasBackground,
    surface = SurfaceWhite,
    onBackground = PrimaryBlack,
    onSurface = PrimaryBlack,
    outline = BorderSubtle
)

@Composable
fun SmartAttendanceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

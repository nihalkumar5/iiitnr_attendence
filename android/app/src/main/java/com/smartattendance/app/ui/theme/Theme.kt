package com.smartattendance.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppllamaLightColorScheme = lightColorScheme(
    primary = BrandAccent,
    onPrimary = Color.White,
    primaryContainer = BrandAccent.copy(alpha = 0.08f),
    onPrimaryContainer = BrandAccent,
    secondary = BrandAccent,
    onSecondary = Color.White,
    tertiary = StatusPresent,
    onTertiary = Color.White,
    background = CanvasBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceNeutral,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    outlineVariant = BorderHighlight
)

private val AppllamaShapes = Shapes(
    small = BadgeShape,
    medium = ButtonShape,
    large = CardShape,
    extraLarge = SheetShape
)

@Composable
fun SmartAttendanceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppllamaLightColorScheme,
        typography = Typography,
        shapes = AppllamaShapes,
        content = content
    )
}

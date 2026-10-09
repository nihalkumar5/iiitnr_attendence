package com.smartattendance.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Intersemester Visual Design System Background.
 * Renders an off-white canvas with a subtle, clean grey line grid texture.
 */
fun Modifier.intersemesterBackground(): Modifier = this.drawBehind {
    // 1. Off-white canvas fill
    drawRect(color = CanvasBackground)

    // 2. Subtle, clean grey line grid texture
    val gridColor = Color(0xFF94A3B8).copy(alpha = 0.14f)
    val strokeWidth = 1.0.dp.toPx()
    val spacing = 24.dp.toPx()
    val width = size.width
    val height = size.height

    // Vertical grid lines
    var x = 0f
    while (x <= width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = strokeWidth
        )
        x += spacing
    }

    // Horizontal grid lines
    var y = 0f
    while (y <= height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = strokeWidth
        )
        y += spacing
    }
}

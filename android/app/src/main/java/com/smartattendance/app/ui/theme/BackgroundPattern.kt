package com.smartattendance.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Intersemester Visual Design System Background.
 * Renders an off-white canvas with clean, modern engineering grid lines.
 */
fun Modifier.intersemesterBackground(): Modifier = this.drawBehind {
    // 1. Off-white canvas fill
    drawRect(color = CanvasBackground)

    // 2. Clean, subtle grey engineering grid lines (Graph paper texture)
    val gridLineColor = Color(0xFFCBD5E1).copy(alpha = 0.40f)
    val strokeWidthPx = 1.dp.toPx()
    val spacing = 22.dp.toPx()
    val width = size.width
    val height = size.height

    // Vertical grid lines
    var x = 0f
    while (x <= width) {
        drawLine(
            color = gridLineColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = strokeWidthPx
        )
        x += spacing
    }

    // Horizontal grid lines
    var y = 0f
    while (y <= height) {
        drawLine(
            color = gridLineColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = strokeWidthPx
        )
        y += spacing
    }
}

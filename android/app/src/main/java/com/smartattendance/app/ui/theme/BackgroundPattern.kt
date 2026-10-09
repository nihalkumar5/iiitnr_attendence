package com.smartattendance.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Intersemester Visual Design System Background.
 * Renders an off-white canvas with a subtle, tiny grey dot-pattern texture.
 */
fun Modifier.intersemesterBackground(): Modifier = this.drawBehind {
    // 1. Off-white canvas fill
    drawRect(color = CanvasBackground)

    // 2. Subtle, tiny grey dot-pattern texture
    val dotColor = Color(0xFF94A3B8).copy(alpha = 0.22f)
    val dotRadius = 1.0.dp.toPx()
    val spacing = 22.dp.toPx()
    val width = size.width
    val height = size.height

    var x = spacing / 2
    while (x < width) {
        var y = spacing / 2
        while (y < height) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            y += spacing
        }
        x += spacing
    }
}

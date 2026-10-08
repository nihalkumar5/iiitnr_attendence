package com.smartattendance.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// =========================================================================
// APPOLLAMA SHAPE SCALE
// Strict Law: "Shape lock. One corner-radius scale, stated as a rule:
// actions are pills / 12, cards 16, inputs 12, badges 8, sheets 24.
// Never ad-hoc mixed radii."
// =========================================================================

val CardShape = RoundedCornerShape(16.dp)
val ElevatedCardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(12.dp)
val InputShape = RoundedCornerShape(12.dp)
val BadgeShape = RoundedCornerShape(8.dp)
val PillShape = RoundedCornerShape(999.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val DialogShape = RoundedCornerShape(20.dp)

package com.smartattendance.app.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// APPOLLAMA DESIGN SYSTEM COLOR TOKENS
// Strict Rules:
// 1. One Accent, Locked: BrandAccent (Cobalt 600 - 0xFF2563EB)
// 2. One Grey Family: Cool Greys (Slate 50 to Slate 950)
// 3. Crisp Semantic Status: Emerald (Success), Amber (Warning), Red (Error)
// =========================================================================

// Canvas & Surfaces (Linear / Apple HIG Clean Canvas)
val CanvasBackground = Color(0xFFF8FAFC)        // Slate-50: Crisp, clean light background
val CardBackground = Color(0xFFFFFFFF)          // Pure White: Primary card surface
val CardElevated = Color(0xFFFFFFFF)            // Pure White: Elevated surface
val SurfaceWhite = Color(0xFFF1F5F9)            // Slate-100: Secondary neutral container
val SurfaceNeutral = Color(0xFFF1F5F9)          // Slate-100: Secondary neutral container
val SurfaceNeutralSubtle = Color(0xFFF8FAFC)    // Slate-50: Inset container
val DarkSurface = Color(0xFF0F172A)             // Slate-900: Dark telemetry HUD surface

// Hairline Borders (Crisp 1dp structural definition, no blurry drop shadows)
val BorderSubtle = Color(0xFFE2E8F0)            // Slate-200: Hairline border
val BorderHairline = Color(0xFFE2E8F0)          // Slate-200: Hairline border alias
val BorderHighlight = Color(0xFFCBD5E1)         // Slate-300: Active / focused border
val AccentPill = Color(0xFFF1F5F9)              // Slate-100: Tag / pill background

// High-Contrast Cool-Grey Typography
val TextPrimary = Color(0xFF0F172A)             // Slate-900: High-contrast headline / title
val TextSecondary = Color(0xFF475569)           // Slate-600: Body / description text
val TextMuted = Color(0xFF94A3B8)               // Slate-400: Timestamps / secondary meta
val PrimaryBlack = Color(0xFF0F172A)            // Slate-900
val SecondaryGray = Color(0xFF475569)           // Slate-600

// Locked Brand Accent (Single Accent for CTAs, Active States, and Focus)
val BrandAccent = Color(0xFF2563EB)             // Electric Cobalt 600
val BrandSky = Color(0xFF2563EB)                // Aliased to BrandAccent for consistency
val BrandIndigo = Color(0xFF2563EB)             // Aliased to BrandAccent for consistency
val BrandPurple = Color(0xFF2563EB)             // Aliased to BrandAccent for consistency

// Semantic Verification & Status
val StatusPresent = Color(0xFF059669)           // Emerald 600: Verified / Present
val StatusPresentGlow = Color(0xFF10B981)       // Emerald 500: Active status ring
val StatusPresentBg = Color(0xFFECFDF5)         // Mint 50: Verified container
val StatusPresentBorder = Color(0xFFA7F3D0)     // Mint 200: Verified hairline border
val StatusReview = Color(0xFFD97706)            // Amber 600: Pending / Warning
val StatusReviewBg = Color(0xFFFFFBEB)          // Amber 50: Pending container
val StatusReviewBorder = Color(0xFFFDE68A)      // Amber 200: Pending hairline border
val StatusAbsent = Color(0xFFDC2626)            // Red 600: Absent / Flagged
val StatusAbsentBg = Color(0xFFFEF2F2)          // Rose 50: Error container
val StatusAbsentBorder = Color(0xFFFECACA)      // Rose 200: Error hairline border

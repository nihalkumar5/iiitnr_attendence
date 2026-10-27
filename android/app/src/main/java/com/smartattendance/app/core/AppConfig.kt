package com.smartattendance.app.core

/**
 * Global Application & Development Flags
 */
object AppConfig {
    /**
     * PRO TRICK 1: Development / Testing Mode Flag.
     * - When TRUE: Bypasses hardware device locking. Any student or roll number
     *   can log in and mark attendance on this test device without "DEVICE LOCKED" headache.
     * - When FALSE: Activates strict 1-Student = 1-Device anti-proxy hardware lock
     *   for real college deployment.
     */
    const val DEV_MODE = true
}

/**
 * BLE Protocol & Radio Constants for Smart Attendance System
 */

export const SMART_ATTENDANCE_BLE_CONSTANTS = {
  // Primary 128-bit custom service UUID for Smart Attendance
  SERVICE_UUID: "0000FD5A-0000-1000-8000-00805F9B34FB",
  
  // Compact 16-bit UUID (0xFD5A) for legacy BLE advertisement packets
  SERVICE_UUID_16_BIT: 0xFD5A,
  
  // Rotating Ephemeral Token (RET) duration in seconds
  TOKEN_ROTATION_INTERVAL_SECONDS: 30,
  
  // Token size in bytes (64-bit entropy) and hex characters
  TOKEN_BYTE_LENGTH: 8,
  TOKEN_HEX_LENGTH: 16,
  
  // Path-loss calibrated RSSI at 1 meter distance (dBm)
  RSSI_1_METER_CALIBRATED: -59,
  
  // RSSI cutoff threshold for classroom proximity (dBm)
  CLASSROOM_RSSI_THRESHOLD: -80,
  
  // Boundary RSSI cutoff for rejecting hallway / adjacent room spillover (dBm)
  MAX_REJECT_RSSI_THRESHOLD: -85,
  
  // Student duty-cycle scan timings (in milliseconds)
  STUDENT_SCAN_WINDOW_MS: 10_000,    // Active scanning for 10 seconds
  STUDENT_SCAN_INTERVAL_MS: 60_000,  // Scans once every 60 seconds (10s on, 50s cooldown)
  
  // Aggregated presence batch upload interval (in milliseconds)
  PRESENCE_BATCH_SYNC_INTERVAL_MS: 180_000, // 3 minutes
  
  // Dynamic QR Fallback duration
  QR_FALLBACK_VALIDITY_SECONDS: 15,
} as const;

export const ATTENDANCE_POLICY_DEFAULTS = {
  PRESENT_THRESHOLD_RATIO: 0.60, // >= 60% coverage = PRESENT
  REVIEW_THRESHOLD_RATIO: 0.20,  // 20% - 59% coverage = REVIEW
  ABSENT_THRESHOLD_RATIO: 0.20,  // < 20% coverage = ABSENT
  TIMELINE_WINDOW_MINUTES: 3,    // 3-minute discrete observation windows
} as const;

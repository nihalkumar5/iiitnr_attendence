/**
 * Core Data Models & API Contracts for Smart Attendance System
 */

export type UserRole = 'SUPER_ADMIN' | 'DEPT_ADMIN' | 'TEACHER' | 'STUDENT';
export type SessionStatus = 'ACTIVE' | 'CALCULATING' | 'IN_REVIEW' | 'COMPLETED' | 'CANCELLED';
export type AttendanceStatus = 'PRESENT' | 'REVIEW' | 'ABSENT';
export type VerificationMethod = 'BLE_AUTO' | 'MANUAL_TEACHER' | 'QR_FALLBACK';
export type DeviceStatus = 'ACTIVE' | 'BLOCKED' | 'PENDING_APPROVAL';

export interface UserProfile {
  id: string;
  institution_id: string;
  name: string;
  email: string;
  phone?: string;
  role: UserRole;
  is_active: boolean;
  created_at: string;
}

export interface StudentProfile {
  id: string;
  user_id: string;
  roll_number: string;
  program_id: string;
  semester: number;
  section_id: string;
  status: string;
  user: UserProfile;
}

export interface TeacherProfile {
  id: string;
  user_id: string;
  employee_id: string;
  department_id: string;
  designation?: string;
  user: UserProfile;
}

export interface ClassroomInfrastructure {
  id: string;
  institution_id: string;
  name: string;
  building?: string;
  floor?: number;
  capacity?: number;
  wifi_ssid?: string;
  wifi_bssid?: string;           // Target AP hardware MAC (e.g. "a4:2b:b0:12:34:56")
  secondary_bssids?: string[];
  beacon_uuid?: string;
  beacon_major?: number;
  beacon_minor?: number;
  is_rtt_supported?: boolean;    // 802.11mc Wi-Fi RTT responder
}

export interface ClassSession {
  id: string;
  subject_id: string;
  teacher_id: string;
  section_id: string;
  classroom_id?: string;
  classroom?: ClassroomInfrastructure;
  room: string;
  day_of_week: number;
  start_time: string;
  end_time: string;
  subject_name?: string;
  subject_code?: string;
  teacher_name?: string;
}

export interface AttendanceSession {
  id: string;
  class_id: string;
  teacher_id: string;
  session_secret: string;
  start_time: string;
  end_time?: string;
  expected_end_time?: string;
  status: SessionStatus;
  present_threshold: number;
  review_threshold: number;
  created_at: string;
}

export interface EphemeralBleToken {
  token: string;              // 16 hex characters
  timestamp_window: number;   // Epoch / 30
  session_id: string;
}

export interface PresenceObservation {
  session_id: string;
  token?: string;
  rssi?: number;
  wifi_bssid?: string;
  wifi_ssid?: string;
  wifi_rssi?: number;
  wifi_rtt_distance_meters?: number;
  timestamp: string;
}

export interface PresenceSyncPayload {
  device_id: string;
  events: PresenceObservation[];
}

export interface AttendanceRecordItem {
  record_id: string;
  student_id: string;
  roll_number: string;
  name: string;
  presence_percentage: number;
  status: AttendanceStatus;
  verification_method: VerificationMethod;
  wifi_ap_verified?: boolean;
  ble_verified?: boolean;
  rtt_distance_meters?: number;
  sensor_details?: {
    wifi_ratio?: number;
    ble_ratio?: number;
    rtt_avg_meters?: number;
  };
  notes?: string;
  marked_at: string;
}

export interface SessionSummary {
  session_id: string;
  total_students: number;
  present_count: number;
  review_count: number;
  absent_count: number;
  total_windows?: number;
}

export interface DynamicQrPayload {
  qr_payload: string;
  expires_in_seconds: number;
}

# Smart Attendance System — API Contracts & Specifications

All API endpoints run over HTTPS and use JSON payloads. Endpoints authenticate via Supabase JWT headers (`Authorization: Bearer <token>`).

---

## 1. Authentication & Device Identity

### 1.1 Login
**`POST /auth/login`**
Authenticate user with email/password or institutional SSO.

**Request:**
```json
{
  "email": "sharma@iitdemo.edu",
  "password": "securePassword123"
}
```

**Response (200 OK):**
```json
{
  "access_token": "eyJhbGciOi...",
  "refresh_token": "r_...",
  "user": {
    "id": "10000000-0000-0000-0000-000000000001",
    "name": "Dr. Sharma",
    "email": "sharma@iitdemo.edu",
    "role": "TEACHER",
    "institution_id": "a0000000-0000-0000-0000-000000000001",
    "teacher_id": "20000000-0000-0000-0000-000000000001"
  }
}
```

---

### 1.2 Register Device (Student Anti-Proxy)
**`POST /devices/register`**
Binds the student app's cryptographic installation ID to their student profile.

**Headers:** `Authorization: Bearer <student_token>`

**Request:**
```json
{
  "installation_id": "9f848209-7236-4122-bd55-94e82b7dc001",
  "device_model": "Pixel 8 Pro",
  "os_version": "Android 15 (API 35)",
  "platform": "ANDROID"
}
```

**Response (200 OK):**
```json
{
  "device_id": "40000000-0000-0000-0000-000000000001",
  "student_id": "30000000-0000-0000-0000-000000000001",
  "status": "ACTIVE",
  "registered_at": "2026-09-30T23:00:00Z"
}
```

---

## 2. Lecture & Attendance Session Management

### 2.1 Start Lecture Session
**`POST /attendance/session/start`**
Teacher starts lecture. Generates ephemeral session secret for BLE broadcast.

**Headers:** `Authorization: Bearer <teacher_token>`

**Request:**
```json
{
  "class_id": "50000000-0000-0000-0000-000000000001",
  "expected_duration_minutes": 60
}
```

**Response (201 Created):**
```json
{
  "session_id": "60000000-0000-0000-0000-000000000001",
  "class_id": "50000000-0000-0000-0000-000000000001",
  "subject_name": "Data Structures & Algorithms",
  "room": "Room A-204",
  "start_time": "2026-09-30T10:00:00Z",
  "expected_end_time": "2026-09-30T11:00:00Z",
  "status": "ACTIVE",
  "ble_advertising_config": {
    "service_uuid": "0000FD5A-0000-1000-8000-00805F9B34FB",
    "session_secret": "e4f8a3c9b7d2e1f0a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8",
    "rotation_seconds": 30
  }
}
```

---

### 2.2 End Lecture / Take Attendance
**`POST /attendance/session/end`**
Teacher ends lecture broadcast. System calculates presence coverage and outputs classification.

**Headers:** `Authorization: Bearer <teacher_token>`

**Request:**
```json
{
  "session_id": "60000000-0000-0000-0000-000000000001"
}
```

**Response (200 OK):**
```json
{
  "session_id": "60000000-0000-0000-0000-000000000001",
  "status": "IN_REVIEW",
  "summary": {
    "total_students": 50,
    "present": 46,
    "review": 2,
    "absent": 2
  }
}
```

---

### 2.3 Get Session Review Details
**`GET /attendance/session/:id`**
Fetches current session review status, breakdown, and student list.

**Response (200 OK):**
```json
{
  "session_id": "60000000-0000-0000-0000-000000000001",
  "class_name": "Data Structures & Algorithms",
  "room": "Room A-204",
  "status": "IN_REVIEW",
  "summary": {
    "total": 50,
    "present": 46,
    "review": 2,
    "absent": 2
  },
  "records": [
    {
      "record_id": "70000000-0000-0000-0000-000000000001",
      "student_id": "30000000-0000-0000-0000-000000000001",
      "roll_number": "26DSAI001",
      "name": "Rahul Kumar",
      "presence_percentage": 94.0,
      "status": "PRESENT",
      "verification_method": "BLE_AUTO"
    },
    {
      "record_id": "70000000-0000-0000-0000-000000000002",
      "student_id": "30000000-0000-0000-0000-000000000003",
      "roll_number": "26DSAI003",
      "name": "Priya Sharma",
      "presence_percentage": 48.0,
      "status": "REVIEW",
      "verification_method": "BLE_AUTO"
    },
    {
      "record_id": "70000000-0000-0000-0000-000000000003",
      "student_id": "30000000-0000-0000-0000-000000000004",
      "roll_number": "26DSAI004",
      "name": "Karan Verma",
      "presence_percentage": 0.0,
      "status": "ABSENT",
      "verification_method": "BLE_AUTO"
    }
  ]
}
```

---

### 2.4 Manual Override (Teacher)
**`PATCH /attendance/record/:id`**
Teacher manually adjusts status during review. Triggers audit log entry.

**Headers:** `Authorization: Bearer <teacher_token>`

**Request:**
```json
{
  "status": "PRESENT",
  "reason": "Student phone battery died, confirmed physically present in row 3"
}
```

**Response (200 OK):**
```json
{
  "record_id": "70000000-0000-0000-0000-000000000002",
  "status": "PRESENT",
  "verification_method": "MANUAL_TEACHER",
  "updated_at": "2026-09-30T11:05:00Z"
}
```

---

### 2.5 Submit Attendance
**`POST /attendance/session/:id/submit`**
Finalizes the attendance session. Locks records against further student submissions.

**Headers:** `Authorization: Bearer <teacher_token>`

**Response (200 OK):**
```json
{
  "success": true,
  "session_id": "60000000-0000-0000-0000-000000000001",
  "status": "COMPLETED",
  "submitted_at": "2026-09-30T11:06:00Z",
  "final_summary": {
    "total": 50,
    "present": 47,
    "review": 1,
    "absent": 2
  }
}
```

---

## 3. Student BLE Presence Synchronization

### 3.1 Batch Sync Presence Observations
**`POST /presence/sync`**
Student's background service batches observations collected over the last 3–5 minutes.

**Headers:** `Authorization: Bearer <student_token>`

**Request:**
```json
{
  "device_id": "40000000-0000-0000-0000-000000000001",
  "events": [
    {
      "session_id": "60000000-0000-0000-0000-000000000001",
      "token": "a1b2c3d4e5f60718",
      "rssi": -64,
      "timestamp": "2026-09-30T10:05:12Z"
    },
    {
      "session_id": "60000000-0000-0000-0000-000000000001",
      "token": "f8e7d6c5b4a39281",
      "rssi": -68,
      "timestamp": "2026-09-30T10:08:24Z"
    }
  ]
}
```

**Response (200 OK):**
```json
{
  "synced_count": 2,
  "rejected_count": 0
}
```

---

## 4. Fallback Dynamic QR Endpoints

### 4.1 Request Dynamic QR (Teacher)
**`POST /attendance/session/:id/qr-code`**
Generates a 15-second valid HMAC-signed QR token for presentation to students.

**Response (200 OK):**
```json
{
  "qr_payload": "SA-QR:60000000-0000-0000-0000-000000000001:1759296000:7e3d8f1a",
  "expires_in_seconds": 15
}
```

### 4.2 Verify Dynamic QR (Student)
**`POST /attendance/session/qr-verify`**
Student scans QR code.

**Request:**
```json
{
  "qr_payload": "SA-QR:60000000-0000-0000-0000-000000000001:1759296000:7e3d8f1a",
  "device_id": "40000000-0000-0000-0000-000000000001"
}
```

**Response (200 OK):**
```json
{
  "status": "VERIFIED_PENDING_CONFIRMATION",
  "message": "QR verification recorded. Awaiting teacher submission."
}
```

---

## 5. Reports & History

### 5.1 Student Attendance History
**`GET /student/attendance`**

**Response (200 OK):**
```json
{
  "overall_percentage": 82.4,
  "classes_attended": 28,
  "total_classes": 34,
  "subject_breakdown": [
    { "code": "CS501", "name": "Data Structures", "percentage": 91.0, "attended": 10, "total": 11 },
    { "code": "CS502", "name": "Machine Learning", "percentage": 84.0, "attended": 9, "total": 11 },
    { "code": "CS503", "name": "DBMS", "percentage": 76.0, "attended": 9, "total": 12 }
  ],
  "recent_records": [
    { "date": "2026-09-30", "subject": "Data Structures", "status": "PRESENT" },
    { "date": "2026-09-29", "subject": "DBMS", "status": "PRESENT" }
  ]
}
```

### 5.2 Admin Reports
**`GET /admin/reports?department_id=...&from=...&to=...&format=json`**
Supports JSON, CSV, and PDF export query parameters.

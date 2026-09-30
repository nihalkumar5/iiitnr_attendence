# Smart Attendance System — UI Route Map & Screen Specifications

## 1. Design System & Style Guidelines

### 1.1 Palette & Design Tokens
```css
--bg-canvas:     #F8F8F6; /* Warm institutional paper background */
--fg-primary:    #111111; /* High contrast text / primary actions */
--fg-secondary:  #666666; /* Subtitles, timestamps, captions */
--border-subtle: #E5E5E5; /* Minimal card borders */
--surface-card:  #FFFFFF; /* Elevated clean cards */
--state-success: #16803C; /* Present: Verified attendance */
--state-warning: #B7791F; /* Review: Inconclusive presence (20-59%) */
--state-error:   #C53030; /* Absent: Insufficient presence (<20%) */
```

### 1.2 Design Language
- **Typography**: Inter / Roboto / System Font.
- **Form Factor**: Utilitarian, high whitespace, rounded card corners (12–16 dp / px).
- **Core Principle**: One clear primary call-to-action (CTA) per screen. No gimmicky animations. Student home has **NO** manual "Mark Attendance" button.

---

## 2. Student Mobile Application (Jetpack Compose)

```
[Splash] ──▶ [Login] ──▶ [Device Setup / Verification]
                                 │
                                 ▼
                     [Student Main Scaffold]
                     ├── 1. Home (Passively tracking, Today's schedule)
                     ├── 2. Attendance Overview & Analytics
                     ├── 3. Attendance History (Past dates & logs)
                     ├── 4. Bluetooth & Device Status Diagnostic
                     └── 5. Settings & Profile
```

### Screen Details:
1. **`SplashDestination`**: Checks Supabase session & local Room cache.
2. **`LoginDestination`**: Institutional email/password or SSO login.
3. **`DeviceSetupDestination`**: Generates device installation ID, requests runtime `BLUETOOTH_SCAN` & `POST_NOTIFICATIONS` permissions.
4. **`StudentHomeDestination`**:
   - Greeting: "Good morning, Rahul"
   - Current / Next lecture card: "Data Structures | Room A-204 | 10:00 – 11:00"
   - Status badge: "✓ Automatically tracked in background"
   - Overall Semester Metric: "82.4% Cumulative Attendance"
   - Metric pill: "28 / 34 Classes attended"
5. **`AttendanceOverviewDestination`**: Subject breakdown cards (Data Structures: 91%, Machine Learning: 84%, DBMS: 76%, Networks: 88%).
6. **`AttendanceHistoryDestination`**: Chronological log by date (e.g., "September 30 — CS501 Data Structures — ✓ Present").
7. **`DeviceStatusDestination`**: Diagnostic screen showing Bluetooth hardware state, battery optimization whitelist status, and last synced timestamp.
8. **`QRFallbackScannerDestination`**: Minimal scanner modal opened ONLY when requested by teacher for fallback verification.

---

## 3. Teacher Mobile Application (Jetpack Compose)

```
[Login] ──▶ [Teacher Main Scaffold]
                  ├── 1. Home & Today's Timetable
                  │         └── [Select Class] ──▶ [Start Lecture Modal]
                  │                                         │
                  │                                         ▼
                  │                                 [Active Lecture Screen]
                  │                                 (BLE Advertising Active)
                  │                                         │
                  │                                         ▼ [End Lecture]
                  │                                 [Attendance Review Screen]
                  │                                 (Present / Review / Absent)
                  │                                         │
                  │                                         ├─▶ [Dynamic QR Dialog]
                  │                                         │
                  │                                         ▼ [Submit Attendance]
                  │                                 [Submission Confirmation]
                  ├── 2. Classes & Subject Catalogs
                  ├── 3. Attendance History & Edit Logs
                  └── 4. Reports & Exports
```

### Screen Details:
1. **`TeacherHomeDestination`**:
   - Header: "Good morning, Dr. Sharma"
   - "Today's Schedule" card list:
     - 10:00 AM — Data Structures (M.Tech DSAI, Room A-204) → **[Start Lecture]**
     - 12:00 PM — Machine Learning (M.Tech DSAI, Room A-302) → **[Upcoming]**
2. **`ActiveLectureDestination`**:
   - Subject & Room: "Data Structures — Room A-204 (10:00 – 11:00)"
   - Timer: "Lecture in progress: 42m elapsed"
   - Live Counter: "47 / 50 Students detected"
   - Radio Status: "🟢 BLE Broadcast Active (Rotating Token #8F)"
   - Primary CTA: **[End Lecture & Take Attendance]**
3. **`AttendanceReviewDestination`**:
   - Summary Pills:
     - 🟢 46 Present
     - 🟡 2 Review
     - 🔴 2 Absent
   - Search bar for quick filter by student name or roll number.
   - Interactive student list:
     - "✓ Rahul Kumar — 94% — Present" (Click to toggle)
     - "? Priya Sharma — 48% — Review" (Click to override or verify)
     - "✕ Karan Verma — 0% — Absent"
   - Action toolbar: **[Show Dynamic QR Fallback]** | **[Submit Attendance]**
4. **`DynamicQRDialog`**: Full-screen modal rendering rotating QR code with 15-second countdown timer.
5. **`SubmissionSuccessDestination`**: "✓ Attendance Submitted", summary metrics, link to session audit log.

---

## 4. Admin Web Dashboard (Next.js 14+ App Router)

```
/ (Root)
 ├── /login (Admin Authentication)
 └── /dashboard (Protected Layout with Sidebar Navigation)
      ├── /overview (KPI Cards: Total Students, Teachers, Classes Today, Attendance Rate)
      ├── /departments (Manage Depts & Academic Programs)
      ├── /classes (Master Timetable & Lecture Scheduling)
      ├── /subjects (Subject Catalog & Credits)
      ├── /students (Enrollment, Roll numbers, Registered Devices)
      ├── /teachers (Faculty Management)
      ├── /attendance (Live and Historical Attendance Sessions)
      ├── /reports (Institutional Compliance Reports with CSV / PDF Export)
      └── /settings (Institution Threshold Configuration: 60% Present, 20% Review)
```

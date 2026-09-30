# Smart Attendance System — Architecture & System Specification

## 1. Executive Summary

Smart Attendance System is an institutional-grade, zero-friction automated attendance platform designed for colleges and universities.

### Core Value Proposition
- **Teacher**: Tap "Start Lecture" → Teach normally → Tap "Take Attendance" → Review anomalies → Tap "Submit".
- **Student**: Enter the classroom → Phone passively verifies presence via Bluetooth Low Energy (BLE) → No interaction required. No QR scanning, no roll-call, no manual buttons.
- **Administration**: Real-time departmental metrics, tamper-resistant audit trails, customizable attendance policies, and accredited compliance reports (CSV/PDF).

---

## 2. System Architecture

```
                    ┌──────────────────────────────────────────────┐
                    │               ADMIN DASHBOARD                │
                    │         Next.js 14+ / TypeScript / Tailwind   │
                    └──────────────────────┬───────────────────────┘
                                           │ HTTPS / Supabase Client
                                           ▼
┌──────────────────────────────┐    ┌──────────────────────────────────┐    ┌──────────────────────────────┐
│       TEACHER APP            │    │        BACKEND PLATFORM          │    │        STUDENT APP           │
│   (Android Native/Compose)   ├───▶│      (Supabase / PostgreSQL)     │◀───┤   (Android Native/Compose)   │
│                              │    │                                  │    │                              │
│ • Session Controller         │    │ • Row Level Security (RLS)       │    │ • AttendanceScannerService   │
│ • Ephemeral BLE Advertiser   │    │ • Edge Functions (Token, Sync)   │    │ • Proximity Path-Loss Filter │
│ • Review & Override Engine   │    │ • Attendance Evaluation Engine   │    │ • Local Room Observation DB  │
│ • Dynamic QR Fallback        │    │ • Audit Log Ledger               │    │ • Batched Presence Syncer    │
└──────────────┬───────────────┘    └─────────────────┬────────────────┘    └──────────────┬───────────────┘
               │                                      │                                    │
               │ BLE Advertising                      │ Supabase Realtime                  │ BLE Passive Scanning
               │ Service UUID: 0000FD5A-...           │                                    │ Service Filter Match
               └──────────────────────────────────────┼────────────────────────────────────┘
                                                      │
                                                      ▼
                                            ┌────────────────────┐
                                            │ PostgreSQL Engine  │
                                            │ + Timescale/Cron   │
                                            └────────────────────┘
```

---

## 3. Technology Stack

| Domain | Technology | Rationale |
| :--- | :--- | :--- |
| **Mobile Apps** | Android Native (Kotlin 2.0+, Jetpack Compose) | Precise control over BLE advertising (`BluetoothLeAdvertiser`) and low-power scanning (`BluetoothLeScanner`), background execution control, and foreground services. |
| **Architecture** | Clean Architecture + MVVM + Coroutines + Flow | Separation of concerns, testability, offline-first reliability. |
| **Local Cache** | Android Jetpack Room | Local buffer of presence samples when student has spotty cellular/Wi-Fi coverage in lecture halls. |
| **Backend / DB** | Supabase (PostgreSQL 16) | High performance relational database, built-in Row-Level Security, Edge Functions (Deno/TypeScript), and Realtime websockets. |
| **Admin Web** | Next.js 14+ (App Router), TypeScript, Tailwind CSS | Server-rendered high performance dashboard, institutional UI aesthetics, rapid CSV/PDF generation. |
| **Security** | HMAC-SHA256, Rotating Ephemeral Tokens, Installation Fingerprinting | Anti-proxy, anti-replay, anti-screenshot protection without invasive spyware. |

---

## 4. BLE Proximity & Anti-Proxy Engine

### 4.1 Teacher Phone as Temporary BLE Beacon
Instead of deploying expensive static hardware beacons across hundreds of classrooms:
1. When the teacher taps **"Start Lecture"**, the backend generates a cryptographically secure 256-bit `session_secret` and establishes `attendance_sessions`.
2. The teacher's Android app starts a foreground advertising service:
   - **Service UUID**: `0000FD5A-0000-1000-8000-00805F9B34FB` (Custom Smart Attendance Service UUID)
   - **Manufacturer Data / Service Data**: Contains a **Rotating Ephemeral Token (RET)**:
     $$\text{Token}_t = \text{Truncate}_{16}(\text{HMAC-SHA256}(\text{session\_secret}, \text{session\_id} \parallel \lfloor t / 30 \rfloor))$$
   - The token rotates every **30 seconds**.
   - Tokens never include static classroom IDs, teacher IDs, or roll numbers.

### 4.2 Student Phone Passive Detection
1. The student Android app runs `AttendanceScannerService`:
   - Scans in periodic cycles (e.g., scans for 10 seconds, sleeps for 50 seconds during class time slots).
   - Filters hardware scans at the BLE chip level using `ScanFilter.Builder().setServiceUuid(...)`.
2. When the advertisement is caught:
   - Checks received signal strength indicator (RSSI):
     $$\text{RSSI} \ge \text{RSSI\_THRESHOLD} \quad (\text{e.g., } -80 \text{ dBm})$$
   - Records sample locally in Room database: `(session_id, token, timestamp, rssi)`.
3. **Batched Upload**: The app batches observations every 3–5 minutes or at lecture close rather than uploading per packet.

### 4.3 Anti-Proxy Protection
- **No hardware MAC relying**: Android randomizes BLE MACs; the system uses cryptographically verified tokens linked to registered `installation_id`.
- **Single-Device Registration**: Each student account binds to one verified device installation. Attempting to switch devices requires administrative or email re-verification.
- **Rotating Tokens**: A student cannot take a photo or screenshot of a code and send it to an absent peer; tokens expire within 30 seconds.
- **Relay-Attack Mitigation**: Server checks timestamp validity against the session's active secret window ($\pm 1$ time window tolerance).

---

## 5. Attendance Calculation Engine

Attendance is never marked on a single binary ping. Proximity is sampled across the lecture timeline.

### 5.1 Timeline Sampling Algorithm
Let a lecture have duration $T = t_{\text{end}} - t_{\text{start}}$ divided into uniform 5-minute evaluation windows $W_1, W_2, \dots, W_N$.
A window $W_k$ is marked **Observed** if at least one valid presence event from the student's registered device fell within that window with $\text{RSSI} \ge -80 \text{ dBm}$.

$$\text{Coverage Ratio } C = \frac{\sum_{k=1}^N \mathbf{1}_{W_k \text{ is Observed}}}{N}$$

### 5.2 Threshold Classification
The institutional administrator can configure thresholds per department or institution. Defaults:

| Threshold | Classification | Action |
| :--- | :--- | :--- |
| $C \ge 60\%$ | 🟢 **PRESENT** | Automatically validated. |
| $20\% \le C < 60\%$ | 🟡 **REVIEW** | Flagged to teacher at lecture end with exact coverage %. |
| $C < 20\%$ | 🔴 **ABSENT** | Student did not spend meaningful time in classroom. |

---

## 6. Fallback Mechanism: Dynamic Rolling QR

If a student's Bluetooth is disabled, phone ran out of battery earlier, or device was throttled by OS:
1. Teacher taps **"Verify Student"** or **"Dynamic QR"** on the Review screen.
2. Screen renders a high-entropy, TOTP-style QR code refreshing every 15 seconds:
   $$\text{QR\_Data} = \text{session\_id} \parallel \text{timestamp} \parallel \text{HMAC-SHA256}(\text{session\_secret}, \text{timestamp} \parallel \text{salt})$$
3. Student scans the QR code using their registered app camera.
4. Server verifies freshness and sets `verification_method = 'QR_FALLBACK'`.
5. The record is flagged for teacher confirmation before final submission.

---

## 7. Privacy & Ethical Boundaries

| In Scope (Collected) | Explicitly Excluded (NOT Collected) |
| :--- | :--- |
| Student ID, Roll Number, Name | Continuous GPS location or geofences |
| Ephemeral BLE presence timestamps | Camera stream (used solely on-demand for QR fallback) |
| Bluetooth RSSI value during active lectures | Microphone / Audio |
| Device installation ID & OS version | Contacts, files, browsing history |

Raw presence events are purged after 30 days by an automated Supabase retention cron; only finalized `attendance_records` are permanently stored.

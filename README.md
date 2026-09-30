# Smart Attendance System (Standalone MVP)

> **Zero-Friction Institutional Attendance Engine**  
> Fusing **Classroom Wi-Fi AP (BSSID)** + **Continuous BLE Proximity (Rotating Ephemeral Tokens)** + **Wi-Fi RTT (802.11mc)** with Automated Teacher Review and Dynamic QR Fallback.

---

## 1. System Vision & Core Value Proposition

- **Teacher Experience**:
  - Tap **[Start Lecture]** → Teach naturally → Tap **[Take Attendance]** at conclusion → Review flagged exceptions → Tap **[Submit]**.
  - Zero roll calls, zero student queues, zero hardware maintenance.
- **Student Experience**:
  - Walk into classroom → Phone passively verifies presence in background → Put phone in bag/pocket.
  - Zero QR codes to scan, zero buttons to press, zero biometric data surrendered.
- **Administrator Value**:
  - Centralized departmental rosters, classroom AP BSSID mappings, audit trails, and official compliance reports (CSV/PDF).

---

## 2. Multi-Sensor Hybrid Architecture

```
                    ┌──────────────────────────────────────────────┐
                    │               ADMIN DASHBOARD                │
                    │         Next.js 14+ / TypeScript / Tailwind   │
                    └──────────────────────┬───────────────────────┘
                                           │ HTTPS / RLS
                                           ▼
┌──────────────────────────────┐    ┌──────────────────────────────────┐    ┌──────────────────────────────┐
│       TEACHER APP            │    │        BACKEND PLATFORM          │    │        STUDENT APP           │
│   (Android Native/Compose)   ├───▶│      (Supabase / PostgreSQL)     │◀───┤   (Android Native/Compose)   │
│                              │    │                                  │    │                              │
│ • Session Controller         │    │ • Row Level Security (RLS)       │    │ • AttendanceScannerService   │
│ • Temporary BLE Beacon       │    │ • Multi-Modal Presence Fusion    │    │ • Wi-Fi AP BSSID Inspector   │
│ • Review & Override Engine   │    │ • 30-Day Auto Retention Purge    │    │ • Wi-Fi RTT 802.11mc Ranging │
│ • Dynamic QR Fallback (15s)  │    │ • Immutable Audit Ledger         │    │ • Local Room Observation DB  │
└──────────────┬───────────────┘    └─────────────────┬────────────────┘    └──────────────┬───────────────┘
               │                                      │                                    │
               │ BLE Advertising                      │ Supabase Realtime                  │ Multi-Modal Sensing
               │ Service UUID: 0xFD5A                 │                                    │ Wi-Fi + BLE + RTT
               └──────────────────────────────────────┼────────────────────────────────────┘
                                                      │
                                                      ▼
                                            ┌────────────────────┐
                                            │ PostgreSQL Engine  │
                                            └────────────────────┘
```

---

## 3. The 3-Tier Multi-Modal Sensing Model

| Tier | Sensing Technology | Primary Question Answered | Hardware Role |
| :--- | :--- | :--- | :--- |
| **Tier 1 (Universal)** | **Classroom Wi-Fi AP (BSSID)** | *"Is the student's phone connected to the router in Room A-204?"* | Existing college access point |
| **Tier 2 (Proximity)** | **BLE Ephemeral Token (RET)** | *"Is the student physically within the classroom enclosure (<15m)?"* | Teacher's phone or fixed beacon |
| **Tier 3 (Precision)** | **Wi-Fi RTT (802.11mc)** | *"What is the sub-meter radial distance to the access point?"* | Supported modern Android devices |

### Presence Evaluation Matrix

$$\text{Final Presence Score} = (0.50 \times \text{Wi-Fi AP Coverage}) + (0.50 \times \text{BLE Coverage}) + \text{RTT Bonus}$$

- $\ge 60\% \implies$ 🟢 **PRESENT** (Strong multi-modal evidence)
- $20\% \text{ to } 59\% \implies$ 🟡 **REVIEW** (e.g. Wi-Fi verified but Bluetooth off, or brief presence)
- $< 20\% \implies$ 🔴 **ABSENT** (Cafeteria, outside, or hallway)

---

## 4. Repository & Monorepo Structure

```
smart-attendance/
├── docs/                                    # Technical architecture & specs
│   ├── ARCHITECTURE.md                      # High-level system architecture
│   ├── HYBRID_WIFI_BLE_RTT_ARCHITECTURE.md  # Multi-sensor fusion deep dive
│   ├── BLE_PROTOCOL_SPEC.md                 # 0xFD5A payload & HMAC rotation
│   ├── DATABASE_SCHEMA.md                   # Complete data dictionary
│   ├── API_CONTRACTS.md                     # REST/HTTPS API endpoints
│   └── UI_ROUTE_MAP.md                      # Compose & Next.js screen flows
├── supabase/                                # Supabase & PostgreSQL backend
│   ├── config.toml                          # Local Supabase configuration
│   ├── seed.sql                             # Demo campus seed data
│   └── migrations/
│       ├── 20260930000001_initial_schema.sql
│       ├── 20260930000002_rls_policies.sql
│       ├── 20260930000003_attendance_engine_functions.sql
│       ├── 20260930000004_retention_and_audit.sql
│       └── 20260930000005_hybrid_presence_engine.sql
├── shared/                                  # Shared TypeScript types & constants
│   ├── constants/ble.ts
│   └── types/index.ts
├── admin-web/                               # Next.js 14+ Admin Web Portal
│   ├── package.json
│   ├── tailwind.config.ts
│   └── src/app/
│       ├── page.tsx                         # Institutional Login
│       └── dashboard/
│           ├── page.tsx                     # Overview & Live KPIs
│           ├── attendance/page.tsx          # Multi-Sensor Session Telemetry
│           ├── reports/page.tsx             # Official CSV Export & Ledgers
│           └── infrastructure/page.tsx      # Classroom AP & Beacon Mapping
├── android/                                 # Android Native Mobile App (Kotlin + Compose)
│   ├── build.gradle.kts
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       └── java/com/smartattendance/app/
│           ├── MainActivity.kt
│           ├── core/
│           │   ├── ble/                     # BLE Advertiser, Scanner & RET Generator
│           │   ├── sensor/                  # Wi-Fi AP & Wi-Fi RTT Managers
│           │   └── engine/                  # AttendanceCalculator (Hybrid Matrix)
│           └── ui/
│               ├── theme/                   # Utilitarian institutional palette
│               ├── prototype/               # BlePrototypeScreen (Hybrid Studio)
│               ├── teacher/                 # Teacher Home, Active Lecture & Review
│               └── student/                 # Student Home (Zero-button) & History
└── tests/
    └── simulate-attendance-flow.mjs         # Runnable multi-sensor verification test
```

---

## 5. Verification & Testing

To test the mathematical multi-sensor presence evaluation:

```bash
node tests/simulate-attendance-flow.mjs
```

### Verified Test Output:
```
=============================================================================
 SMART ATTENDANCE — HYBRID MULTI-SENSOR PRESENCE MATRIX VERIFICATION
=============================================================================

| Student           | Wi-Fi AP | BLE | RTT Dist | Raw Pres | Final Score | Result     |
|-------------------|----------|-----|----------|----------|-------------|------------|
| Student A (Rahul) |    ✓     |  ✓  |  4.2m    |   92%    |    97%      | 🟢 Present |
| Student B (Aman)  |    ✓     |  ✓  |  6.8m    |   71%    |    76%      | 🟢 Present |
| Student C (Priya) |    ✓     |  ✗  |  5.1m    |   80%    |    45%      | 🟡 Review  |
| Student D (Vikram) |    ✓     |  ✓  |  14.5m   |   12%    |    12%      | 🟡 Review  |
| Student E (Karan) |    ✗     |  ✗  |  N/A     |   0%     |    0%       | 🔴 Absent  |
```

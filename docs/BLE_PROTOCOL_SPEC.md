# Smart Attendance System — BLE Protocol & Radio Specification

## 1. Overview & Constraints

The Smart Attendance BLE architecture enables the teacher's Android smartphone to act as a temporary beacon during active lectures, while enrolled students' smartphones passively scan, detect, and record presence in the background without user intervention.

### Core Principles
1. **Zero Hardware Cost**: Uses existing teacher & student Android hardware.
2. **Ephemeral Identity**: Never broadcasts static MAC addresses, student IDs, teacher IDs, or permanent classroom names.
3. **Battery Stewardship**: Duty-cycled adaptive scanning instead of continuous radio drain.
4. **Android Compliance**: Complies with Android 12–15 runtime Bluetooth permissions, Doze mode restrictions, and foreground service requirements.

---

## 2. BLE Advertising Protocol (Teacher Device)

### 2.1 Service Definition
- **Primary 16-bit UUID / 128-bit UUID**:
  - Full UUID: `0000FD5A-0000-1000-8000-00805F9B34FB`
  - Assigned 16-bit Service UUID: `0xFD5A`
- **Advertisement Mode**: `AdvertiseSettings.ADVERTISE_MODE_BALANCED` (~250 ms advertising interval)
- **Tx Power Level**: `AdvertiseSettings.ADVERTISE_TX_POWER_HIGH` (Provides uniform coverage across standard 50–100 seat lecture halls up to 15–20 meters).
- **Connectable**: `false` (Non-connectable undirected advertising). No BLE connection or pairing overhead is required; all verification data resides in the broadcast payload.

### 2.2 Advertisement Payload Structure (31-byte standard PDU)

```
┌───────────┬──────────────┬──────────────┬──────────────────┬──────────────────────┐
│ Length    │ Type (Flags) │ Flags Data   │ Length           │ Type (16-bit UUID)   │
│ (1 byte)  │ (1 byte)     │ (1 byte)     │ (1 byte)         │ (1 byte)             │
│ 0x02      │ 0x01         │ 0x06 (LE Gen)│ 0x03             │ 0x03                 │
├───────────┴──────────────┴──────────────┴──────────────────┴──────────────────────┤
│ Service UUID (2 bytes): 0x5A, 0xFD                                                │
├───────────┬──────────────┬───────────────────────────────┬────────────────────────┤
│ Length    │ Type         │ Service UUID                  │ Ephemeral Token Data   │
│ (1 byte)  │ (0x16 Serv.) │ (2 bytes: 0x5A, 0xFD)         │ (8 bytes = 64-bit RET) │
│ 0x0B      │ 0x16         │ 0x5A, 0xFD                    │ T_0 ... T_7            │
├───────────┴──────────────┴───────────────────────────────┴────────────────────────┤
│ Optional 1-byte Tx Power Calibrated at 1m (e.g. -59 dBm)                          │
└───────────────────────────────────────────────────────────────────────────────────┘
```

Total payload size = 3 + 4 + 12 + 2 = 21 bytes (well within the 31-byte legacy BLE limit for maximum compatibility across all Android devices).

---

## 3. Cryptographic Ephemeral Rotating Token (RET)

### 3.1 Token Generation Formula
$$\text{Window } w = \left\lfloor \frac{\text{Current\_Epoch\_Seconds}}{30} \right\rfloor$$
$$\text{HMAC} = \text{HMAC-SHA256}(\text{Session\_Secret}, \text{Session\_UUID} \parallel w)$$
$$\text{Token} = \text{HMAC}[0..7] \quad (8 \text{ raw bytes})$$

- Rotates every **30 seconds**.
- The student app records the 8-byte token (formatted as 16 hex characters).
- Server verifies token validity using $\pm 1$ window tolerance ($30 \text{ s} \times 3 = 90 \text{ s}$ acceptance window) to absorb slight clock drift between mobile devices and server NTP.

---

## 4. BLE Scanner Protocol (Student Device)

### 4.1 Android Scanning Lifecycle & Duty Cycle
To preserve battery while ensuring high presence confidence:

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  Active Scan    │       │     Cooldown    │       │   Active Scan   │
│   (10 Seconds)  ├──────▶│   (50 Seconds)  ├──────▶│  (10 Seconds)   │
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

- **Scan Mode**: `ScanSettings.SCAN_MODE_LOW_POWER` (or `SCAN_MODE_BALANCED` during the first 10 minutes of scheduled class time).
- **Hardware Filter**:
  ```kotlin
  val filter = ScanFilter.Builder()
      .setServiceUuid(ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB"))
      .build()
  ```
  Hardware-level filtering allows the Android Bluetooth chipset to discard all non-matching BLE traffic without waking up the main application CPU.

### 4.2 Proximity & RSSI Path-Loss Model
Signal strength (RSSI) is governed by the log-distance path loss model:
$$\text{RSSI}(d) = \text{RSSI}_0 - 10 \cdot n \cdot \log_{10}(d) + X_\sigma$$
Where:
- $\text{RSSI}_0 \approx -59 \text{ dBm}$ (measured at 1 meter calibrated distance)
- $n \approx 2.4$ (indoor lecture hall with desk obstacles and human body attenuation)
- $d$ = distance in meters

**Thresholds:**
- **In Classroom**: $\text{RSSI} \ge -80 \text{ dBm}$ (typically within 10–15 meters line-of-sight in lecture hall).
- **Hallway / Adjacent Room**: $\text{RSSI} < -85 \text{ dBm}$ (attenuated by concrete wall / door by 10–18 dBm).
- **Recorded Observation**: Events with $\text{RSSI} \ge -85 \text{ dBm}$ are accepted as candidate presence samples.

---

## 5. Android OS Permissions & Requirements

### 5.1 Android Manifest Permissions
```xml
<!-- BLE Permissions for Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Legacy Permissions for Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />

<!-- Background execution & Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### 5.2 Foreground Service Requirement
On modern Android (Android 10+), background BLE scanning is strictly throttled by the OS if the app does not run a foreground service.
- The Student App launches `AttendanceScannerService` as a `FOREGROUND_SERVICE_CONNECTED_DEVICE` during active timetable lecture hours.
- A persistent, quiet notification is displayed:
  > **Smart Attendance Active**
  > Passively tracking lecture attendance for CS501.

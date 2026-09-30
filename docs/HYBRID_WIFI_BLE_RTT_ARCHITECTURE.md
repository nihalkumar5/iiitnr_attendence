# Smart Attendance System — Hybrid Multi-Modal Architecture (Wi-Fi + BLE + Wi-Fi RTT)

## 1. Executive Vision: Multi-Sensor Proximity Fusion

While Bluetooth Low Energy (BLE) provides close-range proximity (<15m), relying solely on BLE can face mobile OS background throttling or Bluetooth-disabled scenarios. Conversely, relying solely on university Wi-Fi SSID suffers from "campus-wide spillover" (a student in the cafeteria 200 meters away can be connected to `eduroam` or `Campus-WiFi`).

The **Smart Attendance Hybrid Architecture** resolves this by fusing:
1. **Tier 1 (Universal Infrastructure): Wi-Fi AP BSSID Identification**
   - Classroom-specific Access Point hardware MAC address (BSSID).
   - Validates that the student's phone is associated with the exact physical router serving Room A-204, not the general campus network.
2. **Tier 2 (Proximity Verification): BLE Ephemeral Beaconing**
   - Fixed classroom BLE beacon OR Teacher's phone acting as temporary beacon fallback.
   - Validates localized physical presence within the room enclosure ($RSSI \ge -80\text{ dBm}$).
3. **Tier 3 (Precision Ranging where supported): Wi-Fi RTT (802.11mc)**
   - Nanosecond Round Trip Time measurement.
   - Estimates exact radial distance (e.g. $4.2\text{ meters} \pm 1\text{m}$) from the classroom AP without GPS.

---

## 2. Multi-Sensor Presence Fusion Model

```
                          TEACHER
                             │
                      Start Lecture
                             │
                             ▼
                    Attendance Backend
                             │
              ┌──────────────┴──────────────┐
              │                             │
         Classroom Wi-Fi              Classroom BLE
       AP BSSID Mapping              Rotating Token
       (e.g., AP-204)                (0xFD5A Service)
              │                             │
              └──────────────┬──────────────┘
                             ▼
                       Student Device
                             │
                   Unified Presence Agent
                             │
       ┌─────────────────────┼─────────────────────┐
       │ Tier 1              │ Tier 2              │ Tier 3 (Supported)
       ▼                     ▼                     ▼
  Connected Wi-Fi           BLE Proximity        Wi-Fi RTT Ranging
  AP BSSID Match           HMAC Ephemeral        Distance to AP
  (Weight: 45%)            (Weight: 45%)         (Bonus: 10%)
       │                     │                     │
       └─────────────────────┼─────────────────────┘
                             ▼
                 Multi-Modal Confidence Score
                             │
               ┌─────────────┴─────────────┐
               ▼                           ▼
        Score ≥ 60%                 Score 20% – 59%
      🟢 CONFIDENT PRESENT         🟡 TEACHER REVIEW
                                    (e.g. Wi-Fi ✓, BLE ✗)
```

---

## 3. Multi-Sensor Presence Matrix

| Scenario | Connected AP (BSSID) | BLE Signal (RET) | Wi-Fi RTT Distance | Computed Score | Classification | Teacher Action |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Student A (Ideal)** | Matches Room AP (`AP-204`) | Detected ($RSSI = -62$) | $4.5\text{ m}$ (In Room) | **100%** | 🟢 **PRESENT** | None (Zero friction) |
| **Student B (BT Disabled)** | Matches Room AP (`AP-204`) | Not Detected | $5.1\text{ m}$ (In Room) | **55%** | 🟡 **REVIEW** | Teacher confirms ("Present in room, BT off") |
| **Student C (In Corridor)** | Mismatched AP (`AP-HALLWAY`) | Weak Bleed ($RSSI = -92$) | $18.4\text{ m}$ (Outside) | **10%** | 🔴 **ABSENT** | Discarded automatically |
| **Student D (Cafeteria)** | Campus SSID, AP-CAFE | None | N/A | **0%** | 🔴 **ABSENT** | Discarded automatically |
| **Student E (Legacy Phone)** | Matches Room AP (`AP-204`) | Detected ($RSSI = -68$) | Not Supported by OS | **90%** | 🟢 **PRESENT** | Automatically normalized |

---

## 4. Hardware Deployment Topologies

The system supports two complementary deployment modes:

### Mode A: Zero-Hardware MVP (Teacher Phone Beacon + Existing AP)
- Uses the university's existing Wi-Fi AP in the classroom (recorded by administrator as BSSID).
- Teacher's Android phone broadcasts the rotating BLE token.
- Lowest cost to deploy (0 ₹ capital expenditure).

### Mode B: Campus Infrastructure Mode (Fixed Classroom Beacon + Managed AP)
- Classroom contains a permanent, wall-mounted BLE beacon ($10/room, 3-5 year battery).
- Classroom AP is registered in the institutional catalog with known BSSID and (optionally) 802.11mc RTT responder coordinates.
- Teacher merely taps "Start Lecture" on web or phone; the classroom infrastructure autonomously authenticates student presence.
- Removes reliance on teacher's phone battery or Bluetooth state.

---

## 5. Security & Anti-Spoofing Protections

### 5.1 Preventing Wi-Fi Hotspot BSSID Spoofing
A student cannot simply set their phone hotspot BSSID to match `AP-204`:
1. The student app reports both `BSSID` and `SSID`, plus the Gateway IP and internal DHCP lease.
2. The student's device must be capable of establishing an active HTTPS TLS connection to the university backend *through* that network interface.
3. The presence engine requires concurrent BLE validation; even if Wi-Fi BSSID were somehow spoofed, the absence of the cryptographically rotating BLE token will prevent automatic "Present" status and cap the score at 40% (triggering mandatory Teacher Review).

### 5.2 Wi-Fi RTT (802.11mc) Physical Layer Verification
Wi-Fi RTT relies on Time of Flight (ToF) radio physics at the 802.11mc chip layer:
- $d = \frac{c \cdot (t_4 - t_1 - (t_3 - t_2))}{2}$
- Cannot be spoofed by VPNs, proxy apps, or location mockers because the physical radio timing happens inside the Wi-Fi modem firmware.

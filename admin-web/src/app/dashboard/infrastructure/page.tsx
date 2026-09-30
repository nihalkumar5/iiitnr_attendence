import { Wifi, Radio, Plus, CheckCircle, MapPin, Shield } from "lucide-react";

export default function InfrastructurePage() {
  const classrooms = [
    {
      id: "rm-1",
      name: "Room A-204",
      building: "Academic Complex Block A",
      floor: 2,
      capacity: 60,
      wifiSsid: "IIT-Campus-WiFi",
      wifiBssid: "a4:2b:b0:12:34:56",
      secondaryBssids: ["a4:2b:b0:12:34:57 (5GHz)"],
      beaconUuid: "0000FD5A-0000-1000-8000-00805F9B34FB",
      beaconMajorMinor: "Major: 10 · Minor: 204",
      rttSupported: true,
      status: "ACTIVE",
    },
    {
      id: "rm-2",
      name: "Room A-302",
      building: "Academic Complex Block A",
      floor: 3,
      capacity: 45,
      wifiSsid: "IIT-Campus-WiFi",
      wifiBssid: "a4:2b:b0:12:34:88",
      secondaryBssids: ["a4:2b:b0:12:34:89 (5GHz)"],
      beaconUuid: "0000FD5A-0000-1000-8000-00805F9B34FB",
      beaconMajorMinor: "Major: 10 · Minor: 302",
      rttSupported: false,
      status: "ACTIVE",
    },
    {
      id: "rm-3",
      name: "Room B-105",
      building: "Science & Engineering Block B",
      floor: 1,
      capacity: 120,
      wifiSsid: "IIT-Campus-WiFi",
      wifiBssid: "a4:2b:b0:12:35:12",
      secondaryBssids: ["a4:2b:b0:12:35:13", "a4:2b:b0:12:35:14"],
      beaconUuid: "Teacher Phone Fallback",
      beaconMajorMinor: "Dynamic RET",
      rttSupported: true,
      status: "ACTIVE",
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary">Classroom Infrastructure & Sensors</h1>
          <p className="text-sm text-secondary mt-1">Manage physical Access Point (BSSID) mappings and fixed BLE beacon telemetry</p>
        </div>

        <button className="px-4 py-2.5 bg-primary text-white text-xs font-semibold rounded-lg hover:bg-black transition-colors flex items-center gap-2 shadow-sm">
          <Plus className="w-4 h-4" />
          <span>Register New Classroom AP</span>
        </button>
      </div>

      {/* Classroom Infrastructure Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {classrooms.map((room) => (
          <div key={room.id} className="card-clean p-6 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between">
                <span className="text-xs px-2.5 py-1 rounded-full bg-pill text-primary font-bold">
                  {room.name}
                </span>
                <span className="text-xs text-success font-semibold flex items-center gap-1">
                  <CheckCircle className="w-3.5 h-3.5" />
                  <span>{room.status}</span>
                </span>
              </div>

              <h2 className="text-base font-bold text-primary mt-3">{room.building}</h2>
              <p className="text-xs text-secondary">Floor {room.floor} · Capacity: {room.capacity} seats</p>

              <hr className="my-4 border-subtle" />

              {/* Wi-Fi AP Info */}
              <div className="space-y-2">
                <div className="flex items-center gap-2 text-xs font-semibold text-primary">
                  <Wifi className="w-4 h-4 text-primary" />
                  <span>Wi-Fi Access Point (Tier 1)</span>
                </div>
                <div className="bg-canvas p-2.5 rounded-lg text-xs space-y-1 font-mono">
                  <p className="text-secondary"><span className="text-primary font-medium font-sans">SSID:</span> {room.wifiSsid}</p>
                  <p className="text-secondary"><span className="text-primary font-medium font-sans">Primary BSSID:</span> {room.wifiBssid}</p>
                  <p className="text-secondary"><span className="text-primary font-medium font-sans">Mesh/5GHz:</span> {room.secondaryBssids.join(", ")}</p>
                </div>
              </div>

              {/* BLE Beacon Info */}
              <div className="space-y-2 mt-4">
                <div className="flex items-center gap-2 text-xs font-semibold text-primary">
                  <Radio className="w-4 h-4 text-primary" />
                  <span>BLE Proximity Beacon (Tier 2)</span>
                </div>
                <div className="bg-canvas p-2.5 rounded-lg text-xs space-y-1">
                  <p className="text-secondary font-mono text-[11px] truncate">{room.beaconUuid}</p>
                  <p className="text-secondary text-xs">{room.beaconMajorMinor}</p>
                </div>
              </div>

              {/* Wi-Fi RTT 802.11mc */}
              <div className="mt-4 flex items-center justify-between text-xs">
                <span className="text-secondary">Wi-Fi RTT (802.11mc):</span>
                {room.rttSupported ? (
                  <span className="px-2 py-0.5 rounded bg-success/10 text-success font-semibold">
                    Supported (Sub-meter)
                  </span>
                ) : (
                  <span className="px-2 py-0.5 rounded bg-pill text-secondary font-medium">
                    Standard AP
                  </span>
                )}
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-subtle flex items-center justify-between">
              <button className="text-xs text-primary font-semibold hover:underline">
                Edit AP Hardware
              </button>
              <button className="text-xs text-secondary hover:text-primary">
                Test Ping
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

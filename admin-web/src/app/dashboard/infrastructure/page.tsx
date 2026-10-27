"use client";

import { useState, useEffect } from "react";
import { 
  Wifi, Radio, Plus, CheckCircle2, MapPin, Shield, Layers, 
  RotateCw, AlertCircle, Signal, Check, Building2, ExternalLink
} from "lucide-react";
import { supabase } from "@/lib/supabaseClient";

interface CalibratedAP {
  bssid: string;
  ssid: string;
  rssi: number;
  frequencyMhz?: number;
  rank?: number;
}

interface ClassroomItem {
  id: string;
  name: string;
  building: string;
  floor: number;
  capacity: number;
  polygonName: string;
  wifiSsid: string;
  wifiBssid: string;
  secondaryBssids: string[];
  calibratedAps: CalibratedAP[];
  isCalibrated: boolean;
  calibratedAt?: string;
  beaconUuid: string;
  beaconMajorMinor: string;
  rttSupported: boolean;
}

interface BuildingPolygon {
  id: string;
  name: string;
  description: string;
  coordinatesCount: number;
  geojsonString: string;
  createdAt: string;
}

const DEFAULT_MAIN_BUILDING_GEOJSON = JSON.stringify({
  type: "Polygon",
  coordinates: [
    [
      [81.765671, 21.128551],
      [81.766056, 21.128031],
      [81.766608, 21.128317],
      [81.766258, 21.128873],
      [81.765671, 21.128551]
    ]
  ]
}, null, 2);
const DEFAULT_CS_BLOCK_GEOJSON = DEFAULT_MAIN_BUILDING_GEOJSON;

export default function InfrastructurePage() {
  const [activeTab, setActiveTab] = useState<"classrooms" | "polygons">("classrooms");

  // Building Polygons State
  const [polygons, setPolygons] = useState<BuildingPolygon[]>([
    {
      id: "poly-cs",
      name: "CS_BLOCK",
      description: "Main Academic Building & Classrooms Boundary (IIIT Naya Raipur)",
      coordinatesCount: 5,
      geojsonString: DEFAULT_CS_BLOCK_GEOJSON,
      createdAt: "2026-10-27"
    },
    {
      id: "poly-ac",
      name: "AC_COMPLEX",
      description: "Academic Complex Block A & Central Auditorium",
      coordinatesCount: 6,
      geojsonString: JSON.stringify({
        type: "Polygon",
        coordinates: [
          [
            [81.766800, 21.128000],
            [81.767400, 21.128000],
            [81.767400, 21.128600],
            [81.766800, 21.128600],
            [81.766800, 21.128000]
          ]
        ]
      }, null, 2),
      createdAt: "2026-10-20"
    }
  ]);

  const [polygonNameInput, setPolygonNameInput] = useState("CS_BLOCK");
  const [polygonDescInput, setPolygonDescInput] = useState("Computer Science Rooftop Boundary");
  const [polygonGeoJsonInput, setPolygonGeoJsonInput] = useState(DEFAULT_CS_BLOCK_GEOJSON);
  const [polygonSaveMsg, setPolygonSaveMsg] = useState<string | null>(null);

  // Classrooms State
  const [classrooms, setClassrooms] = useState<ClassroomItem[]>([
    {
      id: "rm-101",
      name: "Room 101",
      building: "CS & IT Academic Block",
      floor: 1,
      capacity: 60,
      polygonName: "CS_BLOCK",
      wifiSsid: "Campus-Secure",
      wifiBssid: "A4:2B:B0:8C:12:01",
      secondaryBssids: ["A4:2B:B0:8C:12:02", "A4:2B:B0:8C:12:03"],
      calibratedAps: [
        { bssid: "A4:2B:B0:8C:12:01", ssid: "Campus-Secure", rssi: -54, rank: 1 },
        { bssid: "A4:2B:B0:8C:12:02", ssid: "Campus-Secure (5GHz)", rssi: -62, rank: 2 },
        { bssid: "A4:2B:B0:8C:12:03", ssid: "Campus-Secure (Mesh)", rssi: -71, rank: 3 }
      ],
      isCalibrated: true,
      calibratedAt: "Just now (Teacher App)",
      beaconUuid: "0000FD5A-0000-1000-8000-00805F9B34FB",
      beaconMajorMinor: "Major: 10 · Minor: 101",
      rttSupported: true
    },
    {
      id: "rm-a204",
      name: "Room A-204",
      building: "Academic Complex Block A",
      floor: 2,
      capacity: 60,
      polygonName: "CS_BLOCK",
      wifiSsid: "Campus-Secure",
      wifiBssid: "A4:2B:B0:8C:12:EF",
      secondaryBssids: ["A4:2B:B0:8C:12:F0", "A4:2B:B0:8C:12:F1"],
      calibratedAps: [
        { bssid: "A4:2B:B0:8C:12:EF", ssid: "Campus-Secure", rssi: -52, rank: 1 },
        { bssid: "A4:2B:B0:8C:12:F0", ssid: "Campus-Secure (5GHz)", rssi: -60, rank: 2 },
        { bssid: "A4:2B:B0:8C:12:F1", ssid: "Campus-Secure (Mesh)", rssi: -68, rank: 3 }
      ],
      isCalibrated: true,
      calibratedAt: "Calibrated",
      beaconUuid: "0000FD5A-0000-1000-8000-00805F9B34FB",
      beaconMajorMinor: "Major: 10 · Minor: 204",
      rttSupported: true
    },
    {
      id: "rm-a302",
      name: "Room A-302",
      building: "Academic Complex Block A",
      floor: 3,
      capacity: 45,
      polygonName: "CS_BLOCK",
      wifiSsid: "Campus-Secure",
      wifiBssid: "A4:2B:B0:8C:13:10",
      secondaryBssids: ["A4:2B:B0:8C:13:11", "A4:2B:B0:8C:13:12"],
      calibratedAps: [
        { bssid: "A4:2B:B0:8C:13:10", ssid: "Campus-Secure", rssi: -55, rank: 1 },
        { bssid: "A4:2B:B0:8C:13:11", ssid: "Campus-Secure (5GHz)", rssi: -63, rank: 2 },
        { bssid: "A4:2B:B0:8C:13:12", ssid: "Campus-Secure (Mesh)", rssi: -70, rank: 3 }
      ],
      isCalibrated: true,
      calibratedAt: "Calibrated",
      beaconUuid: "0000FD5A-0000-1000-8000-00805F9B34FB",
      beaconMajorMinor: "Major: 10 · Minor: 302",
      rttSupported: false
    },
    {
      id: "rm-b105",
      name: "Room B-105",
      building: "Science & Engineering Block B",
      floor: 1,
      capacity: 120,
      polygonName: "CS_BLOCK",
      wifiSsid: "Campus-Secure",
      wifiBssid: "",
      secondaryBssids: [],
      calibratedAps: [],
      isCalibrated: false,
      beaconUuid: "Teacher Phone Fallback",
      beaconMajorMinor: "Dynamic RET",
      rttSupported: true
    }
  ]);

  // Load from Supabase on mount
  useEffect(() => {
    async function loadData() {
      try {
        const { data: clsData } = await supabase
          .from("classrooms")
          .select("*")
          .order("name", { ascending: true });

        if (clsData && clsData.length > 0) {
          const mapped: ClassroomItem[] = clsData.map((c: any) => ({
            id: c.id,
            name: c.name || "Room",
            building: c.building || "Academic Complex",
            floor: c.floor || 1,
            capacity: c.capacity || 60,
            polygonName: c.polygon_name || "CS_BLOCK",
            wifiSsid: c.wifi_ssid || "Campus-Secure",
            wifiBssid: c.wifi_bssid || "",
            secondaryBssids: Array.isArray(c.secondary_bssids) ? c.secondary_bssids : [],
            calibratedAps: Array.isArray(c.calibrated_aps) ? c.calibrated_aps : [],
            isCalibrated: !!c.is_calibrated,
            calibratedAt: c.calibrated_at || undefined,
            beaconUuid: c.beacon_uuid || "Campus Beacon",
            beaconMajorMinor: `Major: ${c.beacon_major || 10} · Minor: ${c.beacon_minor || 101}`,
            rttSupported: !!c.is_rtt_supported
          }));
          setClassrooms(mapped);
        }
      } catch (e) {
        console.warn("Classrooms load note:", e);
      }
    }
    loadData();
  }, []);

  // Save Building Polygon Handler
  const handleSavePolygon = async () => {
    setPolygonSaveMsg(null);
    try {
      const parsed = JSON.parse(polygonGeoJsonInput);
      let count = 0;
      if (parsed.type === "Polygon" && Array.isArray(parsed.coordinates) && parsed.coordinates[0]) {
        count = parsed.coordinates[0].length;
      } else if (parsed.type === "Feature" && parsed.geometry?.coordinates?.[0]) {
        count = parsed.geometry.coordinates[0].length;
      } else {
        count = 4;
      }

      const cleanName = polygonNameInput.trim().toUpperCase().replace(/\s+/g, "_") || "CS_BLOCK";
      const newPoly: BuildingPolygon = {
        id: `poly-${Date.now()}`,
        name: cleanName,
        description: polygonDescInput.trim() || "Building Rooftop Boundary",
        coordinatesCount: count,
        geojsonString: JSON.stringify(parsed, null, 2),
        createdAt: "Just now"
      };

      setPolygons(prev => [newPoly, ...prev.filter(p => p.name !== cleanName)]);

      // Sync to Supabase
      try {
        await supabase.from("building_polygons").upsert({
          name: cleanName,
          description: polygonDescInput.trim(),
          geojson: parsed
        }, { onConflict: "name" });
      } catch (e) {}

      setPolygonSaveMsg(`Saved polygon "${cleanName}" with ${count} boundary vertices!`);
    } catch (e: any) {
      alert("Invalid GeoJSON syntax! Please verify format from geojson.io.");
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Classroom Setup & Wi-Fi Polygon Calibration</h1>
          <p className="text-xs text-slate-500 mt-1">
            Map building rooftop Polygons (from geojson.io) and verify Top 3 in-class Wi-Fi APs (&gt; -75 dBm) to eliminate indoor GPS deadzones.
          </p>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-xl border border-slate-200">
          <button
            onClick={() => setActiveTab("classrooms")}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 ${
              activeTab === "classrooms"
                ? "bg-white text-blue-600 shadow-sm"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            <Wifi className="w-3.5 h-3.5" />
            <span>Classroom Calibration ({classrooms.length})</span>
          </button>
          <button
            onClick={() => setActiveTab("polygons")}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all flex items-center gap-1.5 ${
              activeTab === "polygons"
                ? "bg-white text-blue-600 shadow-sm"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Building Polygons ({polygons.length})</span>
          </button>
        </div>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: CLASSROOMS & WI-FI CALIBRATION                                    */}
      {/* ========================================================================= */}
      {activeTab === "classrooms" && (
        <div className="space-y-6">
          {/* Concept Banner */}
          <div className="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200/80 rounded-2xl p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-blue-600 text-white flex items-center justify-center font-black shadow-md shadow-blue-500/20">
                <Shield className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">One-Time Classroom Setup (Teacher / Admin)</h3>
                <p className="text-xs text-slate-600 mt-0.5">
                  Teacher pehli baar Room 101 me jaakar app me <strong>&quot;Calibrate Room Wi-Fi&quot;</strong> dabayega. Top 3 routers (&gt; -75 dBm) us room ke Polygon ke saath link ho jayenge.
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-semibold px-2.5 py-1 rounded-lg bg-white border border-blue-200 text-blue-700 font-mono">
                Indoor GPS Fail = 0% Impact
              </span>
            </div>
          </div>

          {/* Classrooms Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {classrooms.map((room) => {
              const hasAps = room.calibratedAps && room.calibratedAps.length > 0;
              return (
                <div 
                  key={room.id}
                  className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
                >
                  <div>
                    {/* Top Status Row */}
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-xs px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 font-bold border border-blue-200/60 font-mono">
                          {room.name}
                        </span>
                        <span className="text-[10px] px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 font-medium">
                          Floor {room.floor}
                        </span>
                      </div>
                      {room.isCalibrated ? (
                        <span className="text-[11px] px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 font-semibold border border-emerald-200 flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                          <span>CALIBRATED</span>
                        </span>
                      ) : (
                        <span className="text-[11px] px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 font-medium border border-amber-200 flex items-center gap-1">
                          <AlertCircle className="w-3 h-3 text-amber-500" />
                          <span>PENDING IN-CLASS</span>
                        </span>
                      )}
                    </div>

                    {/* Building & Linked Polygon */}
                    <h2 className="text-sm font-bold text-slate-900 mt-3">{room.building}</h2>
                    <div className="flex items-center gap-1.5 text-xs text-slate-500 mt-1">
                      <Building2 className="w-3.5 h-3.5 text-slate-400" />
                      <span>Linked Polygon:</span>
                      <span className="font-semibold text-blue-600 font-mono bg-blue-50 px-1.5 py-0.5 rounded">
                        {room.polygonName || "CS_BLOCK"}
                      </span>
                    </div>

                    <hr className="my-3.5 border-slate-100" />

                    {/* Calibrated APs Section */}
                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs font-semibold text-slate-800">
                        <div className="flex items-center gap-1.5">
                          <Wifi className="w-3.5 h-3.5 text-blue-600" />
                          <span>Top 3 Calibrated Wi-Fi Routers (&gt; -75 dBm)</span>
                        </div>
                      </div>

                      {hasAps ? (
                        <div className="space-y-1.5">
                          {room.calibratedAps.slice(0, 3).map((ap, idx) => (
                            <div 
                              key={idx}
                              className="bg-slate-50 border border-slate-100 rounded-xl p-2 flex items-center justify-between text-[11px]"
                            >
                              <div className="flex items-center gap-2">
                                <span className="w-4 h-4 rounded-full bg-blue-100 text-blue-700 text-[10px] font-bold flex items-center justify-center">
                                  {idx + 1}
                                </span>
                                <div>
                                  <span className="font-mono text-slate-900 font-semibold block">{ap.bssid}</span>
                                  <span className="text-[10px] text-slate-500">{ap.ssid}</span>
                                </div>
                              </div>
                              <div className="flex items-center gap-1.5">
                                <span className={`text-[10px] font-bold font-mono px-1.5 py-0.5 rounded ${
                                  ap.rssi >= -60 ? "bg-emerald-100 text-emerald-800" :
                                  ap.rssi >= -70 ? "bg-blue-100 text-blue-800" : "bg-amber-100 text-amber-800"
                                }`}>
                                  {ap.rssi} dBm
                                </span>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="bg-amber-50/70 border border-dashed border-amber-200 rounded-xl p-3 text-center">
                          <p className="text-xs text-amber-800 font-medium">No APs calibrated yet</p>
                          <p className="text-[11px] text-amber-600 mt-0.5">Teacher must open Android App in {room.name} and tap &quot;Calibrate Room Wi-Fi&quot;.</p>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Card Footer */}
                  <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                    <span className="text-[11px] text-slate-400">
                      {room.isCalibrated ? (room.calibratedAt || "Calibrated & Secured") : "Capacity: " + room.capacity + " seats"}
                    </span>
                    <button 
                      onClick={() => {
                        const simulatedAps: CalibratedAP[] = [
                          { bssid: "A4:2B:B0:8C:12:01", ssid: "Campus-Secure", rssi: -53, rank: 1 },
                          { bssid: "A4:2B:B0:8C:12:02", ssid: "Campus-Secure (5GHz)", rssi: -61, rank: 2 },
                          { bssid: "A4:2B:B0:8C:12:03", ssid: "Campus-Secure (Mesh)", rssi: -69, rank: 3 }
                        ];
                        setClassrooms(prev => prev.map(c => c.id === room.id ? {
                          ...c,
                          isCalibrated: true,
                          calibratedAps: simulatedAps,
                          calibratedAt: "Just now (Web Sync)"
                        } : c));
                      }}
                      className="text-blue-600 hover:text-blue-800 font-semibold hover:underline"
                    >
                      {room.isCalibrated ? "Re-sync" : "Simulate Calibration"}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: BUILDING POLYGONS (GEOJSON.IO)                                     */}
      {/* ========================================================================= */}
      {activeTab === "polygons" && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* GeoJSON Input Form */}
          <div className="lg:col-span-5 bg-white border border-slate-200 rounded-2xl p-6 space-y-4 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-slate-900">Define Building Polygon</h3>
                <p className="text-xs text-slate-500">Draw building rooftop boundary on geojson.io and paste here</p>
              </div>
              <a 
                href="https://geojson.io" 
                target="_blank" 
                rel="noreferrer"
                className="text-xs text-blue-600 font-semibold hover:underline flex items-center gap-1"
              >
                <span>Open geojson.io</span>
                <ExternalLink className="w-3 h-3" />
              </a>
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-700 block mb-1">Polygon Identifier / Tag</label>
              <input
                type="text"
                value={polygonNameInput}
                onChange={(e) => setPolygonNameInput(e.target.value)}
                placeholder="e.g. CS_BLOCK, AC_COMPLEX"
                className="w-full text-xs font-mono font-semibold px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-700 block mb-1">Building Description</label>
              <input
                type="text"
                value={polygonDescInput}
                onChange={(e) => setPolygonDescInput(e.target.value)}
                placeholder="e.g. Computer Science Rooftop Boundary"
                className="w-full text-xs px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-700 block mb-1">GeoJSON Polygon Coordinates</label>
              <textarea
                rows={9}
                value={polygonGeoJsonInput}
                onChange={(e) => setPolygonGeoJsonInput(e.target.value)}
                className="w-full text-[11px] font-mono px-3 py-2 bg-slate-900 text-emerald-400 border border-slate-800 rounded-xl focus:outline-none"
              />
            </div>

            {polygonSaveMsg && (
              <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-3 text-xs text-emerald-800 font-medium flex items-center gap-2">
                <Check className="w-4 h-4 text-emerald-600" />
                <span>{polygonSaveMsg}</span>
              </div>
            )}

            <button
              onClick={handleSavePolygon}
              className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl shadow-md shadow-blue-500/20 transition-all flex items-center justify-center gap-2"
            >
              <Check className="w-4 h-4" />
              <span>Save & Link Polygon to Database</span>
            </button>
          </div>

          {/* Active Polygons List */}
          <div className="lg:col-span-7 space-y-4">
            <h3 className="text-sm font-bold text-slate-900">Active Building Polygons in Database</h3>
            
            <div className="space-y-4">
              {polygons.map((poly) => (
                <div 
                  key={poly.id}
                  className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono font-bold px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 border border-blue-200">
                        {poly.name}
                      </span>
                      <span className="text-xs text-slate-500 font-medium">
                        {poly.coordinatesCount} Boundary Vertices
                      </span>
                    </div>
                    <span className="text-[11px] text-slate-400 font-mono">
                      {poly.createdAt}
                    </span>
                  </div>

                  <p className="text-xs text-slate-600">{poly.description}</p>

                  <div className="bg-slate-900 text-slate-300 p-3 rounded-xl font-mono text-[10px] overflow-x-auto max-h-36">
                    <pre>{poly.geojsonString}</pre>
                  </div>

                  <div className="flex items-center justify-between pt-2 text-xs">
                    <span className="text-slate-500">
                      Linked Rooms: <strong className="text-slate-800 font-mono">Room 101, Room A-204, Room A-302</strong>
                    </span>
                    <button 
                      onClick={() => {
                        setPolygonNameInput(poly.name);
                        setPolygonDescInput(poly.description);
                        setPolygonGeoJsonInput(poly.geojsonString);
                      }}
                      className="text-blue-600 font-semibold hover:underline"
                    >
                      Load into Editor
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

"use client";

import { LiveWifiSearchSelector } from "@/components/LiveWifiSearchSelector";

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { 
  Users, 
  PlusCircle, 
  Play, 
  Copy, 
  Check, 
  ArrowLeft, 
  BookOpen, 
  QrCode, 
  Send, 
  UserCheck, 
  UserX, 
  Clock, 
  MapPin, 
  Wifi, 
  Crosshair, 
  ShieldCheck, 
  RefreshCw, 
  ChevronDown, 
  Edit2, 
  Signal, 
  CheckCircle2, 
  LogOut, 
  Download, 
  AlertTriangle, 
  Radio, 
  Sparkles, 
  ExternalLink, 
  Database,
  Archive,
  Layers,
  History,
  Shield,
  Activity,
  CheckCheck,
  Calendar,
  X,
  ChevronRight,
  TrendingUp,
  Award,
  RotateCcw
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { 
  getClassroomAnchor, 
  setClassroomAnchor, 
  getGeofenceMode, 
  setGeofenceMode, 
  GeofenceMode, 
  DEFAULT_CLASSROOM_LATITUDE, 
  DEFAULT_CLASSROOM_LONGITUDE 
} from "@/lib/geoFence";
import { supabase } from "@/lib/supabaseClient";
import { 
  fetchLiveClassesFromDB, 
  getActiveSessionFromDB, 
  startAttendanceSessionInDB, 
  createClassInDB, 
  fetchSessionRecordsFromDB, 
  finalizeSessionInDB,
  recordStudentAttendanceInDB,
  fetchPendingUnbindRequestsFromDB,
  approveDeviceUnbindInDB,
  updateLiveSessionWifiInDB,
  fetchCompletedSessionsFromDB,
  DBCompletedSession,
  BoundDevice, 
  DBClass, 
  DBStudent, 
  DBSession 
} from "@/lib/attendanceService";

interface NetworkStatus {
  interface: string;
  localIp: string;
  gateway: string;
  subnet: string;
  detectedSsid: string;
  detectedWifiName: string;
  nearbyNetworks?: string[];
}

function mergeRosterWithRecords(
  enrolled: DBStudent[],
  records: DBStudent[],
  overrides: Record<string, "PRESENT" | "ABSENT"> = {}
): DBStudent[] {
  const recordsByRoll = new Map<string, DBStudent>();
  for (const r of (records || [])) {
    const key = (r.rollNo || "").toUpperCase().trim();
    if (key) {
      recordsByRoll.set(key, r);
    }
  }

  const seenRolls = new Set<string>();
  const merged: DBStudent[] = [];

  for (const student of (enrolled || [])) {
    const rollKey = (student.rollNo || "").toUpperCase().trim();
    if (!rollKey || seenRolls.has(rollKey)) continue;
    seenRolls.add(rollKey);

    const live = recordsByRoll.get(rollKey);
    const manualStatus = (student.id ? overrides[student.id] : undefined) || (rollKey ? overrides[rollKey] : undefined);

    if (live) {
      merged.push({
        ...student,
        ...live,
        status: manualStatus || live.status || "PRESENT"
      });
    } else {
      merged.push({
        ...student,
        status: manualStatus || "ABSENT",
        wifiStatus: "DISCONNECTED",
        detectedWifi: "Waiting for Wi-Fi...",
        distanceMeters: undefined,
        verifiedAt: undefined
      });
    }
  }

  for (const [rollKey, live] of recordsByRoll.entries()) {
    if (seenRolls.has(rollKey)) continue;
    seenRolls.add(rollKey);

    const manualStatus = (live.id ? overrides[live.id] : undefined) || (rollKey ? overrides[rollKey] : undefined);
    merged.push({
      ...live,
      status: manualStatus || live.status || "PRESENT"
    });
  }

  return merged;
}

export default function TeacherPortal() {
  const router = useRouter();
  const [teacherName, setTeacherName] = useState("Dr. Rajesh Sharma");
  const [teacherEmail, setTeacherEmail] = useState("sharma_0251@iiitnr.edu.in");
  const [classes, setClasses] = useState<DBClass[]>([]);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  // Active Navigation Tab
  const [activeTab, setActiveTab] = useState<"live" | "classes" | "completed" | "security" | "radar">("live");

  // Hardware Live Networks
  const [detectedNetwork, setDetectedNetwork] = useState<NetworkStatus | null>(null);
  const [isDetectingNetwork, setIsDetectingNetwork] = useState(false);
  const [wifiPresets, setWifiPresets] = useState<string[]>([
    "IPG3 VVDH",
    "Pranjal",
    "FTTH",
    "FTTH-5G",
    "Aditya Jha",
    "DIR-615-FD5C",
    "OPPO A79 5G",
    "Locked out",
    "ACP Pradyuman",
    "Archer C20"
  ]);

  // Geofence & Anti-Proxy Mode
  const [geoMode, setGeoModeState] = useState<GeofenceMode>("demo_inside");
  const [anchorCoords, setAnchorCoords] = useState<{ lat: number; lon: number; isCustom: boolean }>({
    lat: DEFAULT_CLASSROOM_LATITUDE,
    lon: DEFAULT_CLASSROOM_LONGITUDE,
    isCustom: false
  });
  
  // Create Class Form State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [subjectName, setSubjectName] = useState("");
  const [subjectCode, setSubjectCode] = useState("");
  const [section, setSection] = useState("");
  const [roomNo, setRoomNo] = useState("Room A-204");
  const [wifiSsid, setWifiSsid] = useState("Pranjal");
  const [isCalibratingGps, setIsCalibratingGps] = useState(false);
  const [selectedQrClass, setSelectedQrClass] = useState<DBClass | null>(null);

  // Active Session State
  const [activeSession, setActiveSession] = useState<DBSession | null>(null);
  const [activeClass, setActiveClass] = useState<DBClass | null>(null);
  const [liveStudents, setLiveStudents] = useState<DBStudent[]>([]);
  const [sessionSubmitted, setSessionSubmitted] = useState(false);
  const [manualOverrides, setManualOverrides] = useState<Record<string, "PRESENT" | "ABSENT">>({});
  const manualOverridesRef = useRef<Record<string, "PRESENT" | "ABSENT">>({});
  manualOverridesRef.current = manualOverrides;
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Completed Sessions State
  const [completedSessions, setCompletedSessions] = useState<DBCompletedSession[]>([]);
  const [selectedCompletedSession, setSelectedCompletedSession] = useState<DBCompletedSession | null>(null);
  const [isLoadingCompleted, setIsLoadingCompleted] = useState(false);

  // Device Security & Unbind State
  const [showDeviceModal, setShowDeviceModal] = useState(false);
  const [unbindRequests, setUnbindRequests] = useState<BoundDevice[]>([]);
  const [loadingUnbind, setLoadingUnbind] = useState(false);

  const loadPendingUnbindRequests = async () => {
    setLoadingUnbind(true);
    try {
      const list = await fetchPendingUnbindRequestsFromDB();
      setUnbindRequests(list);
    } catch (e) {} finally {
      setLoadingUnbind(false);
    }
  };

  const loadCompletedSessions = async () => {
    setIsLoadingCompleted(true);
    try {
      const list = await fetchCompletedSessionsFromDB();
      setCompletedSessions(list);
    } catch (e) {
      console.warn("Could not load completed sessions:", e);
    } finally {
      setIsLoadingCompleted(false);
    }
  };

  const handleApproveUnbind = async (devId: string, studentName: string, roll: string) => {
    const ok = await approveDeviceUnbindInDB(devId);
    if (ok) {
      showToast(`✅ Device unbound for ${studentName} (${roll}). Student can now bind new phone.`);
      await loadPendingUnbindRequests();
    } else {
      showToast("Could not unbind device. Try again.");
    }
  };

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const handleGoogleSignIn = async () => {
    try {
      const redirectOrigin = typeof window !== "undefined" ? window.location.origin : "https://admin-web-peach-one.vercel.app";
      await supabase.auth.signInWithOAuth({
        provider: "google",
        options: {
          redirectTo: `${redirectOrigin}/teacher`
        }
      });
    } catch (e) {
      console.error("Google faculty sign in error:", e);
    }
  };

  const handleLogout = async () => {
    try {
      await supabase.auth.signOut();
    } catch (e) {}
    localStorage.removeItem("smart_attendance_active_session_code");
    showToast("Logged out successfully");
    setTimeout(() => {
      router.push("/");
    }, 400);
  };

  // Detect real hardware network
  const fetchRealNetworkStatus = async () => {
    setIsDetectingNetwork(true);
    try {
      const res = await fetch("/api/network-status");
      const data = await res.json();
      if (data.success) {
        setDetectedNetwork(data);
        const liveSsid = data.detectedSsid || "Pranjal";
        setWifiSsid(liveSsid);
        if (data.nearbyNetworks && data.nearbyNetworks.length > 0) {
          setWifiPresets(data.nearbyNetworks);
        }
      }
    } catch (e) {
      console.warn("Network status error:", e);
    } finally {
      setIsDetectingNetwork(false);
    }
  };

  const loadDatabaseData = async () => {
    const dbClasses = await fetchLiveClassesFromDB();
    let customList: DBClass[] = [];
    try {
      const stored = localStorage.getItem("smart_attendance_teacher_custom_classes");
      if (stored) customList = JSON.parse(stored);
    } catch (e) {}

    const combined = [...customList, ...dbClasses];
    if (combined.length > 0) {
      setClasses(combined);
    }

    const active = await getActiveSessionFromDB();
    if (active) {
      const savedWifi = localStorage.getItem("smart_attendance_active_wifi_ssid");
      if (savedWifi) {
        active.wifiSsid = savedWifi;
      }
      setActiveSession(active);
      const matched = combined.find(c => c.id === active.classId);
      if (matched) {
        setActiveClass(matched);
      }
      const records = await fetchSessionRecordsFromDB(active.id);
      const enrolled = matched?.students || [];
      const merged = mergeRosterWithRecords(enrolled, records, manualOverridesRef.current);
      setLiveStudents(merged);
    }
  };

  useEffect(() => {
    try { localStorage.removeItem("smart_attendance_global_classes"); } catch (e) {}
    fetchRealNetworkStatus();
    loadDatabaseData();
    loadPendingUnbindRequests();
    loadCompletedSessions();

    supabase.auth.getSession().then(({ data: { session } }) => {
      if (session?.user) {
        const metadata = session.user.user_metadata || {};
        const name = metadata.full_name || metadata.name || "Dr. Rajesh Sharma";
        const email = session.user.email || "sharma_0251@iiitnr.edu.in";
        setTeacherName(name);
        setTeacherEmail(email);
      }
    });

    const { data: authSub } = supabase.auth.onAuthStateChange((event, session) => {
      if (session?.user) {
        const metadata = session.user.user_metadata || {};
        const name = metadata.full_name || metadata.name || "Dr. Rajesh Sharma";
        const email = session.user.email || "sharma_0251@iiitnr.edu.in";
        setTeacherName(name);
        setTeacherEmail(email);
        showToast(`Signed in via Google as ${name}`);
      }
    });

    setGeoModeState(getGeofenceMode());
    const anchor = getClassroomAnchor();
    setAnchorCoords(anchor);
  }, []);

  useEffect(() => {
    if (!activeSession) return;

    const refreshRecords = async () => {
      const dbRecords = await fetchSessionRecordsFromDB(activeSession.id);
      setActiveClass(prevClass => {
        const enrolled = prevClass?.students || [];
        setLiveStudents(mergeRosterWithRecords(enrolled, dbRecords, manualOverridesRef.current));
        return prevClass;
      });
    };

    refreshRecords();
    const interval = setInterval(refreshRecords, 1000);

    const channel = supabase
      .channel("teacher-live-attendance-" + activeSession.id)
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "attendance_records"
        },
        async () => {
          const dbRecords = await fetchSessionRecordsFromDB(activeSession.id);
          setActiveClass(prevClass => {
            const enrolled = prevClass?.students || [];
            setLiveStudents(mergeRosterWithRecords(enrolled, dbRecords, manualOverridesRef.current));
            return prevClass;
          });
        }
      )
      .subscribe();

    return () => {
      clearInterval(interval);
      supabase.removeChannel(channel);
    };
  }, [activeSession]);

  const handleRealTimeWifiChange = async (newSsid: string) => {
    if (!newSsid.trim()) return;
    const trimmed = newSsid.trim();
    if (activeSession) {
      setActiveSession(prev => prev ? { ...prev, wifiSsid: trimmed } : null);
      await updateLiveSessionWifiInDB(activeSession.id, trimmed);
    }
    setWifiSsid(trimmed);
    localStorage.setItem("smart_attendance_active_wifi_ssid", trimmed);
    showToast(`⚡ Live Classroom Wi-Fi switched to "${trimmed}" across all devices!`);
  };

  const handleCreateClass = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!subjectName.trim()) return;

    let created: DBClass | null = null;
    try {
      created = await createClassInDB({
        subjectName,
        subjectCode: subjectCode.trim() || undefined,
        roomNo: roomNo || "Lab 3 (Computing Center)",
        wifiSsid: wifiSsid || "Pranjal"
      });
    } catch (e) {
      console.warn("DB Create Class Warning:", e);
    }

    const randHex = Math.random().toString(36).substring(2, 6).toUpperCase();
    const finalCode = (subjectCode.trim() || "CS" + Math.floor(100 + Math.random() * 900)) + "-" + randHex;

    const finalClass: DBClass = created || {
      id: "cls-" + Date.now(),
      subjectCode: subjectCode.trim() || "CS" + Math.floor(100 + Math.random() * 900),
      subjectName: subjectName.trim(),
      section: section.trim() || "Batch A",
      joinCode: finalCode,
      roomNo: roomNo || "Lab 3 (Computing Center)",
      wifiSsid: wifiSsid || "Pranjal",
      latitude: anchorCoords.lat,
      longitude: anchorCoords.lon,
      teacherId: "teacher-rajesh",
      teacherName,
      students: [
        {
          id: "st-demo-1",
          name: "Pooja Verma",
          rollNo: "26CS042",
          email: "26cs042@student.iiitnr.edu.in",
          status: "PRESENT",
          distanceMeters: 3.4,
          wifiStatus: "CONNECTED",
          detectedWifi: wifiSsid || "Pranjal",
          verifiedAt: "Zero-Touch Wi-Fi Verified",
          joinedAt: new Date().toISOString()
        },
        {
          id: "st-demo-2",
          name: "Pranjal",
          rollNo: "26CS015",
          email: "26cs015@student.iiitnr.edu.in",
          status: "PRESENT",
          distanceMeters: 3.8,
          wifiStatus: "CONNECTED",
          detectedWifi: wifiSsid || "Pranjal",
          verifiedAt: "Zero-Touch Wi-Fi Verified",
          joinedAt: new Date().toISOString()
        }
      ],
      createdAt: new Date().toISOString()
    };

    setClasses(prev => [finalClass, ...prev]);
    try {
      const stored = localStorage.getItem("smart_attendance_teacher_custom_classes");
      const list = stored ? JSON.parse(stored) : [];
      localStorage.setItem("smart_attendance_teacher_custom_classes", JSON.stringify([finalClass, ...list]));
    } catch (e) {}

    setShowCreateModal(false);
    setSubjectName("");
    setSubjectCode("");
    setSection("");

    await handleStartAttendance(finalClass);
    showToast(`⚡ Class "${finalClass.subjectName}" is now LIVE with Join Code: ${finalClass.joinCode}!`);
  };

  const handleStartAttendance = async (cls: DBClass, isRetake: boolean = false) => {
    // 1 Lecture = 1 Attendance per Day safeguard
    if (!isRetake) {
      const todayDone = completedSessions.find(s => {
        if (s.classId !== cls.id && s.subjectCode !== cls.subjectCode) return false;
        const sessDate = new Date(s.endTime || s.startTime).toDateString();
        return sessDate === new Date().toDateString();
      });

      if (todayDone) {
        const confirmRetake = window.confirm(
          `⚠️ Attendance for "${cls.subjectName}" is already completed for today (${todayDone.presentCount} present students).

Do you want to re-take attendance for this lecture?`
        );
        if (!confirmRetake) return;
      }
    }
    const targetWifi = cls.wifiSsid || wifiSsid || "Pranjal";
    const session = await startAttendanceSessionInDB(cls.id, cls.teacherId, targetWifi);
    
    const currentSession: DBSession = session || {
      id: "sess-" + Date.now(),
      classId: cls.id,
      teacherId: cls.teacherId,
      status: "ACTIVE",
      startTime: new Date().toISOString(),
      joinCode: cls.joinCode,
      subjectCode: cls.subjectCode,
      subjectName: cls.subjectName,
      roomNo: cls.roomNo,
      wifiSsid: cls.wifiSsid || "Pranjal"
    };

    if (cls.wifiSsid) {
      currentSession.wifiSsid = cls.wifiSsid;
    }

    setActiveSession(currentSession);
    setActiveClass(cls);
    setManualOverrides({});
    manualOverridesRef.current = {};
    const initialRoster = mergeRosterWithRecords(cls.students || [], [], {});
    setLiveStudents(initialRoster);
    setSessionSubmitted(false);

    if (cls.students && cls.students.length > 0) {
      for (const st of cls.students) {
        recordStudentAttendanceInDB({
          sessionId: currentSession.id,
          rollNo: st.rollNo,
          name: st.name,
          email: st.email || `${st.rollNo}@student.iiitnr.edu.in`,
          status: "PRESENT",
          distanceMeters: 3.4,
          wifiSsid: currentSession.wifiSsid || "Pranjal",
          isWifiMatched: true
        }).catch(err => console.warn("Zero-Touch attendance sync warning:", err));
      }
    }

    localStorage.setItem("smart_attendance_active_session_code", currentSession.joinCode);
    localStorage.setItem("smart_attendance_active_class_id", currentSession.classId);
    localStorage.setItem("smart_attendance_active_wifi_ssid", currentSession.wifiSsid);
    localStorage.setItem("smart_attendance_active_room", currentSession.roomNo);

    setActiveTab("live");
    showToast(`⚡ Live Attendance Active for ${cls.subjectCode} (${cls.joinCode})`);
  };

  const handleCopyCode = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    showToast(`Join Code "${code}" copied!`);
    setTimeout(() => setCopiedCode(null), 2500);
  };

  const handleToggleStudentStatus = async (studentId: string) => {
    const student = liveStudents.find(s => s.id === studentId || s.rollNo === studentId);
    if (!student || !activeSession) return;
    const next = student.status === "PRESENT" ? "ABSENT" : "PRESENT";

    // Immediate optimistic update
    setManualOverrides(ov => ({ ...ov, [studentId]: next, [student.rollNo]: next }));
    manualOverridesRef.current = { ...manualOverridesRef.current, [studentId]: next, [student.rollNo]: next };

    setLiveStudents(prev => prev.map(s => {
      if (s.id === studentId || s.rollNo === studentId) {
        return { 
          ...s, 
          status: next as "PRESENT" | "ABSENT",
          verifiedAt: next === "PRESENT" ? (s.verifiedAt || "Faculty Verified") : undefined
        };
      }
      return s;
    }));

    showToast(`Marked ${student.name} as ${next}`);

    // Persist to Supabase so polling never reverts it
    try {
      const { data: rec } = await supabase
        .from("attendance_records")
        .select("id")
        .eq("session_id", activeSession.id)
        .eq("student_id", student.id)
        .maybeSingle();

      if (rec?.id) {
        await supabase
          .from("attendance_records")
          .update({
            status: next,
            verification_method: "MANUAL_TEACHER",
            marked_at: new Date().toISOString()
          })
          .eq("id", rec.id);
      } else {
        await supabase
          .from("attendance_records")
          .insert({
            session_id: activeSession.id,
            student_id: student.id,
            status: next,
            verification_method: "MANUAL_TEACHER",
            wifi_ap_verified: next === "PRESENT",
            rtt_distance_meters: student.distanceMeters || 3.4,
            presence_percentage: next === "PRESENT" ? 100.0 : 0.0,
            marked_at: new Date().toISOString(),
            sensor_details: {
              student_name: student.name,
              roll_number: student.rollNo,
              wifi_ssid: activeSession.wifiSsid,
              timestamp: new Date().toISOString()
            }
          });
      }
    } catch (e) {
      console.warn("Could not persist manual toggle to DB:", e);
    }
  };

  // FINALIZE LECTURE AND MOVE TO COMPLETED ARCHIVE SECTION
  const handleFinishLecture = async () => {
    if (!activeSession) return;
    const finishedTitle = activeSession.subjectName;
    await finalizeSessionInDB(activeSession.id);
    setSessionSubmitted(true);
    localStorage.removeItem("smart_attendance_active_session_code");
    
    showToast(`✓ "${finishedTitle}" attendance submitted & moved to Completed Archives!`);
    
    setActiveSession(null);
    setActiveClass(null);
    await loadDatabaseData();
    await loadCompletedSessions();
    setActiveTab("completed");
  };

  const handleExportCSV = () => {
    if (!activeSession) return;
    const headers = ["Roll No", "Student Name", "Status", "Distance (m)", "Wi-Fi AP", "Verified Time"];
    const rows = liveStudents.map(s => [
      s.rollNo,
      `"${s.name}"`,
      s.status,
      s.distanceMeters || "3.4",
      s.detectedWifi || activeSession.wifiSsid,
      s.verifiedAt || "10:02 AM"
    ]);

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map(e => e.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `Attendance_${activeSession.subjectCode}_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast("📥 Attendance CSV Report Downloaded!");
  };

  const handleExportCompletedCSV = (sess: DBCompletedSession) => {
    const headers = ["Roll No", "Student Name", "Status", "Verified Time", "Wi-Fi Verified"];
    const rows = sess.records.map(r => [
      r.rollNo,
      `"${r.name}"`,
      r.status,
      r.verifiedAt ? new Date(r.verifiedAt).toLocaleTimeString() : "Verified",
      r.wifiVerified ? "YES" : "NO"
    ]);

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map(e => e.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `Attendance_${sess.subjectCode}_${sess.id.slice(0, 6)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast(`📥 Exported CSV for ${sess.subjectCode}!`);
  };

  const presentCount = liveStudents.filter(s => s.status === "PRESENT").length;
  const absentCount = liveStudents.filter(s => s.status === "ABSENT").length;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex font-sans selection:bg-blue-500 selection:text-white">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-50 bg-blue-600/95 backdrop-blur-md text-white px-5 py-2.5 rounded-2xl shadow-2xl border border-blue-200 text-xs font-bold tracking-wide flex items-center gap-2 animate-in fade-in slide-in-from-top-3">
          <Sparkles className="w-4 h-4 text-blue-500" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ==================== LEFT SIDEBAR ==================== */}
      <aside className="w-64 shrink-0 bg-white border-r border-slate-200 shadow-sm flex flex-col justify-between hidden md:flex sticky top-0 h-screen z-30">
        <div className="p-5 space-y-6 overflow-y-auto">
          {/* Logo & Branding */}
          <div className="flex items-center gap-3 pb-4 border-b border-slate-200">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-blue-600 to-blue-700 flex items-center justify-center text-white font-black text-lg shadow-lg shadow-blue-500/20">
              IIIT
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-extrabold text-sm tracking-tight text-white">IIIT Naya Raipur</span>
              </div>
              <p className="text-[11px] text-slate-600 font-medium">Smart Attendance OS</p>
            </div>
          </div>

          {/* Real-time DB Engine Status */}
          <div className="p-3 rounded-2xl bg-slate-50/70 border border-slate-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
              <span className="text-[11px] font-bold text-slate-700">Supabase Engine</span>
            </div>
            <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-bold uppercase">
              Online
            </span>
          </div>

          {/* Navigation Links */}
          <div className="space-y-1.5">
            <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider px-3 mb-2">
              Faculty Console
            </div>

            {/* 1. Live Lecture */}
            <button
              onClick={() => setActiveTab("live")}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === "live"
                  ? "bg-blue-600 text-white shadow-sm font-semibold"
                  : "text-slate-600 hover:text-white hover:bg-slate-100"
              }`}
            >
              <div className="flex items-center gap-3">
                <Radio className={`w-4 h-4 ${activeSession ? "text-emerald-400 animate-pulse" : ""}`} />
                <span>Live Lecture</span>
              </div>
              {activeSession ? (
                <span className="text-[9px] font-extrabold px-1.5 py-0.5 rounded-full bg-emerald-500 text-slate-950 uppercase tracking-wider animate-pulse">
                  ACTIVE
                </span>
              ) : (
                <span className="text-[10px] text-slate-500 font-mono">Idle</span>
              )}
            </button>

            {/* 2. Classes & Batches */}
            <button
              onClick={() => setActiveTab("classes")}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === "classes"
                  ? "bg-blue-600 text-white shadow-sm font-semibold"
                  : "text-slate-600 hover:text-white hover:bg-slate-100"
              }`}
            >
              <div className="flex items-center gap-3">
                <BookOpen className="w-4 h-4" />
                <span>Classes & Batches</span>
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-700">
                {classes.length}
              </span>
            </button>

            {/* 3. Completed Lectures (NEW DEDICATED SECTION) */}
            <button
              onClick={() => setActiveTab("completed")}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === "completed"
                  ? "bg-emerald-600 text-white shadow-lg shadow-emerald-600/30"
                  : "text-slate-600 hover:text-white hover:bg-slate-100"
              }`}
            >
              <div className="flex items-center gap-3">
                <CheckCheck className="w-4 h-4 text-emerald-400" />
                <span>Completed Lectures</span>
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                {completedSessions.length}
              </span>
            </button>

            {/* 4. Device Security */}
            <button
              onClick={() => setActiveTab("security")}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === "security"
                  ? "bg-blue-600 text-white shadow-sm font-semibold"
                  : "text-slate-600 hover:text-white hover:bg-slate-100"
              }`}
            >
              <div className="flex items-center gap-3">
                <ShieldCheck className="w-4 h-4" />
                <span>Device Security</span>
              </div>
              {unbindRequests.length > 0 && (
                <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full bg-amber-500 text-slate-950 font-mono animate-bounce">
                  {unbindRequests.length}
                </span>
              )}
            </button>

            {/* 5. Wi-Fi & Sensor Radar */}
            <button
              onClick={() => setActiveTab("radar")}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                activeTab === "radar"
                  ? "bg-blue-600 text-white shadow-sm font-semibold"
                  : "text-slate-600 hover:text-white hover:bg-slate-100"
              }`}
            >
              <div className="flex items-center gap-3">
                <Signal className="w-4 h-4 text-cyan-400" />
                <span>Wi-Fi & AP Radar</span>
              </div>
              <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-cyan-500/10 text-cyan-400">
                {wifiSsid}
              </span>
            </button>
          </div>

          {/* Quick Wi-Fi Widget in Sidebar */}
          <div className="p-3.5 rounded-2xl bg-gradient-to-br from-slate-950 to-slate-900 border border-slate-200 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold text-slate-600 uppercase tracking-wider flex items-center gap-1.5">
                <Wifi className="w-3.5 h-3.5 text-cyan-400" />
                Classroom AP
              </span>
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
            </div>
            <div className="text-xs font-extrabold text-white truncate">
              {activeSession ? activeSession.wifiSsid : wifiSsid}
            </div>
            <p className="text-[10px] text-slate-500">
              Anti-spoof hardware BSSID verified
            </p>
          </div>
        </div>

        {/* Sidebar Footer: Profile & Logout */}
        <div className="p-4 border-t border-slate-200 bg-slate-50/50 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 text-white font-bold text-xs flex items-center justify-center shrink-0">
                RS
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-white truncate">{teacherName}</p>
                <p className="text-[10px] text-slate-600 truncate font-mono">{teacherEmail}</p>
              </div>
            </div>
            <button
              onClick={handleLogout}
              title="Sign Out"
              className="p-1.5 rounded-lg text-slate-600 hover:text-rose-400 hover:bg-slate-100 transition-all cursor-pointer"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* ==================== MAIN CONTENT AREA ==================== */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Top Header Bar */}
        <header className="border-b border-slate-200 bg-white/60 backdrop-blur-xl sticky top-0 z-20 px-4 sm:px-8 py-3.5 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            {/* Mobile Sidebar Navigation Pills */}
            <div className="flex md:hidden gap-1 overflow-x-auto py-1">
              {[
                { id: "live", label: "Live", icon: Radio },
                { id: "classes", label: "Classes", icon: BookOpen },
                { id: "completed", label: "Done", icon: CheckCheck },
                { id: "security", label: "Security", icon: ShieldCheck }
              ].map(item => (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id as any)}
                  className={`px-3 py-1 rounded-xl text-xs font-bold flex items-center gap-1.5 cursor-pointer ${
                    activeTab === item.id ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-600"
                  }`}
                >
                  <item.icon className="w-3.5 h-3.5" />
                  <span>{item.label}</span>
                </button>
              ))}
            </div>

            <div className="hidden md:block">
              <div className="flex items-center gap-2 text-xs text-slate-600 font-medium">
                <span>Console</span>
                <span>/</span>
                <span className="text-white font-bold capitalize">
                  {activeTab === "live" ? "Live Lecture Console" : 
                   activeTab === "classes" ? "Classes & Course Batches" : 
                   activeTab === "completed" ? "Completed Attendance Archive" : 
                   activeTab === "security" ? "Device Security Center" : "Sensor & AP Radar"}
                </span>
              </div>
              <h1 className="text-lg font-black text-white tracking-tight">
                {activeTab === "live" ? "Lecture Attendance Radar" : 
                 activeTab === "classes" ? "Scheduled Batches" : 
                 activeTab === "completed" ? "Completed Lectures & Archives" : 
                 activeTab === "security" ? "Hardware Device Locks" : "Hardware Wi-Fi Telemetry"}
              </h1>
            </div>
          </div>

          {/* Quick Actions in Header */}
          <div className="flex items-center gap-2 sm:gap-3">
            {/* Target Wi-Fi Selector with Live Search */}
            <LiveWifiSearchSelector
              currentWifi={activeSession ? activeSession.wifiSsid : wifiSsid}
              onSelectWifi={handleRealTimeWifiChange}
              wifiList={wifiPresets}
              onScanNearby={fetchRealNetworkStatus}
              isScanning={isDetectingNetwork}
              variant="header"
            />

            {/* Create Class Button */}
            <button
              onClick={() => setShowCreateModal(true)}
              className="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs flex items-center gap-1.5 shadow-md shadow-indigo-600/20 transition-all cursor-pointer"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">New Batch</span>
            </button>


            {/* Student Console Link */}
            <Link
              href="/student"
              target="_blank"
              className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 hover:text-white font-bold text-xs flex items-center gap-1.5 transition-all border border-slate-200"
            >
              <ExternalLink className="w-3.5 h-3.5 text-blue-400" />
              <span className="hidden sm:inline">Student View</span>
            </Link>
          </div>
        </header>

        {/* Dashboard Body Content */}
        <main className="p-4 sm:p-8 space-y-8 max-w-7xl w-full mx-auto">

          {/* ==================== TAB 1: LIVE LECTURE ==================== */}
          {activeTab === "live" && (
            <div className="space-y-6">
              {activeSession ? (
                /* LIVE SESSION ACTIVE CARD */
                <div className="p-6 sm:p-8 rounded-3xl bg-gradient-to-b from-slate-900/90 to-slate-950 border border-blue-200 shadow-2xl space-y-6 relative overflow-hidden backdrop-blur-xl">
                  {/* Decorative glowing gradient backdrop */}
                  <div className="absolute top-0 right-0 w-96 h-96 bg-blue-600/10 rounded-full blur-3xl pointer-events-none -mr-20 -mt-20"></div>

                  {/* Header Row */}
                  <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 relative z-10">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-extrabold text-[11px] tracking-wide uppercase">
                          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
                          Live Lecture Active
                        </span>
                        <span className="text-xs text-slate-600 font-medium">
                          • {activeSession.roomNo}
                        </span>
                      </div>
                      <h2 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                        {activeSession.subjectName}
                      </h2>
                      <p className="text-xs font-mono text-blue-600">
                        {activeSession.subjectCode} • Started at {new Date(activeSession.startTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                      </p>
                    </div>

                    {/* Join Code Capsule */}
                    <div className="flex items-center gap-3 bg-slate-50/80 border border-slate-200 p-2.5 rounded-2xl shadow-xl">
                      <div className="px-3">
                        <span className="text-[10px] text-slate-600 uppercase font-bold tracking-wider block">Student Join Code</span>
                        <span className="text-xl font-mono font-black text-white tracking-wider">
                          {activeSession.joinCode}
                        </span>
                      </div>
                      <button
                        onClick={() => handleCopyCode(activeSession.joinCode)}
                        className="p-2.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 hover:text-white transition-all cursor-pointer"
                        title="Copy Code"
                      >
                        {copiedCode === activeSession.joinCode ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                      </button>
                      <button
                        onClick={() => setSelectedQrClass(activeClass)}
                        className="p-2.5 rounded-xl bg-blue-600/30 hover:bg-blue-600/50 text-blue-600 transition-all cursor-pointer border border-blue-200"
                        title="Show QR Code"
                      >
                        <QrCode className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {/* Target Wi-Fi Banner */}
                  <div className="p-3.5 rounded-2xl bg-slate-50/60 border border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                    <div className="flex items-center gap-2 text-slate-700">
                      <Wifi className="w-4 h-4 text-cyan-400" />
                      <span>Classroom Broadcast Wi-Fi:</span>
                      <span className="font-extrabold text-cyan-300 px-2 py-0.5 rounded-lg bg-cyan-500/10 border border-cyan-500/20">
                        {activeSession.wifiSsid}
                      </span>
                      <span className="text-[10px] text-emerald-400 font-mono">LIVE SYNC</span>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="text-slate-600">Switch AP:</span>
                      <LiveWifiSearchSelector
                        currentWifi={activeSession.wifiSsid}
                        onSelectWifi={handleRealTimeWifiChange}
                        wifiList={wifiPresets}
                        onScanNearby={fetchRealNetworkStatus}
                        isScanning={isDetectingNetwork}
                        variant="inline"
                      />
                    </div>
                  </div>

                  {/* 3 Metric KPI Cards */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div className="p-4 rounded-2xl bg-slate-50/70 border border-slate-200 text-center">
                      <span className="text-[10px] uppercase font-bold text-slate-600 tracking-wider block">Enrolled Students</span>
                      <span className="text-3xl font-black text-white mt-1 block">
                        {liveStudents.length}
                      </span>
                    </div>

                    <div className="p-4 rounded-2xl bg-emerald-950/30 border border-emerald-500/30 text-center">
                      <span className="text-[10px] uppercase font-bold text-emerald-400 tracking-wider block">Present / Verified</span>
                      <span className="text-3xl font-black text-emerald-400 mt-1 block">
                        {presentCount}
                      </span>
                    </div>

                    <div className="p-4 rounded-2xl bg-rose-950/30 border border-rose-500/30 text-center">
                      <span className="text-[10px] uppercase font-bold text-rose-400 tracking-wider block">Absent / Pending</span>
                      <span className="text-3xl font-black text-rose-400 mt-1 block">
                        {absentCount}
                      </span>
                    </div>
                  </div>

                  {/* Enrolled Live Roster Table */}
                  <div className="space-y-3">
                    <div className="flex items-center justify-between text-xs font-bold text-slate-700">
                      <span className="flex items-center gap-2">
                        <Users className="w-4 h-4 text-blue-600" />
                        <span>Enrolled Attendance Roster ({presentCount}/{liveStudents.length} Verified)</span>
                      </span>
                      <span className="text-[10px] font-mono text-emerald-400 flex items-center gap-1.5">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                        Real-time Live Sync
                      </span>
                    </div>

                    <div className="space-y-2">
                      {liveStudents.length === 0 ? (
                        <div className="p-8 text-center text-slate-500 text-xs">
                          Waiting for students to connect to classroom Wi-Fi...
                        </div>
                      ) : (
                        liveStudents.map(student => {
                          const isPresent = student.status === "PRESENT";
                          return (
                            <div 
                              key={student.id || student.rollNo}
                              className="p-3.5 rounded-2xl bg-slate-50/60 border border-slate-200 flex items-center justify-between gap-3 hover:border-slate-200 transition-all"
                            >
                              <div className="flex items-center gap-3">
                                <div className="w-9 h-9 rounded-xl bg-white border border-slate-200 shadow-sm text-slate-900 font-mono font-bold text-xs flex items-center justify-center shrink-0">
                                  {student.rollNo.slice(-3) || "ST"}
                                </div>
                                <div>
                                  <span className="text-xs font-bold text-white block">{student.name}</span>
                                  <span className="text-[10px] text-slate-600 font-mono">
                                    Roll: {student.rollNo} • {student.verifiedAt || "Zero-Touch Wi-Fi"}
                                  </span>
                                </div>
                              </div>

                              <button
                                onClick={() => handleToggleStudentStatus(student.id || student.rollNo)}
                                className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 cursor-pointer transition-all ${
                                  isPresent
                                    ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 hover:bg-rose-500/10 hover:text-rose-400 hover:border-rose-500/30"
                                    : "bg-rose-500/10 text-rose-400 border border-rose-500/30 hover:bg-emerald-500/10 hover:text-emerald-400 hover:border-emerald-500/30"
                                }`}
                              >
                                {isPresent ? <UserCheck className="w-3.5 h-3.5" /> : <UserX className="w-3.5 h-3.5" />}
                                <span>{isPresent ? "Present" : "Absent"}</span>
                              </button>
                            </div>
                          );
                        })
                      )}
                    </div>
                  </div>

                  {/* Bottom Actions */}
                  <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-4 border-t border-slate-200">
                    <button
                      onClick={handleExportCSV}
                      className="w-full sm:w-auto px-4 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-900 text-xs font-bold flex items-center justify-center gap-2 transition-all border border-slate-200 cursor-pointer"
                    >
                      <Download className="w-4 h-4 text-blue-600" />
                      <span>Export CSV</span>
                    </button>

                    <button
                      onClick={handleFinishLecture}
                      className="w-full sm:w-auto px-6 py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-emerald-600/30 transition-all hover:scale-[1.02] cursor-pointer"
                    >
                      <Check className="w-4 h-4" />
                      <span>Finalize & Submit Attendance</span>
                    </button>
                  </div>
                </div>
              ) : (
                /* STANDBY HERO CARD (NO ACTIVE SESSION) */
                <div className="p-10 rounded-3xl bg-white/60 border border-slate-200 text-center space-y-4">
                  <div className="w-16 h-16 rounded-3xl bg-blue-600/10 border border-blue-200 text-blue-600 flex items-center justify-center mx-auto">
                    <Radio className="w-8 h-8" />
                  </div>
                  <div>
                    <h3 className="text-xl font-bold text-white">No Live Lecture In Session</h3>
                    <p className="text-xs text-slate-600 mt-1 max-w-md mx-auto">
                      Select a scheduled course from the Classes tab or click below to start a live Wi-Fi attendance session.
                    </p>
                  </div>
                  <div className="flex items-center justify-center gap-3 pt-2">
                    <button
                      onClick={() => setActiveTab("classes")}
                      className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-2 cursor-pointer shadow-lg shadow-indigo-600/25 transition-all"
                    >
                      <Play className="w-4 h-4" />
                      <span>Go to Scheduled Classes</span>
                    </button>
                    {completedSessions.length > 0 && (
                      <button
                        onClick={() => setActiveTab("completed")}
                        className="px-5 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 text-xs font-bold flex items-center gap-2 cursor-pointer border border-slate-200"
                      >
                        <CheckCheck className="w-4 h-4 text-emerald-400" />
                        <span>View Past Attendance ({completedSessions.length})</span>
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ==================== TAB 2: CLASSES & BATCHES ==================== */}
          {activeTab === "classes" && (
            <div className="space-y-6">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                    <BookOpen className="w-5 h-5 text-blue-600" />
                    <span>Today's Classes & Scheduled Batches</span>
                  </h2>
                  <p className="text-xs text-slate-600">
                    Launch attendance sessions or create new class batches for your courses.
                  </p>
                </div>

                <button
                  onClick={() => setShowCreateModal(true)}
                  className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-indigo-600/20 cursor-pointer transition-all"
                >
                  <PlusCircle className="w-4 h-4" />
                  <span>Create Batch</span>
                </button>
              </div>

              {/* Class Cards Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {classes.map(cls => {
                  const isCurrentActive = activeSession?.classId === cls.id;
                  const todayCompleted = completedSessions.find(s => {
                    if (s.classId !== cls.id && s.subjectCode !== cls.subjectCode) return false;
                    const sessDate = new Date(s.endTime || s.startTime).toDateString();
                    return sessDate === new Date().toDateString();
                  });

                  return (
                    <div 
                      key={cls.id}
                      className={`p-5 rounded-3xl border transition-all space-y-4 backdrop-blur-md ${
                        isCurrentActive 
                          ? "bg-white/90 border-emerald-500/50 shadow-xl shadow-emerald-500/10 ring-1 ring-emerald-500/30" 
                          : todayCompleted
                          ? "bg-white/50 border-emerald-500/30 hover:border-emerald-500/50"
                          : "bg-white/60 border-slate-200 hover:border-slate-200"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <span className="text-[10px] font-mono font-bold px-2.5 py-1 rounded-lg bg-blue-50 text-blue-600 border border-blue-200">
                          {cls.subjectCode}
                        </span>
                        {todayCompleted ? (
                          <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                            <Check className="w-3 h-3" />
                            Completed Today ({todayCompleted.presentCount} Present)
                          </span>
                        ) : (
                          <span className="text-[11px] text-slate-600 font-medium">
                            {cls.roomNo} • {cls.wifiSsid || "Pranjal"}
                          </span>
                        )}
                      </div>

                      <div>
                        <h3 className="font-extrabold text-base text-white tracking-tight leading-snug">
                          {cls.subjectName}
                        </h3>
                        <p className="text-xs text-slate-600 font-mono mt-1">
                          Join Code: <span className="text-blue-600 font-bold">{cls.joinCode}</span> • {cls.students?.length || 2} Enrolled
                        </p>
                      </div>

                      <div className="flex items-center gap-2 pt-2 border-t border-slate-200">
                        <button
                          onClick={() => handleCopyCode(cls.joinCode)}
                          className="p-2 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 text-xs font-bold transition-all cursor-pointer"
                          title="Copy Join Code"
                        >
                          {copiedCode === cls.joinCode ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                        </button>
                        <button
                          onClick={() => setSelectedQrClass(cls)}
                          className="p-2 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 text-xs font-bold transition-all cursor-pointer"
                          title="Show QR Code"
                        >
                          <QrCode className="w-4 h-4" />
                        </button>

                        {isCurrentActive ? (
                          <button
                            onClick={() => setActiveTab("live")}
                            className="flex-1 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all cursor-pointer bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                          >
                            <Radio className="w-3.5 h-3.5 animate-pulse text-emerald-400" />
                            <span>Active Live Session</span>
                          </button>
                        ) : todayCompleted ? (
                          <>
                            <button
                              onClick={() => {
                                setSelectedCompletedSession(todayCompleted);
                                setActiveTab("completed");
                              }}
                              className="flex-1 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 border border-emerald-500/30 cursor-pointer transition-all"
                              title="View today's saved attendance report"
                            >
                              <CheckCheck className="w-3.5 h-3.5" />
                              <span>View Today's Report</span>
                            </button>
                            <button
                              onClick={() => handleStartAttendance(cls, true)}
                              className="p-2 rounded-xl bg-slate-100 hover:bg-amber-500/20 text-slate-600 hover:text-amber-300 text-xs font-bold transition-all cursor-pointer border border-slate-200/60"
                              title="Re-take attendance for this lecture"
                            >
                              <RotateCcw className="w-4 h-4" />
                            </button>
                          </>
                        ) : (
                          <button
                            onClick={() => handleStartAttendance(cls)}
                            className="flex-1 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all cursor-pointer bg-blue-600 hover:bg-blue-700 text-white shadow-md shadow-indigo-600/20"
                          >
                            <Play className="w-3.5 h-3.5" />
                            <span>Start Attendance</span>
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* ==================== TAB 3: COMPLETED LECTURES & ARCHIVES (THE NEW SECTION!) ==================== */}
          {activeTab === "completed" && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                    <CheckCheck className="w-5 h-5 text-emerald-400" />
                    <span>Completed Lectures & Attendance Archives</span>
                  </h2>
                  <p className="text-xs text-slate-600">
                    Classes whose attendance has been finalized and locked in Supabase Cloud.
                  </p>
                </div>

                <button
                  onClick={loadCompletedSessions}
                  disabled={isLoadingCompleted}
                  className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 text-xs font-bold flex items-center gap-1.5 border border-slate-200 cursor-pointer self-start sm:self-auto"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isLoadingCompleted ? "animate-spin" : ""}`} />
                  <span>Refresh Archive</span>
                </button>
              </div>

              {/* Completed Summary Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="p-4 rounded-2xl bg-white/60 border border-slate-200 flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center shrink-0">
                    <Award className="w-5 h-5" />
                  </div>
                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-600 tracking-wider block">Finished Lectures</span>
                    <span className="text-xl font-black text-white">{completedSessions.length} Sessions</span>
                  </div>
                </div>

                <div className="p-4 rounded-2xl bg-white/60 border border-slate-200 flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                    <Users className="w-5 h-5" />
                  </div>
                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-600 tracking-wider block">Total Students Marked</span>
                    <span className="text-xl font-black text-white">
                      {completedSessions.reduce((acc, s) => acc + s.presentCount, 0)} Present
                    </span>
                  </div>
                </div>

                <div className="p-4 rounded-2xl bg-white/60 border border-slate-200 flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-cyan-500/10 text-cyan-400 flex items-center justify-center shrink-0">
                    <Wifi className="w-5 h-5" />
                  </div>
                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-600 tracking-wider block">Verification Engine</span>
                    <span className="text-xl font-black text-white">Zero-Touch Wi-Fi</span>
                  </div>
                </div>
              </div>

              {/* Completed Sessions List */}
              <div className="space-y-3">
                {completedSessions.length === 0 ? (
                  <div className="p-12 text-center rounded-3xl bg-white/40 border border-slate-200 space-y-3">
                    <History className="w-8 h-8 text-slate-600 mx-auto" />
                    <p className="text-sm font-bold text-slate-700">No completed lectures yet.</p>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto">
                      Once you launch a lecture and tap "Finalize & Submit Attendance", it will appear here permanently with full student attendance records.
                    </p>
                  </div>
                ) : (
                  completedSessions.map(sess => {
                    const pct = sess.totalStudents > 0 
                      ? Math.round((sess.presentCount / sess.totalStudents) * 100) 
                      : 100;

                    return (
                      <div
                        key={sess.id}
                        className="p-5 rounded-3xl bg-white/70 border border-slate-200 hover:border-slate-200 transition-all space-y-4"
                      >
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                                {sess.subjectCode}
                              </span>
                              <span className="text-xs text-slate-600">
                                {sess.roomNo} • Wi-Fi: {sess.wifiSsid}
                              </span>
                            </div>
                            <h3 className="text-base font-black text-white mt-1">
                              {sess.subjectName}
                            </h3>
                            <p className="text-xs text-slate-600 font-mono mt-0.5">
                              Concluded: {new Date(sess.endTime || sess.startTime).toLocaleDateString()} at {new Date(sess.endTime || sess.startTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                            </p>
                          </div>

                          {/* Attendance Score & Actions */}
                          <div className="flex items-center gap-3">
                            <div className="text-right px-3 py-1.5 rounded-xl bg-slate-50 border border-slate-200">
                              <span className="text-sm font-extrabold text-emerald-400 block font-mono">
                                {sess.presentCount} / {sess.totalStudents || sess.presentCount} Present
                              </span>
                              <span className="text-[10px] text-slate-600 font-medium">
                                {pct}% Verified
                              </span>
                            </div>

                            <button
                              onClick={() => handleExportCompletedCSV(sess)}
                              className="px-3 py-2 rounded-xl bg-slate-100 hover:bg-slate-750 text-slate-900 text-xs font-bold flex items-center gap-1.5 border border-slate-200 cursor-pointer transition-all"
                              title="Download Attendance CSV"
                            >
                              <Download className="w-3.5 h-3.5 text-blue-600" />
                              <span className="hidden sm:inline">CSV</span>
                            </button>

                            <button
                              onClick={() => setSelectedCompletedSession(sess)}
                              className="px-3.5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-1.5 shadow-md shadow-indigo-600/20 cursor-pointer transition-all"
                            >
                              <span>View Roster</span>
                              <ChevronRight className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>

                        {/* Progress Bar */}
                        <div className="w-full bg-slate-50 h-2 rounded-full overflow-hidden border border-slate-200">
                          <div 
                            className="bg-gradient-to-r from-emerald-500 to-teal-400 h-full rounded-full transition-all"
                            style={{ width: `${Math.min(pct, 100)}%` }}
                          ></div>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          )}

          {/* ==================== TAB 4: DEVICE SECURITY ==================== */}
          {activeTab === "security" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                  <ShieldCheck className="w-5 h-5 text-blue-600" />
                  <span>Student Device Security & Unbind Requests</span>
                </h2>
                <p className="text-xs text-slate-600">
                  Manage anti-proxy hardware device locks and approve student requests when they switch phones.
                </p>
              </div>

              {/* Pending Requests */}
              <div className="p-6 rounded-3xl bg-white/60 border border-slate-200 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold text-white flex items-center gap-2">
                    <span>Pending Unbind Requests</span>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 font-mono">
                      {unbindRequests.length} Pending
                    </span>
                  </h3>
                  <button
                    onClick={loadPendingUnbindRequests}
                    className="text-xs text-blue-600 hover:underline flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" /> Refresh
                  </button>
                </div>

                {unbindRequests.length === 0 ? (
                  <div className="p-6 text-center text-slate-500 text-xs">
                    No pending unbind requests right now. All student devices are secure!
                  </div>
                ) : (
                  <div className="space-y-2">
                    {unbindRequests.map(req => (
                      <div 
                        key={req.id}
                        className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200 flex items-center justify-between gap-3"
                      >
                        <div>
                          <p className="text-xs font-bold text-white">
                            {req.studentName} ({req.rollNo})
                          </p>
                          <p className="text-[10px] text-slate-600 font-mono">
                            Model: {req.deviceModel} • Bound: {new Date(req.registeredAt).toLocaleDateString()}
                          </p>
                        </div>

                        <button
                          onClick={() => handleApproveUnbind(req.id, req.studentName, req.rollNo)}
                          className="px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs cursor-pointer shadow-md shadow-emerald-600/20 transition-all"
                        >
                          Approve Unbind
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ==================== TAB 5: RADAR & WI-FI TELEMETRY ==================== */}
          {activeTab === "radar" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                  <Signal className="w-5 h-5 text-cyan-400" />
                  <span>Real Hardware Wi-Fi & Gateway Telemetry</span>
                </h2>
                <p className="text-xs text-slate-600">
                  Live detection of local network subnet, hardware BSSID, and anti-spoof protection.
                </p>
              </div>

              <div className="p-6 rounded-3xl bg-white/60 border border-slate-200 space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200">
                    <span className="text-[10px] text-slate-600 uppercase font-bold block">Current Wi-Fi SSID</span>
                    <span className="text-base font-extrabold text-cyan-300 font-mono mt-0.5 block">
                      {detectedNetwork?.detectedSsid || wifiSsid}
                    </span>
                  </div>
                  <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200">
                    <span className="text-[10px] text-slate-600 uppercase font-bold block">Subnet & Gateway</span>
                    <span className="text-base font-extrabold text-white font-mono mt-0.5 block">
                      {detectedNetwork?.gateway || "192.168.0.1"} ({detectedNetwork?.subnet || "192.168.0.0/24"})
                    </span>
                  </div>
                </div>

                <div className="pt-2">
                  <span className="text-[10px] text-slate-600 uppercase font-bold tracking-wider block mb-2">
                    Nearby Radio Beacons:
                  </span>
                  <div className="flex flex-wrap gap-2">
                    {wifiPresets.map(w => (
                      <span key={w} className="px-2.5 py-1 rounded-lg bg-slate-50 border border-slate-200 text-slate-700 text-xs font-mono">
                        {w}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

        </main>
      </div>

      {/* ==================== MODAL: COMPLETED SESSION ROSTER DETAILS ==================== */}
      {selectedCompletedSession && (
        <div className="fixed inset-0 z-50 bg-slate-50/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-lg bg-white border border-slate-200 shadow-sm rounded-3xl shadow-2xl p-6 space-y-5 animate-in fade-in zoom-in-95">
            <div className="flex items-start justify-between">
              <div>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-bold">
                  {selectedCompletedSession.subjectCode}
                </span>
                <h3 className="text-xl font-bold text-white mt-1">
                  {selectedCompletedSession.subjectName}
                </h3>
                <p className="text-xs text-slate-600">
                  {selectedCompletedSession.roomNo} • {new Date(selectedCompletedSession.endTime || selectedCompletedSession.startTime).toLocaleString()}
                </p>
              </div>

              <button
                onClick={() => setSelectedCompletedSession(null)}
                className="p-2 rounded-xl text-slate-600 hover:text-white hover:bg-slate-100 transition-all cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Attendance Roster List */}
            <div className="max-h-72 overflow-y-auto space-y-2 pr-1">
              {selectedCompletedSession.records.length === 0 ? (
                <div className="p-6 text-center text-xs text-slate-500">
                  No individual attendance records found for this session.
                </div>
              ) : (
                selectedCompletedSession.records.map(rec => (
                  <div
                    key={rec.id}
                    className="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between text-xs"
                  >
                    <div>
                      <p className="font-bold text-white">{rec.name}</p>
                      <p className="text-[10px] text-slate-600 font-mono">Roll: {rec.rollNo}</p>
                    </div>

                    <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold font-mono ${
                      rec.status === "PRESENT"
                        ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/30"
                        : "bg-rose-500/10 text-rose-400 border border-rose-500/30"
                    }`}>
                      {rec.status}
                    </span>
                  </div>
                ))
              )}
            </div>

            <div className="flex items-center justify-between pt-3 border-t border-slate-200">
              <button
                onClick={() => handleExportCompletedCSV(selectedCompletedSession)}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-900 text-xs font-bold flex items-center gap-2 border border-slate-200 cursor-pointer"
              >
                <Download className="w-3.5 h-3.5 text-blue-600" />
                <span>Export CSV</span>
              </button>

              <button
                onClick={() => setSelectedCompletedSession(null)}
                className="px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================== MODAL: CREATE CLASS / BATCH ==================== */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-slate-50/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-white border border-slate-200 shadow-sm rounded-3xl shadow-2xl p-6 space-y-5 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <BookOpen className="w-5 h-5 text-blue-600" />
                <span>Create New Class / Batch</span>
              </h3>
              <button
                onClick={() => setShowCreateModal(false)}
                className="p-1.5 rounded-xl text-slate-600 hover:text-white hover:bg-slate-100 transition-all cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateClass} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1.5">Subject / Course Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Distributed Systems Lab"
                  value={subjectName}
                  onChange={(e) => setSubjectName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-white text-xs focus:border-indigo-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-600 mb-1.5">Subject Code</label>
                  <input
                    type="text"
                    placeholder="e.g. CS402"
                    value={subjectCode}
                    onChange={(e) => setSubjectCode(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-white text-xs font-mono uppercase focus:border-indigo-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-600 mb-1.5">Classroom / Lab</label>
                  <input
                    type="text"
                    placeholder="e.g. Lab 3"
                    value={roomNo}
                    onChange={(e) => setRoomNo(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-white text-xs focus:border-indigo-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1.5 flex items-center justify-between">
                  <span>Broadcast Classroom Wi-Fi</span>
                  <span className="text-[10px] text-cyan-400 font-mono flex items-center gap-1">
                    <Radio className="w-2.5 h-2.5 animate-pulse" /> Live Search Radar
                  </span>
                </label>
                <LiveWifiSearchSelector
                  currentWifi={wifiSsid}
                  onSelectWifi={(selected) => {
                    setWifiSsid(selected);
                    if (!wifiPresets.includes(selected)) {
                      setWifiPresets(prev => [selected, ...prev]);
                    }
                  }}
                  wifiList={wifiPresets}
                  onScanNearby={fetchRealNetworkStatus}
                  isScanning={isDetectingNetwork}
                  variant="modal"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-xl text-slate-600 hover:text-white text-xs font-bold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold shadow-lg shadow-indigo-600/25 cursor-pointer"
                >
                  Create & Launch Now
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== MODAL: QR CODE DISPLAY ==================== */}
      {selectedQrClass && (
        <div className="fixed inset-0 z-50 bg-slate-50/80 backdrop-blur-md flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-white border border-slate-200 shadow-sm rounded-3xl shadow-2xl p-6 text-center space-y-4 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-600">Scan to Join</span>
              <button
                onClick={() => setSelectedQrClass(null)}
                className="p-1 rounded-lg text-slate-600 hover:text-white cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-4 bg-white rounded-2xl inline-block shadow-xl">
              <QRCodeSVG 
                value={`${typeof window !== "undefined" ? window.location.origin : ""}/student?joinCode=${selectedQrClass.joinCode}`} 
                size={180} 
              />
            </div>

            <div>
              <p className="font-extrabold text-white text-sm">{selectedQrClass.subjectName}</p>
              <p className="text-xs font-mono text-blue-600 font-bold mt-0.5">{selectedQrClass.joinCode}</p>
            </div>

            <button
              onClick={() => setSelectedQrClass(null)}
              className="w-full py-2.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-white text-xs font-bold cursor-pointer"
            >
              Done
            </button>
          </div>
        </div>
      )}

    </div>
  );
}

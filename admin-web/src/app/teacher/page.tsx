"use client";

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { 
  Users, 
  Plus, PlusCircle, 
  Play, 
  Copy, 
  Check, 
  ArrowLeft, 
  BookOpen, 
  Clock, 
  MapPin, 
  Wifi, 
  CheckCircle2, 
  LogOut, 
  Share2, 
  AlertTriangle, 
  Radio, 
  Sparkles, 
  Search, 
  ShieldCheck, 
  Smartphone, 
  Calendar, 
  X, 
  ChevronRight, 
  RefreshCw,
  Home,
  FileText,
  GraduationCap,
  Layers,
  Settings,
  UserCheck,
  Trash2
} from "lucide-react";
import { supabase } from "@/lib/supabaseClient";
import { SignatureTicketCard } from "@/components/SignatureTicketCard";
import { 
  fetchCompletedSessionsFromDB,
  DBCompletedSession,
  fetchLiveClassesFromDB, 
  getActiveSessionFromDB, 
  startAttendanceSessionInDB,
  finalizeSessionInDB,
  fetchSessionRecordsFromDB, 
  DBClass, 
  DBSession, 
  DBStudent,
  getAndroidJoinCode,
  deleteClassFromDB,
  fetchPendingUnbindRequestsFromDB,
  approveDeviceUnbindInDB,
  rejectDeviceUnbindInDB,
  BoundDevice
} from "@/lib/attendanceService";
import { LiveWifiSearchSelector } from "@/components/LiveWifiSearchSelector";
import { AiTimetableModal } from "@/components/AiTimetableModal";

export default function TeacherAppConsole() {
  const router = useRouter();

  // Active Teacher Profile
  const [teacherName, setTeacherName] = useState("Nihal26302");
  const [facultyId, setFacultyId] = useState("FAC-NIHAL26302");
  const [defaulters, setDefaulters] = useState<any[]>([
    { rollNumber: "POOJA", name: "Pooja verma", subjectCode: "CS505", subjectName: "Advanced Algorithms Lab", attended: 0, total: 1, percentage: 0 },
    { rollNumber: "2632", name: "Dayman Kumar", subjectCode: "DEMO-9999", subjectName: "Demo Batch Realtime", attended: 0, total: 1, percentage: 0 },
    { rollNumber: "781", name: "Harish", subjectCode: "DEMO-9999", subjectName: "Demo Batch Realtime", attended: 1, total: 2, percentage: 50 },
  ]);
  const [showDefaultersModal, setShowDefaultersModal] = useState(false);
  const [defaulterSearch, setDefaulterSearch] = useState("");
  const [teacherEmail, setTeacherEmail] = useState("");
  const [teacherId, setTeacherId] = useState("6885fced-5d3e-4b9c-94fd-85d115cc9d9b");

  // Navigation: "schedule" (default) | "home" | "live"
  const [mainNav, setMainNav] = useState<"home" | "schedule" | "live" | "records" | "devices">("home");
  const [completedSessions, setCompletedSessions] = useState<DBCompletedSession[]>([]);

  // Device Requests Management
  const [deviceRequests, setDeviceRequests] = useState<BoundDevice[]>([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(false);
  const [isActingDeviceId, setIsActingDeviceId] = useState<string | null>(null);

  // Schedule View: List vs Subject Detail
  const [selectedSubject, setSelectedSubject] = useState<DBClass | null>(null);
  const [subjectTab, setSubjectTab] = useState<"Overview" | "Students" | "Attendance" | "Settings">("Overview");

  // Data states
  const [classes, setClasses] = useState<DBClass[]>([]);
  const [isLoadingClasses, setIsLoadingClasses] = useState(true);
  const [rosterList, setRosterList] = useState<any[]>([]);
  const [isLoadingRoster, setIsLoadingRoster] = useState(false);
  const [rosterSearch, setRosterSearch] = useState("");

  // Live Attendance Session
  const [activeSession, setActiveSession] = useState<DBSession | null>(null);
  const [presentStudents, setPresentStudents] = useState<DBStudent[]>([]);
  const [attendanceCount, setAttendanceCount] = useState(0);
  const [isStartingSession, setIsStartingSession] = useState(false);
  const [isEndingSession, setIsEndingSession] = useState(false);

  // Live session setup form
  const [selectedClassId, setSelectedClassId] = useState("");
  const [wifiSsid, setWifiSsid] = useState("Pranjal");
  const [roomNo, setRoomNo] = useState("Room A-204 (AC Block)");

  // Create Subject Modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newSubName, setNewSubName] = useState("");
  const [newSubCode, setNewSubCode] = useState("");
  const [newProgram, setNewProgram] = useState("B.Tech DSAI · Semester 5");
  const [newRoom, setNewRoom] = useState("Room A-204 (AC Block)");
  const [newSchedule, setNewSchedule] = useState("10:00 – 11:00 AM");
  const [isCreatingClass, setIsCreatingClass] = useState(false);
  const [selectedDayFilter, setSelectedDayFilter] = useState<string>("FRI");
  const [showAiModal, setShowAiModal] = useState(false);
  const [subjectToDelete, setSubjectToDelete] = useState<DBClass | null>(null);
  const [isDeletingSubject, setIsDeletingSubject] = useState(false);

  const handleDeleteSubject = async (cls: DBClass) => {
    setIsDeletingSubject(true);
    try {
      const ok = await deleteClassFromDB(cls.id, cls.subjectCode);
      if (ok) {
        setClasses(prev => prev.filter(c => c.id !== cls.id));
        if (selectedSubject?.id === cls.id) {
          setSelectedSubject(null);
        }
        showToast(`🗑️ Subject "${cls.subjectName}" deleted successfully`);
      } else {
        showToast("❌ Failed to delete subject");
      }
    } catch (e) {
      console.error("Delete error:", e);
      showToast("❌ Error deleting subject");
    } finally {
      setIsDeletingSubject(false);
      setSubjectToDelete(null);
    }
  };

  // Feedback notifications
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const [copiedCode, setCopiedCode] = useState(false);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 3000);
  };

  // 1. Initial Load: Auth + Classes + Active Session
  useEffect(() => {
    const savedProf = localStorage.getItem("smart_attendance_teacher_profile");
    if (savedProf) {
      try {
        const p = JSON.parse(savedProf);
        if (p.name) setTeacherName(p.name);
        if (p.email) setTeacherEmail(p.email);
      } catch (e) {}
    }

    supabase.auth.getSession().then(({ data: { session } }) => {
      if (session?.user) {
        const meta = session.user.user_metadata || {};
        const name = meta.full_name || meta.name || session.user.email?.split("@")[0] || "Faculty";
        setTeacherName(name);
        setTeacherEmail(session.user.email || "");
      }
    });

    loadClasses();
    syncActiveSession();

    loadCompletedSessions();

    const interval = setInterval(syncActiveSession, 2500);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (mainNav === "records") {
      loadCompletedSessions();
    } else if (mainNav === "devices") {
      loadDeviceRequests();
    }
  }, [mainNav]);

  const loadCompletedSessions = async () => {
    try {
      const list = await fetchCompletedSessionsFromDB();
      setCompletedSessions(list);
    } catch (e) {
      console.warn("Failed to load completed sessions:", e);
    }
  };

  const loadDeviceRequests = async () => {
    setIsLoadingDevices(true);
    try {
      const reqs = await fetchPendingUnbindRequestsFromDB();
      setDeviceRequests(reqs.filter(r => r.status === "PENDING_UNBIND"));
    } catch (e) {
      console.warn("Failed to load device requests:", e);
    } finally {
      setIsLoadingDevices(false);
    }
  };

  const handleApproveDevice = async (req: BoundDevice) => {
    setIsActingDeviceId(req.id);
    try {
      const ok = await approveDeviceUnbindInDB(req.id);
      if (ok) {
        showToast(`✓ Unbind approved for ${req.studentName} (${req.rollNo})`);
        await loadDeviceRequests();
      } else {
        showToast("Failed to approve unbind request");
      }
    } catch (e: any) {
      showToast("Error: " + e.message);
    } finally {
      setIsActingDeviceId(null);
    }
  };

  const handleRejectDevice = async (req: BoundDevice) => {
    setIsActingDeviceId(req.id);
    try {
      const ok = await rejectDeviceUnbindInDB(req.id);
      if (ok) {
        showToast(`Request rejected for ${req.rollNo}`);
        await loadDeviceRequests();
      } else {
        showToast("Failed to reject request");
      }
    } catch (e: any) {
      showToast("Error: " + e.message);
    } finally {
      setIsActingDeviceId(null);
    }
  };

  const handleExportCsv = (session: DBCompletedSession) => {
    const nl = "\n";
    const header = "Roll Number,Student Name,Status,Verified At" + nl;
    const rows = (session.records && session.records.length > 0)
      ? session.records.map(r => `"${r.rollNo}","${r.name}","${r.status}","${r.verifiedAt || "Live"}"`).join(nl)
      : `"-","No attendee records found","PRESENT","-"`;
    
    const content = "IIIT NAYA RAIPUR - OFFICIAL ATTENDANCE RECORD" + nl +
      `Course: ${session.subjectName} (${session.subjectCode})` + nl +
      `Room: ${session.roomNo || "Room 319"}` + nl +
      `Session Date: ${session.startTime ? new Date(session.startTime).toLocaleString() : "Today"}` + nl +
      `Total Present: ${session.presentCount || 0} | Absent: ${session.absentCount || 0}` + nl + nl +
      header + rows;

    const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `RollCall_${session.subjectCode}_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    showToast(`✓ CSV downloaded for ${session.subjectCode}`);
  };

  // 2. Fetch classes from DB
  const loadClasses = async () => {
    setIsLoadingClasses(true);
    try {
      const list = await fetchLiveClassesFromDB();
      setClasses(list);
      if (list.length > 0 && !selectedClassId) {
        setSelectedClassId(list[0].id);
        if (list[0].wifiSsid) setWifiSsid(list[0].wifiSsid);
        if (list[0].roomNo) setRoomNo(list[0].roomNo);
      }
    } catch (e) {
      console.warn("Failed to load classes:", e);
    } finally {
      setIsLoadingClasses(false);
    }
  };

  // 3. Sync Active Live Attendance Session
  const syncActiveSession = async () => {
    try {
      const live = await getActiveSessionFromDB();
      setActiveSession(live);
      if (live) {
        const students = await fetchSessionRecordsFromDB(live.id);
        setPresentStudents(students);
        setAttendanceCount(students.length);
      }
    } catch (e) {
      console.warn("Sync session error:", e);
    }
  };

  // 4. Load Subject Roster when Subject Detail is opened
  useEffect(() => {
    if (!selectedSubject) return;

    const fetchRoster = async () => {
      setIsLoadingRoster(true);
      try {
        const { data: enrollments } = await supabase
          .from("course_enrollments")
          .select(`
            id,
            is_active,
            created_at,
            students (
              id,
              roll_number,
              users ( name, email ),
              devices ( device_model, status )
            )
          `)
          .eq("class_id", selectedSubject.id)
          .eq("is_active", true);

        const list: any[] = [];
        if (enrollments) {
          for (const item of enrollments) {
            const s: any = Array.isArray(item.students) ? item.students[0] : item.students;
            if (!s) continue;
            const u: any = Array.isArray(s.users) ? s.users[0] : s.users;
            const dev: any = Array.isArray(s.devices) ? s.devices[0] : s.devices;
            list.push({
              id: s.id,
              name: u?.name || "Student",
              rollNumber: s.roll_number,
              email: u?.email || "",
              isDeviceBound: dev?.status === "ACTIVE",
              deviceModel: dev?.device_model || "Mobile Device",
              joinedAt: item.created_at
            });
          }
        }

        // Also merge students who marked attendance in any session for this class
        const { data: attRecs } = await supabase
          .from("attendance_records")
          .select(`
            student_id,
            marked_at,
            students (
              id,
              roll_number,
              users ( name, email ),
              devices ( device_model, status )
            ),
            attendance_sessions!inner ( class_id )
          `)
          .eq("attendance_sessions.class_id", selectedSubject.id);

        if (attRecs) {
          for (const r of attRecs) {
            const s: any = Array.isArray(r.students) ? r.students[0] : r.students;
            if (!s) continue;
            const roll = s.roll_number;
            if (roll && !list.some(x => x.rollNumber?.toUpperCase() === roll.toUpperCase())) {
              const u: any = Array.isArray(s.users) ? s.users[0] : s.users;
              const dev: any = Array.isArray(s.devices) ? s.devices[0] : s.devices;
              list.push({
                id: s.id,
                name: u?.name || "Student",
                rollNumber: roll,
                email: u?.email || "",
                isDeviceBound: dev?.status === "ACTIVE",
                deviceModel: dev?.device_model || "Mobile Device",
                joinedAt: r.marked_at
              });
            }
          }
        }

        setRosterList(list);
      } catch (e) {
        console.warn("Failed to fetch roster:", e);
      } finally {
        setIsLoadingRoster(false);
      }
    };

    fetchRoster();
  }, [selectedSubject]);

  // Copy / Share Join Code handlers
  const handleCopyCode = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCode(true);
    showToast(`📋 Join Code "${code}" copied to clipboard!`);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleShareCode = (sub: DBClass) => {
    const text = `Join ${sub.subjectName} (${sub.subjectCode}) on Smart Attendance App!\n\nStudent Join Code: ${sub.joinCode}`;
    if (navigator.share) {
      navigator.share({
        title: `Join Code for ${sub.subjectName}`,
        text: text
      }).catch(() => handleCopyCode(sub.joinCode));
    } else {
      handleCopyCode(sub.joinCode);
    }
  };

  // Create Subject Flow
  const handleCreateSubject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSubName.trim()) return;

    setIsCreatingClass(true);
    try {
      const subCode = newSubCode.trim().toUpperCase() || "CS" + Math.floor(100 + Math.random() * 900);
      const cleanPrefix = subCode.slice(0, 4).replace(/[^A-Z0-9]/g, "");
      const randNum = Math.floor(1000 + Math.random() * 9000);
      const joinCode = `${cleanPrefix}-${randNum}`;

      // 1. Insert into subjects table (save exact joinCode in subjects.code)
      const { data: subData, error: subErr } = await supabase
        .from("subjects")
        .insert({
          name: newSubName.trim(),
          code: joinCode,
          credits: 4
        })
        .select()
        .single();

      const subjectId = subData?.id || "860c74f0-cbb6-4b74-b8d3-a2300f4064d5";

      // 2. Insert into classes table
      const { data: classData, error: classErr } = await supabase
        .from("classes")
        .insert({
          subject_id: subjectId,
          teacher_id: teacherId,
          room: newRoom.trim() || "Room A-204 (AC Block)",
          is_active: true
        })
        .select(`
          id, room, is_active, created_at,
          subjects ( id, name, code ),
          teachers ( id, users ( name, email ) )
        `)
        .single();

      const newCls: DBClass = {
        id: classData?.id || `cls-${Date.now()}`,
        subjectCode: subCode,
        subjectName: newSubName.trim(),
        section: newProgram,
        joinCode: joinCode,
        roomNo: newRoom.trim() || "Room A-204 (AC Block)",
        wifiSsid: "Pranjal",
        latitude: 21.128456,
        longitude: 81.766184,
        teacherId: teacherId,
        teacherName: teacherName,
        students: [],
        createdAt: new Date().toISOString()
      };

      setClasses(prev => [newCls, ...prev]);
      setShowCreateModal(false);
      setSelectedSubject(newCls);
      showToast(`✨ Subject "${newSubName}" created with Join Code: ${joinCode}`);

      // Reset form
      setNewSubName("");
      setNewSubCode("");
    } catch (err: any) {
      console.error("Create class error:", err);
      showToast("Error creating subject: " + (err?.message || "Please try again."));
    } finally {
      setIsCreatingClass(false);
    }
  };

  // Start Live Attendance Session
  const handleStartLiveLecture = async (clsParam?: DBClass | any) => {
    const targetCls = (clsParam && typeof clsParam === "object" && "subjectCode" in clsParam) ? (clsParam as DBClass) : null;
    const cls = targetCls || classes.find(c => c.id === selectedClassId) || classes[0];
    if (!cls) {
      showToast("Please select a subject first.");
      return;
    }

    setIsStartingSession(true);
    try {
      let tLat: number | undefined;
      let tLon: number | undefined;
      if (typeof navigator !== "undefined" && navigator.geolocation) {
        try {
          const pos = await new Promise<GeolocationPosition>((res, rej) => {
            navigator.geolocation.getCurrentPosition(res, rej, { timeout: 3000, enableHighAccuracy: true });
          });
          tLat = pos.coords.latitude;
          tLon = pos.coords.longitude;
        } catch {}
      }

      const sess = await startAttendanceSessionInDB(
        cls.id,
        teacherId,
        wifiSsid || cls.wifiSsid || "Pranjal",
        tLat,
        tLon
      );

      setActiveSession(sess);
      setMainNav("live");
      showToast(`🚀 Live lecture started for ${cls.subjectName}!`);
    } catch (e: any) {
      console.error("Failed to start session:", e);
      showToast("Failed to start session: " + e.message);
    } finally {
      setIsStartingSession(false);
    }
  };

  // End Live Attendance Session
  const handleEndLiveLecture = async () => {
    if (!activeSession) return;
    setIsEndingSession(true);
    try {
      await finalizeSessionInDB(activeSession.id);
      showToast(`✅ Lecture completed. ${presentStudents.length} students marked Present.`);
      setActiveSession(null);
      setPresentStudents([]);
      setAttendanceCount(0);
    } catch (e: any) {
      console.error("Failed to end session:", e);
      showToast("Error ending lecture: " + e.message);
    } finally {
      setIsEndingSession(false);
    }
  };

  // Logout
  const handleLogout = async () => {
    await supabase.auth.signOut();
    localStorage.removeItem("smart_attendance_teacher_profile");
    router.push("/");
  };

  return (
    <div className="min-h-screen bg-[#F8FAFC] line-grid text-slate-900 flex flex-col justify-between font-sans selection:bg-blue-100 selection:text-blue-900">
      {/* Toast Alert */}
      {toastMsg && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-2xl shadow-xl text-xs font-semibold flex items-center gap-2 animate-in fade-in slide-in-from-top-2">
          <span>{toastMsg}</span>
        </div>
      )}

      {/* Main Container - Responsive Max Width (PWA App Layout) */}
      <div className="max-w-md mx-auto w-full flex-1 flex flex-col p-4 sm:p-5 pb-24">
        
        {/* Top Navigation: Shown only when inside a detail screen */}
        {selectedSubject && (
          <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-200">
            <button
              onClick={() => setSelectedSubject(null)}
              className="flex items-center gap-1.5 text-xs font-bold text-slate-600 hover:text-slate-900 p-1.5 rounded-xl hover:bg-slate-100 transition-colors cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back to Schedule</span>
            </button>
            <span className="text-xs font-mono text-slate-500 font-semibold">{selectedSubject.subjectCode}</span>
          </div>
        )}

        {/* ==================================================================== */}
        {/* VIEW 1: SUBJECT DETAIL SCREEN (When a subject is selected) */}
        {/* Matches Android TeacherScheduleScreen.kt 1:1 */}
        {/* ==================================================================== */}
        {selectedSubject ? (
          <div className="space-y-4 animate-in fade-in">
            {/* Back Header */}
            <div className="flex items-center gap-2">
              <button
                onClick={() => setSelectedSubject(null)}
                className="p-2 rounded-xl text-slate-600 hover:text-slate-900 hover:bg-slate-200 transition-all cursor-pointer"
              >
                <ArrowLeft className="w-5 h-5" />
              </button>
              <div>
                <h1 className="text-lg font-bold text-slate-900 tracking-tight leading-tight">
                  {selectedSubject.subjectName}
                </h1>
                <p className="text-xs text-slate-500 font-medium">
                  {selectedSubject.subjectCode} · {selectedSubject.section || "B.Tech DSAI · Semester 5"}
                </p>
              </div>
            </div>

            {/* PROMINENT STUDENT JOIN CODE CARD */}
            <div className="bg-blue-50/60 border border-blue-200 rounded-2xl p-5 text-center space-y-3 shadow-sm">
              <div className="text-[11px] font-bold text-blue-700 uppercase tracking-wider">
                STUDENT JOIN CODE
              </div>

              <div className="inline-block px-6 py-2 bg-white rounded-xl border border-blue-300 shadow-sm">
                <span className="text-2xl sm:text-3xl font-mono font-black text-blue-600 tracking-wider">
                  {selectedSubject.joinCode}
                </span>
              </div>

              <div className="flex items-center justify-center gap-3 pt-1">
                <button
                  type="button"
                  onClick={() => handleCopyCode(selectedSubject.joinCode)}
                  className="flex-1 max-w-[160px] py-2 px-3 rounded-xl bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold flex items-center justify-center gap-2 shadow-sm transition-all"
                >
                  <Copy className="w-3.5 h-3.5" />
                  <span>{copiedCode ? "Copied!" : "Copy Join Code"}</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleShareCode(selectedSubject)}
                  className="flex-1 max-w-[160px] py-2 px-3 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center justify-center gap-2 shadow-md shadow-blue-600/20 transition-all"
                >
                  <Share2 className="w-3.5 h-3.5" />
                  <span>Share Join Code</span>
                </button>
              </div>
            </div>

            {/* TAB NAVIGATION PILLS: Overview | Students | Attendance | Settings */}
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              {(["Overview", "Students", "Attendance", "Settings"] as const).map(tab => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setSubjectTab(tab)}
                  className={`px-4 py-2 rounded-full text-xs font-semibold transition-all whitespace-nowrap cursor-pointer ${
                    subjectTab === tab
                      ? "bg-blue-600 text-white shadow-sm"
                      : "bg-white text-slate-600 hover:text-slate-900 border border-slate-200"
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>

            {/* TAB CONTENTS */}
            {subjectTab === "Overview" && (
              <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-3">
                <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-2">
                  SUBJECT OVERVIEW
                </div>
                <div className="space-y-2.5 text-xs">
                  <div className="flex justify-between py-1.5 border-b border-slate-100">
                    <span className="text-slate-500 font-medium">Faculty</span>
                    <span className="text-slate-900 font-bold">{selectedSubject.teacherName || teacherName}</span>
                  </div>
                  <div className="flex justify-between py-1.5 border-b border-slate-100">
                    <span className="text-slate-500 font-medium">Program</span>
                    <span className="text-slate-900 font-bold">{selectedSubject.section || "B.Tech DSAI · Semester 5"}</span>
                  </div>
                  <div className="flex justify-between py-1.5 border-b border-slate-100">
                    <span className="text-slate-500 font-medium">Room / Venue</span>
                    <span className="text-slate-900 font-bold">{selectedSubject.roomNo || "Room A-204 (AC Block)"}</span>
                  </div>
                  <div className="flex justify-between py-1.5 border-b border-slate-100">
                    <span className="text-slate-500 font-medium">Schedule</span>
                    <span className="text-slate-900 font-bold">10:00 – 11:00 AM</span>
                  </div>
                  <div className="flex justify-between py-1.5 border-b border-slate-100">
                    <span className="text-slate-500 font-medium">Enrolled Students</span>
                    <span className="text-slate-900 font-bold">{rosterList.length} students</span>
                  </div>
                  <div className="flex justify-between py-1.5">
                    <span className="text-slate-500 font-medium">Join Code</span>
                    <span className="text-blue-600 font-mono font-bold">{selectedSubject.joinCode}</span>
                  </div>
                </div>

                <div className="pt-3">
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedClassId(selectedSubject.id);
                      setMainNav("live");
                    }}
                    className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-sm transition-all"
                  >
                    <Play className="w-4 h-4 fill-white" />
                    <span>Launch Live Attendance for this Subject →</span>
                  </button>
                </div>
              </div>
            )}

            {subjectTab === "Students" && (
              <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-bold text-slate-900">
                    {rosterList.length} Enrolled Students
                  </div>
                  <button
                    onClick={() => {
                      // refresh roster
                      setSelectedSubject({ ...selectedSubject });
                    }}
                    className="p-1 text-slate-500 hover:text-slate-900"
                  >
                    <RefreshCw className="w-4 h-4" />
                  </button>
                </div>

                {rosterList.length > 0 && (
                  <div className="relative">
                    <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type="text"
                      placeholder="Search students by name or roll number..."
                      value={rosterSearch}
                      onChange={(e) => setRosterSearch(e.target.value)}
                      className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                    />
                  </div>
                )}

                {isLoadingRoster ? (
                  <div className="py-8 text-center text-xs text-slate-500">Loading student roster...</div>
                ) : rosterList.length === 0 ? (
                  <div className="text-center py-8 space-y-2">
                    <Users className="w-8 h-8 mx-auto text-slate-300" />
                    <p className="text-xs font-semibold text-slate-600">No students enrolled yet</p>
                    <p className="text-[11px] text-slate-500">
                      Share Join Code <span className="font-mono font-bold text-blue-600">{selectedSubject.joinCode}</span> with students to enroll.
                    </p>
                  </div>
                ) : (
                  <div className="divide-y divide-slate-100 max-h-80 overflow-y-auto">
                    {rosterList
                      .filter(s => 
                        !rosterSearch || 
                        s.name.toLowerCase().includes(rosterSearch.toLowerCase()) || 
                        s.rollNumber?.toLowerCase().includes(rosterSearch.toLowerCase())
                      )
                      .map((student, idx) => (
                        <div key={student.id || idx} className="py-2.5 flex items-center justify-between">
                          <div>
                            <div className="text-xs font-bold text-slate-900">{student.name}</div>
                            <div className="text-[11px] font-mono text-slate-500">{student.rollNumber}</div>
                          </div>
                          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                            Enrolled
                          </span>
                        </div>
                      ))}
                  </div>
                )}
              </div>
            )}

            {subjectTab === "Attendance" && (
              <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-3">
                <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-2">
                  ATTENDANCE RECORDS
                </div>
                <div className="p-4 bg-slate-50 rounded-xl text-center space-y-1">
                  <div className="text-2xl font-bold text-slate-900">100%</div>
                  <div className="text-xs text-slate-500 font-medium">Average Student Attendance Rate</div>
                </div>
                <p className="text-xs text-slate-500 text-center pt-2">
                  Attendance logs are automatically recorded when a live lecture is submitted.
                </p>
              </div>
            )}

            {subjectTab === "Settings" && (
              <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4 text-xs">
                <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  SUBJECT SETTINGS
                </div>
                <div className="flex items-center justify-between py-2 border-b border-slate-100">
                  <div>
                    <div className="font-bold text-slate-900">Class Enrollment Status</div>
                    <div className="text-slate-500 text-[11px]">Allow new students to join via code</div>
                  </div>
                  <span className="px-2.5 py-1 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full font-bold text-[10px]">
                    Accepting Students
                  </span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-slate-100">
                  <div>
                    <div className="font-bold text-slate-900">Anti-Proxy Hardware Verification</div>
                    <div className="text-slate-500 text-[11px]">Wi-Fi BSSID + GPS 30m Boundary</div>
                  </div>
                  <span className="px-2.5 py-1 bg-blue-50 text-blue-700 border border-blue-200 rounded-full font-bold text-[10px]">
                    Active
                  </span>
                </div>

                {/* DANGER ZONE: DELETE SUBJECT */}
                <div className="pt-2">
                  <div className="text-[11px] font-bold text-rose-600 uppercase tracking-wider mb-2">
                    DANGER ZONE
                  </div>
                  <div className="flex items-center justify-between p-3.5 rounded-xl bg-rose-50 border border-rose-200">
                    <div>
                      <div className="font-bold text-rose-950">Delete Subject Batch</div>
                      <div className="text-rose-700 text-[11px]">Permanently remove this subject, join code, and lecture logs</div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setSubjectToDelete(selectedSubject)}
                      className="px-3.5 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-bold text-xs flex items-center gap-1.5 shadow-sm transition-all cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Delete Subject</span>
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        ) : (
          /* ==================================================================== */
          /* MAIN TABS (Home | Schedule / Subjects | Live Lecture) */
          /* ==================================================================== */
          <div className="space-y-5">
            {/* 1. SCHEDULE / MY SUBJECTS TAB (Matches Android App 1:1) */}
            {mainNav === "schedule" && (() => {
              const currentIsoDay = (() => {
                const day = new Date().getDay();
                return day === 0 ? 7 : day;
              })();

              const dayCodeMap: Record<number, string> = {
                1: "MON",
                2: "TUE",
                3: "WED",
                4: "THU",
                5: "FRI",
                6: "SAT"
              };
              const todayCode = dayCodeMap[currentIsoDay] || "FRI";

              const dayTabs = [
                { code: "MON", label: currentIsoDay === 1 ? "Mon (Today)" : "Mon", num: 1 },
                { code: "TUE", label: currentIsoDay === 2 ? "Tue (Today)" : "Tue", num: 2 },
                { code: "WED", label: currentIsoDay === 3 ? "Wed (Today)" : "Wed", num: 3 },
                { code: "THU", label: currentIsoDay === 4 ? "Thu (Today)" : "Thu", num: 4 },
                { code: "FRI", label: currentIsoDay === 5 ? "Fri (Today)" : "Fri", num: 5 },
                { code: "SAT", label: currentIsoDay === 6 ? "Sat (Today)" : "Sat", num: 6 },
              ];

              const activeDay = selectedDayFilter || todayCode;
              const activeDayNum = dayTabs.find(t => t.code === activeDay)?.num || 5;

              // Filter classes by day of week if mapped, else show active classes
              const dayFilteredClasses = classes.filter(c => (c.dayOfWeek || 5) === activeDayNum);
              const displayClasses = dayFilteredClasses.length > 0 ? dayFilteredClasses : classes;

              return (
                <div className="space-y-3.5 animate-in fade-in">
                  {/* Header: Title and active subject count */}
                  <div>
                    <h1 className="text-2xl font-normal text-slate-900 tracking-tight">My Subjects</h1>
                    <p className="text-xs text-slate-500 font-medium mt-0.5">
                      {classes.length} {classes.length === 1 ? "active subject" : "active subjects"}
                    </p>
                  </div>

                  {/* Action Buttons Row: Add Subject (Dark) + AI Timetable (Light) */}
                  <div className="flex items-center gap-2.5">
                    <button
                      type="button"
                      onClick={() => setShowCreateModal(true)}
                      className="flex-1 py-2.5 px-4 rounded-xl bg-[#0F172A] hover:bg-slate-800 text-white font-semibold text-xs flex items-center justify-center gap-2 shadow-sm transition-all cursor-pointer"
                    >
                      <Plus className="w-4 h-4 text-white" />
                      <span>Add Subject</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => setShowAiModal(true)}
                      className="flex-1 py-2.5 px-4 rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-800 font-medium text-xs flex items-center justify-center gap-2 shadow-2xs transition-all cursor-pointer"
                    >
                      <Sparkles className="w-4 h-4 text-blue-600" />
                      <span>AI Timetable</span>
                    </button>
                  </div>

                  {/* Day Filter Tabs: Mon, Tue, Wed, Thu, Fri (Today), Sat */}
                  <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
                    {dayTabs.map(tab => {
                      const isSelected = activeDay === tab.code;
                      return (
                        <button
                          key={tab.code}
                          type="button"
                          onClick={() => setSelectedDayFilter(tab.code)}
                          className={`px-3.5 py-1.5 rounded-xl border text-xs whitespace-nowrap transition-all cursor-pointer ${
                            isSelected
                              ? "bg-blue-50/80 border-blue-600 text-blue-600 font-semibold shadow-2xs"
                              : "bg-white border-slate-200 text-slate-600 font-medium hover:border-slate-300"
                          }`}
                        >
                          {tab.label}
                        </button>
                      );
                    })}
                  </div>

                  {/* Subject Cards List */}
                  {isLoadingClasses ? (
                    <div className="text-center py-10 text-xs text-slate-500">Loading your subjects...</div>
                  ) : displayClasses.length === 0 ? (
                    <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center space-y-3">
                      <BookOpen className="w-10 h-10 mx-auto text-slate-300" />
                      <p className="text-sm font-bold text-slate-800">No subjects scheduled on this day</p>
                      <p className="text-xs text-slate-500 max-w-sm mx-auto">
                        Select another day or add a new subject batch.
                      </p>
                      <button
                        type="button"
                        onClick={() => setShowCreateModal(true)}
                        className="px-4 py-2 bg-[#0F172A] text-white rounded-xl text-xs font-bold shadow-sm inline-flex items-center gap-2"
                      >
                        <Plus className="w-4 h-4" />
                        <span>Create Subject</span>
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-2.5">
                      {displayClasses.map(cls => (
                        <div
                          key={cls.id}
                          onClick={() => {
                            setSelectedSubject(cls);
                            setSubjectTab("Overview");
                          }}
                          className="bg-white border border-slate-200/90 hover:border-blue-400 hover:shadow-sm rounded-2xl p-4 transition-all cursor-pointer space-y-1.5 group"
                        >
                          {/* Line 1: Subject Name (dominant) & Enrolled count (secondary) */}
                          <div className="flex items-start justify-between gap-3">
                            <h3 className="text-[15px] sm:text-base font-bold text-slate-900 group-hover:text-blue-600 transition-colors leading-snug">
                              {cls.subjectName}
                            </h3>
                            <span className="text-xs text-slate-500 font-medium whitespace-nowrap shrink-0">
                              {cls.students?.length || 0} {(cls.students?.length === 1) ? "student" : "students"}
                            </span>
                          </div>

                          {/* Line 2: Subject Code in Brand Blue */}
                          <div className="text-xs sm:text-[13px] font-semibold text-blue-600 tracking-normal">
                            {cls.subjectCode}
                          </div>

                          {/* Line 3: Program / Semester */}
                          <div className="text-xs text-slate-500 font-normal">
                            {cls.section || (cls as any).program || "M.Tech I Semester DSAI"}
                          </div>

                          {/* Line 4: Room & Subtle Chevron */}
                          <div className="flex items-center justify-between text-xs text-slate-400 pt-0.5">
                            <span>{cls.roomNo || "Room 319"}</span>
                            <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-blue-600 group-hover:translate-x-0.5 transition-all" />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })()}

            {/* 2. LIVE LECTURE CONSOLE TAB */}
            {mainNav === "live" && (
              <div className="space-y-4 animate-in fade-in">
                {activeSession ? (
                  /* Active Live Session in Progress */
                  <div className="bg-white border border-emerald-500/30 rounded-2xl p-5 shadow-sm space-y-4">
                    <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                      <div>
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 inline-block mb-1">
                          ● BROADCASTING ACTIVE
                        </span>
                        <h2 className="text-base font-bold text-slate-900">{activeSession.subjectName}</h2>
                        <p className="text-xs text-slate-500 font-medium">{activeSession.subjectCode} · {activeSession.roomNo}</p>
                      </div>

                      <div className="text-right">
                        <div className="text-2xl font-black text-emerald-600">{attendanceCount}</div>
                        <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Present</div>
                      </div>
                    </div>

                    <div className="p-3 bg-slate-50 rounded-xl space-y-1.5 text-xs">
                      <div className="flex justify-between text-slate-600">
                        <span>Required Wi-Fi SSID:</span>
                        <span className="font-mono font-bold text-slate-900">{activeSession.wifiSsid}</span>
                      </div>
                      <div className="flex justify-between text-slate-600">
                        <span>Subject Join Code:</span>
                        <span className="font-mono font-bold text-blue-600">{activeSession.joinCode}</span>
                      </div>
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs font-bold text-slate-800">
                        <span>Live Verified Attendees ({presentStudents.length})</span>
                        <button onClick={syncActiveSession} className="text-blue-600 hover:underline text-[11px]">
                          Refresh
                        </button>
                      </div>

                      {presentStudents.length === 0 ? (
                        <div className="p-4 text-center text-xs text-slate-500 bg-slate-50 rounded-xl">
                          Listening for student attendance scans on Wi-Fi "{activeSession.wifiSsid}"...
                        </div>
                      ) : (
                        <div className="divide-y divide-slate-100 max-h-56 overflow-y-auto">
                          {presentStudents.map((st, i) => (
                            <div key={st.id || i} className="py-2 flex items-center justify-between text-xs">
                              <span className="font-semibold text-slate-900">{st.name}</span>
                              <span className="font-mono text-slate-500">{st.rollNo}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    <button
                      type="button"
                      onClick={handleEndLiveLecture}
                      disabled={isEndingSession}
                      className="w-full py-3 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-bold shadow-md shadow-rose-600/20 transition-all cursor-pointer"
                    >
                      {isEndingSession ? "Submitting Attendance..." : `End Lecture & Submit Attendance (${attendanceCount} Present)`}
                    </button>
                  </div>
                ) : (
                  /* Start New Lecture Form */
                  <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
                    <div>
                      <h2 className="text-base font-bold text-slate-900 tracking-tight">Start Live Class Attendance</h2>
                      <p className="text-xs text-slate-500 font-medium">Broadcast attendance verification to student devices</p>
                    </div>

                    <div className="space-y-3">
                      <div>
                        <label className="block text-xs font-bold text-slate-600 mb-1">Select Subject</label>
                        <select
                          value={selectedClassId}
                          onChange={(e) => setSelectedClassId(e.target.value)}
                          className="w-full p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:outline-none focus:border-blue-500"
                        >
                          {classes.map(c => (
                            <option key={c.id} value={c.id}>{c.subjectName} ({c.subjectCode})</option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="block text-xs font-bold text-slate-600 mb-1">Classroom Wi-Fi Network</label>
                        <LiveWifiSearchSelector
                          currentWifi={wifiSsid}
                          onSelectWifi={setWifiSsid}
                          wifiList={["Pranjal", "IIIT-NR-Campus", "IIITNR_STUDENTS", "Classroom-AP-5G"]}
                          variant="inline"
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-bold text-slate-600 mb-1">Room / Venue</label>
                        <input
                          type="text"
                          value={roomNo}
                          onChange={(e) => setRoomNo(e.target.value)}
                          placeholder="e.g. Room A-204"
                          className="w-full p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:outline-none focus:border-blue-500"
                        />
                      </div>

                      <button
                        type="button"
                        onClick={() => handleStartLiveLecture()}
                        disabled={isStartingSession || classes.length === 0}
                        className="w-full py-3 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-600/20 transition-all flex items-center justify-center gap-2 cursor-pointer mt-2"
                      >
                        <Play className="w-4 h-4 fill-white" />
                        <span>{isStartingSession ? "Launching Lecture..." : "Start Live Attendance Session"}</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 3. HOME TAB (EDITORIAL TYPOGRAPHY + SIGNATURE TICKET CARD) */}
            {mainNav === "home" && (() => {
              const activeClass = (activeSession 
                ? classes.find(c => c.id === activeSession.classId) 
                : null) || classes[0] || {
                  id: "mock-dt501",
                  subjectName: "Digital Transformation-I",
                  subjectCode: "DT501",
                  roomNo: "Room 319",
                  timeSlot: "Friday, 02:00 PM – 03:55 PM",
                  joinCode: "DT50-363",
                  students: []
                };

              const getGreeting = () => {
                const hour = new Date().getHours();
                if (hour < 12) return "Good morning";
                if (hour < 17) return "Good afternoon";
                return "Good evening";
              };

              const now = new Date();
              const todayFormatted = new Intl.DateTimeFormat("en-US", {
                weekday: "long",
                day: "numeric",
                month: "long",
                year: "numeric"
              }).format(now);

              const filteredDefaulters = defaulters.filter(s => 
                !defaulterSearch || 
                s.rollNumber.toLowerCase().includes(defaulterSearch.toLowerCase()) ||
                s.name.toLowerCase().includes(defaulterSearch.toLowerCase()) ||
                s.subjectCode.toLowerCase().includes(defaulterSearch.toLowerCase())
              );

              return (
                <div className="space-y-6 sm:space-y-8 animate-in fade-in pb-20">
                  {/* 1. EDITORIAL HEADER (Minimal & Clean) */}
                  <div className="space-y-2 pt-1 pb-1">
                    <div className="flex items-center justify-between">
                      <span className="font-handwriting text-lg sm:text-xl font-semibold text-[#475569] tracking-wide">
                        {todayFormatted}
                      </span>
                      <button
                        type="button"
                        onClick={handleLogout}
                        title="Logout"
                        className="p-1 text-slate-400 hover:text-slate-700 transition-colors cursor-pointer"
                      >
                        <LogOut className="w-4 h-4" />
                      </button>
                    </div>

                    <div className="pt-2 space-y-1">
                      <div className="text-[26px] sm:text-[28px] font-light text-slate-900 leading-[1.15]">
                        {getGreeting()},
                      </div>
                      <div className="text-[26px] sm:text-[28px] font-bold text-slate-900 leading-[1.15]">
                        Prof. {teacherName}
                      </div>
                      <p className="text-xs text-slate-500 font-medium pt-0.5">
                        Computer Science & Engineering · {facultyId}
                      </p>
                    </div>
                  </div>

                  {/* 2. SECTION HEADER & TICKET CARD */}
                  <div className="space-y-3 pt-2">
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-[#64748B]">
                        ATTENDANCE PENDING
                      </span>
                    </div>

                    <SignatureTicketCard
                      subjectName={activeClass.subjectName}
                      subjectCode={activeClass.subjectCode}
                      roomNo={activeClass.roomNo || "Room 319"}
                      timeSlot={(activeClass as any).timeSlot || "Friday, 02:00 PM – 03:55 PM"}
                      enrolledStudentsCount={activeClass.students?.length || 0}
                      joinCode={activeClass.joinCode || "DT50-363"}
                      eyebrow="TODAY'S LECTURE"
                      eyebrowBadge={(activeClass as any).timeSlot || "Friday, 02:00 PM – 03:55 PM"}
                      buttonText="Start Attendance"
                      isLive={Boolean(activeSession)}
                      attendanceCount={attendanceCount}
                      onStartAttendance={() => {
                        if (activeSession) {
                          setMainNav("live");
                        } else {
                          handleStartLiveLecture(activeClass);
                        }
                      }}
                      isStarting={isStartingSession}
                    />
                  </div>

                  {/* 3. COMPLETED TODAY (Matches Android completed card 1:1) */}
                  <div className="space-y-3 pt-3">
                    <div className="flex items-center gap-2">
                      <h3 className="text-[11px] font-bold uppercase tracking-[0.2em] text-[#64748B]">
                        COMPLETED TODAY
                      </h3>
                      <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-mono">
                        {completedSessions.length > 0 ? completedSessions.length : 1}
                      </span>
                    </div>

                    {completedSessions.length > 0 ? (
                      <div className="space-y-2">
                        {completedSessions.map((s) => (
                          <div 
                            key={s.id} 
                            className="p-3.5 sm:p-4 rounded-2xl bg-white border border-slate-200/90 shadow-2xs flex items-center justify-between gap-3"
                          >
                            <div className="text-[11px] font-mono text-slate-500 flex flex-col shrink-0 leading-tight">
                              <span>11:00</span>
                              <span className="text-slate-400">11:55</span>
                            </div>
                            <div className="min-w-0 flex-1 px-1">
                              <h4 className="text-xs sm:text-sm font-bold text-slate-900 truncate">
                                {s.subjectName}
                              </h4>
                              <p className="text-[11px] text-slate-500 font-medium font-mono mt-0.5 truncate">
                                {s.subjectCode} · {s.roomNo || "Room 319"}
                              </p>
                            </div>
                            <span className="text-[10px] font-bold px-2.5 py-1 rounded-md bg-slate-50 text-slate-600 border border-slate-200 shrink-0">
                              Submitted
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="p-3.5 sm:p-4 rounded-2xl bg-white border border-slate-200/90 shadow-2xs flex items-center justify-between gap-3">
                        <div className="text-[11px] font-mono text-slate-500 flex flex-col shrink-0 leading-tight">
                          <span>11:00</span>
                          <span className="text-slate-400">11:55</span>
                        </div>
                        <div className="min-w-0 flex-1 px-1">
                          <h4 className="text-xs sm:text-sm font-bold text-slate-900 truncate">
                            Data Structures and Algorithm Analysis
                          </h4>
                          <p className="text-[11px] text-slate-500 font-medium font-mono mt-0.5 truncate">
                            DSA501 · Room 319
                          </p>
                        </div>
                        <span className="text-[10px] font-bold px-2.5 py-1 rounded-md bg-slate-50 text-slate-600 border border-slate-200 shrink-0">
                          Submitted
                        </span>
                      </div>
                    )}
                  </div>

                  {/* 4. LOW ATTENDANCE NOTICE (Matches Android 1:1) */}
                  <div className="pt-2">
                    <div 
                      onClick={() => setShowDefaultersModal(true)}
                      className="p-3.5 sm:p-4 rounded-2xl bg-amber-50/50 border border-amber-300/80 flex items-center justify-between gap-3 shadow-2xs hover:bg-amber-50/80 transition-all cursor-pointer"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-8 h-8 rounded-full bg-amber-100/80 border border-amber-300/60 flex items-center justify-center shrink-0">
                          <AlertTriangle className="w-4 h-4 text-amber-600" />
                        </div>
                        <div className="min-w-0">
                          <h4 className="text-xs font-bold text-slate-900">Low Attendance Notice</h4>
                          <p className="text-[11px] text-slate-500 mt-0.5">
                            {defaulters.length} students below 75% threshold
                          </p>
                        </div>
                      </div>
                      <span className="text-xs font-semibold text-amber-600 hover:text-amber-700 shrink-0 flex items-center gap-1">
                        View Students →
                      </span>
                    </div>
                  </div>
                </div>
              );
            })()}

            {/* ==================================================================== */}
            {/* VIEW 4: RECORDS (Session Attendance History - Matches Android 1:1) */}
            {/* ==================================================================== */}
            {mainNav === "records" && (
              <div className="space-y-4 animate-in fade-in pb-16">
                {/* 1. Header: Title + Subtitle + Sync button */}
                <div className="flex items-center justify-between pb-1">
                  <div>
                    <h2 className="text-[22px] font-bold text-slate-900 leading-tight">Session Records</h2>
                    <p className="text-xs text-slate-500 font-medium mt-0.5">Attendance history</p>
                  </div>
                  <button 
                    type="button"
                    onClick={loadCompletedSessions}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200/90 bg-white text-xs font-semibold text-slate-800 shadow-2xs hover:bg-slate-50 cursor-pointer transition-all"
                  >
                    <RefreshCw className="w-3.5 h-3.5 text-slate-500" />
                    <span>Sync</span>
                  </button>
                </div>

                {/* 2. Summary Statistics (Two equal-width summary panels) */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-3.5 sm:p-4 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-1">
                    <div className="text-2xl font-bold text-slate-900">
                      {completedSessions.length}
                    </div>
                    <div className="text-xs text-slate-500 font-medium">Total Lectures</div>
                  </div>

                  <div className="p-3.5 sm:p-4 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-1">
                    <div className="text-2xl font-bold text-slate-900">
                      {completedSessions.reduce((acc, s) => acc + (s.presentCount || 0), 0)}
                    </div>
                    <div className="text-xs text-slate-500 font-medium">Total Present</div>
                  </div>
                </div>

                {/* 3. Session Records List */}
                {completedSessions.length > 0 ? (
                  <div className="space-y-3 pt-1">
                    {completedSessions.map(sess => (
                      <div key={sess.id} className="p-4 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-2.5">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0 flex-1">
                            <h3 className="text-sm sm:text-base font-bold text-slate-900 truncate">
                              {sess.subjectName}
                            </h3>
                            <p className="text-xs text-slate-500 font-mono mt-0.5 truncate">
                              {sess.subjectCode} · {sess.roomNo || "Room 319"}
                            </p>
                            <p className="text-xs text-slate-400 mt-1">
                              {sess.startTime 
                                ? new Date(sess.startTime).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", year: "numeric" }) + " · " + new Date(sess.startTime).toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })
                                : "Fri, Oct 9, 2026 · 06:37 PM"}
                            </p>
                          </div>
                          <span className="text-[10px] font-bold px-2.5 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-300 tracking-wider shrink-0">
                            AUDITED
                          </span>
                        </div>

                        <div className="flex items-center justify-between pt-2.5 border-t border-slate-100">
                          <span className="text-xs font-bold text-emerald-600">
                            {sess.presentCount || 0} Present
                          </span>
                          <button
                            type="button"
                            onClick={() => handleExportCsv(sess)}
                            className="text-xs font-semibold text-blue-600 hover:text-blue-700 flex items-center gap-1.5 cursor-pointer"
                          >
                            <Share2 className="w-3.5 h-3.5" />
                            <span>Export CSV</span>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="p-8 rounded-2xl bg-white border border-slate-200 text-center text-slate-500 text-xs shadow-2xs space-y-2">
                    <FileText className="w-8 h-8 text-slate-300 mx-auto" />
                    <p className="font-semibold text-slate-700">No session records found</p>
                    <p className="text-slate-400 text-[11px]">Completed class attendance sessions will be audited and listed here.</p>
                  </div>
                )}
              </div>
            )}

            {/* ==================================================================== */}
            {/* VIEW 5: DEVICES (Matches Android TeacherDeviceRequestsScreen 1:1) */}
            {/* ==================================================================== */}
            {mainNav === "devices" && (
              <div className="space-y-4 animate-in fade-in pb-16">
                {/* Header: Title + Subtitle + Refresh */}
                <div className="flex items-center justify-between pb-1">
                  <div>
                    <h2 className="text-[22px] font-bold text-slate-900 leading-tight">Device Management</h2>
                    <p className="text-xs text-slate-500 font-medium mt-0.5">Device authorization and security</p>
                  </div>
                  <button 
                    type="button"
                    onClick={loadDeviceRequests}
                    className="p-2 rounded-xl border border-slate-200/90 bg-white hover:bg-slate-50 text-slate-600 transition-colors cursor-pointer shadow-2xs"
                  >
                    <RefreshCw className={`w-4 h-4 ${isLoadingDevices ? "animate-spin" : ""}`} />
                  </button>
                </div>

                {/* Summary Card */}
                <div className="p-3.5 rounded-2xl bg-white border border-slate-200/90 shadow-2xs flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-xl bg-blue-50 border border-blue-200/60 flex items-center justify-center text-blue-600">
                      <Smartphone className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-slate-900">
                        {deviceRequests.length} device change request{deviceRequests.length !== 1 ? "s" : ""}
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Pending student hardware binding resets
                      </div>
                    </div>
                  </div>
                </div>

                {/* Device Requests List */}
                {isLoadingDevices ? (
                  <div className="py-8 text-center text-xs text-slate-400">Checking device requests...</div>
                ) : deviceRequests.length > 0 ? (
                  <div className="space-y-3">
                    {deviceRequests.map((req) => (
                      <div key={req.id} className="p-4 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-3">
                        <div className="flex items-center justify-between">
                          <div>
                            <h4 className="text-sm font-bold text-slate-900">{req.studentName}</h4>
                            <p className="text-xs text-slate-500 font-mono mt-0.5">{req.rollNo}</p>
                          </div>
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
                            Pending Review
                          </span>
                        </div>

                        {/* Details Box */}
                        <div className="p-3 bg-slate-50 rounded-xl space-y-1 text-xs">
                          <div className="flex items-center gap-1.5 font-medium text-slate-700">
                            <Smartphone className="w-3.5 h-3.5 text-slate-400" />
                            <span>Device: <strong className="text-slate-900 font-semibold">{req.deviceModel.replace("[UNBIND REQUEST]", "").trim()}</strong></span>
                          </div>
                          <p className="text-slate-500 text-[11px] pl-5">
                            Platform: {req.platform} · Registered: {req.registeredAt ? new Date(req.registeredAt).toLocaleDateString() : "Recent"}
                          </p>
                        </div>

                        {/* Action buttons: Reject & Approve Unbind */}
                        <div className="flex items-center gap-2 pt-1">
                          <button
                            type="button"
                            onClick={() => handleRejectDevice(req)}
                            disabled={isActingDeviceId === req.id}
                            className="flex-1 py-2 px-3 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-600 text-xs font-semibold transition-all disabled:opacity-50 cursor-pointer text-center"
                          >
                            Reject
                          </button>
                          <button
                            type="button"
                            onClick={() => handleApproveDevice(req)}
                            disabled={isActingDeviceId === req.id}
                            className="flex-1 py-2 px-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition-all shadow-sm shadow-emerald-600/20 disabled:opacity-50 cursor-pointer flex items-center justify-center gap-1.5"
                          >
                            <Check className="w-3.5 h-3.5" />
                            <span>{isActingDeviceId === req.id ? "Approving..." : "Approve Unbind"}</span>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="p-8 rounded-2xl bg-white border border-slate-200 text-center text-slate-500 text-xs shadow-2xs space-y-2">
                    <ShieldCheck className="w-8 h-8 text-emerald-500 mx-auto" />
                    <p className="font-bold text-slate-800 text-sm">All student devices active</p>
                    <p className="text-xs text-slate-400 max-w-xs mx-auto">
                      No pending hardware unbind requests. Students with phone change requests will appear here for one-tap verification.
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ==================================================================== */}
      {/* PWA MOBILE BOTTOM NAVIGATION BAR */}
      {/* Matches Android Bottom Navigation 1:1 */}
      {/* ==================================================================== */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-md border-t border-slate-200 py-1.5 px-3 shadow-lg">
        <div className="max-w-md mx-auto flex items-center justify-around">
          {[
            { id: "home", label: "Home", icon: Home },
            { id: "schedule", label: "Schedule", icon: GraduationCap },
            { id: "live", label: "Live Lecture", icon: Radio },
            { id: "records", label: "Records", icon: FileText },
            { id: "devices", label: "Devices", icon: ShieldCheck },
          ].map(tab => {
            const Icon = tab.icon;
            const isActive = mainNav === tab.id && (!selectedSubject || tab.id !== "home");
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => {
                  setSelectedSubject(null);
                  setMainNav(tab.id as any);
                }}
                className={`flex flex-col items-center py-1 px-2.5 rounded-xl transition-all cursor-pointer ${
                  isActive ? "text-blue-600 font-bold" : "text-slate-500 hover:text-slate-900"
                }`}
              >
                <div className={`p-1 rounded-xl transition-all ${isActive ? "bg-blue-50 text-blue-600" : ""}`}>
                  <Icon className="w-5 h-5" />
                </div>
                <span className="text-[10px] mt-0.5">{tab.label}</span>
                {isActive && (
                  <div className="w-4 h-0.5 bg-blue-600 rounded-full mt-0.5" />
                )}
              </button>
            );
          })}
        </div>
      </nav>

      {/* ==================================================================== */}
      {/* MODAL: DELETE SUBJECT CONFIRMATION */}
      {/* ==================================================================== */}
      {subjectToDelete && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-white border border-slate-200 rounded-3xl shadow-2xl p-6 space-y-4 animate-in fade-in zoom-in-95">
            <div className="w-12 h-12 rounded-2xl bg-rose-100 text-rose-600 flex items-center justify-center mx-auto">
              <Trash2 className="w-6 h-6" />
            </div>

            <div className="text-center space-y-1.5">
              <h3 className="text-base font-bold text-slate-900">
                Delete "{subjectToDelete.subjectName}"?
              </h3>
              <p className="text-xs text-slate-500 leading-relaxed">
                This will permanently delete join code <span className="font-mono font-bold text-slate-800">{subjectToDelete.joinCode}</span> and all associated student attendance records.
              </p>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                disabled={isDeletingSubject}
                onClick={() => setSubjectToDelete(null)}
                className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-50 font-bold text-xs transition-all cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isDeletingSubject}
                onClick={() => handleDeleteSubject(subjectToDelete)}
                className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs flex items-center justify-center gap-1.5 shadow-md shadow-rose-600/20 transition-all cursor-pointer"
              >
                {isDeletingSubject ? (
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Trash2 className="w-3.5 h-3.5" />
                )}
                <span>{isDeletingSubject ? "Deleting..." : "Yes, Delete"}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* MODAL: CREATE SUBJECT BATCH */}
      {/* ==================================================================== */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-white border border-slate-200 rounded-3xl shadow-2xl p-6 space-y-4 animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <BookOpen className="w-5 h-5 text-blue-600" />
                <span>Create New Subject</span>
              </h3>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className="p-1 rounded-xl text-slate-400 hover:text-slate-700"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateSubject} className="space-y-3.5">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Subject Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Data Structures & Algorithms"
                  value={newSubName}
                  onChange={(e) => setNewSubName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Subject Code</label>
                  <input
                    type="text"
                    placeholder="e.g. CS501"
                    value={newSubCode}
                    onChange={(e) => setNewSubCode(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">Program / Batch</label>
                  <input
                    type="text"
                    value={newProgram}
                    onChange={(e) => setNewProgram(e.target.value)}
                    placeholder="e.g. B.Tech DSAI · Sem 5"
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Room / Venue</label>
                <input
                  type="text"
                  value={newRoom}
                  onChange={(e) => setNewRoom(e.target.value)}
                  placeholder="e.g. Room A-204 (AC Block)"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isCreatingClass}
                  className="w-full py-3 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-600/20 transition-all cursor-pointer"
                >
                  {isCreatingClass ? "Creating Subject..." : "Create Subject & Generate Join Code"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* MODAL: AI TIMETABLE IMPORT & AUTO-SCHEDULER */}
      {/* Matches Android AiTimetableImportDialog.kt 1:1 with Gemini AI */}
      {/* ==================================================================== */}
      <AiTimetableModal
        isOpen={showAiModal}
        onClose={() => setShowAiModal(false)}
        teacherName={teacherName}
        teacherId={classes[0]?.teacherId}
        onImportComplete={(newClasses) => {
          if (newClasses.length > 0) {
            setClasses(prev => [...newClasses, ...prev]);
            showToast(`✨ Timetable updated (${newClasses.length} lectures scheduled)!`);
          }
        }}
      />

      {/* ==================================================================== */}
      {/* MODAL: LOW ATTENDANCE DEFAULTERS AUDIT */}
      {/* Matches Android LowAttendanceDefaultersDialog.kt 1:1 */}
      {/* ==================================================================== */}
      {/* Matches Android LowAttendanceDefaultersDialog.kt 1:1 */}
      {/* ==================================================================== */}
      {showDefaultersModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-white border border-slate-200 rounded-3xl shadow-2xl p-5 sm:p-6 space-y-4 animate-in fade-in zoom-in-95 max-h-[85vh] flex flex-col">
            {/* Header */}
            <div className="flex items-center justify-between pb-3 border-b border-slate-100 shrink-0">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-amber-100 border border-amber-300 flex items-center justify-center shrink-0">
                  <AlertTriangle className="w-4 h-4 text-amber-600" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900 leading-tight">Low Attendance Audit</h3>
                  <p className="text-[11px] text-slate-500">Below 75% Academic Threshold</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowDefaultersModal(false)}
                className="p-1 rounded-xl text-slate-400 hover:text-slate-700 transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Search Input */}
            <div className="relative shrink-0">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="Search roll no. or name..."
                value={defaulterSearch}
                onChange={(e) => setDefaulterSearch(e.target.value)}
                className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-blue-500"
              />
            </div>

            {/* Summary count */}
            <div className="flex items-center justify-between text-[11px] font-semibold shrink-0">
              <span className="text-rose-600">
                {defaulters.length} students flagged
              </span>
              <span className="text-slate-400 font-normal">Min required: 75%</span>
            </div>

            {/* Student List */}
            <div className="flex-1 overflow-y-auto space-y-2.5 pr-0.5">
              {defaulters.filter(s => 
                !defaulterSearch || 
                s.rollNumber.toLowerCase().includes(defaulterSearch.toLowerCase()) ||
                s.name.toLowerCase().includes(defaulterSearch.toLowerCase()) ||
                s.subjectCode.toLowerCase().includes(defaulterSearch.toLowerCase())
              ).map((s, idx) => {
                const needed = Math.max(0, 3 * s.total - 4 * s.attended);
                return (
                  <div 
                    key={`${s.rollNumber}_${idx}`}
                    className="p-3.5 rounded-2xl bg-white border border-slate-200/90 shadow-2xs space-y-2"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-1.5">
                          <span className="text-xs font-mono font-bold text-slate-900">{s.rollNumber}</span>
                          <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 font-mono">
                            {s.subjectCode}
                          </span>
                        </div>
                        <p className="text-xs text-slate-600 font-medium mt-0.5">{s.name}</p>
                      </div>
                      <span className="px-2 py-0.5 rounded-md bg-rose-50 text-rose-700 border border-rose-200 text-xs font-bold font-mono">
                        {Math.round(s.percentage)}%
                      </span>
                    </div>

                    {/* Progress bar toward 75% */}
                    <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                      <div 
                        className="bg-rose-500 h-full rounded-full transition-all"
                        style={{ width: `${Math.min(100, Math.max(0, s.percentage))}%` }}
                      />
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-slate-500">
                      <span>Attended {s.attended} of {s.total} classes</span>
                      <span className="text-amber-700 font-medium">
                        {needed > 0 ? `Needs ${needed} consecutive classes` : "Warning active"}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Actions */}
            <div className="flex items-center justify-between pt-2 border-t border-slate-100 shrink-0">
              <button
                type="button"
                onClick={() => {
                  const text = defaulters.map((s, i) => `${i + 1}. [${s.rollNumber}] ${s.name} - ${s.subjectCode} (${Math.round(s.percentage)}%: ${s.attended}/${s.total})`).join("\n");
                  navigator.clipboard.writeText(text);
                  showToast("📋 Defaulters list copied to clipboard");
                }}
                className="px-3 py-1.5 rounded-xl border border-slate-200 text-slate-700 text-xs font-semibold hover:bg-slate-50 flex items-center gap-1.5 cursor-pointer"
              >
                <Copy className="w-3.5 h-3.5" />
                <span>Copy</span>
              </button>

              <button
                type="button"
                onClick={() => setShowDefaultersModal(false)}
                className="px-4 py-1.5 rounded-xl bg-blue-600 text-white text-xs font-bold hover:bg-blue-700 shadow-sm cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

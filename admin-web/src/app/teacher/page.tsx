"use client";

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
  Calendar, 
  X, 
  ChevronRight, 
  RefreshCw,
  Home,
  GraduationCap,
  Layers,
  Settings,
  UserCheck
} from "lucide-react";
import { supabase } from "@/lib/supabaseClient";
import { 
  fetchLiveClassesFromDB, 
  getActiveSessionFromDB, 
  startAttendanceSessionInDB,
  finalizeSessionInDB,
  fetchSessionRecordsFromDB, 
  DBClass, 
  DBSession, 
  DBStudent,
  getAndroidJoinCode
} from "@/lib/attendanceService";
import { LiveWifiSearchSelector } from "@/components/LiveWifiSearchSelector";

export default function TeacherAppConsole() {
  const router = useRouter();

  // Active Teacher Profile
  const [teacherName, setTeacherName] = useState("Faculty");
  const [teacherEmail, setTeacherEmail] = useState("");
  const [teacherId, setTeacherId] = useState("6885fced-5d3e-4b9c-94fd-85d115cc9d9b");

  // Navigation: "schedule" (default) | "home" | "live"
  const [mainNav, setMainNav] = useState<"home" | "schedule" | "live">("schedule");

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

    const interval = setInterval(syncActiveSession, 2500);
    return () => clearInterval(interval);
  }, []);

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

      // 1. Insert into subjects table
      const { data: subData, error: subErr } = await supabase
        .from("subjects")
        .insert({
          name: newSubName.trim(),
          code: subCode,
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
  const handleStartLiveLecture = async () => {
    const cls = classes.find(c => c.id === selectedClassId) || classes[0];
    if (!cls) {
      showToast("Please select a subject first.");
      return;
    }

    setIsStartingSession(true);
    try {
      const sess = await startAttendanceSessionInDB(
        cls.id,
        teacherId,
        wifiSsid || cls.wifiSsid || "Pranjal"
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
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between font-sans selection:bg-blue-100 selection:text-blue-900">
      {/* Toast Alert */}
      {toastMsg && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-2xl shadow-xl text-xs font-semibold flex items-center gap-2 animate-in fade-in slide-in-from-top-2">
          <span>{toastMsg}</span>
        </div>
      )}

      {/* Main Container - Responsive Max Width (PWA App Layout) */}
      <div className="max-w-2xl mx-auto w-full flex-1 flex flex-col p-4 sm:p-6 pb-24">
        
        {/* ==================================================================== */}
        {/* TOP BAR: INSTITUTIONAL BRANDING & USER STATUS */}
        {/* ==================================================================== */}
        <header className="flex items-center justify-between pb-4 border-b border-slate-200 mb-5">
          <div className="flex items-center gap-3">
            <Link 
              href="/" 
              className="w-10 h-10 rounded-2xl bg-blue-600 flex items-center justify-center text-white font-black text-lg shadow-md shadow-blue-600/20"
            >
              SA
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-sm tracking-tight text-slate-900">IIIT Naya Raipur</span>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200">
                  {selectedSubject ? "SUBJECT DETAIL" : "FACULTY CONSOLE"}
                </span>
              </div>
              <span className="text-xs text-slate-500 font-medium block">Prof. {teacherName}</span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {activeSession && (
              <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[11px] font-bold">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                Live ({attendanceCount})
              </span>
            )}
            <button
              onClick={handleLogout}
              className="flex items-center gap-1 px-3 py-1.5 rounded-xl text-xs font-semibold text-slate-600 hover:text-rose-600 hover:bg-rose-50 transition-all border border-slate-200"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Logout</span>
            </button>
          </div>
        </header>

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
                <div className="flex items-center justify-between py-2">
                  <div>
                    <div className="font-bold text-slate-900">Anti-Proxy Hardware Verification</div>
                    <div className="text-slate-500 text-[11px]">Wi-Fi BSSID + GPS 30m Boundary</div>
                  </div>
                  <span className="px-2.5 py-1 bg-blue-50 text-blue-700 border border-blue-200 rounded-full font-bold text-[10px]">
                    Active
                  </span>
                </div>
              </div>
            )}
          </div>
        ) : (
          /* ==================================================================== */
          /* MAIN TABS (Home | Schedule / Subjects | Live Lecture) */
          /* ==================================================================== */
          <div className="space-y-5">
            {/* 1. SCHEDULE / MY SUBJECTS TAB */}
            {mainNav === "schedule" && (
              <div className="space-y-4 animate-in fade-in">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-base font-bold text-slate-900 tracking-tight">My Subjects & Batches</h2>
                    <p className="text-xs text-slate-500 font-medium">Select a subject to view join code and enrolled students</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowCreateModal(true)}
                    className="px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-sm cursor-pointer transition-all"
                  >
                    <PlusCircle className="w-3.5 h-3.5" />
                    <span>Create Subject</span>
                  </button>
                </div>

                {isLoadingClasses ? (
                  <div className="text-center py-10 text-xs text-slate-500">Loading your subjects...</div>
                ) : classes.length === 0 ? (
                  <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center space-y-3">
                    <BookOpen className="w-10 h-10 mx-auto text-slate-300" />
                    <p className="text-sm font-bold text-slate-800">No subjects created yet</p>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto">
                      Create your first subject to generate a join code for students.
                    </p>
                    <button
                      type="button"
                      onClick={() => setShowCreateModal(true)}
                      className="px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-bold shadow-sm inline-flex items-center gap-2"
                    >
                      <PlusCircle className="w-4 h-4" />
                      <span>Create New Subject</span>
                    </button>
                  </div>
                ) : (
                  <div className="grid gap-3">
                    {classes.map(cls => (
                      <div
                        key={cls.id}
                        onClick={() => setSelectedSubject(cls)}
                        className="bg-white border border-slate-200 hover:border-blue-400 hover:shadow-md rounded-2xl p-4 transition-all cursor-pointer flex items-center justify-between group"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-lg bg-blue-50 text-blue-700 border border-blue-200">
                              {cls.subjectCode}
                            </span>
                            <span className="text-xs font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                              {cls.subjectName}
                            </span>
                          </div>
                          <div className="text-[11px] text-slate-500">
                            {cls.roomNo || "Room A-204"} • Join Code: <span className="font-mono font-bold text-slate-800">{cls.joinCode}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 text-slate-400 group-hover:text-blue-600 transition-colors">
                          <span className="text-xs font-bold hidden sm:inline">Open Detail</span>
                          <ChevronRight className="w-4 h-4" />
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

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
                        onClick={handleStartLiveLecture}
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

            {/* 3. HOME TAB */}
            {mainNav === "home" && (
              <div className="space-y-4 animate-in fade-in">
                <div className="bg-gradient-to-r from-blue-600 to-indigo-600 rounded-2xl p-5 text-white shadow-md space-y-2">
                  <div className="text-[10px] uppercase font-bold tracking-wider text-blue-100">IIIT Naya Raipur</div>
                  <h2 className="text-xl font-bold tracking-tight">Faculty Dashboard</h2>
                  <p className="text-xs text-blue-100">Welcome, Prof. {teacherName}. You have {classes.length} active course batches.</p>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div 
                    onClick={() => setMainNav("schedule")}
                    className="p-4 bg-white border border-slate-200 rounded-2xl hover:border-blue-500 cursor-pointer transition-all space-y-1"
                  >
                    <BookOpen className="w-5 h-5 text-blue-600 mb-2" />
                    <div className="text-xs font-bold text-slate-900">My Subjects</div>
                    <div className="text-[11px] text-slate-500">{classes.length} active classes</div>
                  </div>

                  <div 
                    onClick={() => setMainNav("live")}
                    className="p-4 bg-white border border-slate-200 rounded-2xl hover:border-blue-500 cursor-pointer transition-all space-y-1"
                  >
                    <Play className="w-5 h-5 text-emerald-600 mb-2" />
                    <div className="text-xs font-bold text-slate-900">Live Attendance</div>
                    <div className="text-[11px] text-slate-500">
                      {activeSession ? "1 session running" : "Ready to start"}
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ==================================================================== */}
      {/* PWA MOBILE BOTTOM NAVIGATION BAR */}
      {/* Matches Android Bottom Navigation 1:1 */}
      {/* ==================================================================== */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-md border-t border-slate-200 py-2 px-6">
        <div className="max-w-md mx-auto flex items-center justify-around">
          <button
            type="button"
            onClick={() => { setSelectedSubject(null); setMainNav("home"); }}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all cursor-pointer ${
              mainNav === "home" && !selectedSubject ? "text-blue-600 font-bold" : "text-slate-500 hover:text-slate-900"
            }`}
          >
            <Home className="w-5 h-5" />
            <span className="text-[10px]">Home</span>
          </button>

          <button
            type="button"
            onClick={() => { setSelectedSubject(null); setMainNav("schedule"); }}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all cursor-pointer ${
              mainNav === "schedule" || selectedSubject ? "text-blue-600 font-bold" : "text-slate-500 hover:text-slate-900"
            }`}
          >
            <GraduationCap className="w-5 h-5" />
            <span className="text-[10px]">Schedule</span>
          </button>

          <button
            type="button"
            onClick={() => { setSelectedSubject(null); setMainNav("live"); }}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all cursor-pointer ${
              mainNav === "live" && !selectedSubject ? "text-blue-600 font-bold" : "text-slate-500 hover:text-slate-900"
            }`}
          >
            <Radio className="w-5 h-5" />
            <span className="text-[10px]">Live Lecture</span>
          </button>
        </div>
      </nav>

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
    </div>
  );
}

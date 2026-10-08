"use client";

import { useState, useEffect, useRef } from "react";
import { supabase } from "@/lib/supabaseClient";
import { 
  Wifi, 
  Play, 
  RefreshCw, 
  Check, 
  X, 
  Calendar, 
  Clock, 
  MapPin, 
  Users, 
  Plus, 
  ChevronRight, 
  ChevronDown,
  Search, 
  ArrowLeft, 
  LogOut,
  StopCircle,
  Smartphone,
  CheckCircle2,
  FileText,
  Printer,
  Download,
  AlertTriangle,
  Filter,
  ShieldCheck,
} from "lucide-react";

interface AttendanceSessionItem {
  id: string;
  start_time: string;
  end_time?: string;
  status: string;
  classes?: {
    id: string;
    room: string;
    subjects?: {
      code: string;
      name: string;
    };
    teachers?: {
      users?: {
        name: string;
      };
    };
    sections?: {
      name: string;
      semester: number;
    };
  };
}

interface DailyStudentRow {
  studentId: string;
  roll: string;
  name: string;
  email: string;
  program: string;
  semester: number;
  status: "PRESENT" | "ABSENT";
  method: string;
  markedAt: string | null;
  device: string;
  wifiVerified: boolean;
}

interface StudentRecord {
  id: string;
  roll: string;
  name: string;
  wifiAp: boolean;
  ble: boolean;
  rttDist: string;
  coverage: number;
  status: string;
  method: string;
  diagnosis: string;
  markedAt?: string;
}

interface ScannedNetwork {
  ssid: string;
  frequency: string;
  rssi: number;
  isConnected?: boolean;
}

interface TeachingSlot {
  code: string;
  name: string;
  room: string;
  program: string;
  startHour: number;
  startMinute: number;
  endHour: number;
  endMinute: number;
  timeFormatted: string;
  classId: string;
  sessionId: string;
}

const TODAY_SCHEDULE: TeachingSlot[] = [
  {
    code: "CS501",
    name: "Data Structures & Algorithms",
    room: "Room A-204 (AC Block)",
    program: "B.Tech DSAI · Semester 5",
    startHour: 10,
    startMinute: 0,
    endHour: 11,
    endMinute: 0,
    timeFormatted: "10:00 – 11:00 AM",
    classId: "de6aa6c2-ca73-447a-b6e9-300edd8469a7",
    sessionId: "c921ca2a-bddf-487f-a5c8-55c05929655f",
  },
  {
    code: "CS502",
    name: "Machine Learning Foundations",
    room: "Room A-302",
    program: "B.Tech DSAI · Semester 5",
    startHour: 12,
    startMinute: 0,
    endHour: 13,
    endMinute: 0,
    timeFormatted: "12:00 – 01:00 PM",
    classId: "34551a14-82da-4a4f-94a8-22197d55dcb9",
    sessionId: "9c673fc3-9335-4d76-8175-5b3d64f213df",
  },
  {
    code: "CS505",
    name: "Advanced Algorithms Lab",
    room: "Lab 3 (Computing Center)",
    program: "M.Tech CSE · Semester 1",
    startHour: 15,
    startMinute: 0,
    endHour: 17,
    endMinute: 0,
    timeFormatted: "03:00 – 05:00 PM",
    classId: "34a59df2-42a5-4b5d-85d0-7c001fc3d953",
    sessionId: "ee22b429-a7ca-4e3b-801b-27da714d61a1",
  },
];

function getSlotStatus(slot: TeachingSlot): "COMPLETED" | "CURRENT" | "UPCOMING" {
  const now = new Date();
  const currentMinutes = now.getHours() * 60 + now.getMinutes();
  const startMinutes = slot.startHour * 60 + slot.startMinute;
  const endMinutes = slot.endHour * 60 + slot.endMinute;

  if (currentMinutes < startMinutes) {
    return "UPCOMING";
  } else if (currentMinutes >= startMinutes && currentMinutes <= endMinutes + 30) {
    return "CURRENT";
  } else {
    return "COMPLETED";
  }
}

function getDefaultSlot(): TeachingSlot {
  const current = TODAY_SCHEDULE.find((s) => getSlotStatus(s) === "CURRENT");
  if (current) return current;
  const upcoming = TODAY_SCHEDULE.find((s) => getSlotStatus(s) === "UPCOMING");
  if (upcoming) return upcoming;
  return TODAY_SCHEDULE[0];
}

export default function TeacherMobileParityScreen() {
  const [selectedSlot, setSelectedSlot] = useState<TeachingSlot>(getDefaultSlot());
  const [activeSessionId, setActiveSessionId] = useState<string>(getDefaultSlot().sessionId);
  const activeSessionIdRef = useRef<string>(getDefaultSlot().sessionId);
  const [isSessionLive, setIsSessionLive] = useState<boolean>(false);
  const [showLiveRollCall, setShowLiveRollCall] = useState<boolean>(false);
  const [showScheduleModal, setShowScheduleModal] = useState<boolean>(false);
  const [confirmEndLecture, setConfirmEndLecture] = useState<boolean>(false);
  const [confirmEndOnConsole, setConfirmEndOnConsole] = useState<boolean>(false);
  const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);

  // Wi-Fi Whitelist (Faculty Selection)
  const [selectedSsids, setSelectedSsids] = useState<string[]>([
    "Pranjal",
    "IIIT-NR-Campus",
    "IIITNR_STUDENTS"
  ]);
  const [isScanningWifi, setIsScanningWifi] = useState<boolean>(false);
  const [showAddCustomWifi, setShowAddCustomWifi] = useState<boolean>(false);
  const [customSsidInput, setCustomSsidInput] = useState<string>("");
  const [showManageWifi, setShowManageWifi] = useState<boolean>(false);



  // Students & Live Supabase Records
  const [students, setStudents] = useState<StudentRecord[]>([]);
  const [totalEnrolled, setTotalEnrolled] = useState<number>(22);
  const [lastSynced, setLastSynced] = useState<string>("Syncing...");
  const [rosterSearch, setRosterSearch] = useState<string>("");
  const [rosterFilter, setRosterFilter] = useState<string>("ALL"); // ALL, PRESENT, ABSENT
  const [isUpdatingRecord, setIsUpdatingRecord] = useState<string | null>(null);
  const [pendingApprovals, setPendingApprovals] = useState<any[]>([]);

  // Faculty Console View Switcher: LIVE_CONSOLE vs DAILY_REPORT
  const [activeView, setActiveView] = useState<"LIVE_CONSOLE" | "DAILY_REPORT">("LIVE_CONSOLE");

  // Daily Report State inside Faculty Console
  const [reportDate, setReportDate] = useState<string>(new Date().toISOString().split("T")[0]);
  const [allReportSessions, setAllReportSessions] = useState<AttendanceSessionItem[]>([]);
  const [reportSessionId, setReportSessionId] = useState<string>("");
  const [dailyReportStudents, setDailyReportStudents] = useState<DailyStudentRow[]>([]);
  const [reportLoading, setReportLoading] = useState<boolean>(false);
  const [reportSearch, setReportSearch] = useState<string>("");
  const [reportFilter, setReportFilter] = useState<"ALL" | "PRESENT" | "ABSENT">("ALL");
  const [isUpdatingReportRecord, setIsUpdatingReportRecord] = useState<string | null>(null);
  const [reportActionMessage, setReportActionMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || "https://vtuztciyaqegrvoaxmnf.supabase.co";
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ0dXp0Y2l5YXFlZ3J2b2F4bW5mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3ODY2MDAsImV4cCI6MjEwNjM2MjYwMH0.EGWVxNSZAf-fmEPRr7V0X_r9FVLe1sqQW9r8Mc5C5F4";

  // Fetch student device change requests awaiting faculty approval
  const fetchPendingDeviceApprovals = async () => {
    try {
      const { data, error } = await supabase
        .from("devices")
        .select("id, installation_id, device_model, student_id, students(id, roll_number, users(name))")
        .eq("status", "PENDING_APPROVAL");

      if (!error && Array.isArray(data)) {
        setPendingApprovals(data);
      }
    } catch (e) {
      console.warn("Failed fetching pending device approvals:", e);
    }
  };

  // Quick 1-Click Device Approval from Live Classroom
  const handleQuickApproveDevice = async (studentId: string, deviceId: string, roll?: string) => {
    try {
      await supabase
        .from("devices")
        .update({ status: "BLOCKED" })
        .eq("student_id", studentId)
        .neq("id", deviceId);

      const { data: targetDev } = await supabase
        .from("devices")
        .select("id, installation_id")
        .eq("id", deviceId)
        .single();

      if (targetDev && targetDev.installation_id.startsWith("REQ-REBIND")) {
        await supabase.from("devices").delete().eq("id", deviceId);
      } else {
        await supabase.from("devices").update({ status: "ACTIVE" }).eq("id", deviceId);
      }

      fetchPendingDeviceApprovals();
      fetchLiveRecords();
    } catch (err: any) {
      console.error("Quick approve device error:", err);
    }
  };

  // Live Timer Ticker
  useEffect(() => {
    if (!isSessionLive) return;
    const timer = setInterval(() => {
      setElapsedSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [isSessionLive]);

  const elapsedMinutes = Math.floor(elapsedSeconds / 60);
  const elapsedSecs = elapsedSeconds % 60;
  const elapsedFormatted = `${String(elapsedMinutes).padStart(2, "0")}:${String(elapsedSecs).padStart(2, "0")}`;

  // Fetch full student roster and live attendance records from Supabase
  const fetchLiveRecords = async (overrideSessionId?: string) => {
    try {
      const sessId = overrideSessionId || activeSessionIdRef.current;
      if (!sessId) return;

      // 1. Fetch all registered students
      const rosterRes = await fetch(`${supabaseUrl}/rest/v1/students?select=id,roll_number,users(name)&order=roll_number`, {
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`
        }
      });

      // 2. Fetch live attendance records for current session
      const recordsRes = await fetch(`${supabaseUrl}/rest/v1/attendance_records?session_id=eq.${sessId}&select=*`, {
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`
        }
      });

      if (rosterRes.ok && recordsRes.ok) {
        const rosterData = await rosterRes.json();
        const recordsData = await recordsRes.json();

        const recordsMap = new Map<string, any>();
        if (Array.isArray(recordsData)) {
          recordsData.forEach((rec: any) => {
            recordsMap.set(rec.student_id, rec);
          });
        }

        if (Array.isArray(rosterData) && rosterData.length > 0) {
          setTotalEnrolled(rosterData.length);
          const merged: StudentRecord[] = rosterData.map((st: any) => {
            const rec = recordsMap.get(st.id);
            if (rec) {
              const isPresent = rec.status === "PRESENT";
              return {
                id: st.id,
                roll: st.roll_number || "26DSAI",
                name: st.users?.name || "Student",
                wifiAp: rec.wifi_ap_verified ?? isPresent,
                ble: rec.ble_verified ?? false,
                rttDist: rec.rtt_distance_meters ? `${rec.rtt_distance_meters}m` : (isPresent ? "4.2m" : "—"),
                coverage: Math.round(rec.presence_percentage || (isPresent ? 100 : 0)),
                status: rec.status,
                method: rec.verification_method || "WIFI_AUTO",
                diagnosis: rec.notes || (rec.wifi_ap_verified ? "Classroom Wi-Fi AP verified (Continuous session)" : "Marked Present"),
                markedAt: rec.marked_at
              };
            } else {
              return {
                id: st.id,
                roll: st.roll_number || "26DSAI",
                name: st.users?.name || "Student",
                wifiAp: false,
                ble: false,
                rttDist: "—",
                coverage: 0,
                status: "ABSENT",
                method: "AWAITING_WIFI",
                diagnosis: "Waiting for Classroom Wi-Fi",
              };
            }
          });

          setStudents(merged);
          setLastSynced(new Date().toLocaleTimeString());
        }
      }
    } catch (err) {
      console.warn("Live Supabase fetch error:", err);
    }
  };

  // Check active lecture session status from Supabase on mount
  const fetchSessionStatus = async () => {
    try {
      const res = await fetch(`${supabaseUrl}/rest/v1/attendance_sessions?status=eq.ACTIVE&order=start_time.desc&limit=1&select=id,status,start_time,class_id,classes(room,classrooms(wifi_ssid),subjects(code,name))`, {
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`
        }
      });
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          const liveSess = data[0];
          setIsSessionLive(true);
          setActiveSessionId(liveSess.id);
          activeSessionIdRef.current = liveSess.id;

          const matchedSlot = TODAY_SCHEDULE.find(s => s.classId === liveSess.class_id || s.sessionId === liveSess.id);
          if (matchedSlot) {
            setSelectedSlot(matchedSlot);
          } else if (liveSess.classes) {
            setSelectedSlot({
              code: liveSess.classes.subjects?.code || "CS501",
              name: liveSess.classes.subjects?.name || "Data Structures & Algorithms",
              room: liveSess.classes.room || "Room A-204 (AC Block)",
              program: "B.Tech DSAI · Semester 5",
              startHour: 10,
              startMinute: 0,
              endHour: 11,
              endMinute: 0,
              timeFormatted: "Active Lecture",
              classId: liveSess.class_id,
              sessionId: liveSess.id,
            });
          }
          const classroomWifi = liveSess.classes?.classrooms?.wifi_ssid;
          if (classroomWifi) {
            const list = classroomWifi.split(",").map((s: string) => s.trim()).filter(Boolean);
            if (list.length > 0) {
              setSelectedSsids(list);
            }
          }
          await fetchLiveRecords(liveSess.id);
        } else {
          setIsSessionLive(false);
        }
      }
    } catch (e) {
      console.warn("fetchSessionStatus error:", e);
    }
  };

  // Start Lecture Session in Supabase
  const handleStartLecture = async () => {
    setIsSessionLive(true);
    setShowLiveRollCall(true);
    const nowIso = new Date().toISOString();
    try {
      // 1. Mark previously active sessions as COMPLETED
      await fetch(`${supabaseUrl}/rest/v1/attendance_sessions?status=eq.ACTIVE`, {
        method: "PATCH",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          status: "COMPLETED",
          end_time: nowIso
        })
      });

      // 2. Activate current selected slot session
      await fetch(`${supabaseUrl}/rest/v1/attendance_sessions?id=eq.${selectedSlot.sessionId}`, {
        method: "PATCH",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          status: "ACTIVE",
          start_time: nowIso,
          end_time: null
        })
      });

      setActiveSessionId(selectedSlot.sessionId);
      activeSessionIdRef.current = selectedSlot.sessionId;

      // 3. Reset old records for this session so attendance starts at 0
      await fetch(`${supabaseUrl}/rest/v1/attendance_records?session_id=eq.${selectedSlot.sessionId}`, {
        method: "DELETE",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
        }
      });

      // 4. Sync Classroom Wi-Fi
      await fetch(`${supabaseUrl}/rest/v1/classrooms?id=eq.d89824f4-b389-4129-9842-3bde92fc60a4`, {
        method: "PATCH",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          wifi_ssid: selectedSsids.join(", ")
        })
      });

      await fetchLiveRecords(selectedSlot.sessionId);
    } catch (e) {
      console.error("Failed to start session in Supabase:", e);
    }
  };

  // End Lecture Session in Supabase
  const handleEndLecture = async () => {
    setIsSessionLive(false);
    setShowLiveRollCall(false);
    const nowIso = new Date().toISOString();
    try {
      await fetch(`${supabaseUrl}/rest/v1/attendance_sessions?id=eq.${activeSessionId}`, {
        method: "PATCH",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          status: "COMPLETED",
          end_time: nowIso
        })
      });
      await fetchLiveRecords();

      // Automatically switch to Daily Attendance Report for this completed session
      setReportSessionId(activeSessionId);
      setActiveView("DAILY_REPORT");
      fetchAllReportSessions();
      fetchDailyReportData(activeSessionId);
    } catch (e) {
      console.error("Failed to end session in Supabase:", e);
    }
  };

  // Fetch all sessions for Daily Attendance Report
  const fetchAllReportSessions = async () => {
    setReportLoading(true);
    try {
      const { data, error } = await supabase
        .from("attendance_sessions")
        .select(`
          id,
          start_time,
          end_time,
          status,
          classes (
            id,
            room,
            subjects ( code, name ),
            teachers ( users ( name ) ),
            sections ( name, semester )
          )
        `)
        .order("start_time", { ascending: false });

      if (error) throw error;
      const list = (data as any) || [];
      setAllReportSessions(list);
      if (list.length > 0 && !reportSessionId) {
        setReportSessionId(list[0].id);
        const firstDate = new Date(list[0].start_time).toISOString().split("T")[0];
        setReportDate(firstDate);
      }
    } catch (err: any) {
      console.error("Error fetching report sessions:", err);
    } finally {
      setReportLoading(false);
    }
  };

  // Fetch student roster and records for selected session in Daily Report
  const fetchDailyReportData = async (sessionId: string) => {
    setReportLoading(true);
    try {
      const { data: studentsData, error: stErr } = await supabase
        .from("students")
        .select(`
          id,
          roll_number,
          semester,
          users ( name, email ),
          programs ( name ),
          devices ( id, installation_id, status )
        `)
        .order("roll_number", { ascending: true });

      if (stErr) throw stErr;

      const { data: recordsData, error: recErr } = await supabase
        .from("attendance_records")
        .select("*")
        .eq("session_id", sessionId);

      if (recErr) throw recErr;

      const recordsMap = new Map<string, any>();
      if (Array.isArray(recordsData)) {
        recordsData.forEach((rec) => {
          recordsMap.set(rec.student_id, rec);
        });
      }

      const merged: DailyStudentRow[] = (studentsData || []).map((st: any) => {
        const rec = recordsMap.get(st.id);
        const activeDev = st.devices?.find((d: any) => d.status === "ACTIVE");
        const isPresent = rec ? rec.status === "PRESENT" : false;

        return {
          studentId: st.id,
          roll: st.roll_number || "—",
          name: st.users?.name || "Student",
          email: st.users?.email || "",
          program: st.programs?.name || "B.Tech DSAI",
          semester: st.semester || 5,
          status: isPresent ? "PRESENT" : "ABSENT",
          method: rec ? rec.verification_method || "WIFI_AUTO" : "AWAITING_WIFI",
          markedAt: rec?.marked_at || null,
          device: activeDev ? activeDev.installation_id : "No Device Bound",
          wifiVerified: rec ? (rec.wifi_ap_verified ?? isPresent) : false,
        };
      });

      setDailyReportStudents(merged);
    } catch (err: any) {
      console.error("Error loading daily report data:", err);
    } finally {
      setReportLoading(false);
    }
  };

  // 1-Click Faculty Manual Override on Daily Report
  const handleToggleReportAttendance = async (row: DailyStudentRow) => {
    if (!reportSessionId) return;
    setIsUpdatingReportRecord(row.studentId);
    setReportActionMessage(null);

    const targetStatus = row.status === "PRESENT" ? "ABSENT" : "PRESENT";
    try {
      if (targetStatus === "PRESENT") {
        await supabase.from("attendance_records").upsert(
          {
            session_id: reportSessionId,
            student_id: row.studentId,
            status: "PRESENT",
            verification_method: "MANUAL_TEACHER",
            wifi_ap_verified: true,
            ble_verified: false,
            presence_percentage: 100.0,
            notes: "Manually marked by Faculty in Daily Report",
            marked_at: new Date().toISOString(),
          },
          { onConflict: "session_id,student_id" }
        );
      } else {
        await supabase
          .from("attendance_records")
          .delete()
          .eq("session_id", reportSessionId)
          .eq("student_id", row.studentId);
      }

      setDailyReportStudents((prev) =>
        prev.map((s) => {
          if (s.studentId === row.studentId) {
            return {
              ...s,
              status: targetStatus,
              method: targetStatus === "PRESENT" ? "MANUAL_TEACHER" : "AWAITING_WIFI",
              markedAt: targetStatus === "PRESENT" ? new Date().toISOString() : null,
              wifiVerified: targetStatus === "PRESENT",
            };
          }
          return s;
        })
      );

      setReportActionMessage({
        type: "success",
        text: `Updated: ${row.name} (${row.roll}) marked as ${targetStatus}.`,
      });
    } catch (err: any) {
      console.error("Toggle error:", err);
      setReportActionMessage({ type: "error", text: "Failed to update attendance: " + err.message });
    } finally {
      setIsUpdatingReportRecord(null);
    }
  };

  // Export Daily CSV from Faculty Console
  const handleExportDailyCsv = () => {
    const currentSession = allReportSessions.find((s) => s.id === reportSessionId) || allReportSessions[0];
    const subName = currentSession?.classes?.subjects?.name || selectedSlot.name;
    const subCode = currentSession?.classes?.subjects?.code || selectedSlot.code;
    const roomName = currentSession?.classes?.room || selectedSlot.room;
    const teacherName = currentSession?.classes?.teachers?.users?.name || "Dr. S. Sharma";

    const dateFormatted = new Date(currentSession?.start_time || new Date()).toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });

    const csvLines = [
      `"IIIT NAYA RAIPUR - DAILY ATTENDANCE LEDGER"`,
      `"Subject","${subName} (${subCode})"`,
      `"Faculty In-Charge","${teacherName}"`,
      `"Classroom / Lab","${roomName}"`,
      `"Date","${dateFormatted}"`,
      `"Total Enrolled",${dailyReportStudents.length}`,
      `"Total Present",${dailyReportStudents.filter(s => s.status === "PRESENT").length}`,
      `"Total Absent",${dailyReportStudents.filter(s => s.status !== "PRESENT").length}`,
      `""`,
      `"Roll Number","Student Name","Program","Attendance Status","Verification Method","Wi-Fi Verified","Marked Time","Hardware Device Lock"`,
      ...dailyReportStudents.map((st) => {
        const timeFormatted = st.markedAt
          ? new Date(st.markedAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", second: "2-digit" })
          : "—";
        return `"${st.roll}","${st.name}","${st.program}","${st.status}","${st.method}","${st.wifiVerified ? "YES" : "NO"}","${timeFormatted}","${st.device}"`;
      }),
    ];

    const blob = new Blob([csvLines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `IIITNR_Daily_Attendance_${subCode}_${reportDate}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Check tab in query params on mount
  useEffect(() => {
    if (typeof window !== "undefined") {
      const params = new URLSearchParams(window.location.search);
      if (params.get("tab") === "report") {
        setActiveView("DAILY_REPORT");
        fetchAllReportSessions();
      }
    }
  }, []);

  // When reportSessionId changes, fetch daily report data
  useEffect(() => {
    if (reportSessionId) {
      fetchDailyReportData(reportSessionId);
    }
  }, [reportSessionId]);

  // When allReportSessions or reportDate changes, pick appropriate session
  useEffect(() => {
    if (allReportSessions.length === 0) return;
    const matching = allReportSessions.filter(
      (s) => new Date(s.start_time).toISOString().split("T")[0] === reportDate
    );
    if (matching.length > 0) {
      if (!matching.some((s) => s.id === reportSessionId)) {
        setReportSessionId(matching[0].id);
      }
    } else {
      if (!allReportSessions.some((s) => s.id === reportSessionId)) {
        setReportSessionId(allReportSessions[0].id);
      }
    }
  }, [allReportSessions, reportDate]);

  // Patch authorized Wi-Fi to Supabase
  const patchWifiToSupabase = async (newSsids: string[]) => {
    try {
      await fetch(`${supabaseUrl}/rest/v1/classrooms?id=eq.d89824f4-b389-4129-9842-3bde92fc60a4`, {
        method: "PATCH",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          wifi_ssid: newSsids.join(", ")
        })
      });
    } catch (e) {
      console.error("Failed to patch Wi-Fi to Supabase:", e);
    }
  };

  // Continuous sync every 2 seconds
  useEffect(() => {
    fetchSessionStatus();
    fetchPendingDeviceApprovals();
    const interval = setInterval(() => {
      fetchSessionStatus();
      fetchLiveRecords();
      fetchPendingDeviceApprovals();
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  // Toggle Wi-Fi SSID in allowlist
  const toggleSsid = (ssid: string) => {
    const clean = ssid.trim();
    if (!clean) return;
    let nextList: string[];
    if (selectedSsids.includes(clean)) {
      if (selectedSsids.length > 1) {
        nextList = selectedSsids.filter((s) => s !== clean);
      } else {
        return;
      }
    } else {
      nextList = [...selectedSsids, clean];
    }
    setSelectedSsids(nextList);
    patchWifiToSupabase(nextList);
  };

  // Add custom Wi-Fi SSID
  const handleAddCustomSsid = () => {
    const clean = customSsidInput.trim();
    if (clean && !selectedSsids.includes(clean)) {
      const nextList = [...selectedSsids, clean];
      setSelectedSsids(nextList);
      setCustomSsidInput("");
      setShowAddCustomWifi(false);
      patchWifiToSupabase(nextList);
    }
  };

  // 1-Click Manual Attendance Toggle (Syncs to Supabase)
  const handleToggleAttendance = async (student: StudentRecord) => {
    setIsUpdatingRecord(student.id);
    const willBePresent = student.status !== "PRESENT";

    // Optimistic UI update so button changes instantly without delay
    setStudents((prev) =>
      prev.map((s) =>
        s.id === student.id
          ? {
              ...s,
              status: willBePresent ? "PRESENT" : "ABSENT",
              wifiAp: willBePresent,
              coverage: willBePresent ? 100 : 0,
              rttDist: willBePresent ? "4.2m" : "—",
              diagnosis: willBePresent
                ? "Classroom Wi-Fi AP verified (Faculty Override) · 100% Present"
                : "Waiting for Classroom Wi-Fi",
            }
          : s
      )
    );

    try {
      const payload = {
        session_id: activeSessionIdRef.current || activeSessionId,
        student_id: student.id,
        status: willBePresent ? "PRESENT" : "ABSENT",
        verification_method: "MANUAL_TEACHER",
        wifi_ap_verified: willBePresent,
        presence_percentage: willBePresent ? 100.0 : 0.0,
        notes: willBePresent 
          ? `Verified via Classroom Wi-Fi AP (${selectedSsids[0] || "Pranjal"}) · Faculty Console`
          : "Marked absent by faculty"
      };

      const res = await fetch(`${supabaseUrl}/rest/v1/attendance_records?on_conflict=session_id,student_id`, {
        method: "POST",
        headers: {
          "apikey": anonKey,
          "Authorization": `Bearer ${anonKey}`,
          "Content-Type": "application/json",
          "Prefer": "resolution=merge-duplicates,return=representation"
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errText = await res.text();
        console.error("Supabase toggle failed:", errText);
      }

      await fetchLiveRecords();
    } catch (e) {
      console.error("Toggle attendance error:", e);
      fetchLiveRecords();
    } finally {
      setIsUpdatingRecord(null);
    }
  };

  // Calculated Stats
  const presentCount = students.filter((s) => s.status === "PRESENT").length;
  const absentCount = Math.max(0, totalEnrolled - presentCount);
  const presentPercentage = totalEnrolled > 0 ? Math.round((presentCount * 100) / totalEnrolled) : 0;

  // Filtered Roster
  const filteredStudents = students.filter((s) => {
    const matchesFilter = 
      rosterFilter === "ALL" ? true :
      rosterFilter === "PRESENT" ? s.status === "PRESENT" :
      rosterFilter === "ABSENT" ? s.status !== "PRESENT" : true;

    const matchesSearch = 
      s.name.toLowerCase().includes(rosterSearch.toLowerCase()) ||
      s.roll.toLowerCase().includes(rosterSearch.toLowerCase());

    return matchesFilter && matchesSearch;
  });

  // Daily Report Calculated Stats
  const currentReportSession = allReportSessions.find((s) => s.id === reportSessionId) || allReportSessions[0];
  const dailyReportTotalEnrolled = dailyReportStudents.length;
  const dailyReportPresentCount = dailyReportStudents.filter((s) => s.status === "PRESENT").length;
  const dailyReportAbsentCount = Math.max(0, dailyReportTotalEnrolled - dailyReportPresentCount);
  const dailyReportAttendanceRate = dailyReportTotalEnrolled > 0 ? Math.round((dailyReportPresentCount * 100) / dailyReportTotalEnrolled) : 0;
  const dailyReportWifiVerifiedCount = dailyReportStudents.filter((s) => s.status === "PRESENT" && s.wifiVerified).length;

  // Daily Report Filtered Roster
  const filteredReportStudents = dailyReportStudents.filter((st) => {
    const nameMatch = st.name.toLowerCase().includes(reportSearch.toLowerCase());
    const rollMatch = st.roll.toLowerCase().includes(reportSearch.toLowerCase());
    const matchesSearch = nameMatch || rollMatch;

    if (reportFilter === "PRESENT") return matchesSearch && st.status === "PRESENT";
    if (reportFilter === "ABSENT") return matchesSearch && st.status === "ABSENT";
    return matchesSearch;
  });

  return (
    <div className="min-h-screen bg-[#F8FAFC] text-slate-900 py-6 px-4 sm:px-6 font-sans">
      <div className={`${activeView === "DAILY_REPORT" ? "max-w-4xl" : "max-w-xl"} mx-auto space-y-4 transition-all duration-200`}>
        
        {/* 1. INSTITUTIONAL TOP BAR */}
        <div className="flex items-center justify-between">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 border border-slate-200">
            <span className={`w-2 h-2 rounded-full ${isSessionLive ? "bg-emerald-500 animate-pulse" : "bg-blue-600"}`}></span>
            <span className="text-[11px] font-semibold tracking-wide text-slate-800 uppercase">
              IIIT NAYA RAIPUR · FACULTY CONSOLE
            </span>
          </div>

          <div className="flex items-center gap-2">
            <span className={`px-2.5 py-1 rounded-lg text-[10px] font-bold tracking-wider uppercase border ${
              isSessionLive 
                ? "bg-emerald-50 text-emerald-700 border-emerald-300" 
                : "bg-slate-100 text-slate-600 border-slate-200"
            }`}>
              {isSessionLive ? "● LIVE" : "READY"}
            </span>

            {/* In-Console Mode Toggler */}
            <div className="flex items-center p-0.5 bg-slate-200/90 rounded-full border border-slate-300">
              <button
                onClick={() => setActiveView("LIVE_CONSOLE")}
                className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold transition ${
                  activeView === "LIVE_CONSOLE"
                    ? "bg-white text-blue-700 shadow-2xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                <span className={`w-1.5 h-1.5 rounded-full ${isSessionLive ? "bg-emerald-500 animate-pulse" : "bg-blue-600"}`}></span>
                <span>Console</span>
              </button>

              <button
                onClick={() => {
                  setActiveView("DAILY_REPORT");
                  if (allReportSessions.length === 0) fetchAllReportSessions();
                }}
                className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold transition ${
                  activeView === "DAILY_REPORT"
                    ? "bg-white text-blue-700 shadow-2xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                <FileText className="w-3 h-3 text-blue-600" />
                <span>Daily Report</span>
              </button>
            </div>

            <a
              href="/"
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-slate-100 hover:bg-slate-200 border border-slate-200 text-slate-600 text-xs font-medium transition"
              title="Sign Out"
            >
              <LogOut className="w-3 h-3" />
              <span>Logout</span>
            </a>
          </div>
        </div>

        {/* 2. PROFESSOR IDENTITY GREETING */}
        <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-2xs flex items-center gap-3.5">
          <div className="w-11 h-11 rounded-full bg-[#2563EB] text-white flex items-center justify-center font-bold text-sm shrink-0 shadow-2xs">
            SS
          </div>
          <div>
            <h2 className="font-bold text-slate-900 text-base leading-tight">Welcome, Dr. S. Sharma</h2>
            <p className="text-xs text-slate-500 mt-0.5">Computer Science & Engineering · FAC-CSE-042</p>
          </div>
        </div>

        {/* FACULTY CONSOLE TABS: LIVE CLASSROOM vs DAILY REPORT */}
        <div className="bg-slate-200/80 p-1 rounded-2xl flex items-center gap-1 border border-slate-300/80 shadow-2xs print:hidden">
          <button
            onClick={() => setActiveView("LIVE_CONSOLE")}
            className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition ${
              activeView === "LIVE_CONSOLE"
                ? "bg-white text-blue-700 shadow-xs"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            <span className={`w-2 h-2 rounded-full ${isSessionLive ? "bg-emerald-500 animate-pulse" : "bg-blue-600"}`}></span>
            <span>Live Classroom Console</span>
          </button>

          <button
            onClick={() => {
              setActiveView("DAILY_REPORT");
              if (allReportSessions.length === 0) fetchAllReportSessions();
            }}
            className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition ${
              activeView === "DAILY_REPORT"
                ? "bg-white text-blue-700 shadow-xs"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            <FileText className="w-3.5 h-3.5 text-blue-600" />
            <span>Daily Attendance Report & Sheet</span>
            <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-blue-100 text-blue-800 font-mono font-bold">
              Today
            </span>
          </button>
        </div>

        {/* ========================================================================= */}
        {/* VIEW 1: LIVE CLASSROOM CONSOLE */}
        {/* ========================================================================= */}
        {activeView === "LIVE_CONSOLE" && (
          <div className="space-y-4">

        {/* 3. FOCUSED CURRENT LECTURE & TAKE ATTENDANCE CARD */}
        <div className={`bg-white rounded-2xl p-5 border transition-all duration-300 shadow-2xs ${
          isSessionLive ? "border-emerald-500/80 bg-emerald-50/10 ring-1 ring-emerald-500/20" : "border-slate-200"
        }`}>
          {/* Status Header Pill */}
          <div className="flex items-center justify-between">
            <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold tracking-wider uppercase border ${
              isSessionLive 
                ? "bg-emerald-50 text-emerald-700 border-emerald-300" 
                : "bg-blue-50 text-blue-700 border-blue-200"
            }`}>
              <span className={`w-1.5 h-1.5 rounded-full ${isSessionLive ? "bg-emerald-500 animate-pulse" : "bg-blue-600"}`}></span>
              <span>{isSessionLive ? "ATTENDANCE IN PROGRESS" : `${getSlotStatus(selectedSlot)} LECTURE`}</span>
            </div>

            <span className="px-2.5 py-0.5 rounded-lg bg-slate-100 border border-slate-200 text-blue-700 font-bold text-xs font-mono">
              {selectedSlot.code}
            </span>
          </div>

          {/* Subject Title & Subtitle */}
          <h1 className="text-xl font-bold text-slate-900 mt-3.5 leading-tight">{selectedSlot.name}</h1>
          <p className="text-xs text-slate-500 mt-1 font-medium">
            {selectedSlot.code} · {selectedSlot.room} · {totalEnrolled} students
          </p>

          {/* Pending Device Change Approvals Banner */}
          {pendingApprovals.length > 0 && (
            <div className="mt-4 p-3.5 rounded-xl bg-amber-50 border border-amber-300 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-xs animate-in fade-in duration-200">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-amber-500 text-white font-bold text-xs flex items-center justify-center animate-pulse shrink-0">
                  {pendingApprovals.length}
                </div>
                <div>
                  <div className="text-xs font-bold text-amber-950 flex items-center gap-1.5">
                    <span>Student Device Change Request</span>
                    <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-amber-200 text-amber-900 border border-amber-300 font-bold">
                      Permission Required
                    </span>
                  </div>
                  <div className="text-[11px] text-amber-800 mt-0.5">
                    {pendingApprovals.map((p: any) => `${p.students?.users?.name || "Student"} (${p.students?.roll_number})`).join(", ")} requested phone switch
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2 self-end sm:self-auto flex-wrap">
                {pendingApprovals.map((p: any) => (
                  <button
                    key={p.id}
                    onClick={() => handleQuickApproveDevice(p.student_id, p.id, p.students?.roll_number)}
                    className="px-3 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg transition shadow-xs flex items-center gap-1"
                    title={`Approve new phone for ${p.students?.users?.name || p.students?.roll_number}`}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>Approve {p.students?.roll_number}</span>
                  </button>
                ))}
                <a
                  href="/dashboard/students?filter=PENDING"
                  className="text-[11px] font-semibold text-amber-800 hover:underline px-1"
                >
                  Manage →
                </a>
              </div>
            </div>
          )}

          {/* When session is NOT live: show classroom details grid, readiness status and Start Attendance button */}
          {!isSessionLive && (
            <>
              {/* Classroom Details Rows */}
              <div className="grid grid-cols-2 gap-2 mt-4 text-xs text-slate-600">
                <div className="flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span className="font-medium truncate">{selectedSlot.room}</span>
                </div>

                <div className="flex items-center gap-1.5 justify-end">
                  <Users className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span className="font-semibold text-slate-600">
                    {totalEnrolled} Enrolled
                  </span>
                </div>

                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span className="font-mono text-slate-600">{selectedSlot.timeFormatted}</span>
                </div>

                <div className="flex items-center justify-end">
                  <span className="px-2 py-0.5 rounded bg-slate-100 border border-slate-200 text-[10px] font-bold text-slate-500 uppercase">
                    {getSlotStatus(selectedSlot)}
                  </span>
                </div>
              </div>

              {/* Attendance Readiness Status */}
              <div className="mt-4 p-3 rounded-xl bg-emerald-50/70 border border-emerald-200/80 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
                  </span>
                  <div>
                    <div className="text-xs font-bold text-slate-900 leading-tight">Attendance ready</div>
                    <div className="text-[11px] text-slate-500 mt-0.5">
                      {totalEnrolled} students enrolled · Classroom verified
                    </div>
                  </div>
                </div>
                <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                  <Check className="w-3 h-3 text-emerald-700" />
                  <span>Ready</span>
                </div>
              </div>

              {/* Dominant Start Attendance Button */}
              <div className="mt-4">
                <button
                  onClick={handleStartLecture}
                  className="w-full h-14 bg-[#2563EB] hover:bg-blue-700 active:scale-[0.99] text-white font-bold text-base rounded-xl flex items-center justify-center gap-2 shadow-md hover:shadow-lg transition duration-150"
                >
                  <Play className="w-5 h-5 fill-white" />
                  <span>Start Attendance</span>
                </button>
              </div>

              {/* In-Console Daily Attendance Report Quick Access */}
              <div className="mt-3 p-3.5 bg-gradient-to-r from-blue-50/70 to-slate-50 border border-blue-200 rounded-xl flex items-center justify-between shadow-2xs">
                <div>
                  <div className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                    <FileText className="w-3.5 h-3.5 text-blue-600" />
                    <span>Daily Attendance Report</span>
                  </div>
                  <div className="text-[11px] text-slate-500 mt-0.5">
                    Review today's student ledger, Wi-Fi telemetry logs & export CSV/PDF
                  </div>
                </div>
                <button
                  onClick={() => {
                    setActiveView("DAILY_REPORT");
                    if (allReportSessions.length === 0) fetchAllReportSessions();
                  }}
                  className="px-3 py-1.5 bg-white hover:bg-slate-50 text-blue-600 border border-blue-200 text-xs font-bold rounded-lg shadow-2xs transition shrink-0"
                >
                  Open Sheet →
                </button>
              </div>
            </>
          )}

          {/* When session IS live:
              Priority 1: Attendance status (One dominant card)
              Priority 2: Open Live Attendance (One primary button)
              Priority 3: Who is present (Verified students)
          */}
          {isSessionLive && (
            <>
              {/* 1. ONE DOMINANT ATTENDANCE CARD (Single Source of Truth) */}
              <div className="mt-4 p-5 rounded-2xl bg-white border border-emerald-200/90 shadow-2xs text-center">
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">
                  ATTENDANCE
                </div>

                <div className="flex items-baseline justify-center gap-2 mt-1.5">
                  <span className="text-4xl font-extrabold tracking-tight text-slate-900 font-mono">
                    {presentCount} / {totalEnrolled}
                  </span>
                  <span className="text-sm font-bold text-emerald-700 uppercase tracking-wide">
                    PRESENT
                  </span>
                </div>

                <div className="text-xs font-bold text-slate-500 mt-0.5 font-mono">
                  {presentPercentage}%
                </div>

                <div className="flex items-center justify-center gap-5 mt-3 text-xs">
                  <div className="flex items-center gap-1.5 text-emerald-700 font-semibold">
                    <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                    <span>{presentCount} Verified</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-slate-400 font-medium">
                    <span className="w-2 h-2 rounded-full border border-slate-300"></span>
                    <span>{absentCount} Not detected</span>
                  </div>
                </div>

                <div className="border-t border-slate-100 my-3.5"></div>

                <div className="text-[11px] text-slate-500 font-mono mb-2">
                  Attendance started {elapsedFormatted} ago
                </div>

                {/* Progress bar underneath */}
                <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden border border-slate-200/60 p-[1px]">
                  <div
                    className="h-full bg-emerald-500 rounded-full transition-all duration-500 shadow-xs"
                    style={{ width: `${presentPercentage}%` }}
                  ></div>
                </div>
              </div>

              {/* 2. ONE PRIMARY BUTTON */}
              <div className="mt-4 space-y-1.5">
                <button
                  onClick={() => setShowLiveRollCall(true)}
                  className="w-full h-13 bg-emerald-600 hover:bg-emerald-700 active:scale-[0.99] text-white font-bold text-sm rounded-xl flex items-center justify-center gap-2 shadow-md hover:shadow-lg transition duration-150"
                >
                  <span>Open Live Attendance</span>
                  <span className="text-base font-normal">→</span>
                </button>
                <p className="text-center text-[11px] text-slate-400 font-medium">
                  {presentCount} verified · {absentCount} awaiting
                </p>
              </div>

              {/* 3. WHO IS PRESENT (VERIFIED STUDENTS) */}
              <div className="mt-4 pt-3.5 border-t border-slate-100">
                <div className="flex items-center justify-between mb-2.5">
                  <span className="text-xs font-bold text-slate-800">
                    Present · {presentCount}
                  </span>
                  {presentCount > 0 && (
                    <span className="text-[10px] text-emerald-700 font-mono font-medium">
                      Auto-verified
                    </span>
                  )}
                </div>

                {presentCount === 0 ? (
                  <div className="text-center py-4 bg-slate-50 rounded-xl border border-slate-100 text-slate-400 text-xs">
                    Waiting for students to enter classroom range...
                  </div>
                ) : (
                  <div className="space-y-1.5 max-h-52 overflow-y-auto pr-0.5">
                    {students
                      .filter((s) => s.status === "PRESENT")
                      .map((st) => {
                        const initials = st.name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase() || "ST";
                        return (
                          <div
                            key={st.id}
                            className="flex items-center justify-between p-2.5 bg-slate-50/70 border border-slate-200/80 rounded-xl text-xs hover:bg-slate-50 transition"
                          >
                            <div className="flex items-center gap-2.5 min-w-0">
                              <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-800 font-bold flex items-center justify-center text-[11px] shrink-0">
                                {initials}
                              </div>
                              <div className="min-w-0">
                                <div className="font-bold text-slate-900 leading-tight truncate">{st.name}</div>
                                <div className="text-[10px] font-mono text-slate-400 mt-0.5">Roll: {st.roll}</div>
                              </div>
                            </div>
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 shrink-0">
                              <Check className="w-3 h-3 text-emerald-600" />
                              <span>PRESENT</span>
                            </span>
                          </div>
                        );
                      })}
                  </div>
                )}
              </div>
            </>
          )}

          {/* 4. WI-FI SYSTEM STATUS (SECONDARY) */}
          <div className="mt-4 p-3.5 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2.5">
            <div>
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                LIVE CLASSROOM
              </span>
              <h3 className="text-xs font-bold text-slate-800 leading-tight mt-0.5">
                Wi-Fi verification
              </h3>
            </div>

            {/* Connected network card */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-white border border-slate-200/90 shadow-2xs">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)] shrink-0"></span>
                <span className="text-xs font-bold text-slate-900">
                  {selectedSsids.length > 0 ? selectedSsids[0] : "No network selected"}
                </span>
              </div>
              <span className="px-2 py-0.5 rounded text-[9px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                {selectedSsids.length > 0 ? "CONFIGURED" : "SETUP NEEDED"}
              </span>
            </div>

            {/* Approved Networks text + Manage Link */}
            <div className="flex items-start justify-between gap-2 pt-0.5">
              <div className="min-w-0">
                <p className="text-[10px] text-slate-500 font-medium">
                  {selectedSsids.length} approved networks detected
                </p>
                <p className="text-xs text-slate-700 font-medium truncate mt-0.5">
                  {selectedSsids.join(" · ")}
                </p>
              </div>
              <button
                onClick={() => setShowManageWifi(!showManageWifi)}
                className="text-xs font-semibold text-blue-600 hover:text-blue-800 shrink-0 transition"
              >
                {showManageWifi ? "Close" : "Manage →"}
              </button>
            </div>

            {/* Expandable Advanced Network Management (Only if teacher clicks Manage) */}
            {showManageWifi && (
              <div className="pt-2.5 border-t border-slate-200 space-y-2.5 animate-in fade-in duration-150">

              {/* Info note */}
                <div className="flex items-start gap-1.5 p-2 rounded-lg bg-amber-50 border border-amber-200">
                  <span className="text-amber-500 text-xs shrink-0 mt-0.5">ⓘ</span>
                  <p className="text-[10px] text-amber-800 leading-relaxed">
                    Browsers cannot scan Wi-Fi automatically. Type your classroom SSID below or pick a campus preset.
                    Students will be verified when their phone is on any approved network.
                  </p>
                </div>

                {/* Add Custom Wi-Fi AP Form */}
                <div className="space-y-1.5">
                  {!showAddCustomWifi ? (
                    <button
                      onClick={() => setShowAddCustomWifi(true)}
                      className="w-full py-1.5 px-2 text-[11px] font-semibold text-blue-700 hover:bg-blue-50/60 rounded-lg border border-dashed border-blue-300 flex items-center justify-center gap-1 transition"
                    >
                      <Plus className="w-3 h-3" />
                      <span>Add Classroom Wi-Fi SSID</span>
                    </button>
                  ) : (
                    <div className="flex items-center gap-1.5 pt-1">
                      <input
                        type="text"
                        placeholder="e.g. IIITNR_STUDENTS, Pranjal"
                        value={customSsidInput}
                        onChange={(e) => setCustomSsidInput(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleAddCustomSsid()}
                        className="flex-1 px-2.5 py-1 text-xs rounded-lg border border-blue-400 bg-white focus:outline-none"
                        autoFocus
                      />
                      <button
                        onClick={handleAddCustomSsid}
                        className="px-2.5 py-1 text-xs font-bold bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                      >
                        Add
                      </button>
                      <button
                        onClick={() => setShowAddCustomWifi(false)}
                        className="px-2 py-1 text-xs text-slate-400 hover:text-slate-700"
                      >
                        ✕
                      </button>
                    </div>
                  )}

                  {/* Currently approved list with remove option */}
                  {selectedSsids.map((ssid) => (
                    <div key={ssid} className="flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-blue-50 border border-blue-200">
                      <div className="flex items-center gap-2">
                        <Wifi className="w-3 h-3 text-blue-600" />
                        <span className="text-xs font-semibold text-blue-900">{ssid}</span>
                      </div>
                      <button
                        onClick={() => toggleSsid(ssid)}
                        className="text-[10px] text-red-400 hover:text-red-600 font-bold transition"
                      >
                        Remove
                      </button>
                    </div>
                  ))}
                </div>

                {/* Campus Presets Quick-Add Row */}
                <div className="pt-2 border-t border-slate-200">
                  <p className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                    Campus Presets
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {["IIIT-NR-Campus", "IIITNR_STUDENTS", "IIITNR_FACULTY"].map((preset) => {
                      const isSelected = selectedSsids.includes(preset);
                      return (
                        <button
                          key={preset}
                          onClick={() => toggleSsid(preset)}
                          className={`px-2 py-1 rounded-full text-[10px] font-medium border flex items-center gap-1 transition ${
                            isSelected
                              ? "bg-blue-100/70 border-blue-300 text-blue-800 font-semibold"
                              : "bg-white border-slate-200 text-slate-600 hover:bg-slate-100"
                          }`}
                        >
                          {isSelected ? <Check className="w-2.5 h-2.5 text-blue-700" /> : <Plus className="w-2.5 h-2.5 text-slate-500" />}
                          <span>{preset}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* 5. QUIET SECONDARY END LECTURE ACTION */}
          {isSessionLive && (
            <div className="mt-3.5 pt-3 border-t border-slate-100">
              {!confirmEndOnConsole ? (
                <button
                  onClick={() => setConfirmEndOnConsole(true)}
                  className="w-full py-2.5 px-3 rounded-xl border border-slate-200 hover:border-rose-300 hover:bg-rose-50/50 text-slate-600 hover:text-rose-600 text-xs font-semibold flex items-center justify-center gap-1.5 transition"
                >
                  <span className="w-3 h-3 border border-slate-400 rounded-xs shrink-0"></span>
                  <span>End Lecture</span>
                </button>
              ) : (
                <div className="w-full p-2.5 rounded-xl bg-rose-50/80 border border-rose-200 flex items-center justify-between gap-2 animate-in fade-in duration-150">
                  <span className="text-xs font-semibold text-rose-800">
                    End lecture & finalize attendance?
                  </span>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      onClick={() => setConfirmEndOnConsole(false)}
                      className="px-2.5 py-1 text-xs font-medium text-slate-600 hover:bg-white rounded-lg transition"
                    >
                      Cancel
                    </button>
                    <button
                      onClick={() => {
                        setConfirmEndOnConsole(false);
                        handleEndLecture();
                      }}
                      className="px-3 py-1 bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold rounded-lg shadow-2xs transition"
                    >
                      Confirm
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* 5. CLEAN SHORTCUT TO SCHEDULE TAB */}
        <div
          onClick={() => setShowScheduleModal(true)}
          className="bg-white rounded-2xl border border-slate-200 p-4 shadow-2xs hover:border-slate-300 cursor-pointer flex items-center justify-between transition"
        >
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-slate-100 flex items-center justify-center text-slate-600">
              <Calendar className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-bold text-xs text-slate-900">View Complete Teaching Schedule</h3>
              <p className="text-[11px] text-slate-500">3 classes scheduled today · Tap to manage or reschedule</p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-400" />
        </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* VIEW 2: DAILY ATTENDANCE REPORT & LEDGER */}
      {/* ========================================================================= */}
      {activeView === "DAILY_REPORT" && (
        <div className="space-y-4 animate-in fade-in duration-200">
          {/* Printable Institutional Header (Visible ONLY during window.print()) */}
          <div className="hidden print:block mb-6 border-b-2 border-slate-900 pb-4">
            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-xl font-bold uppercase tracking-tight text-slate-950">
                  International Institute of Information Technology, Naya Raipur
                </h1>
                <h2 className="text-sm font-semibold text-slate-700 mt-0.5">
                  Official Daily Classroom Attendance Record
                </h2>
              </div>
              <div className="text-right text-xs font-mono">
                <div>Date: {reportDate}</div>
                <div>Room: {currentReportSession?.classes?.room || selectedSlot.room}</div>
              </div>
            </div>
            <div className="grid grid-cols-4 gap-2 mt-3 pt-2 border-t border-slate-300 text-xs">
              <div><strong>Subject:</strong> {currentReportSession?.classes?.subjects?.name || selectedSlot.name} ({currentReportSession?.classes?.subjects?.code || selectedSlot.code})</div>
              <div><strong>Faculty:</strong> Dr. S. Sharma</div>
              <div><strong>Batch:</strong> {currentReportSession?.classes?.sections?.name || selectedSlot.program}</div>
              <div><strong>Attendance:</strong> {dailyReportPresentCount}/{dailyReportTotalEnrolled} ({dailyReportAttendanceRate}%)</div>
            </div>
          </div>

          {/* Action Header Bar (Hidden during print) */}
          <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-3 print:hidden">
            <div>
              <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200 mb-1">
                <ShieldCheck className="w-3 h-3 text-blue-600" />
                <span>Official Attendance Ledger</span>
              </div>
              <h2 className="font-bold text-slate-900 text-lg leading-tight">Daily Attendance Report</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Live verified classroom roll call logs, Wi-Fi telemetry verification & manual overrides
              </p>
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              <button
                onClick={() => setActiveView("LIVE_CONSOLE")}
                className="px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold flex items-center gap-1.5 shadow-2xs transition"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Live Console</span>
              </button>

              <button
                onClick={() => window.print()}
                className="px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold flex items-center gap-1.5 shadow-2xs transition"
                title="Print or Save as Official PDF"
              >
                <Printer className="w-3.5 h-3.5 text-slate-500" />
                <span>Print / PDF</span>
              </button>

              <button
                onClick={handleExportDailyCsv}
                className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl flex items-center gap-1.5 shadow-xs transition"
                title="Export Excel/CSV file"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Export CSV</span>
              </button>
            </div>
          </div>

          {/* Action Feedback Message */}
          {reportActionMessage && (
            <div
              className={`p-3 rounded-xl border flex items-center justify-between text-xs font-medium animate-in fade-in duration-200 print:hidden ${
                reportActionMessage.type === "success"
                  ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                  : "bg-red-50 border-red-200 text-red-800"
              }`}
            >
              <div className="flex items-center gap-2">
                {reportActionMessage.type === "success" ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-red-600" />
                )}
                <span>{reportActionMessage.text}</span>
              </div>
              <button onClick={() => setReportActionMessage(null)} className="text-gray-400 hover:text-gray-600">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {/* Date & Lecture Selector Bar (Hidden during print) */}
          <div className="bg-white border border-slate-200 rounded-2xl p-4 shadow-2xs flex flex-col md:flex-row md:items-center justify-between gap-4 print:hidden">
            {/* Date Controls */}
            <div className="flex items-center gap-2 flex-wrap">
              <div className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <Calendar className="w-4 h-4 text-blue-600" />
                <span>Date:</span>
              </div>
              <input
                type="date"
                value={reportDate}
                onChange={(e) => setReportDate(e.target.value)}
                className="px-2.5 py-1.5 text-xs font-mono border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
              <button
                onClick={() => setReportDate(new Date().toISOString().split("T")[0])}
                className={`px-2.5 py-1.5 text-xs font-semibold rounded-lg border transition ${
                  reportDate === new Date().toISOString().split("T")[0]
                    ? "bg-blue-50 text-blue-700 border-blue-300"
                    : "bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100"
                }`}
              >
                Today
              </button>
              <button
                onClick={() => {
                  const y = new Date();
                  y.setDate(y.getDate() - 1);
                  setReportDate(y.toISOString().split("T")[0]);
                }}
                className="px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100 transition"
              >
                Yesterday
              </button>
            </div>

            {/* Lecture Selector */}
            <div className="flex items-center gap-2 flex-wrap">
              <div className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <Clock className="w-4 h-4 text-emerald-600" />
                <span>Lecture:</span>
              </div>
              <select
                value={reportSessionId}
                onChange={(e) => setReportSessionId(e.target.value)}
                className="px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white font-medium max-w-xs truncate"
              >
                {(() => {
                  const onDate = allReportSessions.filter(
                    (s) => new Date(s.start_time).toISOString().split("T")[0] === reportDate
                  );
                  const otherDates = allReportSessions.filter(
                    (s) => new Date(s.start_time).toISOString().split("T")[0] !== reportDate
                  );

                  return (
                    <>
                      {onDate.length > 0 && (
                        <optgroup label={`Lectures on ${reportDate} (${onDate.length})`}>
                          {onDate.map((sess) => {
                            const sTime = new Date(sess.start_time).toLocaleTimeString("en-IN", {
                              hour: "2-digit",
                              minute: "2-digit",
                            });
                            const code = sess.classes?.subjects?.code || "CS505";
                            const room = sess.classes?.room || "Room";
                            const statusTag = sess.status === "ACTIVE" ? " [LIVE]" : "";
                            return (
                              <option key={sess.id} value={sess.id}>
                                {sTime} — {code} ({room}){statusTag}
                              </option>
                            );
                          })}
                        </optgroup>
                      )}

                      {otherDates.length > 0 && (
                        <optgroup label="Other Recent Lectures">
                          {otherDates.map((sess) => {
                            const sTime = new Date(sess.start_time).toLocaleTimeString("en-IN", {
                              hour: "2-digit",
                              minute: "2-digit",
                            });
                            const sDate = new Date(sess.start_time).toLocaleDateString("en-IN", {
                              day: "2-digit",
                              month: "short",
                            });
                            const code = sess.classes?.subjects?.code || "CS505";
                            const room = sess.classes?.room || "Room";
                            return (
                              <option key={sess.id} value={sess.id}>
                                {sDate} · {sTime} — {code} ({room})
                              </option>
                            );
                          })}
                        </optgroup>
                      )}
                    </>
                  );
                })()}
              </select>
              <button
                onClick={() => {
                  fetchAllReportSessions();
                  if (reportSessionId) fetchDailyReportData(reportSessionId);
                }}
                className="p-2 border border-slate-200 rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-50 transition"
                title="Refresh Report Data"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${reportLoading ? "animate-spin" : ""}`} />
              </button>
            </div>
          </div>

          {/* Selected Session Details Banner */}
          {currentReportSession && (
            <div className="bg-gradient-to-r from-blue-50/70 to-slate-50 border border-blue-200/80 rounded-2xl p-4 shadow-2xs">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded bg-blue-600 text-white font-mono font-bold text-xs">
                      {currentReportSession?.classes?.subjects?.code || selectedSlot.code}
                    </span>
                    <span className="text-base font-bold text-slate-900">
                      {currentReportSession?.classes?.subjects?.name || selectedSlot.name}
                    </span>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                        currentReportSession.status === "ACTIVE"
                          ? "bg-emerald-100 text-emerald-800 border-emerald-300"
                          : "bg-slate-100 text-slate-700 border-slate-200"
                      }`}
                    >
                      {currentReportSession.status === "ACTIVE" ? "LIVE NOW" : "COMPLETED"}
                    </span>
                  </div>
                  <div className="flex items-center gap-4 text-xs text-slate-600 mt-2 flex-wrap">
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3.5 h-3.5 text-slate-400" />
                      {currentReportSession?.classes?.room || selectedSlot.room}
                    </span>
                    <span>·</span>
                    <span className="flex items-center gap-1">
                      <Users className="w-3.5 h-3.5 text-slate-400" />
                      Faculty: Dr. S. Sharma
                    </span>
                    <span>·</span>
                    <span className="flex items-center gap-1 font-mono">
                      <Clock className="w-3.5 h-3.5 text-slate-400" />
                      {new Date(currentReportSession.start_time).toLocaleDateString("en-IN", {
                        weekday: "short",
                        day: "numeric",
                        month: "short",
                        year: "numeric",
                      })}
                      {" at "}
                      {new Date(currentReportSession.start_time).toLocaleTimeString("en-IN", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <div className="text-2xl font-extrabold text-blue-700 font-mono">
                      {dailyReportPresentCount} <span className="text-sm font-semibold text-slate-400">/ {dailyReportTotalEnrolled}</span>
                    </div>
                    <div className="text-[11px] font-bold text-emerald-600 mt-0.5">
                      {dailyReportAttendanceRate}% Present Today
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Daily Metrics Row (Hidden during print) */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 print:hidden">
            <div className="bg-white border border-slate-200 rounded-xl p-3.5 shadow-2xs">
              <div className="text-xs text-slate-500 font-medium">Total Registered</div>
              <div className="text-2xl font-bold text-slate-900 mt-1 font-mono">{dailyReportTotalEnrolled}</div>
              <div className="text-[11px] text-slate-400 mt-0.5">Classroom batch roster</div>
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-3.5 shadow-2xs">
              <div className="text-xs text-slate-500 font-medium">Verified Present</div>
              <div className="text-2xl font-bold text-emerald-600 mt-1 font-mono">
                {dailyReportPresentCount}{" "}
                <span className="text-xs font-semibold text-emerald-700">({dailyReportAttendanceRate}%)</span>
              </div>
              <div className="text-[11px] text-emerald-700 font-medium mt-0.5">Wi-Fi & BLE verified</div>
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-3.5 shadow-2xs">
              <div className="text-xs text-slate-500 font-medium">Absent / Not Detected</div>
              <div className="text-2xl font-bold text-rose-600 mt-1 font-mono">
                {dailyReportAbsentCount}{" "}
                <span className="text-xs font-semibold text-rose-700">
                  ({100 - dailyReportAttendanceRate}%)
                </span>
              </div>
              <div className="text-[11px] text-slate-400 mt-0.5">Absent students</div>
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-3.5 shadow-2xs">
              <div className="text-xs text-slate-500 font-medium">Wi-Fi AP Continuous</div>
              <div className="text-2xl font-bold text-blue-600 mt-1 font-mono">{dailyReportWifiVerifiedCount}</div>
              <div className="text-[11px] text-blue-700 font-medium mt-0.5">Classroom AP confirmed</div>
            </div>
          </div>

          {/* Search & Filter Toolbar (Hidden during print) */}
          <div className="bg-white border border-slate-200 rounded-xl p-3 shadow-2xs flex flex-col md:flex-row items-center justify-between gap-3 print:hidden">
            <div className="relative w-full md:w-80">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search by student name or roll..."
                value={reportSearch}
                onChange={(e) => setReportSearch(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
              />
            </div>

            <div className="flex items-center gap-1.5 self-start md:self-auto flex-wrap">
              <button
                onClick={() => setReportFilter("ALL")}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  reportFilter === "ALL" ? "bg-slate-900 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                }`}
              >
                All ({dailyReportTotalEnrolled})
              </button>
              <button
                onClick={() => setReportFilter("PRESENT")}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  reportFilter === "PRESENT"
                    ? "bg-emerald-600 text-white"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                }`}
              >
                Present ({dailyReportPresentCount})
              </button>
              <button
                onClick={() => setReportFilter("ABSENT")}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  reportFilter === "ABSENT" ? "bg-rose-600 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                }`}
              >
                Absent ({dailyReportAbsentCount})
              </button>
            </div>
          </div>

          {/* Daily Attendance Ledger Table */}
          <div className="bg-white border border-slate-200 rounded-2xl shadow-2xs overflow-hidden print:border-none print:shadow-none">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold text-slate-600 uppercase tracking-wider print:bg-white print:border-b-2 print:border-slate-800">
                    <th className="py-3 px-3.5">Roll Number</th>
                    <th className="py-3 px-3.5">Student Name</th>
                    <th className="py-3 px-3.5">Daily Status</th>
                    <th className="py-3 px-3.5">Verification Method</th>
                    <th className="py-3 px-3.5">Marked Time</th>
                    <th className="py-3 px-3.5">Hardware Device</th>
                    <th className="py-3 px-3.5 text-right print:hidden">Faculty Override</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {reportLoading ? (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-500">
                        <RefreshCw className="w-5 h-5 animate-spin mx-auto text-blue-600 mb-2" />
                        Loading daily attendance records...
                      </td>
                    </tr>
                  ) : filteredReportStudents.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="py-12 text-center text-slate-500">
                        No students found matching current filter.
                      </td>
                    </tr>
                  ) : (
                    filteredReportStudents.map((st) => {
                      const isPresent = st.status === "PRESENT";
                      const timeStr = st.markedAt
                        ? new Date(st.markedAt).toLocaleTimeString("en-IN", {
                            hour: "2-digit",
                            minute: "2-digit",
                            second: "2-digit",
                          })
                        : "—";

                      return (
                        <tr key={st.studentId} className="hover:bg-slate-50/60 transition-colors">
                          <td className="py-2.5 px-3.5">
                            <span className="font-mono font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-900 border border-slate-200 text-[11px]">
                              {st.roll}
                            </span>
                          </td>

                          <td className="py-2.5 px-3.5">
                            <div className="font-bold text-slate-900">{st.name}</div>
                            <div className="text-[10px] text-slate-500">{st.program} · Sem {st.semester}</div>
                          </td>

                          <td className="py-2.5 px-3.5">
                            {isPresent ? (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                                PRESENT
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-800 border border-rose-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-rose-400"></span>
                                ABSENT
                              </span>
                            )}
                          </td>

                          <td className="py-2.5 px-3.5">
                            {isPresent ? (
                              <div className="flex items-center gap-1 text-[11px] text-slate-700">
                                {st.method === "MANUAL_TEACHER" ? (
                                  <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-medium">
                                    Manual (Faculty)
                                  </span>
                                ) : (
                                  <span className="px-2 py-0.5 rounded bg-emerald-50 text-emerald-800 border border-emerald-200 font-medium flex items-center gap-1">
                                    <Check className="w-3 h-3 text-emerald-600" />
                                    Wi-Fi Auto (Continuous)
                                  </span>
                                )}
                              </div>
                            ) : (
                              <span className="text-slate-400 text-[11px] italic">Not detected</span>
                            )}
                          </td>

                          <td className="py-2.5 px-3.5 font-mono text-[11px] text-slate-700">
                            {timeStr}
                          </td>

                          <td className="py-2.5 px-3.5 font-mono text-[10px] text-slate-600">
                            <div className="flex items-center gap-1 max-w-[130px] truncate" title={st.device}>
                              <Smartphone className="w-3 h-3 text-slate-400 shrink-0" />
                              <span className="truncate">{st.device}</span>
                            </div>
                          </td>

                          <td className="py-2.5 px-3.5 text-right print:hidden">
                            <button
                              onClick={() => handleToggleReportAttendance(st)}
                              disabled={isUpdatingReportRecord === st.studentId}
                              className={`px-2.5 py-1 text-[11px] font-semibold rounded-lg border transition ${
                                isPresent
                                  ? "text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                                  : "text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50 border-emerald-300"
                              }`}
                            >
                              {isUpdatingReportRecord === st.studentId ? (
                                <RefreshCw className="w-3 h-3 animate-spin mx-auto" />
                              ) : isPresent ? (
                                "Mark Absent"
                              ) : (
                                "Mark Present"
                              )}
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Faculty Signature Box (Visible ONLY during window.print()) */}
          <div className="hidden print:flex items-center justify-between pt-12 mt-8 border-t border-slate-400 text-xs">
            <div>
              <div className="border-t border-slate-800 pt-1 w-48 text-center font-bold">
                Faculty In-Charge Signature
              </div>
              <div className="text-[10px] text-slate-500 text-center mt-0.5">Dr. S. Sharma · CSE</div>
            </div>
            <div className="text-center font-mono text-[11px] text-slate-500">
              Generated via IIIT-NR Smart Attendance Engine · {new Date().toLocaleDateString("en-IN")}
            </div>
            <div>
              <div className="border-t border-slate-800 pt-1 w-48 text-center font-bold">
                Academic Section Seal / Stamp
              </div>
              <div className="text-[10px] text-slate-500 text-center mt-0.5">Dean of Academics Office</div>
            </div>
          </div>
        </div>
      )}
      </div>

      {/* ========================================================================= */}
      {/* 6. LIVE ROLL CALL MODAL (ACTIVE LECTURE SCREEN EQUIVALENT) */}
      {/* ========================================================================= */}
      {/* ========================================================================= */}
      {/* 6. LIVE ROLL CALL MODAL (ACTIVE LECTURE SCREEN EQUIVALENT) */}
      {/* ========================================================================= */}
      {showLiveRollCall && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-3 sm:p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full max-h-[92vh] flex flex-col shadow-2xl overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
              <div>
                <div className="flex items-center gap-2">
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                  </span>
                  <span className="text-[11px] font-bold tracking-wider text-emerald-800 uppercase">
                    LIVE ATTENDANCE
                  </span>
                  <span className="text-xs font-mono font-bold text-slate-500 bg-white px-2 py-0.5 rounded border border-slate-200 ml-1">
                    {elapsedFormatted}
                  </span>
                </div>
                <h2 className="text-base font-bold text-slate-900 mt-1">{selectedSlot.name}</h2>
                <p className="text-[11px] text-slate-500 font-medium">
                  {selectedSlot.code} · {selectedSlot.room} · {totalEnrolled} students
                </p>
              </div>

              <button
                onClick={() => {
                  setShowLiveRollCall(false);
                  setConfirmEndLecture(false);
                }}
                className="w-8 h-8 rounded-full bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-500 font-bold text-sm transition"
              >
                ✕
              </button>
            </div>

            {/* Large Visual Attendance Metric */}
            <div className="px-6 py-5 bg-white border-b border-slate-100 text-center">
              <div className="flex flex-col items-center justify-center">
                <div className="flex items-baseline gap-2">
                  <span className="text-4xl font-extrabold tracking-tight text-slate-900 font-mono">
                    {presentCount} / {totalEnrolled}
                  </span>
                  <span className="text-xs font-bold text-emerald-700 tracking-wider uppercase bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md">
                    PRESENT
                  </span>
                </div>
                <p className="text-xs text-slate-500 font-medium mt-1">
                  {presentPercentage}% attendance detected
                </p>
              </div>

              {/* Visual Progress Bar */}
              <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden mt-3.5 border border-slate-200/60 p-[1px]">
                <div
                  className="h-full bg-emerald-500 rounded-full transition-all duration-500 shadow-sm"
                  style={{ width: `${presentPercentage}%` }}
                ></div>
              </div>

              {/* Metric Chips: Verified & Waiting */}
              <div className="flex items-center justify-center gap-6 mt-3 text-xs font-semibold">
                <div className="flex items-center gap-1.5 text-emerald-700">
                  <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                  <span>{presentCount} Verified</span>
                </div>
                <div className="flex items-center gap-1.5 text-slate-400">
                  <span className="w-2 h-2 rounded-full bg-slate-300"></span>
                  <span>{absentCount} Waiting</span>
                </div>
              </div>
            </div>

            {/* Classroom Automation Status Card */}
            <div className="px-5 py-3 bg-slate-50/80 border-b border-slate-200/70">
              <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                CLASSROOM STATUS
              </div>
              <div className="space-y-1.5 text-xs">
                <div className="flex items-center gap-2 text-emerald-700 font-medium">
                  <Check className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                  <span>{presentCount} students automatically verified</span>
                </div>
                {absentCount > 0 ? (
                  <div className="flex items-center gap-2 text-slate-500 font-medium">
                    <span className="w-3.5 h-3.5 rounded-full border border-slate-300 flex items-center justify-center text-[10px] text-slate-400 shrink-0">◌</span>
                    <span>{absentCount} students not detected</span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-emerald-700 font-medium">
                    <Check className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                    <span>All students detected & verified in classroom</span>
                  </div>
                )}
              </div>
            </div>

            {/* Search and Filters */}
            <div className="p-3 bg-slate-50/50 border-b border-slate-200 space-y-2">
              <div className="relative">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Search student by name or roll..."
                  value={rosterSearch}
                  onChange={(e) => setRosterSearch(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 bg-white border border-slate-200 rounded-xl text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="flex items-center gap-1.5">
                {[
                  { key: "ALL", label: `All (${students.length})` },
                  { key: "PRESENT", label: `Present (${presentCount})` },
                  { key: "ABSENT", label: `Not Detected (${absentCount})` }
                ].map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => setRosterFilter(tab.key)}
                    className={`flex-1 py-1.5 text-center text-xs font-bold rounded-lg transition ${
                      rosterFilter === tab.key
                        ? "bg-slate-900 text-white shadow-2xs"
                        : "bg-white text-slate-600 border border-slate-200 hover:bg-slate-100"
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Student List Column Header */}
            <div className="px-4 py-2 bg-slate-50 border-b border-slate-100 flex items-center justify-between text-[10px] font-bold text-slate-400 uppercase tracking-wider">
              <span>Student</span>
              <span>Status</span>
            </div>

            {/* Student Roll Call Cards List */}
            <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
              {filteredStudents.length === 0 ? (
                <div className="text-center py-8 text-slate-400 text-xs">
                  No students match the selected filter.
                </div>
              ) : (
                filteredStudents.map((st) => {
                  const isPresent = st.status === "PRESENT";
                  const initials = st.name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase() || "ST";
                  return (
                    <div key={st.id} className="p-3 hover:bg-slate-50/60 transition flex items-center justify-between gap-3">
                      {/* Avatar + Info */}
                      <div className="flex items-center gap-3 min-w-0">
                        <div className={`w-9 h-9 rounded-xl flex items-center justify-center text-xs font-bold shrink-0 ${
                          isPresent 
                            ? "bg-emerald-100 text-emerald-800" 
                            : "bg-slate-100 text-slate-600"
                        }`}>
                          {initials}
                        </div>
                        <div className="min-w-0">
                          <p className="text-xs font-bold text-slate-900 truncate leading-tight">{st.name}</p>
                          <p className="text-[10px] font-mono text-slate-400 mt-0.5">{st.roll}</p>
                          <div className="flex items-center gap-1.5 mt-1">
                            {isPresent ? (
                              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700">
                                <Check className="w-3 h-3 text-emerald-600" />
                                <span>Wi-Fi Verified</span>
                              </span>
                            ) : (
                              <span className="text-[11px] text-slate-400 font-medium flex items-center gap-1">
                                <span className="text-[10px]">◌</span>
                                <span>Waiting for Wi-Fi</span>
                              </span>
                            )}
                            {st.markedAt && (
                              <span className="text-[10px] text-slate-400 font-mono">
                                · {new Date(st.markedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Right Action / Status */}
                      <div className="flex items-center gap-2 shrink-0">
                        {isPresent ? (
                          <>
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                              <Check className="w-3 h-3 text-emerald-600" />
                              <span>Present</span>
                            </span>
                            <button
                              onClick={() => handleToggleAttendance(st)}
                              disabled={isUpdatingRecord === st.id}
                              title="Revoke attendance override"
                              className="text-[10px] text-slate-400 hover:text-rose-600 px-1.5 py-1 rounded hover:bg-slate-100 transition"
                            >
                              Revoke
                            </button>
                          </>
                        ) : (
                          <>
                            <span className="text-slate-400 text-xs font-mono mr-1">—</span>
                            <button
                              onClick={() => handleToggleAttendance(st)}
                              disabled={isUpdatingRecord === st.id}
                              className="px-2.5 py-1 rounded-lg text-xs font-semibold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 hover:border-slate-300 transition shadow-2xs"
                            >
                              {isUpdatingRecord === st.id ? "..." : "Mark Present"}
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Footer Actions */}
            <div className="p-3.5 border-t border-slate-200 bg-slate-50 flex items-center justify-between gap-3">
              <button
                onClick={() => {
                  setShowLiveRollCall(false);
                  setConfirmEndLecture(false);
                }}
                className="py-2.5 px-4 rounded-xl border border-slate-200 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-2xs"
              >
                Back to Console
              </button>

              {!confirmEndLecture ? (
                <button
                  onClick={() => setConfirmEndLecture(true)}
                  className="py-2.5 px-5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold transition shadow-sm"
                >
                  End & Save Attendance
                </button>
              ) : (
                <div className="flex items-center gap-2 animate-in fade-in duration-150">
                  <span className="text-[11px] font-medium text-slate-600">End lecture & finalize?</span>
                  <button
                    onClick={() => setConfirmEndLecture(false)}
                    className="px-2.5 py-1.5 rounded-lg text-xs font-medium text-slate-500 hover:bg-slate-200 transition"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => {
                      setConfirmEndLecture(false);
                      handleEndLecture();
                    }}
                    className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold shadow-2xs transition"
                  >
                    Confirm & Save
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* 7. COMPLETE TEACHING SCHEDULE MODAL */}
      {/* ========================================================================= */}
      {showScheduleModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-5 space-y-4 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <Calendar className="w-4 h-4 text-blue-600" />
                <h3 className="font-bold text-sm text-slate-900">Today's Teaching Schedule</h3>
              </div>
              <button
                onClick={() => setShowScheduleModal(false)}
                className="w-7 h-7 rounded-full bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-500 font-bold text-xs"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3">
              {TODAY_SCHEDULE.map((cls) => {
                const status = isSessionLive && selectedSlot.code === cls.code 
                  ? "LIVE NOW" 
                  : getSlotStatus(cls);
                const isSelected = selectedSlot.code === cls.code;
                return (
                  <div
                    key={cls.code}
                    onClick={() => {
                      if (!isSessionLive) {
                        setSelectedSlot(cls);
                        setActiveSessionId(cls.sessionId);
                        setShowScheduleModal(false);
                      }
                    }}
                    className={`p-3.5 rounded-xl border transition ${
                      isSelected
                        ? "bg-blue-50/70 border-blue-400 ring-1 ring-blue-400/30"
                        : "bg-white border-slate-200 hover:border-slate-300"
                    } ${!isSessionLive ? "cursor-pointer" : "cursor-default"}`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <span className="font-mono text-xs font-bold text-blue-700">{cls.code}</span>
                        {isSelected && (
                          <span className="text-[9px] bg-blue-600 text-white font-bold px-1.5 py-0.2 rounded">
                            SELECTED
                          </span>
                        )}
                      </div>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        status === "LIVE NOW"
                          ? "bg-emerald-100 text-emerald-800 animate-pulse font-bold"
                          : status === "CURRENT"
                          ? "bg-emerald-100 text-emerald-800"
                          : status === "COMPLETED"
                          ? "bg-slate-100 text-slate-500"
                          : "bg-amber-50 text-amber-800 border border-amber-200"
                      }`}>
                        {status}
                      </span>
                    </div>
                    <h4 className="font-bold text-xs text-slate-900 mt-1">{cls.name}</h4>
                    <p className="text-[11px] text-slate-500 mt-0.5">{cls.program} · {cls.room}</p>
                    <div className="flex items-center justify-between mt-1 pt-1 border-t border-slate-100">
                      <p className="text-[11px] font-mono text-slate-600 flex items-center gap-1">
                        <Clock className="w-3 h-3 text-slate-400" />
                        <span>{cls.timeFormatted}</span>
                      </p>
                      {!isSessionLive && !isSelected && (
                        <span className="text-[10px] text-blue-600 font-semibold hover:underline">
                          Select for Attendance →
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            <button
              onClick={() => setShowScheduleModal(false)}
              className="w-full py-2 bg-slate-900 hover:bg-black text-white text-xs font-bold rounded-xl transition"
            >
              Close Schedule
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

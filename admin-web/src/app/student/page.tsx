"use client";

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { 
  ArrowLeft, 
  KeyRound, 
  CheckCircle2, 
  BookOpen, 
  Clock, 
  MapPin, 
  Wifi, 
  ShieldCheck, 
  Check, 
  AlertCircle,
  Crosshair,
  RefreshCw,
  Sparkles,
  AlertTriangle,
  Signal,
  Zap,
  LogOut,
  Radio,
  Database,
  User,
  GraduationCap,
  Trash2
} from "lucide-react";
import { 
  getBrowserGeofence, 
  GeofenceResult, 
  getGeofenceMode, 
  setGeofenceMode, 
  GeofenceMode 
} from "@/lib/geoFence";
import { supabase } from "@/lib/supabaseClient";
import { 
  getActiveSessionFromDB, 
  recordStudentAttendanceInDB,
  registerOrGetStudentInDB, 
  fetchLiveClassesFromDB,
  fetchStudentEnrolledClassesFromDB,
  getAndroidJoinCode,
  enrollStudentInClassInDB,
  DBClass,
  DBSession,
  bindStudentDeviceInDB,
  getStudentBoundDeviceFromDB,
  requestStudentDeviceUnbindInDB,
  BoundDevice
} from "@/lib/attendanceService";

interface NetworkStatus {
  interface: string;
  localIp: string;
  gateway: string;
  subnet: string;
  isSameSubnet: boolean;
  detectedSsid: string;
  detectedWifiName: string;
}

interface EnrolledClass {
  id: string;
  subjectCode: string;
  subjectName: string;
  section: string;
  joinCode: string;
  roomNo: string;
  wifiSsid: string;
  latitude: number;
  longitude: number;
  joinedAt: string;
  attendancePct: number;
}

const DEFAULT_ENROLLED_CLASSES: EnrolledClass[] = [];

export default function StudentPortal() {
  const router = useRouter();

  // Authentication State
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [authMode, setAuthMode] = useState<"quick" | "signin" | "register">("quick");
  const [inputPassword, setInputPassword] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authSuccessMsg, setAuthSuccessMsg] = useState<string | null>(null);
  const [isSigningInGoogle, setIsSigningInGoogle] = useState(false);
  const [oauthError, setOauthError] = useState<string | null>(null);
  const [studentName, setStudentName] = useState("");
  const [studentEmail, setStudentEmail] = useState("");
  const [rollNo, setRollNo] = useState("");

  // Sign In Form Input State (for new phones)
  const [inputName, setInputName] = useState("");
  const [inputRollNo, setInputRollNo] = useState("");
  const [inputEmail, setInputEmail] = useState("");

  // Real Hardware Network Detection
  const [detectedNetwork, setDetectedNetwork] = useState<NetworkStatus | null>(null);
  const [isDetectingNetwork, setIsDetectingNetwork] = useState(false);
  const [studentConnectedWifi, setStudentConnectedWifi] = useState<string>("Cellular Data (Mobile Network)");
  const [selectedBssid, setSelectedBssid] = useState<string>("00:00:00:00:00:00");
  const [boundDevice, setBoundDevice] = useState<BoundDevice | null>(null);
  const [showUnbindModal, setShowUnbindModal] = useState(false);
  const [unbindReason, setUnbindReason] = useState("");
  const [unbindSubmitting, setUnbindSubmitting] = useState(false);
  const [unbindMessage, setUnbindMessage] = useState<string | null>(null);
  const [deviceMismatchError, setDeviceMismatchError] = useState<string | null>(null);

  // Geofence Mode
  const [geoMode, setGeoModeState] = useState<GeofenceMode>("demo_inside");

  // Join Class State
  const [joinCodeInput, setJoinCodeInput] = useState("");
  const [joinSuccessMsg, setJoinSuccessMsg] = useState<string | null>(null);
  const [joinErrorMsg, setJoinErrorMsg] = useState<string | null>(null);
  
  // Classes & Live Session
  const [myClasses, setMyClasses] = useState<EnrolledClass[]>([]);
  const [availableDbClasses, setAvailableDbClasses] = useState<DBClass[]>([]);
  const [activeSession, setActiveSession] = useState<DBSession | null>(null);
  const [activeSessionCode, setActiveSessionCode] = useState<string | null>(null);

  // Verification Telemetry State
  const [isVerifyingPresence, setIsVerifyingPresence] = useState(false);
  const [verificationResult, setVerificationResult] = useState<GeofenceResult | null>(null);
  const [attendanceStatus, setAttendanceStatus] = useState<"PRESENT" | "ABSENT" | "PENDING">("PENDING");
  const [autoCheckInToast, setAutoCheckInToast] = useState<string | null>(null);
  const [isDbSynced, setIsDbSynced] = useState(false);
  const autoCheckedInRef = useRef<boolean>(false);

  const syncDeviceBinding = async (r: string, n: string, e?: string) => {
    try {
      let instId = localStorage.getItem("smart_attendance_installation_id");
      if (!instId) {
        instId = "inst_" + Math.random().toString(36).substring(2) + Date.now().toString(36);
        localStorage.setItem("smart_attendance_installation_id", instId);
      }
      const ua = typeof navigator !== "undefined" ? navigator.userAgent : "Web Device";
      const model = ua.includes("iPhone") ? "Apple iPhone (Mobile Safari)" : ua.includes("Android") ? "Android Phone (Chrome)" : "Personal Laptop/PC";
      
      const res = await bindStudentDeviceInDB({
        rollNo: r,
        name: n,
        email: e,
        installationId: instId,
        deviceModel: model
      });

      if (!res.success && res.message.includes("ANTI-PROXY")) {
        setDeviceMismatchError(res.message);
      } else {
        setDeviceMismatchError(null);
        const dev = await getStudentBoundDeviceFromDB(r);
        if (dev) setBoundDevice(dev);
      }
    } catch (err) {}
  };

  const processUserSession = async (user: any) => {
  if (!user) return;
  const metadata = user.user_metadata || {};
  const name = metadata.full_name || metadata.name || user.email?.split("@")[0] || "Student";
  const email = user.email || "";

  let matchedRoll = metadata.roll_no || metadata.rollNumber || "";
  if (!matchedRoll) {
    try {
      const { data: dbUser } = await supabase
        .from("users")
        .select("id, name, students(roll_number)")
        .eq("email", email)
        .maybeSingle();

      const sData = (dbUser as any)?.students;
      const sObj = Array.isArray(sData) ? sData[0] : sData;
      if (sObj?.roll_number) {
        matchedRoll = sObj.roll_number;
      }
    } catch (e) {}
  }

  if (!matchedRoll) {
    const emailPrefix = email.split("@")[0].toUpperCase();
    matchedRoll = /^[0-9A-Z]{5,10}$/.test(emailPrefix) 
      ? emailPrefix 
      : `26CS${Math.floor(100 + Math.random() * 900)}`;
  }

  setStudentName(name);
  setRollNo(matchedRoll);
  setStudentEmail(email);
  setIsLoggedIn(true);
  autoCheckedInRef.current = false;
  loadStudentEnrollments(matchedRoll);

  localStorage.setItem("smart_attendance_student_profile", JSON.stringify({
    name,
    rollNo: matchedRoll,
    email
  }));

  await registerOrGetStudentInDB({
    name,
    rollNo: matchedRoll,
    email
  });

  const live = await getActiveSessionFromDB();
  if (live) {
    setActiveSession(live);
    setActiveSessionCode(live.joinCode);
    const saved = localStorage.getItem(`smart_attendance_enrolled_${matchedRoll}`);
    let enrolledList: EnrolledClass[] = [];
    try {
      if (saved) enrolledList = JSON.parse(saved);
    } catch (e) {}
    const isEnrolled = enrolledList.some(c => 
      c.id === live.classId || 
      c.joinCode.toUpperCase() === live.joinCode.toUpperCase() ||
      c.subjectCode.toUpperCase() === live.subjectCode.toUpperCase()
    );
    if (isEnrolled) {
      executePresenceVerification(live, name, matchedRoll, email);
    }
  }
};


  // Check existing session on this phone/device
  useEffect(() => {
    // 0. Handle OAuth redirect errors from URL
    if (typeof window !== "undefined") {
      const currentUrl = new URL(window.location.href);
      const urlErr = currentUrl.searchParams.get("error_description") || currentUrl.searchParams.get("oauth_error") || currentUrl.searchParams.get("error");
      if (urlErr) {
        setOauthError(decodeURIComponent(urlErr.replace(/\+/g, " ")));
        window.history.replaceState({}, document.title, window.location.pathname);
      }
    }

    // 1. Check local saved student profile
    const saved = localStorage.getItem("smart_attendance_student_profile");
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        if (parsed.name && parsed.rollNo) {
          setStudentName(parsed.name);
          setRollNo(parsed.rollNo);
          setStudentEmail(parsed.email || `${parsed.rollNo}@student.iiitnr.edu.in`);
          setIsLoggedIn(true);
          loadStudentEnrollments(parsed.rollNo);
          syncDeviceBinding(parsed.rollNo, parsed.name, parsed.email);
        }
      } catch (e) {}
    }

    // 2. Listen to Supabase Google Session and OAuth Redirects
    supabase.auth.getSession().then(({ data: { session } }) => {
      if (session?.user) {
        processUserSession(session.user);
      }
    });

    const { data: authListener } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (session?.user && (event === "SIGNED_IN" || event === "INITIAL_SESSION")) {
        processUserSession(session.user);
      }
    });

    try { localStorage.removeItem("smart_attendance_global_classes"); } catch (e) {}
    fetchRealNetworkStatus();
    loadClassesFromDB();
    setGeoModeState(getGeofenceMode());

    syncLiveSession();
    const interval = setInterval(syncLiveSession, 1200);

    // Supabase Realtime for instant lecture broadcast
    const sessionChannel = supabase
      .channel("student-realtime-sessions")
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "attendance_sessions" },
        () => {
          syncLiveSession();
        }
      )
      .subscribe();

    const handleStorage = (e: StorageEvent) => {
      if (e.key === "smart_attendance_active_session_code") {
        syncLiveSession();
      }
    };
    window.addEventListener("storage", handleStorage);

    const classesInterval = setInterval(loadClassesFromDB, 3000);

    const classesChannel = supabase
      .channel("student-realtime-classes")
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "classes" },
        () => {
          loadClassesFromDB();
        }
      )
      .subscribe();

    return () => {
      clearInterval(interval);
      clearInterval(classesInterval);
      authListener?.subscription?.unsubscribe();
      supabase.removeChannel(sessionChannel);
      supabase.removeChannel(classesChannel);
      window.removeEventListener("storage", handleStorage);
    };
  }, []);

  const handleGoogleSignIn = async () => {
    setIsSigningInGoogle(true);
    setOauthError(null);
    try {
      const redirectOrigin = typeof window !== "undefined" ? window.location.origin : "https://admin-web-peach-one.vercel.app";
      const { data, error } = await supabase.auth.signInWithOAuth({
        provider: "google",
        options: {
          redirectTo: `${redirectOrigin}/student`
        }
      });
      if (error) {
        console.error("Google sign in error:", error);
        setOauthError(error.message);
        setIsSigningInGoogle(false);
      }
    } catch (e: any) {
      console.error("Google OAuth exception:", e);
      setOauthError(e?.message || "Failed to initiate Google sign in");
      setIsSigningInGoogle(false);
    }
  };

  // Normal Email & Password Authentication Handlers
  const handleEmailSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    setAuthSuccessMsg(null);
    if (!inputEmail.trim() || !inputPassword.trim()) {
      setAuthError("Please enter your email and password.");
      return;
    }
    setAuthLoading(true);

    try {
      const cleanEmail = inputEmail.trim().toLowerCase();
      const { data, error } = await supabase.auth.signInWithPassword({
        email: cleanEmail,
        password: inputPassword
      });

      if (error) {
        setAuthError(error.message || "Invalid email or password");
        setAuthLoading(false);
        return;
      }

      if (data.user) {
        await processUserSession(data.user);
      }
    } catch (err: any) {
      setAuthError(err?.message || "Sign in failed");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleEmailRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    setAuthSuccessMsg(null);

    if (!inputName.trim() || !inputRollNo.trim() || !inputEmail.trim() || !inputPassword.trim()) {
      setAuthError("Please fill in all registration fields.");
      return;
    }

    if (inputPassword.length < 6) {
      setAuthError("Password must be at least 6 characters.");
      return;
    }

    setAuthLoading(true);
    const cleanName = inputName.trim();
    const cleanRoll = inputRollNo.trim().toUpperCase();
    const cleanEmail = inputEmail.trim().toLowerCase();

    try {
      // 1. Register with Supabase Auth
      const { data, error } = await supabase.auth.signUp({
        email: cleanEmail,
        password: inputPassword,
        options: {
          data: {
            full_name: cleanName,
            roll_no: cleanRoll
          }
        }
      });

      if (error) {
        if (error.message.toLowerCase().includes("already registered") || error.message.toLowerCase().includes("exists")) {
          setAuthError("An account with this email already exists. Please switch to Sign In.");
        } else {
          setAuthError(error.message);
        }
        setAuthLoading(false);
        return;
      }

      // 2. Link in Supabase public students table
      await registerOrGetStudentInDB({
        name: cleanName,
        rollNo: cleanRoll,
        email: cleanEmail
      });

      // 3. Set logged in state
      setStudentName(cleanName);
      setRollNo(cleanRoll);
      setStudentEmail(cleanEmail);
      setIsLoggedIn(true);
      autoCheckedInRef.current = false;
      setMyClasses([]);

      localStorage.setItem("smart_attendance_student_profile", JSON.stringify({
        name: cleanName,
        rollNo: cleanRoll,
        email: cleanEmail
      }));

      loadStudentEnrollments(cleanRoll);
      setAuthSuccessMsg("Account registered successfully!");

      const live = await getActiveSessionFromDB();
      if (live) {
        setActiveSession(live);
      }
    } catch (err: any) {
      setAuthError(err?.message || "Registration failed");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleStudentLogin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputName.trim() || !inputRollNo.trim()) return;

    const cleanName = inputName.trim();
    const cleanRoll = inputRollNo.trim().toUpperCase();
    const cleanEmail = inputEmail.trim() || `${cleanRoll.toLowerCase()}@student.iiitnr.edu.in`;

    setStudentName(cleanName);
    setRollNo(cleanRoll);
    setStudentEmail(cleanEmail);
    setIsLoggedIn(true);
    autoCheckedInRef.current = false;
    setAttendanceStatus("PENDING");
    loadStudentEnrollments(cleanRoll);

    localStorage.setItem("smart_attendance_student_profile", JSON.stringify({
      name: cleanName,
      rollNo: cleanRoll,
      email: cleanEmail
    }));

    // Register into Supabase cloud DB immediately
    registerOrGetStudentInDB({
      name: cleanName,
      rollNo: cleanRoll,
      email: cleanEmail
    }).then(() => {
      const saved = localStorage.getItem(`smart_attendance_enrolled_${cleanRoll}`);
      let enrolledList: EnrolledClass[] = [];
      try {
        if (saved) enrolledList = JSON.parse(saved);
      } catch (e) {}
      if (activeSession && enrolledList.some(c => 
        c.id === activeSession.classId || 
        c.joinCode.toUpperCase() === activeSession.joinCode.toUpperCase() || 
        c.subjectCode.toUpperCase() === activeSession.subjectCode.toUpperCase()
      )) {
        executePresenceVerification(activeSession, cleanName, cleanRoll, cleanEmail);
      }
    }).catch(err => console.warn("Background student register warning:", err));
  };



  const handleLogout = async () => {
    try {
      await supabase.auth.signOut();
    } catch (e) {}
    localStorage.removeItem("smart_attendance_student_profile");
    setIsLoggedIn(false);
    setStudentName("");
    setRollNo("");
    setStudentEmail("");
    setInputName("");
    setInputRollNo("");
  };

  // 1. Fetch hardware network
  const fetchRealNetworkStatus = async () => {
    setIsDetectingNetwork(true);
    try {
      const res = await fetch("/api/network-status");
      const data = await res.json();
      if (data.success && data.detectedSsid) {
        setDetectedNetwork(data);
        setStudentConnectedWifi(data.detectedSsid);
        setSelectedBssid(data.detectedBssid || "00:00:00:00:00:00");
      } else {
        setStudentConnectedWifi("Cellular Data (Mobile Network)");
        setSelectedBssid("00:00:00:00:00:00");
      }
    } catch (e) {
      console.warn("Could not detect network:", e);
      setStudentConnectedWifi("Cellular Data (Mobile Network)");
      setSelectedBssid("00:00:00:00:00:00");
    } finally {
      setIsDetectingNetwork(false);
    }
  };

  // 2. Load Real Classes pool from Supabase and auto-prune deleted subjects
  const loadClassesFromDB = async () => {
    const dbClasses = await fetchLiveClassesFromDB();
    if (dbClasses.length > 0) {
      setAvailableDbClasses(dbClasses);

      // Auto-prune: If teacher deleted a class from DB, remove it from student view automatically!
      setMyClasses(prev => {
        const liveClassIds = new Set(dbClasses.map(c => c.id));
        const liveSubjectCodes = new Set(dbClasses.map(c => c.subjectCode.toUpperCase()));
        const liveJoinCodes = new Set(dbClasses.map(c => c.joinCode.toUpperCase()));

        const valid = prev.filter(c => 
          liveClassIds.has(c.id) || 
          liveSubjectCodes.has(c.subjectCode.toUpperCase()) || 
          liveJoinCodes.has(c.joinCode.toUpperCase())
        );

        if (valid.length !== prev.length) {
          const currentRoll = rollNo || (typeof window !== "undefined" ? localStorage.getItem("smart_attendance_student_roll") : null);
          if (currentRoll) {
            localStorage.setItem("smart_attendance_enrolled_" + currentRoll, JSON.stringify(valid));
          }
        }
        return valid;
      });
    }
  };

  const loadStudentEnrollments = async (currentRoll: string) => {
    if (!currentRoll) {
      setMyClasses([]);
      return;
    }
    const saved = localStorage.getItem(`smart_attendance_enrolled_${currentRoll}`);
    let localList: EnrolledClass[] = [];
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed)) localList = parsed;
      } catch (e) {}
    }
    setMyClasses(localList);

    // Sync from real Supabase course_enrollments (cross-platform with Android)
    try {
      const dbEnrollments = await fetchStudentEnrolledClassesFromDB(currentRoll);
      if (dbEnrollments.length > 0) {
        const map = new Map<string, EnrolledClass>();
        localList.forEach(c => map.set(c.id, c));
        dbEnrollments.forEach(c => map.set(c.id, c));
        const merged = Array.from(map.values());
        setMyClasses(merged);
        localStorage.setItem(`smart_attendance_enrolled_${currentRoll}`, JSON.stringify(merged));
      }
    } catch (e) {
      console.warn("Enrollment load sync note:", e);
    }
  };

  // 3. Poll and Sync Active Session from Supabase & Storage
  const syncLiveSession = async () => {
    const dbSession = await getActiveSessionFromDB();
    if (dbSession) {
      setActiveSession(dbSession);
      setActiveSessionCode(dbSession.joinCode);

      // Student wifi is maintained from hardware or simulation selection

      // ZERO-TOUCH WI-FI SYNC: Check if student is already marked present via classroom Wi-Fi AP in DB
      if (rollNo) {
        try {
          const { data: rec } = await supabase
            .from("attendance_records")
            .select("id, status, verification_method, wifi_ap_verified")
            .eq("session_id", dbSession.id)
            .eq("sensor_details->>roll_number", rollNo)
            .maybeSingle();

          if (rec?.status === "PRESENT") {
            setAttendanceStatus("PRESENT");
            autoCheckedInRef.current = true;
          }
        } catch (e) {}
      }
      return;
    }

    // Attendance is ONLY open when teacher has explicitly started it in Supabase DB
    setActiveSession(null);
    setActiveSessionCode(null);
    setAttendanceStatus("PENDING");
    autoCheckedInRef.current = false;
    localStorage.removeItem("smart_attendance_active_session_code");
  };

  // Enrolled check: student must have joined the class to take attendance!
  const isEnrolledInActive = Boolean(
    activeSession &&
    myClasses.some(c => 
      c.id === activeSession.classId || 
      c.joinCode.toUpperCase() === activeSession.joinCode.toUpperCase() ||
      c.subjectCode.toUpperCase() === activeSession.subjectCode.toUpperCase()
    )
  );

  // AUTOMATIC ATTENDANCE EXECUTION (Only triggers if enrolled!)
  useEffect(() => {
    if (!isLoggedIn || !activeSessionCode || !activeSession || !isEnrolledInActive || isVerifyingPresence) {
      return;
    }

    const timer = setTimeout(() => {
      executePresenceVerification(activeSession);
    }, 400);

    return () => clearTimeout(timer);
  }, [isLoggedIn, activeSessionCode, activeSession, isEnrolledInActive, geoMode, studentConnectedWifi]);

  const executePresenceVerification = async (
    sess: DBSession,
    overrideName?: string,
    overrideRoll?: string,
    overrideEmail?: string
  ) => {
    const activeStudentName = overrideName || studentName;
    const activeRollNo = overrideRoll || rollNo;
    const activeEmail = overrideEmail || studentEmail || `${activeRollNo.toLowerCase()}@student.iiitnr.edu.in`;

    if (!activeStudentName || !activeRollNo) return;
    setIsVerifyingPresence(true);
    try {
      const geoResult = await getBrowserGeofence();
      setVerificationResult(geoResult);

      const targetWifi = (sess.wifiSsid || "Pranjal").toLowerCase().trim();
      const currentWifi = (studentConnectedWifi || "").toLowerCase().trim();
      
      // HARDWARE ANTI-HOTSPOT CHECK:
      // Router BSSID must match classroom hardware AP (A4:2B:B0:8C:12:EF). Rogue hotspots with same SSID are rejected!
      const isOfficialBssid = selectedBssid === "A4:2B:B0:8C:12:EF";
      const isWifiMatched = 
        isOfficialBssid && (
          (currentWifi === targetWifi) || 
          (currentWifi.includes(targetWifi) && !currentWifi.includes("fake") && !currentWifi.includes("cellular") && !currentWifi.includes("other")) ||
          (detectedNetwork?.isSameSubnet === true && (currentWifi === targetWifi || currentWifi.includes(targetWifi)))
        );

      const isPresent = geoResult.isInside && isWifiMatched;

      if (isPresent) {
        autoCheckedInRef.current = true;
        setAttendanceStatus("PRESENT");
        setAutoCheckInToast(`⚡ Automatic Attendance Recorded for ${sess.subjectName}!`);
        setTimeout(() => setAutoCheckInToast(null), 4000);

        await recordStudentAttendanceInDB({
          sessionId: sess.id,
          rollNo: activeRollNo,
          name: activeStudentName,
          email: activeEmail,
          status: "PRESENT",
          distanceMeters: geoResult.distanceMeters,
          wifiSsid: currentWifi || "Pranjal",
          isWifiMatched: true
        });
      } else {
        // MONOTONIC ATTENDANCE: Once marked PRESENT, do NOT flip back to ABSENT
        // when phone screen dims, Wi-Fi scans or user switches tabs!
        if (autoCheckedInRef.current || attendanceStatus === "PRESENT") {
          return;
        }
        setAttendanceStatus("ABSENT");
      }
    } catch (err) {
      console.warn("Verification error:", err);
    } finally {
      setIsVerifyingPresence(false);
    }
  };

  const handleModeChange = (mode: GeofenceMode) => {
    setGeoModeState(mode);
    setGeofenceMode(mode);
    autoCheckedInRef.current = false;
    if (activeSession) {
      executePresenceVerification(activeSession);
    }
  };

  const handleUnenrollClass = (classId: string) => {
    const updated = myClasses.filter(c => c.id !== classId);
    setMyClasses(updated);
    if (rollNo) {
      localStorage.setItem(`smart_attendance_enrolled_${rollNo}`, JSON.stringify(updated));
    }
  };

    const handleJoinClass = async (e: React.FormEvent) => {
    e.preventDefault();
    setJoinErrorMsg(null);
    setJoinSuccessMsg(null);

    const entered = joinCodeInput.trim().toUpperCase();
    if (!entered) return;

    if (myClasses.some(c => 
      c.joinCode.toUpperCase() === entered || 
      c.subjectCode.toUpperCase() === entered
    )) {
      setJoinErrorMsg("You are already enrolled in this subject!");
      return;
    }

    // 1. Fetch live classes from DB
    const pool = await fetchLiveClassesFromDB();
    setAvailableDbClasses(pool);

    // 2. Exact match check (NO loose prefix matching)
    let matched = pool.find(c => {
      const androidCode = getAndroidJoinCode(c.subjectCode, c.id).toUpperCase();
      return (
        c.joinCode.toUpperCase() === entered ||
        c.subjectCode.toUpperCase() === entered ||
        androidCode === entered
      );
    });

    // 3. Exact match against active live lecture
    if (!matched && activeSession) {
      const activeAndroidCode = getAndroidJoinCode(activeSession.subjectCode, activeSession.classId).toUpperCase();
      if (
        activeSession.joinCode.toUpperCase() === entered ||
        activeSession.subjectCode.toUpperCase() === entered ||
        activeAndroidCode === entered
      ) {
        matched = {
          id: activeSession.classId,
          subjectCode: activeSession.subjectCode,
          subjectName: activeSession.subjectName,
          section: "Section A",
          joinCode: activeSession.joinCode,
          roomNo: activeSession.roomNo,
          wifiSsid: activeSession.wifiSsid,
          latitude: 21.128456,
          longitude: 81.766184,
          teacherId: activeSession.teacherId,
          teacherName: "Faculty",
          students: [],
          createdAt: activeSession.startTime
        };
      }
    }

    // 4. Exact query to Supabase database for subjects with exact matching code
    if (!matched) {
      try {
        const { data: dbSub } = await supabase
          .from("subjects")
          .select(`
            id, name, code,
            classes (
              id, room, is_active,
              teachers ( id, users ( name ) )
            )
          `)
          .ilike("code", entered)
          .maybeSingle();

        if (dbSub) {
          const clsList: any[] = dbSub.classes || [];
          const cls = clsList.find((x: any) => x.is_active) || clsList[0];
          const t = cls?.teachers;
          const tUser = Array.isArray(t) ? t[0]?.users : t?.users;

          if (cls) {
            matched = {
              id: cls.id,
              subjectCode: dbSub.code,
              subjectName: dbSub.name,
              section: "Section A",
              joinCode: dbSub.code,
              roomNo: cls.room || "Room A-204 (AC Block)",
              wifiSsid: "Pranjal",
              latitude: 21.128456,
              longitude: 81.766184,
              teacherId: t?.id || "",
              teacherName: tUser?.name || "Dr. Sharma",
              students: [],
              createdAt: new Date().toISOString()
            };
          }
        }
      } catch (e) {
        console.warn("DB search error:", e);
      }
    }

    if (!matched) {
      setJoinErrorMsg(`Subject code "${entered}" not found. Verify with teacher console.`);
      return;
    }

    const newEnrolled: EnrolledClass = {
      id: matched.id,
      subjectCode: matched.subjectCode,
      subjectName: matched.subjectName,
      section: matched.section,
      joinCode: matched.joinCode,
      roomNo: matched.roomNo,
      wifiSsid: matched.wifiSsid,
      latitude: matched.latitude,
      longitude: matched.longitude,
      joinedAt: "Just now",
      attendancePct: 100
    };

    const updated = [...myClasses.filter(c => c.id !== matched!.id), newEnrolled];
    setMyClasses(updated);
    if (rollNo) {
      localStorage.setItem(`smart_attendance_enrolled_${rollNo}`, JSON.stringify(updated));
    }

    // Sync enrollment directly to Supabase DB so Teacher Console reflects it immediately
    enrollStudentInClassInDB({
      classId: matched.id,
      rollNo: rollNo,
      name: studentName,
      email: studentEmail
    }).catch(err => console.warn("Enrollment sync warning:", err));

    setJoinSuccessMsg(`Enrolled in ${matched.subjectName} (${matched.joinCode})!`);
    setJoinCodeInput("");

    // If active session is for this subject, immediately mark attendance!
    if (activeSession && (
      activeSession.classId === matched.id || 
      activeSession.joinCode.toUpperCase() === matched.joinCode.toUpperCase() || 
      activeSession.subjectCode.toUpperCase() === matched.subjectCode.toUpperCase()
    )) {
      autoCheckedInRef.current = false;
      setTimeout(() => {
        executePresenceVerification(activeSession);
      }, 250);
    }
  };

  // IF NOT LOGGED IN: SHOW STUDENT SIGN IN / REGISTRATION SCREEN
  if (!isLoggedIn) {
    return (
      <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between p-4 sm:p-6 font-sans">
        <header className="max-w-md mx-auto w-full flex items-center justify-between py-4">
          <div className="flex items-center gap-3">
            <Link 
              href="/" 
              className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-slate-900 font-black text-lg shadow-lg shadow-blue-500/25"
            >
              SA
            </Link>
            <div>
              <span className="font-extrabold text-base tracking-tight text-slate-900">IIIT Naya Raipur</span>
              <span className="text-xs block text-slate-600">Student Sign In</span>
            </div>
          </div>
          <Link href="/" className="text-xs text-slate-600 hover:text-slate-900 flex items-center gap-1">
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Home</span>
          </Link>
        </header>

        <div className="max-w-md mx-auto w-full my-auto py-6">
          <div className="bg-white border border-slate-200 shadow-sm rounded-3xl p-6 sm:p-8 shadow-2xl space-y-6">
            <div className="text-center space-y-1">
              <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 border border-blue-500/20 flex items-center justify-center mx-auto mb-3">
                <GraduationCap className="w-6 h-6" />
              </div>
              <h1 className="text-2xl font-black text-slate-900 tracking-tight">Student Login</h1>
              <p className="text-xs text-slate-600">Enter your details to mark attendance on this device</p>
            </div>

            {/* Google Sign In Option */}
            <div className="space-y-3">
              <button
                type="button"
                onClick={handleGoogleSignIn}
                disabled={isSigningInGoogle}
                className="w-full py-3.5 px-4 bg-white hover:bg-slate-100 active:scale-[0.99] text-slate-900 rounded-2xl font-bold text-sm shadow-xl flex items-center justify-center gap-3 transition-all cursor-pointer border border-slate-200"
              >
                <svg className="w-5 h-5 shrink-0" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                </svg>
                <span>{isSigningInGoogle ? "Connecting Google..." : "Sign in with Google"}</span>
              </button>

              {oauthError && (
                <div className="text-xs text-rose-600 bg-rose-50 border border-rose-200 p-3 rounded-2xl space-y-1">
                  <div className="font-bold flex items-center justify-between">
                    <span>Google Sign In Notice</span>
                    <button type="button" onClick={() => setOauthError(null)} className="text-rose-400 hover:text-rose-600 text-xs font-bold px-1">✕</button>
                  </div>
                  <p className="text-[11px] leading-relaxed text-rose-700">{oauthError}</p>
                  <p className="text-[10px] text-slate-500 pt-1">Tip: You can use <b>⚡ Quick Roll No</b> or <b>Email Sign In / Register</b> below for instant access.</p>
                </div>
              )}

              <div className="flex items-center gap-3 py-1">
                <div className="h-px bg-slate-100 flex-1"></div>
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  or use email & password
                </span>
                <div className="h-px bg-slate-100 flex-1"></div>
              </div>
            </div>

            {/* Tab Selector: Quick Login vs Sign In vs Create Account */}
            <div className="grid grid-cols-3 p-1 bg-slate-50 border border-slate-200 rounded-2xl gap-1">
              <button
                type="button"
                onClick={() => { setAuthMode("quick"); setAuthError(null); setAuthSuccessMsg(null); }}
                className={`py-2 text-[11px] font-bold rounded-xl transition-all cursor-pointer ${
                  authMode === "quick"
                    ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                ⚡ Quick Roll No
              </button>
              <button
                type="button"
                onClick={() => { setAuthMode("signin"); setAuthError(null); setAuthSuccessMsg(null); }}
                className={`py-2 text-[11px] font-bold rounded-xl transition-all cursor-pointer ${
                  authMode === "signin"
                    ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                Email Sign In
              </button>
              <button
                type="button"
                onClick={() => { setAuthMode("register"); setAuthError(null); setAuthSuccessMsg(null); }}
                className={`py-2 text-[11px] font-bold rounded-xl transition-all cursor-pointer ${
                  authMode === "register"
                    ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                Register
              </button>
            </div>

            {authError && (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs animate-in fade-in">
                <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
                <span>{authError}</span>
              </div>
            )}

            {authSuccessMsg && (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs animate-in fade-in">
                <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
                <span>{authSuccessMsg}</span>
              </div>
            )}

            {authMode === "quick" ? (
              <form onSubmit={handleStudentLogin} className="space-y-4">
                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="text-xs font-semibold text-slate-600 uppercase tracking-wider">
                      Full Name *
                    </label>
                    <span className="text-[10px] text-blue-600 font-medium">Instant Verify</span>
                  </div>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Pooja Verma or Rahul"
                    value={inputName}
                    onChange={(e) => setInputName(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Institutional Roll Number *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. 26CS042 or 26CS015"
                    value={inputRollNo}
                    onChange={(e) => setInputRollNo(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm uppercase font-mono tracking-wider"
                  />
                </div>

                

                <button
                  type="submit"
                  className="w-full py-3.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white rounded-xl font-bold text-sm shadow-lg shadow-blue-600/25 transition-all hover:scale-[1.01] cursor-pointer"
                >
                  Sign In to Console & Mark Attendance →
                </button>
              </form>
            ) : authMode === "signin" ? (
              <form onSubmit={handleEmailSignIn} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    College / Personal Email *
                  </label>
                  <input
                    type="email"
                    required
                    placeholder="e.g. rahul@student.iiitnr.edu.in"
                    value={inputEmail}
                    onChange={(e) => setInputEmail(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Password *
                  </label>
                  <input
                    type="password"
                    required
                    placeholder="••••••••"
                    value={inputPassword}
                    onChange={(e) => setInputPassword(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm"
                  />
                </div>

                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full py-3.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white rounded-xl font-bold text-sm shadow-lg shadow-blue-600/25 transition-all hover:scale-[1.01] cursor-pointer"
                >
                  {authLoading ? "Signing In..." : "Sign In to Student Console →"}
                </button>
              </form>
            ) : (
              <form onSubmit={handleEmailRegister} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Full Name *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Rahul Kumar or Pooja Verma"
                    value={inputName}
                    onChange={(e) => setInputName(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Institutional Roll Number *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. 26CS042 or 26DSAI001"
                    value={inputRollNo}
                    onChange={(e) => setInputRollNo(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm uppercase font-mono tracking-wider"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Email Address *
                  </label>
                  <input
                    type="email"
                    required
                    placeholder="e.g. rahul@student.iiitnr.edu.in"
                    value={inputEmail}
                    onChange={(e) => setInputEmail(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-600 uppercase tracking-wider mb-1.5">
                    Create Password * (min 6 characters)
                  </label>
                  <input
                    type="password"
                    required
                    minLength={6}
                    placeholder="••••••••"
                    value={inputPassword}
                    onChange={(e) => setInputPassword(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm"
                  />
                </div>

                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full py-3.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white rounded-xl font-bold text-sm shadow-lg shadow-blue-600/25 transition-all hover:scale-[1.01] cursor-pointer"
                >
                  {authLoading ? "Creating Account..." : "Register & Create Student Account →"}
                </button>
              </form>
            )}

            <div className="text-center pt-1">
              {authMode === "signin" ? (
                <p className="text-xs text-slate-600">
                  New to IIIT-NR Portal?{" "}
                  <button
                    type="button"
                    onClick={() => { setAuthMode("register"); setAuthError(null); setAuthSuccessMsg(null); }}
                    className="text-blue-600 hover:underline font-bold cursor-pointer"
                  >
                    Create an Account
                  </button>
                </p>
              ) : (
                <p className="text-xs text-slate-600">
                  Already have an account?{" "}
                  <button
                    type="button"
                    onClick={() => { setAuthMode("signin"); setAuthError(null); setAuthSuccessMsg(null); }}
                    className="text-blue-600 hover:underline font-bold cursor-pointer"
                  >
                    Sign In here
                  </button>
                </p>
              )}
            </div>


          </div>
        </div>

        <footer className="text-center text-xs text-slate-600 py-4">
          IIIT-NR Smart Attendance System • Student Verification
        </footer>
      </div>
    );
  }

  // IF LOGGED IN: SHOW REGULAR STUDENT CONSOLE
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans selection:bg-blue-500 selection:text-slate-900">
      {/* Toast Notification */}
      {autoCheckInToast && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-50 bg-emerald-600/95 backdrop-blur-md text-slate-900 px-5 py-2.5 rounded-2xl shadow-2xl border border-emerald-400/30 text-xs font-bold tracking-wide flex items-center gap-2 animate-in fade-in slide-in-from-top-3">
          <Zap className="w-4 h-4 fill-emerald-200" />
          <span>{autoCheckInToast}</span>
        </div>
      )}

      {/* Top Navbar */}
      <header className="border-b border-slate-200 bg-white/60 backdrop-blur-xl sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link 
              href="/" 
              className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-slate-900 font-black text-lg shadow-lg shadow-blue-500/25 hover:scale-105 transition-all"
            >
              SA
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-base tracking-tight text-slate-900">IIIT Naya Raipur</span>
                <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md bg-blue-50 text-blue-600 border border-blue-200">
                  Student Console
                </span>
              </div>
              <p className="text-xs text-slate-600">Automatic Zero-Touch Attendance System</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2.5 bg-white border border-slate-200 shadow-sm px-3 py-1.5 rounded-2xl">
              <div className="w-7 h-7 rounded-xl bg-blue-600 text-white flex items-center justify-center text-xs font-bold">
                {studentName ? studentName.slice(0, 2).toUpperCase() : "ST"}
              </div>
              <div className="text-left">
                <span className="text-xs font-bold text-slate-900 block leading-tight">{studentName}</span>
                <span className="text-[10px] text-slate-600 block font-mono">{rollNo}</span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setShowUnbindModal(true)}
              className={`px-2.5 py-1.5 rounded-xl border text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                boundDevice?.status === "PENDING_UNBIND"
                  ? "bg-amber-500/15 border-amber-500/40 text-amber-300"
                  : "bg-white border-slate-200 text-slate-700 hover:border-slate-200 hover:text-slate-900"
              }`}
              title="Hardware Device Lock Status"
            >
              <ShieldCheck className="w-3.5 h-3.5 text-blue-600" />
              <span className="hidden sm:inline">
                {boundDevice?.status === "PENDING_UNBIND" ? "Unbind Pending" : "Device Bound"}
              </span>
            </button>

            <Link
              href="/teacher"
              title="Switch to Faculty Portal"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-50 hover:bg-blue-100 border border-blue-200 text-blue-700 text-xs font-bold transition-all"
            >
              <BookOpen className="w-3.5 h-3.5 text-blue-600" />
              <span className="hidden sm:inline">Faculty Portal</span>
            </Link>

            <button
              onClick={handleLogout}
              title="Sign Out"
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 hover:text-rose-200 text-xs font-bold transition-all cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5 text-rose-400" />
              <span className="hidden sm:inline">Log Out</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-3xl mx-auto w-full px-4 sm:px-6 py-8 space-y-6 flex-1">
        {/* ACTIVE LECTURE ATTENDANCE CARD */}
        {activeSession && isEnrolledInActive ? (
          <section className={`bg-white border rounded-3xl p-6 sm:p-7 space-y-4 shadow-xl transition-all ${
            attendanceStatus === "PRESENT" ? "border-emerald-500/50 shadow-emerald-500/10" : "border-rose-500/40 shadow-rose-500/10"
          }`}>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className={`w-2.5 h-2.5 rounded-full ${attendanceStatus === "PRESENT" ? "bg-emerald-400 animate-ping" : "bg-rose-400"}`}></span>
                  <span className={`text-xs font-bold uppercase tracking-wider ${attendanceStatus === "PRESENT" ? "text-emerald-400" : "text-rose-400"}`}>
                    Live Lecture • {activeSession.roomNo}
                  </span>
                </div>
                <h2 className="text-2xl font-black text-slate-900 tracking-tight">
                  {activeSession.subjectName}
                </h2>
                <span className="text-xs font-mono text-slate-600">{activeSession.subjectCode}</span>
              </div>

              <div className="flex items-center gap-2">
                {attendanceStatus === "PRESENT" ? (
                  <div className="flex items-center gap-2 bg-emerald-500/15 border border-emerald-500/40 px-4 py-2.5 rounded-2xl text-emerald-400 text-xs font-bold shadow-lg shadow-emerald-500/10">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                    <span>Present (Verified)</span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 bg-rose-500/15 border border-rose-500/40 px-4 py-2.5 rounded-2xl text-rose-400 text-xs font-bold">
                    <AlertTriangle className="w-4 h-4" />
                    <span>{studentConnectedWifi.toLowerCase().trim() !== (activeSession.wifiSsid || "pranjal").toLowerCase().trim() ? "Wi-Fi Mismatch (Absent)" : "Outside Classroom"}</span>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => {
                    autoCheckedInRef.current = false;
                    if (activeSession) executePresenceVerification(activeSession);
                  }}
                  className="p-2.5 rounded-xl bg-slate-100 hover:bg-slate-700 text-slate-700 hover:text-slate-900 border border-slate-200 text-xs transition-all cursor-pointer"
                  title="Re-check Presence"
                >
                  <RefreshCw className="w-4 h-4 text-blue-600" />
                </button>
              </div>
            </div>

            {/* Sensor Verification Breakdown */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
              <div className={`p-4 rounded-2xl border text-xs space-y-2 ${
                studentConnectedWifi.toLowerCase().trim() === (activeSession.wifiSsid || "pranjal").toLowerCase().trim()
                  ? "bg-emerald-950/20 border-emerald-500/30 text-emerald-300"
                  : "bg-rose-950/20 border-rose-500/30 text-rose-300"
              }`}>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-bold">
                    <Wifi className="w-4 h-4" />
                    <span>Classroom Wi-Fi Sensor</span>
                  </div>
                  <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                    studentConnectedWifi.toLowerCase().trim() === (activeSession.wifiSsid || "pranjal").toLowerCase().trim()
                      ? "bg-emerald-500/20 text-emerald-300"
                      : "bg-rose-500/20 text-rose-300"
                  }`}>
                    {studentConnectedWifi.toLowerCase().trim() === (activeSession.wifiSsid || "pranjal").toLowerCase().trim()
                      ? "MATCHED"
                      : "MISMATCH"}
                  </span>
                </div>
                <div className="space-y-1 text-slate-700 text-[11px]">
                  <div>Required SSID: <span className="font-mono font-bold text-slate-900">{activeSession.wifiSsid || "Pranjal"}</span></div>
                  <div>Router BSSID (MAC): <span className="font-mono text-emerald-400 font-bold">{selectedBssid}</span></div>
                  <div>
                    {selectedBssid === "A4:2B:B0:8C:12:EF" 
                      ? <span className="text-[10px] text-emerald-400 font-medium">✓ Official Classroom AP Verified</span>
                      : <span className="text-[10px] text-rose-400 font-bold">🚨 Fake Hotspot / BSSID Mismatch</span>}
                  </div>
                </div>

                <div className="pt-1">
                  <label className="text-[10px] font-bold text-slate-600 uppercase tracking-wider block mb-1">
                    Hardware AP & Hotspot Simulation:
                  </label>
                  <select
                    value={studentConnectedWifi + "::" + selectedBssid}
                    onChange={(e) => {
                      const [net, bssid] = e.target.value.split("::");
                      setStudentConnectedWifi(net);
                      setSelectedBssid(bssid || "00:00:00:00:00:00");
                      autoCheckedInRef.current = false;
                      setAttendanceStatus("ABSENT");
                      if (activeSession) {
                        setTimeout(() => executePresenceVerification(activeSession), 50);
                      }
                    }}
                    className="w-full text-xs px-2.5 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-900 focus:outline-none focus:border-blue-500 cursor-pointer"
                  >
                    <option value="Cellular Data (Mobile Network)::00:00:00:00:00:00">Cellular Data / 5G (Mobile) • Disconnected</option>
                    <option value="Other Wi-Fi / External Network::32:11:00:AB:CD:EF">Other Wi-Fi (Hostel / Home / Personal)</option>
                    <option value={(activeSession?.wifiSsid || "Pranjal") + "::A4:2B:B0:8C:12:EF"}>Classroom AP ({activeSession?.wifiSsid || "Pranjal"}) • Official Router Verified</option>
                    <option value={(activeSession?.wifiSsid || "Pranjal") + "::F2:45:67:89:AB:CD"}>⚠️ Fake Mobile Hotspot ({activeSession?.wifiSsid || "Pranjal"} • Rogue BSSID)</option>
                  </select>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-slate-50/60 border border-slate-200 text-xs space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-bold text-slate-900">
                    <MapPin className="w-4 h-4 text-blue-600" />
                    <span>Geofence Sensor</span>
                  </div>
                  <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                    verificationResult?.isInside ? "bg-emerald-500/20 text-emerald-300" : "bg-rose-500/20 text-rose-300"
                  }`}>
                    {verificationResult?.isInside ? "IN CLASS (3.4m)" : "OUTSIDE"}
                  </span>
                </div>
                <div className="text-[11px] text-slate-600 space-y-1">
                  <div>Anchor: <span className="text-slate-900">{activeSession.roomNo}</span></div>
                  <div>Distance: <span className="font-mono text-slate-900">{verificationResult?.distanceMeters || 3.4}m</span></div>
                </div>
              </div>
            </div>

            {studentConnectedWifi.toLowerCase().trim() !== (activeSession.wifiSsid || "pranjal").toLowerCase().trim() && (
              <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
                <span>
                  <b>Not Verified:</b> Your device is not connected to classroom Wi-Fi <b>"{activeSession.wifiSsid || "Pranjal"}"</b>. Connect to this Wi-Fi to get marked Present.
                </span>
              </div>
            )}
          </section>
        ) : null}

        {!activeSession && (
          <section className="bg-white border border-slate-200/90 rounded-3xl p-5 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 border border-blue-100">
                <Clock className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">Attendance Not Started</h3>
                <p className="text-xs text-slate-500">Your professor has not opened attendance yet. When teacher clicks &quot;Start Attendance&quot;, verification will pop up automatically.</p>
              </div>
            </div>
            <span className="text-[11px] font-bold text-slate-500 bg-slate-100 px-3 py-1.5 rounded-full shrink-0 self-start sm:self-auto">
              Waiting for Professor
            </span>
          </section>
        )}

        {/* Join Subject Card */}
        <section className="bg-white border border-slate-200 shadow-sm rounded-3xl p-6 space-y-4 shadow-sm">
          <div className="flex items-center gap-2 text-base font-bold text-slate-900">
            <KeyRound className="w-5 h-5 text-blue-600" />
            <span>Join a Subject Batch</span>
          </div>
          <p className="text-xs text-slate-600">
            Enter the Subject Join Code provided by your professor to enroll.
          </p>

          <form onSubmit={handleJoinClass} className="flex gap-2">
            <input
              type="text"
              required
              placeholder="e.g. CS50-6374"
              value={joinCodeInput}
              onChange={(e) => setJoinCodeInput(e.target.value)}
              className="flex-1 px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:outline-none text-slate-900 text-sm uppercase font-mono tracking-wider"
            />
            <button
              type="submit"
              className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold text-sm shadow-md shadow-blue-600/20 transition-all shrink-0 cursor-pointer"
            >
              Join Batch
            </button>
          </form>

          {joinSuccessMsg && (
            <div className="flex items-center gap-2 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-medium animate-in fade-in">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{joinSuccessMsg}</span>
            </div>
          )}

          {joinErrorMsg && (
            <div className="flex items-center gap-2 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-medium animate-in fade-in">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{joinErrorMsg}</span>
            </div>
          )}
        </section>

        {/* My Enrolled Subjects */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <BookOpen className="w-4 h-4 text-blue-600" />
              <span>My Enrolled Subjects</span>
            </h3>
            <span className="text-xs font-mono text-slate-500">{myClasses.length} {myClasses.length === 1 ? "Subject" : "Subjects"}</span>
          </div>

          <div className="space-y-3">
            {myClasses.length === 0 ? (
              <div className="bg-white border border-slate-200 shadow-sm rounded-2xl p-6 text-center text-xs text-slate-600">
                You haven't enrolled in any subjects yet. Enter a join code above to get started.
              </div>
            ) : (
              myClasses.map((c) => (
                <div
                  key={c.id}
                  className="bg-white border border-slate-200 shadow-sm rounded-2xl p-4 flex items-center justify-between gap-4"
                >
                  <div className="space-y-1">
                    <span className="font-mono text-[10px] font-bold px-2 py-0.5 rounded-lg bg-blue-50 text-blue-700 border border-blue-200 font-bold">
                      {c.subjectCode}
                    </span>
                    <h4 className="text-sm font-bold text-slate-900">{c.subjectName}</h4>
                    <span className="text-[11px] text-slate-600 font-mono block">Code: {c.joinCode} • {c.roomNo}</span>
                  </div>

                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <span className="text-xs font-bold text-emerald-600 block font-mono">100%</span>
                      <span className="text-[10px] text-slate-500 uppercase">Attendance</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleUnenrollClass(c.id)}
                      title="Unenroll from this subject"
                      className="p-2 rounded-xl text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 border border-transparent hover:border-rose-500/20 transition-all cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </section></main>
      {/* UNBIND DEVICE REQUEST MODAL */}
      {showUnbindModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-white border border-slate-200 shadow-sm rounded-3xl p-6 sm:p-7 max-w-md w-full space-y-5 shadow-2xl">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-base">
                <ShieldCheck className="w-5 h-5 text-blue-600" />
                <span>Anti-Proxy Device Lock</span>
              </div>
              <button
                type="button"
                onClick={() => setShowUnbindModal(false)}
                className="text-slate-600 hover:text-slate-900 text-xs px-2 py-1 rounded-lg bg-slate-100"
              >
                ✕ Close
              </button>
            </div>

            <div className="p-4 rounded-2xl bg-slate-50/80 border border-slate-200 space-y-2 text-xs">
              <div className="flex justify-between text-slate-700">
                <span className="text-slate-500">Bound Student:</span>
                <span className="font-bold text-slate-900">{studentName} ({rollNo})</span>
              </div>
              <div className="flex justify-between text-slate-700">
                <span className="text-slate-500">Hardware Model:</span>
                <span className="font-mono text-blue-600">{boundDevice?.deviceModel || "Mobile Device (Bound)"}</span>
              </div>
              <div className="flex justify-between text-slate-700">
                <span className="text-slate-500">Lock Status:</span>
                <span className={`font-bold ${boundDevice?.status === "PENDING_UNBIND" ? "text-amber-400" : "text-emerald-400"}`}>
                  {boundDevice?.status === "PENDING_UNBIND" ? "Pending Teacher Approval" : "ACTIVE (Locked)"}
                </span>
              </div>
            </div>

            <div className="text-xs text-slate-600 space-y-3">
              <p>
                <b>Anti-Proxy Policy:</b> Your account is hardware-locked to this device to prevent proxy attendance. To switch phones or reset your account, request an unbind from faculty.
              </p>
              {boundDevice?.status === "PENDING_UNBIND" ? (
                <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs">
                  ⏳ <b>Request Pending:</b> Unbind request sent to faculty. Once approved by your professor on the faculty console, you can bind a new phone.
                </div>
              ) : (
                <div className="space-y-3">
                  <div>
                    <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">
                      Reason for Unbind (e.g. New Phone, Phone Reset):
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. Bought a new phone / formatted device"
                      value={unbindReason}
                      onChange={(e) => setUnbindReason(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                    />
                  </div>
                  <button
                    type="button"
                    disabled={unbindSubmitting}
                    onClick={async () => {
                      setUnbindSubmitting(true);
                      await requestStudentDeviceUnbindInDB(rollNo, unbindReason);
                      setUnbindSubmitting(false);
                      setUnbindMessage("Unbind request sent to faculty!");
                      const dev = await getStudentBoundDeviceFromDB(rollNo);
                      if (dev) setBoundDevice(dev);
                    }}
                    className="w-full py-3 bg-amber-600 hover:bg-amber-500 text-slate-900 rounded-xl text-xs font-bold shadow-lg shadow-amber-600/20 transition-all cursor-pointer"
                  >
                    {unbindSubmitting ? "Submitting..." : "Request Device Unbind from Teacher →"}
                  </button>
                </div>
              )}
            </div>

            {unbindMessage && (
              <p className="text-xs text-emerald-400 text-center font-medium">{unbindMessage}</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

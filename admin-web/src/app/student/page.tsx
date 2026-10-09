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
  ShieldCheck, ShieldAlert, Lock, Key, 
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
  Trash2,
  Pencil,
  X,
  Mail,
  Hash
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
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authSuccessMsg, setAuthSuccessMsg] = useState<string | null>(null);
  const [isSigningInGoogle, setIsSigningInGoogle] = useState(false);
  const [oauthError, setOauthError] = useState<string | null>(null);

  // Google Account Entry & Post-Login Profile Completion
  const [showProfileSetup, setShowProfileSetup] = useState(false);
  const [profileEmail, setProfileEmail] = useState("");
  const [profileName, setProfileName] = useState("");
  const [profileRollNo, setProfileRollNo] = useState("");
  const [profileProgram, setProfileProgram] = useState("B.Tech DSAI");
  const [profileSemester, setProfileSemester] = useState("Semester 5");
  const [profileSection, setProfileSection] = useState("Section A");

  // Active student profile
  const [studentName, setStudentName] = useState("");
  const [studentEmail, setStudentEmail] = useState("");
  const [rollNo, setRollNo] = useState("");

  // Legacy compatibility states
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
  const [classToUnenroll, setClassToUnenroll] = useState<EnrolledClass | null>(null);
  const [unenrollConfirmText, setUnenrollConfirmText] = useState("");
  const [unenrollAcknowledge, setUnenrollAcknowledge] = useState(false);
  const [availableDbClasses, setAvailableDbClasses] = useState<DBClass[]>([]);
  const [activeSession, setActiveSession] = useState<DBSession | null>(null);
  const [activeSessionCode, setActiveSessionCode] = useState<string | null>(null);

  // Edit Profile States
  const [showEditProfileModal, setShowEditProfileModal] = useState(false);
  const [editName, setEditName] = useState("");
  const [editRollNo, setEditRollNo] = useState("");
  const [editEmail, setEditEmail] = useState("");
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [profileSaveSuccess, setProfileSaveSuccess] = useState<string | null>(null);

  // Mobile App Shell & Declutter Navigation States
  const [activeTab, setActiveTab] = useState<"radar" | "subjects" | "history" | "device" | "profile">("radar");
  const [attendanceHistory, setAttendanceHistory] = useState<any[]>([]);
  const [showJoinInline, setShowJoinInline] = useState(false);
  const [showDiagnostics, setShowDiagnostics] = useState(false);

  // Verification Telemetry State
  const [isVerifyingPresence, setIsVerifyingPresence] = useState(false);
  const [verificationResult, setVerificationResult] = useState<GeofenceResult | null>(null);
  const [attendanceStatus, setAttendanceStatus] = useState<"PRESENT" | "ABSENT" | "PENDING">("PENDING");
  const [autoCheckInToast, setAutoCheckInToast] = useState<string | null>(null);
  const [isDbSynced, setIsDbSynced] = useState(false);
  const autoCheckedInRef = useRef<boolean>(false);
  const markedPresentSessionsRef = useRef<Set<string>>(new Set());

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

      if (!res.success && (res.message?.includes("ANTI") || res.message?.includes("DEVICE") || (res as any).isDeviceMismatch)) {
        setDeviceMismatchError(res.message);
      } else {
        setDeviceMismatchError(null);
        setUnbindMessage(null);
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


  const GOOGLE_CLIENT_ID = "969110741767-fuuhi63q3054eq0plg2qi68opgjspsqs.apps.googleusercontent.com";

  const initializeGoogleGSI = () => {
    if (typeof window === "undefined" || !(window as any).google?.accounts?.id) return;
    try {
      (window as any).google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: handleGoogleCredentialResponse,
        auto_select: false,
        cancel_on_tap_outside: true,
      });
    } catch (e) {
      console.warn("Google GSI initialization error:", e);
    }
  };

  // Check existing session on this phone/device
  useEffect(() => {
    // Load Google Identity Services SDK
    if (typeof window !== "undefined" && !document.getElementById("google-gsi-client")) {
      const script = document.createElement("script");
      script.id = "google-gsi-client";
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      script.defer = true;
      script.onload = () => {
        initializeGoogleGSI();
      };
      document.body.appendChild(script);
    } else {
      setTimeout(initializeGoogleGSI, 300);
    }
    // 0. Handle OAuth callback tokens from URL hash (popup or redirect)
    if (typeof window !== "undefined" && (window.location.hash.includes("id_token=") || window.location.hash.includes("access_token="))) {
      const hashParams = new URLSearchParams(window.location.hash.substring(1));
      const idToken = hashParams.get("id_token");
      const accessToken = hashParams.get("access_token");

      if (idToken || accessToken) {
        if (window.opener && window.opener !== window) {
          try {
            window.opener.postMessage({ type: "GOOGLE_OAUTH_TOKEN", idToken, accessToken }, window.location.origin);
            window.close();
            return;
          } catch (e) {}
        }
        window.history.replaceState({}, document.title, window.location.pathname);
        setAuthLoading(true);

        (async () => {
          if (idToken) {
            try {
              await supabase.auth.signInWithIdToken({
                provider: "google",
                token: idToken,
              });
            } catch (e) {
              console.warn("Supabase ID token sign in:", e);
            }
          }

          if (accessToken) {
            const res = await fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
              headers: { Authorization: "Bearer " + accessToken },
            });
            const user = await res.json();
            await handleVerifiedGoogleUser(user);
          } else if (idToken) {
            const base64Url = idToken.split(".")[1];
            const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
            const jsonPayload = decodeURIComponent(
              atob(base64)
                .split("")
                .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
                .join("")
            );
            const user = JSON.parse(jsonPayload);
            await handleVerifiedGoogleUser(user);
          }
        })()
          .catch(() => setAuthError("Failed to complete Google authentication."))
          .finally(() => setAuthLoading(false));
      }
    }

    const handleOAuthMessage = async (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type === "GOOGLE_OAUTH_TOKEN") {
        const { idToken, accessToken } = event.data;
        setAuthLoading(true);
        try {
          if (idToken) {
            try {
              await supabase.auth.signInWithIdToken({
                provider: "google",
                token: idToken,
              });
            } catch (e) {
              console.warn("Supabase ID token sign in:", e);
            }
          }

          if (accessToken) {
            const res = await fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
              headers: { Authorization: "Bearer " + accessToken },
            });
            const user = await res.json();
            await handleVerifiedGoogleUser(user);
          } else if (idToken) {
            const base64Url = idToken.split(".")[1];
            const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
            const jsonPayload = decodeURIComponent(
              atob(base64)
                .split("")
                .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
                .join("")
            );
            const user = JSON.parse(jsonPayload);
            await handleVerifiedGoogleUser(user);
          }
        } catch (e) {
          setAuthError("Failed to verify Google account from popup.");
        } finally {
          setAuthLoading(false);
        }
      }
    };
    window.addEventListener("message", handleOAuthMessage);

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
      window.removeEventListener("message", handleOAuthMessage);
    };
  }, []);

  const handleVerifiedGoogleUser = async (googleUser: {
    email: string;
    name?: string;
    picture?: string;
    sub?: string;
  }) => {
    const email = googleUser.email?.trim().toLowerCase();
    const displayName = googleUser.name?.trim() || "Student";

    if (!email) {
      setAuthError("Failed to obtain verified email from Google.");
      return;
    }

    setAuthLoading(true);
    setAuthError(null);

    try {
      // 1. Check if user already exists with an established student profile in Supabase
      const { data: dbUser } = await supabase
        .from("users")
        .select("id, name, email, students(id, roll_number, semester, program_id)")
        .ilike("email", email)
        .maybeSingle();

      const sData = (dbUser as any)?.students;
      const studentObj = Array.isArray(sData) ? sData[0] : sData;
      const existingRoll = studentObj?.roll_number?.trim();
      const emailPrefix = email.split("@")[0].toUpperCase();

      const isRollComplete = Boolean(
        existingRoll && existingRoll !== emailPrefix && existingRoll.length >= 3
      );

      if (dbUser && isRollComplete) {
        // Complete profile already exists -> Log in immediately!
        const finalName = dbUser.name || displayName;
        setStudentName(finalName);
        setRollNo(existingRoll);
        setStudentEmail(email);
        setIsLoggedIn(true);

        localStorage.setItem("smart_attendance_student_profile", JSON.stringify({
          name: finalName,
          rollNo: existingRoll,
          email: email
        }));

        loadStudentEnrollments(existingRoll);
        syncDeviceBinding(existingRoll, finalName, email);

        const live = await getActiveSessionFromDB();
        if (live) setActiveSession(live);
      } else {
        // First-time login / incomplete profile -> Prompt to complete student profile
        setProfileEmail(email);
        setProfileName(displayName);
        setProfileRollNo(existingRoll && existingRoll !== emailPrefix ? existingRoll : "");
        if (studentObj?.semester) {
          setProfileSemester("Semester " + studentObj.semester);
        }
        setShowProfileSetup(true);
      }
    } catch (err: any) {
      setAuthError(err?.message || "Google authentication processing failed.");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleGoogleCredentialResponse = async (response: any) => {
    if (!response?.credential) return;
    setAuthLoading(true);
    setAuthError(null);
    try {
      // 1. Authenticate with Supabase Auth so user is created in auth.users with Provider = Google!
      try {
        const { error: sbAuthErr } = await supabase.auth.signInWithIdToken({
          provider: "google",
          token: response.credential,
        });
        if (sbAuthErr) {
          console.warn("Supabase auth.signInWithIdToken note:", sbAuthErr.message);
        }
      } catch (sbErr) {
        console.warn("Supabase auth exception:", sbErr);
      }

      const base64Url = response.credential.split(".")[1];
      const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split("")
          .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
          .join("")
      );
      const googleUser = JSON.parse(jsonPayload);
      await handleVerifiedGoogleUser(googleUser);
    } catch (err: any) {
      setAuthError("Failed to parse Google credential token.");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLaunchGooglePopup = () => {
    setAuthError(null);
    if (typeof window === "undefined") return;

    // A. Official Google Identity Services OAuth2 token client
    if ((window as any).google?.accounts?.oauth2) {
      try {
        const tokenClient = (window as any).google.accounts.oauth2.initTokenClient({
          client_id: GOOGLE_CLIENT_ID,
          scope: "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email openid",
          prompt: "select_account",
          callback: async (tokenResponse: any) => {
            if (tokenResponse?.error) {
              setAuthError(tokenResponse.error_description || "Google sign-in was cancelled.");
              return;
            }
            if (tokenResponse?.access_token) {
              setAuthLoading(true);
              try {
                const res = await fetch("https://www.googleapis.com/oauth2/v3/userinfo", {
                  headers: { Authorization: "Bearer " + tokenResponse.access_token },
                });
                const user = await res.json();
                await handleVerifiedGoogleUser(user);
              } catch (err: any) {
                setAuthError("Failed to fetch verified profile from Google.");
              } finally {
                setAuthLoading(false);
              }
            }
          },
          error_callback: (err: any) => {
            setAuthError(err?.message || "Google popup error.");
          }
        });
        tokenClient.requestAccessToken({ prompt: "select_account" });
        return;
      } catch (e) {
        console.warn("initTokenClient fallback:", e);
      }
    }

    // B. Direct Google OAuth2 popup fallback
    const width = 500;
    const height = 620;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;
    const redirectUri = window.location.origin + window.location.pathname;
    const nonce = Math.random().toString(36).substring(2) + Date.now().toString(36);
    const oauthUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + GOOGLE_CLIENT_ID + "&redirect_uri=" + encodeURIComponent(redirectUri) + "&response_type=id_token%20token&scope=openid%20email%20profile&nonce=" + nonce + "&prompt=select_account";

    const popup = window.open(
      oauthUrl,
      "GoogleSignInPopup",
      "width=" + width + ",height=" + height + ",left=" + left + ",top=" + top + ",status=no,menubar=no,toolbar=no"
    );

    if (!popup || popup.closed || typeof popup.closed === "undefined") {
      window.location.href = oauthUrl;
    }
  };

  const handleCompleteStudentProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanName = profileName.trim();
    const cleanRoll = profileRollNo.trim().toUpperCase();

    if (!cleanName) {
      setAuthError("Please enter your full name.");
      return;
    }
    if (!cleanRoll) {
      setAuthError("Please enter your official college roll number.");
      return;
    }

    setAuthLoading(true);
    setAuthError(null);

    try {
      await registerOrGetStudentInDB({
        name: cleanName,
        rollNo: cleanRoll,
        email: profileEmail
      });

      setStudentName(cleanName);
      setRollNo(cleanRoll);
      setStudentEmail(profileEmail);
      setIsLoggedIn(true);
      autoCheckedInRef.current = false;

      localStorage.setItem("smart_attendance_student_profile", JSON.stringify({
        name: cleanName,
        rollNo: cleanRoll,
        email: profileEmail,
        program: profileProgram,
        semester: profileSemester,
        section: profileSection
      }));

      loadStudentEnrollments(cleanRoll);
      await syncDeviceBinding(cleanRoll, cleanName, profileEmail);

      const live = await getActiveSessionFromDB();
      if (live) setActiveSession(live);
    } catch (err: any) {
      setAuthError(err?.message || "Failed to complete profile.");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleSaveProfile = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const cleanName = editName.trim();
    const cleanRoll = editRollNo.trim().toUpperCase();
    const cleanEmail = editEmail.trim();

    if (!cleanName || !cleanRoll) return;

    setIsSavingProfile(true);
    setProfileSaveSuccess(null);

    try {
      setStudentName(cleanName);
      setRollNo(cleanRoll);
      if (cleanEmail) setStudentEmail(cleanEmail);

      const updated = {
        name: cleanName,
        rollNo: cleanRoll,
        email: cleanEmail || studentEmail,
      };
      localStorage.setItem("smart_attendance_student_profile", JSON.stringify(updated));

      if (cleanRoll !== rollNo) {
        const oldEnrolled = localStorage.getItem(`smart_attendance_enrolled_${rollNo}`);
        if (oldEnrolled) {
          localStorage.setItem(`smart_attendance_enrolled_${cleanRoll}`, oldEnrolled);
        }
        loadStudentEnrollments(cleanRoll);
      }

      await registerOrGetStudentInDB({
        name: cleanName,
        rollNo: cleanRoll,
        email: cleanEmail || studentEmail,
      });

      try {
        if (cleanEmail) {
          await supabase.from("users").update({ name: cleanName }).eq("email", cleanEmail);
        }
      } catch (e) {}

      try {
        await supabase.from("students").update({ roll_number: cleanRoll }).eq("roll_number", rollNo);
      } catch (e) {}

      syncDeviceBinding(cleanRoll, cleanName, cleanEmail || studentEmail);

      setProfileSaveSuccess("Profile updated successfully!");
      setTimeout(() => {
        setProfileSaveSuccess(null);
        setShowEditProfileModal(false);
      }, 900);
    } catch (err: any) {
      console.error("Save profile error:", err);
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleLogout = async () => {
    try {
      await supabase.auth.signOut();
    } catch (e) {}
    localStorage.removeItem("smart_attendance_student_profile");
    localStorage.removeItem("smart_attendance_installation_id");
    setDeviceMismatchError(null);
    setUnbindMessage(null);
    setIsLoggedIn(false);
    setShowProfileSetup(false);
    setStudentName("");
    setRollNo("");
    setStudentEmail("");
    setInputName("");
    setInputRollNo("");
  };

  // Real-time approval listener: automatically clears lock when admin approves unbind
  useEffect(() => {
    if (!deviceMismatchError || !rollNo) return;
    const interval = setInterval(() => {
      syncDeviceBinding(rollNo, studentName, studentEmail);
    }, 2500);
    return () => clearInterval(interval);
  }, [deviceMismatchError, rollNo, studentName, studentEmail]);

  // Load Attendance History
  useEffect(() => {
    if (!rollNo) return;
    const fetchHistory = async () => {
      try {
        const { data } = await supabase
          .from("attendance_records")
          .select("id, status, marked_at, verification_method, notes, attendance_sessions(id, start_time, classes(room, subjects(name, code), teachers(users(name))))")
          .eq("sensor_details->>roll_number", rollNo)
          .order("marked_at", { ascending: false });
        if (data) setAttendanceHistory(data);
      } catch (e) {
        console.warn("Could not fetch attendance history:", e);
      }
    };
    fetchHistory();
  }, [rollNo]);

  // 1. Fetch hardware network
  const fetchRealNetworkStatus = async () => {
    setIsDetectingNetwork(true);
    try {
      const res = await fetch("/api/network-status");
      const data = await res.json();
      if (data.success && data.detectedSsid) {
        setDetectedNetwork(data);
        if (data.isSameSubnet || data.detectedBssid === "A4:2B:B0:8C:12:EF") {
          setStudentConnectedWifi(data.detectedSsid);
          setSelectedBssid(data.detectedBssid || "A4:2B:B0:8C:12:EF");
        } else {
          setStudentConnectedWifi(data.detectedSsid);
          setSelectedBssid(data.detectedBssid || "00:00:00:00:00:00");
        }
      }
    } catch (e) {
      console.warn("Could not detect network:", e);
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

      // ZERO-TOUCH WI-FI SYNC: Check if student is already marked present via classroom Wi-Fi AP or Android app in DB
      if (rollNo) {
        try {
          let isDbMarked = false;
          const { data: recByStudent } = await supabase
            .from("attendance_records")
            .select("id, status, students!inner(roll_number)")
            .eq("session_id", dbSession.id)
            .eq("students.roll_number", rollNo)
            .eq("status", "PRESENT")
            .limit(1)
            .maybeSingle();

          if (recByStudent?.id) {
            isDbMarked = true;
          } else {
            const { data: rec } = await supabase
              .from("attendance_records")
              .select("id, status")
              .eq("session_id", dbSession.id)
              .eq("sensor_details->>roll_number", rollNo)
              .eq("status", "PRESENT")
              .maybeSingle();
            if (rec?.id) isDbMarked = true;
          }

          if (isDbMarked) {
            setAttendanceStatus("PRESENT");
            autoCheckedInRef.current = true;
            markedPresentSessionsRef.current.add(dbSession.id);
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
    markedPresentSessionsRef.current.clear();
    localStorage.removeItem("smart_attendance_active_session_code");
  };

  // Enrolled check: student must have joined the class to take attendance!
  const isEnrolledInActive = Boolean(
    activeSession &&
    (
      myClasses.length === 0 || // Auto-eligible on fresh device/browser login
      myClasses.some(c => 
        c.id === activeSession.classId || 
        c.joinCode.toUpperCase() === activeSession.joinCode.toUpperCase() ||
        c.subjectCode.toUpperCase() === activeSession.subjectCode.toUpperCase()
      )
    )
  );

  // AUTOMATIC ATTENDANCE EXECUTION (Continuously scans every 3.5s while lecture is active)
  useEffect(() => {
    if (!isLoggedIn || !activeSession || !isEnrolledInActive) {
      return;
    }
    // Prevent repeated verification if already marked PRESENT for this session
    if (markedPresentSessionsRef.current.has(activeSession.id) || attendanceStatus === "PRESENT") {
      return;
    }

    // Initial immediate probe
    const timer = setTimeout(() => {
      if (!isVerifyingPresence) {
        executePresenceVerification(activeSession);
      }
    }, 400);

    // Continuous background retry while active lecture is running (critical for iOS Safari & mobile GPS warmup)
    const retryInterval = setInterval(() => {
      if (!isVerifyingPresence && !markedPresentSessionsRef.current.has(activeSession.id)) {
        executePresenceVerification(activeSession);
      }
    }, 3500);

    return () => {
      clearTimeout(timer);
      clearInterval(retryInterval);
    };
  }, [isLoggedIn, activeSession?.id, isEnrolledInActive, geoMode, studentConnectedWifi, attendanceStatus, isVerifyingPresence]);

  const executePresenceVerification = async (
    sess: DBSession,
    overrideName?: string,
    overrideRoll?: string,
    overrideEmail?: string
  ) => {
    if (deviceMismatchError) {
      console.warn("Device mismatch active. Cannot mark attendance.");
      return;
    }
    const activeStudentName = overrideName || studentName;
    const activeRollNo = overrideRoll || rollNo;
    const activeEmail = overrideEmail || studentEmail || `${activeRollNo.toLowerCase()}@student.iiitnr.edu.in`;

    if (!activeStudentName || !activeRollNo) return;
    setIsVerifyingPresence(true);
    try {
      const geoResult = await getBrowserGeofence(sess.latitude, sess.longitude, 30.0);
      setVerificationResult(geoResult);

      const targetWifiName = (sess.wifiSsid || "Pranjal").trim();
      const allowedWifiList = targetWifiName
        .split(",")
        .map((s: string) => s.toLowerCase().trim())
        .filter(Boolean);
      const currentWifi = (studentConnectedWifi || "").toLowerCase().trim();
      
      // HARDWARE ANTI-HOTSPOT CHECK:
      // Reject explicitly simulated rogue hotspots or fake APs
      const isExplicitFake =
        selectedBssid === "F2:45:67:89:AB:CD" ||
        currentWifi.includes("fake") ||
        currentWifi.includes("rogue") ||
        currentWifi.includes("other wi-fi") ||
        currentWifi.includes("disconnected");

      const isSsidAllowed = allowedWifiList.some((target: string) =>
        currentWifi === target ||
        (currentWifi.includes(target) && !currentWifi.includes("fake") && !currentWifi.includes("cellular") && !currentWifi.includes("other"))
      );

      // On mobile browsers (iOS Safari, Android Chrome), raw 802.11 BSSID hardware beacons cannot be scanned directly by Web APIs.
      // Wi-Fi is verified if:
      // 1. Explicit official router BSSID selected (simulation/test), OR
      // 2. Campus subnet detected via /api/network-status, OR
      // 3. SSID matches allowed classroom Wi-Fi list, OR
      // 4. Student is within geofence of faculty phone and not on an explicit fake hotspot, OR
      // 5. Mobile browser on campus where web cannot inspect native Wi-Fi beacons.
      const isMobile = typeof navigator !== "undefined" && /iPhone|iPad|iPod|Android.*Mobile|Mobile/i.test(navigator.userAgent);
      const isWifiMatched = !isExplicitFake && (
        selectedBssid === "A4:2B:B0:8C:12:EF" ||
        detectedNetwork?.isSameSubnet === true ||
        isSsidAllowed ||
        geoResult.isInside ||
        isMobile
      );

      // Auto-reflect connected state in UI when verified
      if (isWifiMatched && (studentConnectedWifi === "Cellular Data (Mobile Network)" || selectedBssid === "00:00:00:00:00:00")) {
        setStudentConnectedWifi(targetWifiName);
        setSelectedBssid("A4:2B:B0:8C:12:EF");
      }

      const isPresent = geoResult.isInside && isWifiMatched;

      if (isPresent) {
        setAttendanceStatus("PRESENT");
        autoCheckedInRef.current = true;

        // ONLY fire toast notification and record to DB ONCE per session!
        if (!markedPresentSessionsRef.current.has(sess.id)) {
          markedPresentSessionsRef.current.add(sess.id);
          setAutoCheckInToast(sess.subjectName);
          setTimeout(() => setAutoCheckInToast(null), 4000);

          await recordStudentAttendanceInDB({
            sessionId: sess.id,
            rollNo: activeRollNo,
            name: activeStudentName,
            email: activeEmail,
            status: "PRESENT",
            distanceMeters: geoResult.distanceMeters,
            wifiSsid: targetWifiName,
            isWifiMatched: true
          });
        }
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

    // 4. Smart query to Supabase database for subjects matching entered code
    if (!matched) {
      try {
        const cleanNoHyphen = entered.replace(/[^A-Z0-9]/g, "");
        const prefix = entered.includes("-") ? entered.split("-")[0] : entered;

        let { data: dbSubList } = await supabase
          .from("subjects")
          .select(`
            id, name, code,
            classes (
              id, room, is_active,
              teachers ( id, users ( name ) )
            )
          `)
          .or(`code.ilike.${entered},code.ilike.${cleanNoHyphen},code.ilike.${prefix}-%,code.ilike.${prefix},name.ilike.%${entered}%`)
          .limit(5);

        const dbSub = dbSubList && dbSubList.length > 0 ? dbSubList[0] : null;

        if (dbSub) {
          let clsList: any[] = dbSub.classes || [];
          let cls = clsList.find((x: any) => x.is_active) || clsList[0];

          if (!cls) {
            const { data: directClass } = await supabase
              .from("classes")
              .select(`id, room, is_active, teachers ( id, users ( name ) )`)
              .eq("subject_id", dbSub.id)
              .limit(1);
            if (directClass && directClass.length > 0) {
              cls = directClass[0];
            } else {
              const { data: teachers } = await supabase.from("teachers").select("id").limit(1);
              const teacherId = teachers?.[0]?.id || "6885fced-5d3e-4b9c-94fd-85d115cc9d9b";
              const { data: newCls } = await supabase
                .from("classes")
                .insert({
                  subject_id: dbSub.id,
                  teacher_id: teacherId,
                  section_id: "b7bd5c04-a4bf-478b-b822-1ca0982b55f4",
                  room: "Room A-204 (AC Block)",
                  day_of_week: 1,
                  start_time: "10:00:00",
                  end_time: "11:00:00",
                  is_active: true
                })
                .select(`id, room, is_active, teachers ( id, users ( name ) )`)
                .single();
              cls = newCls;
            }
          }

          const t = cls?.teachers;
          const tUser = Array.isArray(t) ? t[0]?.users : t?.users;

          matched = {
            id: cls?.id || dbSub.id,
            subjectCode: dbSub.code,
            subjectName: dbSub.name,
            section: "Section A",
            joinCode: dbSub.code,
            roomNo: cls?.room || "Room A-204 (AC Block)",
            wifiSsid: "Pranjal",
            latitude: 21.128456,
            longitude: 81.766184,
            teacherId: t?.id || "",
            teacherName: tUser?.name || "Dr. Sharma",
            students: [],
            createdAt: new Date().toISOString()
          };
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

  // IF NOT LOGGED IN: SHOW GOOGLE SIGN IN & PROFILE COMPLETION
  if (!isLoggedIn) {
    return (
      <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between p-4 selection:bg-blue-500 selection:text-white">
        <header className="max-w-md mx-auto w-full flex items-center justify-between py-4">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-blue-600 text-white flex items-center justify-center font-black text-sm shadow-md shadow-blue-600/20">
              IIIT
            </div>
            <div>
              <span className="font-black tracking-tight text-slate-900 block text-xs">IIIT NAYA RAIPUR</span>
              <span className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider block">Student Portal</span>
            </div>
          </div>
          <Link href="/" className="text-xs text-slate-500 hover:text-slate-900 flex items-center gap-1 font-medium transition-colors">
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Home</span>
          </Link>
        </header>

        {/* TAB SELECTOR PILL (Exact 1:1 match with user screenshot) */}
        <div className="flex items-center gap-1.5 sm:gap-2 mb-6 p-1.5 bg-slate-100/90 rounded-2xl border border-slate-200/90 shadow-2xs overflow-x-auto no-scrollbar">
          {[
            { id: "radar", label: "Live Radar", icon: Radio, count: null },
            { id: "subjects", label: "My Subjects", icon: BookOpen, count: myClasses.length },
            { id: "history", label: "History", icon: Clock, count: null },
            { id: "device", label: "Device Security", icon: ShieldCheck },
            { id: "profile", label: "Student Profile", icon: User },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as any)}
                className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 sm:px-4 rounded-xl text-xs font-bold transition-all cursor-pointer whitespace-nowrap ${
                  isActive
                    ? "bg-white text-blue-600 shadow-xs border border-slate-200"
                    : "text-slate-600 hover:text-slate-900 hover:bg-white/40"
                }`}
              >
                <Icon className={`w-4 h-4 shrink-0 ${isActive ? "text-blue-600" : "text-slate-500"}`} />
                <span>{tab.label}</span>
                {tab.count !== null && tab.count !== undefined && (
                  <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                    isActive ? "bg-blue-50 text-blue-700" : "bg-slate-200 text-slate-600"
                  }`}>
                    {tab.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="max-w-md mx-auto w-full my-auto py-6">
          {showProfileSetup ? (
            <div key="profile-setup-screen" className="bg-white border border-slate-200 shadow-xl rounded-3xl p-6 sm:p-8 space-y-6 animate-in fade-in zoom-in-95">
              <div className="flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => setShowProfileSetup(false)}
                  className="text-xs text-slate-500 hover:text-slate-800 flex items-center gap-1 font-semibold transition-colors cursor-pointer"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  <span>Switch Account</span>
                </button>
                <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-blue-50 text-blue-600 border border-blue-200">
                  Step 2 of 2
                </span>
              </div>

              <div className="text-center space-y-1">
                <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 border border-blue-500/20 flex items-center justify-center mx-auto mb-2">
                  <GraduationCap className="w-6 h-6" />
                </div>
                <h1 className="text-xl font-black text-slate-900 tracking-tight">Complete Student Profile</h1>
                <p className="text-xs text-slate-500">Link your official university roll number with your Google account</p>
              </div>

              {/* Connected Google Account Badge */}
              <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-slate-50 border border-slate-200">
                <div className="w-9 h-9 rounded-xl bg-white border border-slate-200 flex items-center justify-center shadow-xs shrink-0">
                  <svg className="w-4 h-4" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                  </svg>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-900 truncate">{profileEmail}</span>
                    <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-100 text-emerald-800">
                      Connected
                    </span>
                  </div>
                  <span className="text-[10px] text-slate-500 font-medium block">
                    Google Identity Verified
                  </span>
                </div>
              </div>

              {authError && (
                <div className="flex items-center gap-2 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 text-xs">
                  <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
                  <span>{authError}</span>
                </div>
              )}

              <form onSubmit={handleCompleteStudentProfile} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Full Name *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Nihal Kumar"
                    value={profileName}
                    onChange={(e) => setProfileName(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:bg-white focus:outline-none text-slate-900 text-sm font-medium transition-all"
                  />
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1.5">
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                      College Roll Number *
                    </label>
                    <span className="text-[10px] text-rose-600 font-bold">Required</span>
                  </div>
                  <input
                    type="text"
                    required
                    placeholder="e.g. 263200113 or BT23DSAI001"
                    value={profileRollNo}
                    onChange={(e) => setProfileRollNo(e.target.value.toUpperCase())}
                    className="w-full px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:bg-white focus:outline-none text-slate-900 text-sm uppercase font-mono tracking-wider font-bold transition-all"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Branch / Program
                  </label>
                  <div className="grid grid-cols-2 gap-1.5">
                    {["B.Tech DSAI", "B.Tech CSE", "B.Tech ECE", "M.Tech CSE"].map((prog) => (
                      <button
                        key={prog}
                        type="button"
                        onClick={() => setProfileProgram(prog)}
                        className={
                          profileProgram === prog
                            ? "py-2 px-3 text-xs font-bold rounded-xl border text-center transition-all cursor-pointer bg-blue-50 border-blue-600 text-blue-600 shadow-sm"
                            : "py-2 px-3 text-xs font-bold rounded-xl border text-center transition-all cursor-pointer bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100"
                        }
                      >
                        {prog}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Semester
                  </label>
                  <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-none">
                    {["Semester 1", "Semester 2", "Semester 3", "Semester 4", "Semester 5", "Semester 6", "Semester 7", "Semester 8"].map((sem) => (
                      <button
                        key={sem}
                        type="button"
                        onClick={() => setProfileSemester(sem)}
                        className={
                          profileSemester === sem
                            ? "py-1.5 px-3 text-xs font-bold rounded-xl border shrink-0 transition-all cursor-pointer bg-blue-50 border-blue-600 text-blue-600 shadow-sm"
                            : "py-1.5 px-3 text-xs font-bold rounded-xl border shrink-0 transition-all cursor-pointer bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100"
                        }
                      >
                        {sem}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
                    Section
                  </label>
                  <div className="grid grid-cols-3 gap-1.5">
                    {["Section A", "Section B", "Section C"].map((sec) => (
                      <button
                        key={sec}
                        type="button"
                        onClick={() => setProfileSection(sec)}
                        className={
                          profileSection === sec
                            ? "py-2 text-xs font-bold rounded-xl border text-center transition-all cursor-pointer bg-blue-50 border-blue-600 text-blue-600 shadow-sm"
                            : "py-2 text-xs font-bold rounded-xl border text-center transition-all cursor-pointer bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100"
                        }
                      >
                        {sec}
                      </button>
                    ))}
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full py-3.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl font-bold text-sm shadow-lg shadow-blue-600/25 transition-all cursor-pointer flex items-center justify-center gap-2"
                >
                  {authLoading ? (
                    <span>Saving Profile...</span>
                  ) : (
                    <>
                      <span>Save Profile & Enter Portal</span>
                      <Check className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>
            </div>
          ) : (
            <div key="student-login-screen" className="bg-white border border-slate-200 shadow-xl rounded-3xl p-6 sm:p-8 space-y-6">
              <div className="text-center space-y-1">
                <div className="w-14 h-14 rounded-2xl bg-blue-50 text-blue-600 border border-blue-500/20 flex items-center justify-center mx-auto mb-3 shadow-sm">
                  <GraduationCap className="w-7 h-7" />
                </div>
                <h1 className="text-2xl font-black text-slate-900 tracking-tight">Student Login</h1>
                <p className="text-xs text-slate-500">Sign in with your Google account to access attendance</p>
              </div>

              {authError && (
                <div className="flex items-center gap-2 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 text-xs animate-in fade-in">
                  <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
                  <span>{authError}</span>
                </div>
              )}

              {/* Single Official Google Login Button */}
              <div className="pt-2 flex flex-col items-center">
                <button
                  type="button"
                  disabled={authLoading}
                  onClick={handleLaunchGooglePopup}
                  className="w-full py-3.5 px-4 bg-white hover:bg-slate-50 active:scale-[0.99] disabled:opacity-60 text-slate-900 rounded-2xl font-bold text-sm shadow-sm hover:shadow-md flex items-center justify-center gap-3 transition-all cursor-pointer border border-slate-300"
                >
                  <svg className="w-5 h-5 shrink-0" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                  </svg>
                  <span>{authLoading ? "Connecting with Google..." : "Continue with Google"}</span>
                </button>

                <p className="text-[11px] text-slate-500 font-medium text-center mt-3">
                  Google account connects verified institutional or personal identity.
                </p>
              </div>
            </div>
          )}
        </div>

        <footer className="text-center text-xs text-slate-500 py-4">
          IIIT-NR Smart Attendance System • Cryptographic Presence
        </footer>
      </div>
    );
  }

  // IF DEVICE MISMATCH (ANTI-PROXY LOCK ACTIVE): BLOCK ATTENDANCE
  if (deviceMismatchError) {
    return (
      <div className="min-h-screen bg-slate-900 text-white flex flex-col justify-between p-4 selection:bg-rose-500 selection:text-white">
        <header className="max-w-md mx-auto w-full flex items-center justify-between py-4">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-rose-600 text-white flex items-center justify-center font-black text-sm shadow-md shadow-rose-600/30">
              <ShieldAlert className="w-5 h-5 text-white" />
            </div>
            <div>
              <span className="font-bold tracking-tight text-white block text-xs">SECURITY PROTOCOL</span>
              <span className="text-[10px] text-rose-400 font-semibold uppercase tracking-wider block">Anti-Proxy Hardware Lock</span>
            </div>
          </div>
          <button 
            type="button" 
            onClick={handleLogout} 
            className="text-xs text-slate-400 hover:text-white flex items-center gap-1 font-medium transition-colors cursor-pointer"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Sign Out</span>
          </button>
        </header>

        <div className="max-w-md mx-auto w-full my-auto py-6">
          <div className="bg-slate-800/90 border border-rose-500/30 rounded-3xl p-6 sm:p-8 space-y-5 shadow-2xl backdrop-blur-xl animate-in fade-in zoom-in-95">
            <div className="text-center space-y-2">
              <div className="w-16 h-16 rounded-3xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center text-rose-400 mx-auto shadow-inner">
                <Lock className="w-8 h-8 text-rose-500" />
              </div>
              <h2 className="text-xl font-black text-white tracking-tight">Device Access Locked</h2>
              <p className="text-xs text-rose-300 font-medium leading-relaxed">
                Anti-Proxy Security: Multiple device access is prohibited
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-rose-950/40 border border-rose-800/50 text-xs text-rose-200 space-y-2">
              <div className="flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <div className="leading-relaxed font-mono text-[11px]">
                  {deviceMismatchError}
                </div>
              </div>
            </div>

            <div className="p-4 rounded-2xl bg-slate-900/60 border border-slate-700/60 text-xs text-slate-300 space-y-2">
              <div className="flex justify-between items-center text-[11px]">
                <span className="text-slate-400">Student Account:</span>
                <span className="font-bold text-white">{studentName} ({rollNo})</span>
              </div>
              <div className="flex justify-between items-center text-[11px]">
                <span className="text-slate-400">Current Device:</span>
                <span className="font-mono text-slate-300">
                  {typeof navigator !== "undefined" && navigator.userAgent.includes("Android") ? "Android Phone" : typeof navigator !== "undefined" && navigator.userAgent.includes("iPhone") ? "iPhone" : "Browser Client"}
                </span>
              </div>
            </div>

            {unbindMessage ? (
              <div className="p-4 rounded-2xl bg-emerald-950/40 border border-emerald-500/40 text-center space-y-3">
                <CheckCircle2 className="w-6 h-6 text-emerald-400 mx-auto" />
                <p className="text-xs font-bold text-emerald-300">{unbindMessage}</p>
                <p className="text-[11px] text-slate-400 leading-relaxed">
                  Request sent to Central IT Admin. This page automatically unlocks as soon as approval is granted.
                </p>
                <div className="flex items-center justify-center gap-2 py-1">
                  <div className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                  <span className="text-[11px] text-emerald-300 font-semibold">Live listener active: Polling approval...</span>
                </div>
                <div className="flex flex-col gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => syncDeviceBinding(rollNo, studentName, studentEmail)}
                    className="w-full py-2.5 px-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-md"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    <span>Check Status Now</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      localStorage.removeItem("smart_attendance_installation_id");
                      syncDeviceBinding(rollNo, studentName, studentEmail);
                    }}
                    className="w-full py-2 px-3 text-[10px] text-slate-400 hover:text-white transition cursor-pointer"
                  >
                    Reset Hardware Token & Re-verify
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-3 pt-2">
                <textarea
                  value={unbindReason}
                  onChange={(e) => setUnbindReason(e.target.value)}
                  placeholder="Enter reason for device change (e.g. Phone lost, reset, or new device)..."
                  className="w-full p-3 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-rose-500 resize-none h-20"
                />
                <button
                  type="button"
                  disabled={unbindSubmitting}
                  onClick={async () => {
                    setUnbindSubmitting(true);
                    try {
                      const currInstId = typeof window !== "undefined" ? localStorage.getItem("smart_attendance_installation_id") || undefined : undefined;
                      const ok = await requestStudentDeviceUnbindInDB(rollNo, unbindReason || "Device Change / Reset", currInstId);
                      if (ok) {
                        setUnbindMessage("✓ Device Unbind Request Submitted");
                        /* request sent */
                      } else {
                        setUnbindMessage("Failed to submit request. Please try again.");
                      }
                    } catch (e: any) {
                      setUnbindMessage("Error: " + e.message);
                    } finally {
                      setUnbindSubmitting(false);
                    }
                  }}
                  className="w-full py-3.5 px-4 rounded-2xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs shadow-lg shadow-rose-600/30 flex items-center justify-center gap-2 transition-all cursor-pointer disabled:opacity-50"
                >
                  <Key className="w-4 h-4 text-white" />
                  <span>{unbindSubmitting ? "Submitting Request..." : "Request Device Unbind From Central Admin"}</span>
                </button>
                <div className="flex flex-col gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => syncDeviceBinding(rollNo, studentName, studentEmail)}
                    className="w-full py-2 px-3 rounded-xl bg-slate-800/80 hover:bg-slate-700 text-slate-300 font-semibold text-xs border border-slate-700 flex items-center justify-center gap-1.5 transition cursor-pointer"
                  >
                    <RefreshCw className="w-3 h-3" />
                    <span>Check Approval Status</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      localStorage.removeItem("smart_attendance_installation_id");
                      syncDeviceBinding(rollNo, studentName, studentEmail);
                    }}
                    className="text-[10px] text-slate-500 hover:text-slate-300 text-center transition cursor-pointer py-1"
                  >
                    Reset Hardware Token
                  </button>
                </div>
                <p className="text-[10px] text-slate-400 text-center">
                  Only Central IT Administration can authorize device re-registration for anti-proxy compliance.
                </p>
              </div>
            )}
          </div>
        </div>

        <footer className="text-center text-xs text-slate-500 py-4">
          IIIT-NR Anti-Proxy Security System • 1 Device = 1 Student
        </footer>
      </div>
    );
  }

  // IF LOGGED IN: SHOW REGULAR STUDENT CONSOLE (FACULTY MATCHED THEME)
  return (
    <div className="min-h-screen bg-[#F8FAFC] bg-[radial-gradient(#CBD5E1_1px,transparent_1px)] [background-size:20px_20px] text-slate-900 flex flex-col font-sans selection:bg-blue-100 selection:text-blue-900 justify-between">
      {/* Dynamic Island / Top Notification Banner (Apple-grade, matching Faculty UI) */}
      {autoCheckInToast && (
        <div className="fixed top-3 left-1/2 -translate-x-1/2 z-50 max-w-sm w-[92%] sm:w-auto bg-slate-900/95 text-white backdrop-blur-md px-4 py-3 rounded-2xl shadow-2xl border border-slate-800 flex items-center gap-3 animate-in fade-in slide-in-from-top-3">
          <div className="w-8 h-8 rounded-xl bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center shrink-0">
            <CheckCircle2 className="w-4 h-4" />
          </div>
          <div className="flex-1 min-w-0 pr-1">
            <div className="text-[10px] font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>Attendance Recorded</span>
            </div>
            <div className="text-xs font-semibold text-slate-100 truncate">
              {autoCheckInToast.replace(/^⚡\s*Automatic Attendance Recorded for\s*/i, "").replace(/!$/, "") || "Live Session Verified"}
            </div>
          </div>
          <button
            type="button"
            onClick={() => setAutoCheckInToast(null)}
            className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-white transition-colors cursor-pointer"
            title="Dismiss"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Main Container - Responsive Max Width (Matches Faculty Console Layout) */}
      <div className="max-w-2xl mx-auto w-full flex-1 flex flex-col p-4 sm:p-6 pb-12 sm:pb-16">
        {/* ==================================================================== */}
        {/* TOP BAR: INSTITUTIONAL BRANDING & USER STATUS (MATCHES TEACHER) */}
        {/* ==================================================================== */}
        <header className="flex items-center justify-between pb-4 border-b border-slate-200/80 mb-5">
          <div className="flex items-center gap-3">
            <Link 
              href="/" 
              className="w-10 h-10 rounded-2xl bg-gradient-to-br from-blue-600 via-indigo-600 to-blue-700 flex items-center justify-center text-white font-black text-sm tracking-tight shadow-md shadow-blue-500/15 hover:shadow-lg hover:shadow-blue-500/25 ring-1 ring-black/5 hover:scale-[1.02] active:scale-[0.98] transition-all"
              title="Smart Attendance Portal"
            >
              SA
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-[15px] tracking-tight text-slate-900 leading-tight">IIIT Naya Raipur</span>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200/80 uppercase tracking-wider">
                  STUDENT
                </span>
              </div>
              <button
                type="button"
                onClick={() => {
                  setEditName(studentName);
                  setEditRollNo(rollNo);
                  setEditEmail(studentEmail);
                  setProfileSaveSuccess(null);
                  setShowEditProfileModal(true);
                }}
                className="flex items-center gap-1.5 text-xs text-slate-500 font-medium pt-0.5 hover:text-blue-600 cursor-pointer text-left group"
                title="Click to edit profile"
              >
                <span className="truncate max-w-[130px] sm:max-w-none text-slate-700 font-semibold group-hover:text-blue-600 transition-colors">{studentName || "Student"}</span>
                <span className="text-slate-300">•</span>
                <span className="font-mono text-[11px] text-slate-600 bg-slate-100/90 px-1.5 py-0.5 rounded border border-slate-200/70 font-semibold group-hover:border-blue-300 transition-colors">{rollNo || "ID"}</span>
                <Pencil className="w-3 h-3 text-slate-400 group-hover:text-blue-600 transition-colors opacity-0 group-hover:opacity-100" />
              </button>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {activeSession && (
              <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[11px] font-bold shadow-2xs">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                Live
              </span>
            )}
            <button
              type="button"
              onClick={() => {
                setEditName(studentName);
                setEditRollNo(rollNo);
                setEditEmail(studentEmail);
                setProfileSaveSuccess(null);
                setShowEditProfileModal(true);
              }}
              className="p-2 sm:px-2.5 sm:py-1.5 rounded-xl border border-slate-200/80 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer shadow-2xs"
              title="Edit Student Profile"
            >
              <Pencil className="w-3.5 h-3.5 text-slate-500" />
              <span className="hidden sm:inline text-[11px]">Edit</span>
            </button>

            <Link
              href="/teacher"
              className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-slate-700 bg-white hover:bg-slate-50 transition-all border border-slate-200/80 shadow-2xs"
              title="Switch to Faculty Console"
            >
              <GraduationCap className="w-3.5 h-3.5 text-slate-500" />
              <span>Faculty</span>
            </Link>
            <button
              type="button"
              onClick={() => setShowUnbindModal(true)}
              className={`p-2 sm:px-2.5 sm:py-1.5 rounded-xl border text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer shadow-2xs ${
                boundDevice?.status === "PENDING_UNBIND"
                  ? "bg-amber-50 hover:bg-amber-100/80 border-amber-200 text-amber-700"
                  : "bg-white hover:bg-slate-50 border-slate-200/80 text-slate-700"
              }`}
              title={boundDevice?.status === "PENDING_UNBIND" ? "Unbind Pending Teacher Approval" : "Anti-Proxy Hardware Bound"}
            >
              <ShieldCheck className={`w-4 h-4 ${boundDevice?.status === "PENDING_UNBIND" ? "text-amber-500" : "text-emerald-600"}`} />
              <span className="hidden md:inline text-[11px] font-medium text-slate-600">
                {boundDevice?.status === "PENDING_UNBIND" ? "Unbind Pending" : "Protected"}
              </span>
            </button>
          </div>
        </header>

        {/* ==================================================================== */}
        {/* MAIN BODY CONTENT */}
        {/* ==================================================================== */}
        <div className="space-y-4">
          {/* TAB 1: RADAR & LIVE PRESENCE */}
          {activeTab === "radar" && (
            <div className="space-y-4 animate-in fade-in duration-200">
              {/* Active Lecture Card (Matches Faculty Live Session card) */}
              {activeSession && isEnrolledInActive ? (
                <section className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                    <div>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 inline-block mb-1">
                        ● LIVE ATTENDANCE ACTIVE
                      </span>
                      <h2 className="text-base font-bold text-slate-900 tracking-tight">{activeSession.subjectName}</h2>
                      <p className="text-xs text-slate-500 font-medium">{activeSession.subjectCode} · {activeSession.roomNo}</p>
                    </div>

                    <button
                      type="button"
                      onClick={() => {
                        autoCheckedInRef.current = false;
                        if (activeSession) executePresenceVerification(activeSession);
                      }}
                      disabled={isVerifyingPresence}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-xs font-bold text-slate-700 transition-all cursor-pointer shrink-0"
                      title="Re-check Presence"
                    >
                      <RefreshCw className={`w-3.5 h-3.5 text-blue-600 ${isVerifyingPresence ? "animate-spin" : ""}`} />
                      <span>Verify</span>
                    </button>
                  </div>

                  {/* Presence Status Banner */}
                  {attendanceStatus === "PRESENT" ? (
                    <div className="p-3.5 rounded-xl bg-emerald-50/80 border border-emerald-200 flex items-start gap-3">
                      <div className="w-8 h-8 rounded-lg bg-emerald-600 text-white flex items-center justify-center shrink-0">
                        <CheckCircle2 className="w-4 h-4" />
                      </div>
                      <div className="min-w-0">
                        <h4 className="text-xs font-bold text-emerald-900">Zero-Touch Presence Verified!</h4>
                        <p className="text-[11px] text-emerald-700 mt-0.5">
                          You are marked <span className="font-bold">PRESENT</span> for this lecture in {activeSession.roomNo}.
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-3">
                      <div className="flex items-start gap-3">
                        <div className="w-8 h-8 rounded-lg bg-blue-600 text-white flex items-center justify-center shrink-0">
                          <RefreshCw className={`w-4 h-4 ${isVerifyingPresence ? "animate-spin" : ""}`} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <h4 className="text-xs font-bold text-slate-900">Scanning Classroom Signals...</h4>
                          <p className="text-[11px] text-slate-500 mt-0.5">
                            Automated scan running. On iOS Safari / Mobile Browser, tap below to confirm immediately.
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          if (activeSession) {
                            executePresenceVerification(activeSession);
                          }
                        }}
                        disabled={isVerifyingPresence}
                        className="w-full py-2.5 px-3 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer shadow-sm active:scale-[0.99]"
                      >
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Confirm Attendance (Instant Check In)</span>
                      </button>
                    </div>
                  )}

                  {/* Telemetry Grid (Wi-Fi & GPS Geofence) */}
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5 font-bold text-slate-800">
                          <Wifi className="w-3.5 h-3.5 text-blue-600" />
                          <span>Classroom Wi-Fi</span>
                        </div>
                        <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${
                          (activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim())
                            ? "bg-emerald-100 text-emerald-800"
                            : "bg-slate-200 text-slate-700"
                        }`}>
                          {(activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim()) ? "MATCH" : "MISMATCH"}
                        </span>
                      </div>
                      <div className="text-[11px] font-mono text-slate-600 truncate">
                        {(activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim()) ? "AP Verified" : "Not Connected"}
                      </div>
                    </div>

                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5 font-bold text-slate-800">
                          <MapPin className="w-3.5 h-3.5 text-blue-600" />
                          <span>Geofence</span>
                        </div>
                        <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${
                          verificationResult?.isInside ? "bg-emerald-100 text-emerald-800" : "bg-slate-200 text-slate-700"
                        }`}>
                          {verificationResult?.isInside ? "IN CLASS" : "CHECKING"}
                        </span>
                      </div>
                      <div className="text-[11px] text-slate-600 font-mono">
                        Distance: {verificationResult?.distanceMeters || 3.4}m
                      </div>
                    </div>
                  </div>

                  {/* Sensor Simulation Accordion */}
                  <div className="border-t border-slate-100 pt-3">
                    <button
                      type="button"
                      onClick={() => setShowDiagnostics(!showDiagnostics)}
                      className="text-xs text-slate-500 hover:text-slate-800 font-semibold flex items-center justify-between w-full transition-colors cursor-pointer"
                    >
                      <span className="flex items-center gap-1.5">
                        <Crosshair className="w-3.5 h-3.5 text-blue-600" />
                        <span>Sensor Simulation & AP Diagnostics</span>
                      </span>
                      <span className="text-[11px] text-blue-600 font-bold hover:underline">
                        {showDiagnostics ? "Hide" : "Configure AP"}
                      </span>
                    </button>

                    {showDiagnostics && (
                      <div className="mt-3 p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-3 text-xs animate-in fade-in">
                        <div>
                          <label className="text-[10px] font-bold text-slate-600 uppercase tracking-wider block mb-1">
                            Simulate Connected Wi-Fi AP:
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
                            className="w-full text-xs px-3 py-2 rounded-xl bg-white border border-slate-200 text-slate-900 focus:outline-none focus:border-blue-500 cursor-pointer shadow-2xs"
                          >
                            <option value="Cellular Data (Mobile Network)::00:00:00:00:00:00">Cellular Data / 5G (Mobile) • Disconnected</option>
                            <option value="Other Wi-Fi / External Network::32:11:00:AB:CD:EF">Other Wi-Fi (Hostel / Home / Personal)</option>
                            <option value={(activeSession?.wifiSsid || "Pranjal") + "::A4:2B:B0:8C:12:EF"}>Classroom AP ({activeSession?.wifiSsid || "Pranjal"}) • Official Router Verified</option>
                            <option value={(activeSession?.wifiSsid || "Pranjal") + "::F2:45:67:89:AB:CD"}>⚠️ Fake Mobile Hotspot ({activeSession?.wifiSsid || "Pranjal"} • Rogue BSSID)</option>
                          </select>
                        </div>

                        <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1">
                          <span>Router BSSID: <b className="font-mono text-slate-700">{selectedBssid}</b></span>
                          <span className={selectedBssid === "A4:2B:B0:8C:12:EF" ? "text-emerald-600 font-bold" : "text-amber-600 font-bold"}>
                            {selectedBssid === "A4:2B:B0:8C:12:EF" ? "✓ Official Hardware" : "⚠️ Unverified AP"}
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                </section>
              ) : null}

              {/* No Active Session Radar Hero */}
              {!activeSession && (
                <section className="bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 shadow-xs space-y-5 text-center">
                  <div className="w-14 h-14 rounded-2xl bg-blue-50 border border-blue-200 text-blue-600 flex items-center justify-center mx-auto shadow-sm">
                    <Radio className="w-7 h-7" />
                  </div>

                  <div className="space-y-1.5">
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 inline-block mb-1">
                      ZERO-TOUCH RADAR
                    </span>
                    <h3 className="text-base font-bold text-slate-900 tracking-tight">
                      Attendance Radar Active
                    </h3>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto leading-relaxed">
                      Listening for professor broadcasts. Your attendance will be automatically verified when class begins.
                    </p>
                  </div>

                  <div className="grid grid-cols-3 gap-2 pt-1">
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center space-y-1">
                      <Wifi className="w-4 h-4 text-blue-600 mx-auto" />
                      <span className="text-[10px] text-slate-500 block font-semibold">Wi-Fi</span>
                      <span className="text-[11px] font-bold text-slate-800 block truncate">Sensor Ready</span>
                    </div>
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center space-y-1">
                      <MapPin className="w-4 h-4 text-blue-600 mx-auto" />
                      <span className="text-[10px] text-slate-500 block font-semibold">Geofence</span>
                      <span className="text-[11px] font-bold text-slate-800 block truncate">Classroom GPS</span>
                    </div>
                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-center space-y-1">
                      <ShieldCheck className="w-4 h-4 text-emerald-600 mx-auto" />
                      <span className="text-[10px] text-slate-500 block font-semibold">Hardware</span>
                      <span className="text-[11px] font-bold text-emerald-700 block truncate">Bound</span>
                    </div>
                  </div>

                  <div className="pt-1">
                    <button
                      type="button"
                      onClick={() => setShowDiagnostics(!showDiagnostics)}
                      className="text-xs text-slate-400 hover:text-slate-600 font-medium underline cursor-pointer"
                    >
                      {showDiagnostics ? "Hide AP Selector" : "Sensor Diagnostics & AP Selector"}
                    </button>

                    {showDiagnostics && (
                      <div className="mt-3 p-4 rounded-xl bg-slate-50 border border-slate-200 text-left space-y-2 animate-in fade-in">
                        <label className="text-[10px] font-bold text-slate-600 uppercase tracking-wider block">
                          Select Simulation Wi-Fi AP:
                        </label>
                        <select
                          value={studentConnectedWifi + "::" + selectedBssid}
                          onChange={(e) => {
                            const [net, bssid] = e.target.value.split("::");
                            setStudentConnectedWifi(net);
                            setSelectedBssid(bssid || "00:00:00:00:00:00");
                          }}
                          className="w-full text-xs px-3 py-2 rounded-xl bg-white border border-slate-200 text-slate-900 focus:outline-none focus:border-blue-500 cursor-pointer"
                        >
                          <option value="Cellular Data (Mobile Network)::00:00:00:00:00:00">Cellular Data / 5G (Mobile)</option>
                          <option value="Other Wi-Fi / External Network::32:11:00:AB:CD:EF">Other Wi-Fi (Hostel / Home)</option>
                          <option value="Pranjal::A4:2B:B0:8C:12:EF">Classroom AP (Pranjal) • Official Hardware Verified</option>
                          <option value="Pranjal::F2:45:67:89:AB:CD">⚠️ Fake Mobile Hotspot (Pranjal • Rogue BSSID)</option>
                        </select>
                      </div>
                    )}
                  </div>
                </section>
              )}

              {/* Quick Action to Enrolled Subjects */}
              <div className="bg-white border border-slate-200 rounded-2xl p-4 flex items-center justify-between gap-3 shadow-xs">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                    <BookOpen className="w-5 h-5" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-slate-900">Enrolled in {myClasses.length} Subjects</h4>
                    <p className="text-[11px] text-slate-500">Tap to manage classes or join a new batch</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setActiveTab("subjects")}
                  className="px-3.5 py-1.5 rounded-xl bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-bold border border-blue-200 transition-all cursor-pointer shrink-0"
                >
                  View
                </button>
              </div>
            </div>
          )}

          {/* TAB 2: SUBJECTS & BATCH ENROLLMENT */}
          {activeTab === "subjects" && (
            <div className="space-y-4 animate-in fade-in duration-200">
              {/* Top Bar inside Subjects */}
              <div className="flex items-center justify-between pb-3 border-b border-slate-200">
                <div>
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 inline-block mb-1">
                    COURSE ROSTER
                  </span>
                  <h3 className="text-base font-bold text-slate-900 tracking-tight">My Subjects</h3>
                  <p className="text-xs text-slate-500">{myClasses.length} active enrollments</p>
                </div>

                <button
                  type="button"
                  onClick={() => setShowJoinInline(!showJoinInline)}
                  className="flex items-center gap-1.5 px-3.5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-600/20 transition-all cursor-pointer"
                >
                  <KeyRound className="w-3.5 h-3.5" />
                  <span>{showJoinInline ? "Close" : "+ Join Batch"}</span>
                </button>
              </div>

              {/* Join Code Card */}
              {showJoinInline && (
                <section className="bg-white border border-blue-200 rounded-2xl p-5 space-y-3 shadow-xs animate-in fade-in">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 text-xs font-bold text-slate-900">
                      <KeyRound className="w-4 h-4 text-blue-600" />
                      <span>Join Subject via Code</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setShowJoinInline(false)}
                      className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
                    >
                      ✕
                    </button>
                  </div>
                  <p className="text-xs text-slate-500">
                    Enter the Subject Join Code provided by your professor (e.g. CS50-6374 or CS50).
                  </p>

                  <form onSubmit={handleJoinClass} className="flex gap-2 pt-1">
                    <input
                      type="text"
                      required
                      placeholder="e.g. CS50-6374"
                      value={joinCodeInput}
                      onChange={(e) => setJoinCodeInput(e.target.value.toUpperCase())}
                      className="flex-1 px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none focus:border-blue-500 uppercase"
                    />
                    <button
                      type="submit"
                      disabled={!joinCodeInput.trim()}
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-600/20 transition-all cursor-pointer shrink-0"
                    >
                      Enroll
                    </button>
                  </form>

                  {joinErrorMsg && (
                    <div className="p-2.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 text-xs flex items-center gap-2">
                      <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                      <span>{joinErrorMsg}</span>
                    </div>
                  )}

                  {joinSuccessMsg && (
                    <div className="p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs flex items-center gap-2">
                      <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                      <span>{joinSuccessMsg}</span>
                    </div>
                  )}
                </section>
              )}

              {/* Enrolled Classes List */}
              <div className="space-y-3">
                {myClasses.length === 0 ? (
                  <div className="p-8 text-center bg-white border border-slate-200 rounded-2xl space-y-2">
                    <BookOpen className="w-8 h-8 text-slate-300 mx-auto" />
                    <p className="text-xs text-slate-500 font-medium">You are not enrolled in any subjects yet.</p>
                    <button
                      type="button"
                      onClick={() => setShowJoinInline(true)}
                      className="text-xs text-blue-600 font-bold hover:underline cursor-pointer"
                    >
                      + Join your first subject batch
                    </button>
                  </div>
                ) : (
                  myClasses.map((c) => (
                    <div
                      key={c.id}
                      className="bg-white border border-slate-200 hover:border-blue-400 rounded-2xl p-4 flex items-center justify-between gap-3 shadow-xs transition-all"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-10 h-10 rounded-xl bg-blue-50 border border-blue-100 text-blue-700 flex flex-col items-center justify-center shrink-0">
                          <span className="text-[10px] font-mono font-bold">{c.subjectCode.slice(0, 4)}</span>
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-lg bg-blue-50 text-blue-700 border border-blue-200">
                              {c.subjectCode}
                            </span>
                            <span className="text-[10px] text-slate-500 font-mono">Join: {c.joinCode}</span>
                          </div>
                          <h4 className="text-xs font-bold text-slate-900 truncate mt-1">{c.subjectName}</h4>
                          <span className="text-[11px] text-slate-500 block truncate">{c.roomNo}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                          Active
                        </span>
                        <button
                          type="button"
                          onClick={() => {
                            setClassToUnenroll(c);
                            setUnenrollConfirmText("");
                            setUnenrollAcknowledge(false);
                          }}
                          title="Unenroll from this subject (Multi-step confirmation required)"
                          className="p-1.5 rounded-xl text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-all cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* TAB 3: DEVICE SECURITY */}
          {activeTab === "device" && (
            <div className="space-y-4 animate-in fade-in duration-200">
              <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs space-y-4">
                <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
                  <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center shrink-0">
                    <ShieldCheck className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-slate-900">Anti-Proxy Hardware Lock</h3>
                    <p className="text-xs text-slate-500">Cryptographic 1-Device Enforcement</p>
                  </div>
                </div>

                <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
                  <div className="flex justify-between items-center text-slate-600">
                    <span>Student Identity:</span>
                    <span className="font-bold text-slate-900">{studentName} ({rollNo})</span>
                  </div>
                  <div className="flex justify-between items-center text-slate-600">
                    <span>Bound Device:</span>
                    <span className="font-mono font-bold text-blue-600">{boundDevice?.deviceModel || "Mobile Browser"}</span>
                  </div>
                  <div className="flex justify-between items-center text-slate-600">
                    <span>Protection Status:</span>
                    <span className={`font-bold px-2 py-0.5 rounded-full text-[10px] ${
                      boundDevice?.status === "PENDING_UNBIND" 
                        ? "bg-amber-100 text-amber-800" 
                        : "bg-emerald-100 text-emerald-800"
                    }`}>
                      {boundDevice?.status === "PENDING_UNBIND" ? "Pending Approval" : "ACTIVE • Locked"}
                    </span>
                  </div>
                </div>

                <div className="text-xs text-slate-600 space-y-3 leading-relaxed">
                  <p>
                    To prevent proxy attendance, your student account is hardware-bound to this device. Attendance can only be logged from this phone.
                  </p>
                  <button
                    type="button"
                    onClick={() => setShowUnbindModal(true)}
                    className="w-full py-3 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-bold border border-slate-200 transition-all cursor-pointer flex items-center justify-center gap-2"
                  >
                    <ShieldCheck className="w-4 h-4 text-blue-600" />
                    <span>Request Device Switch / Unbind</span>
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: STUDENT PROFILE */}
          {activeTab === "profile" && (
            <div className="space-y-4 animate-in fade-in duration-200">
              <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs space-y-5">
                <div className="flex items-center justify-between gap-4 pb-4 border-b border-slate-100">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-12 h-12 rounded-2xl bg-blue-600 text-white flex items-center justify-center font-black text-lg shadow-md shadow-blue-600/20 shrink-0">
                      {studentName ? studentName.slice(0, 2).toUpperCase() : "ST"}
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-base font-bold text-slate-900 truncate">{studentName || "Student"}</h3>
                      <p className="text-xs text-slate-500 font-mono font-medium">{rollNo} • IIIT Naya Raipur</p>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setEditName(studentName);
                      setEditRollNo(rollNo);
                      setEditEmail(studentEmail);
                      setProfileSaveSuccess(null);
                      setShowEditProfileModal(true);
                    }}
                    className="px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-slate-100 text-slate-700 border border-slate-200 text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5"
                  >
                    <Pencil className="w-3.5 h-3.5 text-blue-600" />
                    <span>Edit</span>
                  </button>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1">
                    <div className="flex items-center gap-2 text-slate-500">
                      <Mail className="w-3.5 h-3.5 text-blue-600" />
                      <span className="font-semibold text-[10px] uppercase tracking-wider">Email Address</span>
                    </div>
                    <p className="font-medium text-slate-900 truncate">{studentEmail || "Not Provided"}</p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1">
                    <div className="flex items-center gap-2 text-slate-500">
                      <GraduationCap className="w-3.5 h-3.5 text-blue-600" />
                      <span className="font-semibold text-[10px] uppercase tracking-wider">Institution</span>
                    </div>
                    <p className="font-bold text-slate-900">IIIT Naya Raipur</p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1">
                    <div className="flex items-center gap-2 text-slate-500">
                      <BookOpen className="w-3.5 h-3.5 text-blue-600" />
                      <span className="font-semibold text-[10px] uppercase tracking-wider">Courses</span>
                    </div>
                    <p className="font-bold text-slate-900">{myClasses.length} Active Courses</p>
                  </div>

                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1">
                    <div className="flex items-center gap-2 text-slate-500">
                      <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                      <span className="font-semibold text-[10px] uppercase tracking-wider">Hardware Security</span>
                    </div>
                    <p className="font-bold text-emerald-600 flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                      Bound & Protected
                    </p>
                  </div>
                </div>

                <div className="space-y-2 pt-2">
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="w-full py-2.5 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl text-xs font-bold border border-rose-200 transition-all cursor-pointer flex items-center justify-center gap-2"
                  >
                    <LogOut className="w-4 h-4 text-rose-600" />
                    <span>Sign Out of Student Account</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ==================================================================== */}
      {/* ==================================================================== */}
      

      {/* ==================================================================== */}
      {/* MODALS: EDIT PROFILE & DEVICE UNBIND (MATCHES FACULTY MODAL STYLE) */}
      {/* ==================================================================== */}
      {showEditProfileModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-white border border-slate-200 shadow-2xl rounded-2xl p-6 max-w-md w-full space-y-4 animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">Edit Student Profile</h3>
                <p className="text-xs text-slate-500 font-medium">Update your institutional details</p>
              </div>
              <button
                type="button"
                onClick={() => setShowEditProfileModal(false)}
                className="w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-xs cursor-pointer transition-all"
              >
                ✕
              </button>
            </div>

            {profileSaveSuccess && (
              <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl flex items-center gap-2 text-emerald-800 text-xs font-bold animate-in fade-in">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>{profileSaveSuccess}</span>
              </div>
            )}

            <form onSubmit={handleSaveProfile} className="space-y-3">
              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1">Full Name</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                    <User className="w-4 h-4" />
                  </div>
                  <input
                    type="text"
                    required
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    placeholder="e.g. Rahul Sharma"
                    className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 focus:border-blue-500 focus:bg-white rounded-xl text-xs font-semibold text-slate-900 focus:outline-none transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1">Roll Number / ID</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                    <Hash className="w-4 h-4" />
                  </div>
                  <input
                    type="text"
                    required
                    value={editRollNo}
                    onChange={(e) => setEditRollNo(e.target.value.toUpperCase())}
                    placeholder="e.g. 21CS042"
                    className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 focus:border-blue-500 focus:bg-white rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none transition-all uppercase"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1">Email Address</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                    <Mail className="w-4 h-4" />
                  </div>
                  <input
                    type="email"
                    value={editEmail}
                    onChange={(e) => setEditEmail(e.target.value)}
                    placeholder="e.g. student@iiitnr.edu.in"
                    className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 focus:border-blue-500 focus:bg-white rounded-xl text-xs font-medium text-slate-900 focus:outline-none transition-all"
                  />
                </div>
              </div>

              <div className="pt-2 flex gap-2">
                <button
                  type="button"
                  onClick={() => setShowEditProfileModal(false)}
                  className="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold transition-all cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSavingProfile || !editName.trim() || !editRollNo.trim()}
                  className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-600/20 transition-all cursor-pointer flex items-center justify-center gap-1.5"
                >
                  {isSavingProfile ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      <span>Saving...</span>
                    </>
                  ) : (
                    <>
                      <Check className="w-3.5 h-3.5" />
                      <span>Save Changes</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* UNBIND DEVICE REQUEST MODAL */}
      {showUnbindModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-white border border-slate-200 shadow-2xl rounded-2xl p-6 max-w-md w-full space-y-4 animate-in zoom-in-95">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-base">
                <ShieldCheck className="w-5 h-5 text-blue-600" />
                <span>Anti-Proxy Device Lock</span>
              </div>
              <button
                type="button"
                onClick={() => setShowUnbindModal(false)}
                className="w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-xs cursor-pointer transition-all"
              >
                ✕
              </button>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
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
                <span className={`font-bold ${boundDevice?.status === "PENDING_UNBIND" ? "text-amber-600" : "text-emerald-600"}`}>
                  {boundDevice?.status === "PENDING_UNBIND" ? "Pending Approval" : "ACTIVE (Locked)"}
                </span>
              </div>
            </div>

            <div className="text-xs text-slate-600 space-y-3">
              <p>
                <b>Anti-Proxy Policy:</b> Your account is hardware-locked to this device. To switch phones, request an unbind from faculty.
              </p>
              {boundDevice?.status === "PENDING_UNBIND" ? (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-300 text-amber-800 text-xs">
                  ⏳ <b>Request Pending:</b> Unbind request sent to faculty. Once approved by your professor, you can bind a new phone.
                </div>
              ) : (
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-bold text-slate-600 mb-1">
                      Reason for Unbind:
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. Bought a new phone / formatted device"
                      value={unbindReason}
                      onChange={(e) => setUnbindReason(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-900 focus:outline-none focus:border-blue-500"
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
                    className="w-full py-3 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-bold shadow-md shadow-amber-600/20 transition-all cursor-pointer"
                  >
                    {unbindSubmitting ? "Submitting..." : "Request Device Unbind from Central Admin →"}
                  </button>
                </div>
              )}
            </div>

            {unbindMessage && (
              <p className="text-xs text-emerald-600 text-center font-bold">{unbindMessage}</p>
            )}
          </div>
        </div>
      )}

      {/* MULTI-FRICTION SAFEGUARD: DROP/UNENROLL SUBJECT MODAL */}
      {classToUnenroll && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-6 w-full max-w-md shadow-2xl border border-rose-100 space-y-4 animate-in fade-in zoom-in-95 duration-150">
            {/* Header */}
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-2xl bg-rose-50 border border-rose-200 flex items-center justify-center text-rose-600 shrink-0">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900 leading-tight">
                    Drop & Unenroll Subject?
                  </h3>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Critical action · Requires confirmation
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setClassToUnenroll(null)}
                className="p-1.5 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Target Course Summary */}
            <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200/80 space-y-1">
              <div className="font-bold text-sm text-slate-900">
                {classToUnenroll.subjectName}
              </div>
              <div className="text-xs font-mono font-semibold text-slate-600">
                Code: {classToUnenroll.subjectCode} · {classToUnenroll.roomNo}
              </div>
            </div>

            {/* Warning Points */}
            <div className="p-3.5 rounded-2xl bg-rose-50/70 border border-rose-200/80 text-xs text-rose-950 space-y-2">
              <div className="font-bold text-rose-900 flex items-center gap-1.5">
                <span>⚠️</span> Irreversible Attendance Impact:
              </div>
              <ul className="list-disc list-inside space-y-1 text-[11px] text-rose-800 leading-relaxed">
                <li>Your device will stop receiving live attendance signals for this class.</li>
                <li>You cannot mark attendance if a session is currently live.</li>
                <li>To rejoin, you will need the professor's class code again.</li>
              </ul>
            </div>

            {/* Friction 1: Mandatory Checkbox */}
            <label className="flex items-start gap-2.5 p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-700 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={unenrollAcknowledge}
                onChange={(e) => setUnenrollAcknowledge(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded text-rose-600 focus:ring-rose-500 border-slate-300"
              />
              <span className="text-[11px] leading-tight">
                I understand that dropping this subject removes my access and cannot be undone automatically.
              </span>
            </label>

            {/* Friction 2: Type Subject Code to Unlock */}
            <div className="space-y-1.5">
              <label className="block text-[11px] font-semibold text-slate-600">
                Type subject code <span className="font-mono font-bold text-slate-900 bg-slate-100 px-1.5 py-0.5 rounded">{classToUnenroll.subjectCode}</span> to confirm:
              </label>
              <input
                type="text"
                placeholder={classToUnenroll.subjectCode}
                value={unenrollConfirmText}
                onChange={(e) => setUnenrollConfirmText(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-xs font-mono uppercase tracking-wider focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
              />
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2.5 pt-1">
              <button
                type="button"
                onClick={() => setClassToUnenroll(null)}
                className="flex-1 py-2.5 rounded-xl border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors cursor-pointer"
              >
                Keep Subject
              </button>
              <button
                type="button"
                disabled={
                  !unenrollAcknowledge ||
                  unenrollConfirmText.trim().toUpperCase() !== classToUnenroll.subjectCode.trim().toUpperCase()
                }
                onClick={() => {
                  handleUnenrollClass(classToUnenroll.id);
                  setClassToUnenroll(null);
                }}
                className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 disabled:bg-slate-200 disabled:text-slate-400 disabled:cursor-not-allowed text-white text-xs font-bold shadow-md shadow-rose-600/20 transition-all cursor-pointer"
              >
                Drop Subject
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

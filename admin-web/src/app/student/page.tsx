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
  Trash2,
  Pencil,
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
  const [activeTab, setActiveTab] = useState<"radar" | "subjects" | "device" | "profile">("radar");
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
    setIsLoggedIn(false);
    setShowProfileSetup(false);
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
    myClasses.some(c => 
      c.id === activeSession.classId || 
      c.joinCode.toUpperCase() === activeSession.joinCode.toUpperCase() ||
      c.subjectCode.toUpperCase() === activeSession.subjectCode.toUpperCase()
    )
  );

  // AUTOMATIC ATTENDANCE EXECUTION (Only triggers if enrolled and not yet marked!)
  useEffect(() => {
    if (!isLoggedIn || !activeSession || !isEnrolledInActive || isVerifyingPresence) {
      return;
    }
    // Prevent repeated verification if already marked PRESENT for this session
    if (markedPresentSessionsRef.current.has(activeSession.id) || attendanceStatus === "PRESENT") {
      return;
    }

    const timer = setTimeout(() => {
      executePresenceVerification(activeSession);
    }, 400);

    return () => clearTimeout(timer);
  }, [isLoggedIn, activeSession?.id, isEnrolledInActive, geoMode, studentConnectedWifi, attendanceStatus]);

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

      const allowedWifiList = (sess.wifiSsid || "Pranjal")
        .split(",")
        .map((s: string) => s.toLowerCase().trim())
        .filter(Boolean);
      const currentWifi = (studentConnectedWifi || "").toLowerCase().trim();
      
      // HARDWARE ANTI-HOTSPOT CHECK:
      // Router BSSID must match classroom hardware AP (A4:2B:B0:8C:12:EF). Rogue hotspots with same SSID are rejected!
      const isOfficialBssid = selectedBssid === "A4:2B:B0:8C:12:EF";
      const isSsidAllowed = allowedWifiList.some((target: string) =>
        currentWifi === target ||
        (currentWifi.includes(target) && !currentWifi.includes("fake") && !currentWifi.includes("cellular") && !currentWifi.includes("other"))
      );
      const isWifiMatched = 
        isOfficialBssid && (
          isSsidAllowed ||
          (detectedNetwork?.isSameSubnet === true && isSsidAllowed)
        );

      const isPresent = geoResult.isInside && isWifiMatched;

      if (isPresent) {
        setAttendanceStatus("PRESENT");
        autoCheckedInRef.current = true;

        // ONLY fire toast notification and record to DB ONCE per session!
        if (!markedPresentSessionsRef.current.has(sess.id)) {
          markedPresentSessionsRef.current.add(sess.id);
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

  // IF LOGGED IN: SHOW REGULAR STUDENT CONSOLE (PREMIUM MOBILE APP SHELL)
  return (
    <div className="min-h-screen bg-slate-100/70 text-slate-900 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      {/* Toast Notification */}
      {autoCheckInToast && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-emerald-600 text-white px-5 py-2.5 rounded-full shadow-2xl border border-emerald-400/30 text-xs font-bold tracking-wide flex items-center gap-2 animate-in fade-in slide-in-from-top-3">
          <Zap className="w-4 h-4 fill-emerald-200" />
          <span>{autoCheckInToast}</span>
        </div>
      )}

      {/* TOP APP BAR (Native App Bar / Sticky Header) */}
      <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200/90 shadow-2xs">
        <div className="max-w-2xl mx-auto px-4 py-2.5 flex items-center justify-between gap-3">
          {/* App Logo & Institution Title */}
          <div className="flex items-center gap-2.5">
            <Link 
              href="/" 
              className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white font-black text-sm shadow-md shadow-blue-500/20 hover:scale-105 active:scale-95 transition-all"
            >
              SA
            </Link>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-extrabold text-sm tracking-tight text-slate-900">IIIT-NR</span>
                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                  Live
                </span>
              </div>
              <p className="text-[11px] text-slate-500 font-medium leading-none mt-0.5">Smart Attendance</p>
            </div>
          </div>

          {/* Right Header Controls */}
          <div className="flex items-center gap-2">
            {/* Student Profile Pill */}
            <button
              type="button"
              onClick={() => setActiveTab("profile")}
              className={`flex items-center gap-2 px-2.5 py-1.5 rounded-full border transition-all cursor-pointer ${
                activeTab === "profile"
                  ? "bg-blue-50 border-blue-400 text-blue-900 ring-2 ring-blue-500/20"
                  : "bg-slate-50 hover:bg-slate-100 border-slate-200 text-slate-800"
              }`}
              title="View Profile"
            >
              <div className="w-6 h-6 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center text-[10px] font-black">
                {studentName ? studentName.slice(0, 2).toUpperCase() : "ST"}
              </div>
              <div className="text-left hidden sm:block">
                <span className="text-xs font-bold leading-tight block truncate max-w-[100px]">{studentName}</span>
                <span className="text-[10px] text-slate-500 font-mono block leading-none">{rollNo}</span>
              </div>
              <span className="text-[11px] font-bold font-mono sm:hidden">{rollNo}</span>
            </button>

            {/* Device Lock Status Shield */}
            <button
              type="button"
              onClick={() => setShowUnbindModal(true)}
              className={`p-2 rounded-xl border text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                boundDevice?.status === "PENDING_UNBIND"
                  ? "bg-amber-50 border-amber-300 text-amber-700"
                  : "bg-slate-50 hover:bg-slate-100 border-slate-200 text-slate-700"
              }`}
              title={boundDevice?.status === "PENDING_UNBIND" ? "Unbind Pending Teacher Approval" : "Anti-Proxy Device Bound"}
            >
              <ShieldCheck className={`w-4 h-4 ${boundDevice?.status === "PENDING_UNBIND" ? "text-amber-600" : "text-emerald-600"}`} />
            </button>

            {/* Sign Out */}
            <button
              onClick={handleLogout}
              title="Sign Out"
              className="p-2 rounded-xl bg-rose-50 hover:bg-rose-100 border border-rose-200 text-rose-600 text-xs font-bold transition-all cursor-pointer"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* DESKTOP TAB SELECTOR (Hidden on small mobile screens) */}
        <div className="hidden md:block border-t border-slate-100 bg-slate-50/50">
          <div className="max-w-2xl mx-auto px-4 py-2 flex items-center justify-center gap-2">
            {[
              { id: "radar", label: "Live Radar", icon: Radio, count: activeSession ? 1 : null },
              { id: "subjects", label: "My Subjects", icon: BookOpen, count: myClasses.length },
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
                  className={`flex items-center gap-2 px-4 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    isActive
                      ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                      : "bg-white text-slate-600 hover:text-slate-900 hover:bg-slate-100 border border-slate-200/80"
                  }`}
                >
                  <Icon className={`w-3.5 h-3.5 ${isActive ? "text-white" : "text-slate-500"}`} />
                  <span>{tab.label}</span>
                  {tab.count !== null && tab.count !== undefined && (
                    <span className={`px-1.5 py-0.2 rounded-full text-[10px] ${isActive ? "bg-white/20 text-white" : "bg-slate-100 text-slate-700 font-mono"}`}>
                      {tab.count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      </header>

      {/* MAIN CONTENT AREA */}
      <main className="max-w-md sm:max-w-xl md:max-w-2xl mx-auto w-full px-4 pt-4 pb-28 md:pb-12 flex-1 space-y-4">
        {/* TAB 1: RADAR & LIVE PRESENCE */}
        {activeTab === "radar" && (
          <div className="space-y-4 animate-in fade-in duration-200">
            {/* If Active Session exists for student's enrolled subject */}
            {activeSession && isEnrolledInActive ? (
              <section className={`bg-white border rounded-3xl p-5 sm:p-6 space-y-4 shadow-xl transition-all ${
                attendanceStatus === "PRESENT" ? "border-emerald-500/50 shadow-emerald-500/10" : "border-rose-400 shadow-rose-500/10"
              }`}>
                {/* Live Banner */}
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <span className="relative flex h-3 w-3">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                      <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500" />
                    </span>
                    <span className="text-xs font-black uppercase tracking-wider text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 rounded-full">
                      Live Class • {activeSession.roomNo}
                    </span>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      autoCheckedInRef.current = false;
                      if (activeSession) executePresenceVerification(activeSession);
                    }}
                    disabled={isVerifyingPresence}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-xs font-bold text-slate-700 transition-all cursor-pointer"
                    title="Re-check Presence"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 text-blue-600 ${isVerifyingPresence ? "animate-spin" : ""}`} />
                    <span>Verify</span>
                  </button>
                </div>

                {/* Subject Details */}
                <div>
                  <span className="text-[11px] font-mono font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-md border border-blue-200">
                    {activeSession.subjectCode}
                  </span>
                  <h2 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight mt-1">
                    {activeSession.subjectName}
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">Faculty • {activeSession.roomNo} • SSID: {activeSession.wifiSsid || "Pranjal"}</p>
                </div>

                {/* Big Presence Verification Banner */}
                {attendanceStatus === "PRESENT" ? (
                  <div className="p-4 rounded-2xl bg-gradient-to-r from-emerald-500/10 to-teal-500/10 border border-emerald-400/40 text-emerald-900 space-y-1 shadow-xs">
                    <div className="flex items-center gap-2 font-black text-sm text-emerald-800">
                      <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
                      <span>Zero-Touch Presence Verified!</span>
                    </div>
                    <p className="text-xs text-emerald-700">
                      You are marked <b>PRESENT</b> for this lecture in {activeSession.roomNo}.
                    </p>
                  </div>
                ) : (
                  <div className="p-4 rounded-2xl bg-amber-50 border border-amber-300 text-amber-900 space-y-1 shadow-xs">
                    <div className="flex items-center gap-2 font-bold text-sm text-amber-800">
                      <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />
                      <span>Verification In Progress / Action Needed</span>
                    </div>
                    <p className="text-xs text-amber-700 leading-relaxed">
                      Make sure your device is connected to classroom Wi-Fi <b>"{activeSession.wifiSsid || "Pranjal"}"</b> and you are inside {activeSession.roomNo}.
                    </p>
                  </div>
                )}

                {/* Two Sensor Verification Cards */}
                <div className="grid grid-cols-2 gap-2.5 pt-1">
                  {/* Wi-Fi Card */}
                  <div className={`p-3 rounded-2xl border text-xs space-y-1.5 ${
                    (activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim())
                      ? "bg-emerald-50/70 border-emerald-200 text-emerald-900"
                      : "bg-rose-50/70 border-rose-200 text-rose-900"
                  }`}>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1 font-bold">
                        <Wifi className="w-3.5 h-3.5" />
                        <span>Classroom Wi-Fi</span>
                      </div>
                      <span className={`text-[9px] font-black px-1.5 py-0.5 rounded ${
                        (activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim())
                          ? "bg-emerald-200 text-emerald-900"
                          : "bg-rose-200 text-rose-900"
                      }`}>
                        {(activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim()) ? "MATCH" : "MISMATCH"}
                      </span>
                    </div>
                    <div className="text-[11px] font-mono truncate">
                      {(activeSession.wifiSsid || "Pranjal").split(",").map((s: string) => s.toLowerCase().trim()).includes(studentConnectedWifi.toLowerCase().trim()) ? "AP Verified" : "Not Connected"}
                    </div>
                  </div>

                  {/* Geofence Card */}
                  <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200 text-xs space-y-1.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1 font-bold text-slate-800">
                        <MapPin className="w-3.5 h-3.5 text-blue-600" />
                        <span>Geofence</span>
                      </div>
                      <span className={`text-[9px] font-black px-1.5 py-0.5 rounded ${
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

                {/* Collapsible Simulation / Testing Accordion */}
                <div className="border-t border-slate-100 pt-3">
                  <button
                    type="button"
                    onClick={() => setShowDiagnostics(!showDiagnostics)}
                    className="text-xs text-slate-500 hover:text-slate-800 font-bold flex items-center justify-between w-full transition-colors cursor-pointer"
                  >
                    <span className="flex items-center gap-1.5">
                      <Crosshair className="w-3.5 h-3.5 text-blue-600" />
                      <span>Sensor Simulation & AP Testing</span>
                    </span>
                    <span className="text-[11px] text-blue-600 underline">
                      {showDiagnostics ? "Hide" : "Configure AP"}
                    </span>
                  </button>

                  {showDiagnostics && (
                    <div className="mt-3 p-3.5 rounded-2xl bg-slate-50 border border-slate-200 space-y-3 text-xs animate-in fade-in">
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

            {/* When NO Active Session: Show Sleek App Radar Hero */}
            {!activeSession && (
              <section className="bg-white border border-slate-200/90 rounded-3xl p-6 sm:p-8 shadow-xs space-y-6 text-center">
                {/* Radar Pulse Visual */}
                <div className="relative w-28 h-28 mx-auto flex items-center justify-center">
                  <div className="absolute inset-0 rounded-full bg-blue-100/60 animate-ping opacity-30" />
                  <div className="absolute inset-3 rounded-full bg-blue-50 border border-blue-200 animate-pulse" />
                  <div className="relative w-14 h-14 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white shadow-lg shadow-blue-500/30">
                    <Radio className="w-7 h-7 animate-pulse" />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-50 border border-blue-200 text-blue-700 text-xs font-bold">
                    <span className="w-2 h-2 rounded-full bg-blue-600 animate-ping" />
                    Zero-Touch Radar Listening
                  </div>
                  <h3 className="text-xl font-black text-slate-900 tracking-tight">
                    Attendance Not Started
                  </h3>
                  <p className="text-xs text-slate-500 max-w-sm mx-auto leading-relaxed">
                    Your professor has not opened attendance yet. Verification will activate automatically as soon as class begins.
                  </p>
                </div>

                {/* 3 Status Telemetry Pills */}
                <div className="grid grid-cols-3 gap-2 pt-2">
                  <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200/70 text-center space-y-1">
                    <Wifi className="w-4 h-4 text-blue-600 mx-auto" />
                    <span className="text-[10px] text-slate-500 block font-semibold">Wi-Fi Sensor</span>
                    <span className="text-[11px] font-bold text-slate-800 block truncate">Ready</span>
                  </div>
                  <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200/70 text-center space-y-1">
                    <MapPin className="w-4 h-4 text-blue-600 mx-auto" />
                    <span className="text-[10px] text-slate-500 block font-semibold">Geofence</span>
                    <span className="text-[11px] font-bold text-slate-800 block truncate">Classroom GPS</span>
                  </div>
                  <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200/70 text-center space-y-1">
                    <ShieldCheck className="w-4 h-4 text-emerald-600 mx-auto" />
                    <span className="text-[10px] text-slate-500 block font-semibold">Hardware</span>
                    <span className="text-[11px] font-bold text-emerald-700 block truncate">Bound</span>
                  </div>
                </div>

                {/* Simulation AP Toggle */}
                <div className="pt-2">
                  <button
                    type="button"
                    onClick={() => setShowDiagnostics(!showDiagnostics)}
                    className="text-xs text-slate-400 hover:text-slate-600 font-medium underline cursor-pointer"
                  >
                    {showDiagnostics ? "Hide AP Selector" : "Sensor Diagnostics & AP Selector"}
                  </button>

                  {showDiagnostics && (
                    <div className="mt-3 p-4 rounded-2xl bg-slate-50 border border-slate-200 text-left space-y-2 animate-in fade-in">
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
            <div className="bg-white border border-slate-200/90 rounded-2xl p-4 flex items-center justify-between gap-3 shadow-2xs">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                  <BookOpen className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Enrolled in {myClasses.length} Subjects</h4>
                  <p className="text-xs text-slate-500">Tap to manage classes or join a new batch</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setActiveTab("subjects")}
                className="px-3.5 py-2 rounded-xl bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-bold transition-all cursor-pointer shrink-0"
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
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-lg font-black text-slate-900 tracking-tight">My Subjects</h3>
                <p className="text-xs text-slate-500">{myClasses.length} active enrollments</p>
              </div>

              <button
                type="button"
                onClick={() => setShowJoinInline(!showJoinInline)}
                className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-600/20 transition-all cursor-pointer"
              >
                <KeyRound className="w-3.5 h-3.5" />
                <span>{showJoinInline ? "Close" : "+ Join Batch"}</span>
              </button>
            </div>

            {/* Join Code Card (Collapsible or directly shown if opened) */}
            {showJoinInline && (
              <section className="bg-white border border-blue-200 rounded-3xl p-5 sm:p-6 space-y-3 shadow-md animate-in fade-in zoom-in-98">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
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
                  Enter the Subject Join Code provided by your teacher (e.g. CS50-6374 or CS50).
                </p>

                <form onSubmit={handleJoinClass} className="flex gap-2 pt-1">
                  <input
                    type="text"
                    required
                    placeholder="e.g. CS50-6374"
                    value={joinCodeInput}
                    onChange={(e) => setJoinCodeInput(e.target.value)}
                    className="flex-1 px-4 py-2.5 rounded-xl bg-slate-50 border border-slate-200 focus:border-blue-500 focus:bg-white focus:outline-none text-slate-900 text-sm uppercase font-mono tracking-wider font-bold transition-all"
                  />
                  <button
                    type="submit"
                    className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold text-xs shadow-md shadow-blue-600/20 transition-all shrink-0 cursor-pointer"
                  >
                    Join
                  </button>
                </form>

                {joinSuccessMsg && (
                  <div className="flex items-center gap-2 p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-medium animate-in fade-in">
                    <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-600" />
                    <span>{joinSuccessMsg}</span>
                  </div>
                )}

                {joinErrorMsg && (
                  <div className="flex items-center gap-2 p-2.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-medium animate-in fade-in">
                    <AlertCircle className="w-4 h-4 shrink-0 text-rose-500" />
                    <span>{joinErrorMsg}</span>
                  </div>
                )}
              </section>
            )}

            {/* List of enrolled classes */}
            <div className="space-y-2.5">
              {myClasses.length === 0 ? (
                <div className="bg-white border border-slate-200/90 rounded-3xl p-8 text-center space-y-3 shadow-2xs">
                  <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto">
                    <BookOpen className="w-6 h-6" />
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-slate-900">No Enrolled Subjects Yet</h4>
                    <p className="text-xs text-slate-500 mt-1 max-w-xs mx-auto">
                      Ask your professor for the 6-character subject code and enter it to begin tracking your attendance.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowJoinInline(true)}
                    className="px-4 py-2 bg-blue-50 hover:bg-blue-100 text-blue-700 rounded-xl text-xs font-bold transition-all cursor-pointer inline-flex items-center gap-1.5"
                  >
                    <KeyRound className="w-3.5 h-3.5" />
                    <span>Enter Join Code</span>
                  </button>
                </div>
              ) : (
                myClasses.map((c) => (
                  <div
                    key={c.id}
                    className="bg-white border border-slate-200/90 hover:border-slate-300 rounded-2xl p-4 flex items-center justify-between gap-3 shadow-2xs transition-all"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-blue-50 to-indigo-50 border border-blue-100 text-blue-700 flex flex-col items-center justify-center shrink-0">
                        <span className="text-[10px] font-black uppercase font-mono leading-none">{c.subjectCode.slice(0, 4)}</span>
                        <span className="text-[8px] font-bold text-slate-500 leading-none mt-0.5">BATCH</span>
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px] font-mono font-bold px-1.5 py-0.2 rounded bg-slate-100 text-slate-700 border border-slate-200">
                            {c.subjectCode}
                          </span>
                          <span className="text-[10px] text-slate-400 font-mono">Code: {c.joinCode}</span>
                        </div>
                        <h4 className="text-sm font-bold text-slate-900 truncate mt-0.5">{c.subjectName}</h4>
                        <span className="text-[11px] text-slate-500 block truncate">{c.roomNo}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3 shrink-0">
                      <div className="text-right">
                        <span className="text-xs font-black text-emerald-600 block font-mono">100%</span>
                        <span className="text-[9px] text-slate-400 uppercase font-bold">Verified</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleUnenrollClass(c.id)}
                        title="Unenroll from this subject"
                        className="p-2 rounded-xl text-slate-400 hover:text-rose-500 hover:bg-rose-50 border border-transparent hover:border-rose-200 transition-all cursor-pointer"
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
            <div className="bg-white border border-slate-200 rounded-3xl p-6 sm:p-7 space-y-5 shadow-xs">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center shrink-0">
                  <ShieldCheck className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-black text-slate-900">Anti-Proxy Hardware Lock</h3>
                  <p className="text-xs text-slate-500">Cryptographic 1-Device Enforcement</p>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200/90 space-y-2.5 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Student Identity:</span>
                  <span className="font-bold text-slate-900">{studentName} ({rollNo})</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Bound Device:</span>
                  <span className="font-mono font-bold text-blue-600">{boundDevice?.deviceModel || "Mobile Browser Bound"}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-500">Protection Status:</span>
                  <span className={`font-bold px-2 py-0.5 rounded-full text-[11px] ${
                    boundDevice?.status === "PENDING_UNBIND" 
                      ? "bg-amber-100 text-amber-800" 
                      : "bg-emerald-100 text-emerald-800"
                  }`}>
                    {boundDevice?.status === "PENDING_UNBIND" ? "Unbind Pending Teacher Approval" : "ACTIVE • Locked"}
                  </span>
                </div>
              </div>

              <div className="text-xs text-slate-600 space-y-3 leading-relaxed">
                <p>
                  To prevent proxy attendance, your university profile is bound to this device. Attendance can only be logged from this phone.
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
          <div className="space-y-4 animate-in fade-in duration-300">
            {/* Main Profile Hero Card */}
            <div className="relative overflow-hidden bg-gradient-to-b from-white via-white to-blue-50/40 border border-slate-200/90 rounded-3xl p-6 sm:p-7 shadow-lg shadow-slate-200/50 space-y-6">
              {/* Decorative Subtle Ambient Glow */}
              <div className="absolute top-0 right-0 -mr-16 -mt-16 w-48 h-48 rounded-full bg-blue-500/10 blur-3xl pointer-events-none" />
              <div className="absolute bottom-0 left-0 -ml-16 -mb-16 w-48 h-48 rounded-full bg-indigo-500/10 blur-3xl pointer-events-none" />

              {/* Profile Card Header */}
              <div className="relative flex items-center justify-between gap-4">
                <div className="flex items-center gap-4 min-w-0">
                  <div className="relative shrink-0">
                    <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-blue-500 text-white flex items-center justify-center font-black text-xl shadow-lg shadow-blue-500/30 ring-4 ring-blue-50">
                      {studentName ? studentName.slice(0, 2).toUpperCase() : "ST"}
                    </div>
                    {/* Active verified presence pulse dot */}
                    <span className="absolute -bottom-1 -right-1 w-4 h-4 rounded-full bg-emerald-500 ring-2 ring-white flex items-center justify-center shadow-xs" title="Device Active">
                      <span className="w-1.5 h-1.5 rounded-full bg-white" />
                    </span>
                  </div>

                  <div className="min-w-0 space-y-1">
                    <div className="flex items-center gap-2">
                      <h3 className="text-xl font-extrabold text-slate-900 tracking-tight truncate">{studentName || "Student"}</h3>
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-blue-50 text-blue-700 border border-blue-200/80">
                        Verified
                      </span>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs font-mono font-bold text-blue-700 bg-blue-100/70 px-2.5 py-0.5 rounded-lg border border-blue-200">
                        {rollNo || "No Roll"}
                      </span>
                      <span className="text-xs text-slate-500 font-medium truncate">
                        IIIT Naya Raipur
                      </span>
                    </div>
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
                  className="shrink-0 px-3.5 py-2 rounded-xl bg-white hover:bg-blue-50 text-blue-600 border border-slate-200 hover:border-blue-300 text-xs font-bold shadow-xs hover:shadow-sm transition-all cursor-pointer flex items-center gap-1.5 active:scale-95"
                  title="Edit Profile"
                >
                  <Pencil className="w-3.5 h-3.5" />
                  <span>Edit</span>
                </button>
              </div>

              {/* Pro Metric Widgets Grid (2x2) */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                {/* Email Widget */}
                <div className="p-3.5 rounded-2xl bg-white/90 border border-slate-200/90 shadow-2xs space-y-1">
                  <div className="flex items-center gap-2 text-slate-400">
                    <Mail className="w-3.5 h-3.5 text-blue-500" />
                    <span className="font-semibold text-slate-500 uppercase tracking-wider text-[10px]">Email Address</span>
                  </div>
                  <p className="font-semibold text-slate-900 truncate" title={studentEmail}>
                    {studentEmail || "Not Provided"}
                  </p>
                </div>

                {/* Institution Widget */}
                <div className="p-3.5 rounded-2xl bg-white/90 border border-slate-200/90 shadow-2xs space-y-1">
                  <div className="flex items-center gap-2 text-slate-400">
                    <GraduationCap className="w-3.5 h-3.5 text-indigo-500" />
                    <span className="font-semibold text-slate-500 uppercase tracking-wider text-[10px]">Campus & Institute</span>
                  </div>
                  <p className="font-bold text-slate-900 truncate">
                    IIIT Naya Raipur
                  </p>
                </div>

                {/* Enrolled Courses Widget */}
                <div className="p-3.5 rounded-2xl bg-white/90 border border-slate-200/90 shadow-2xs space-y-1">
                  <div className="flex items-center gap-2 text-slate-400">
                    <BookOpen className="w-3.5 h-3.5 text-amber-500" />
                    <span className="font-semibold text-slate-500 uppercase tracking-wider text-[10px]">Enrolled Subjects</span>
                  </div>
                  <p className="font-bold text-slate-900">
                    {myClasses.length} Active Courses
                  </p>
                </div>

                {/* Hardware Security Widget */}
                <div className="p-3.5 rounded-2xl bg-white/90 border border-slate-200/90 shadow-2xs space-y-1">
                  <div className="flex items-center gap-2 text-slate-400">
                    <ShieldCheck className="w-3.5 h-3.5 text-emerald-500" />
                    <span className="font-semibold text-slate-500 uppercase tracking-wider text-[10px]">Device Security</span>
                  </div>
                  <p className="font-bold text-emerald-600 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                    Hardware Bound & Protected
                  </p>
                </div>
              </div>

              {/* Action Buttons Hub */}
              <div className="space-y-2.5 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    setEditName(studentName);
                    setEditRollNo(rollNo);
                    setEditEmail(studentEmail);
                    setProfileSaveSuccess(null);
                    setShowEditProfileModal(true);
                  }}
                  className="w-full py-3.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 active:scale-[0.99] text-white rounded-2xl text-xs font-extrabold shadow-md shadow-blue-500/25 transition-all cursor-pointer flex items-center justify-center gap-2"
                >
                  <Pencil className="w-4 h-4 text-blue-100" />
                  <span>Edit Profile Details</span>
                </button>

                <button
                  type="button"
                  onClick={handleLogout}
                  className="w-full py-3 bg-rose-50/80 hover:bg-rose-100/90 active:scale-[0.99] text-rose-600 rounded-2xl text-xs font-bold border border-rose-200/80 transition-all cursor-pointer flex items-center justify-center gap-2"
                >
                  <LogOut className="w-4 h-4 text-rose-500" />
                  <span>Sign Out of Account</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* MOBILE BOTTOM NAVIGATION BAR (Floating App Dock on phones) */}
      <nav className="md:hidden fixed bottom-3 inset-x-3 z-40 max-w-md mx-auto bg-white/95 backdrop-blur-2xl border border-slate-200/90 rounded-3xl shadow-xl shadow-slate-900/10 px-2 py-1.5 pb-[max(0.45rem,env(safe-area-inset-bottom))]">
        <div className="flex items-center justify-around">
          {[
            { id: "radar", label: "Live Radar", icon: Radio, badge: activeSession ? "LIVE" : null },
            { id: "subjects", label: "Subjects", icon: BookOpen, count: myClasses.length },
            { id: "device", label: "Security", icon: ShieldCheck },
            { id: "profile", label: "Profile", icon: User },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as any)}
                className={`relative flex flex-col items-center gap-0.5 py-1 px-3 rounded-2xl transition-all cursor-pointer ${
                  isActive ? "text-blue-600 scale-105 font-black" : "text-slate-400 hover:text-slate-600 font-semibold"
                }`}
              >
                <div className={`p-1.5 rounded-xl transition-all ${isActive ? "bg-blue-600 text-white shadow-md shadow-blue-500/30" : ""}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <span className="text-[10px] tracking-tight">{tab.label}</span>
                {tab.badge && (
                  <span className="absolute -top-1 -right-0.5 px-1.5 py-0.2 bg-emerald-500 text-white text-[8px] font-black rounded-full animate-pulse shadow-xs">
                    {tab.badge}
                  </span>
                )}
                {tab.count !== undefined && tab.count > 0 && !tab.badge && (
                  <span className="absolute -top-1 -right-0.5 px-1.5 py-0.2 bg-slate-100 border border-slate-300 text-slate-700 text-[8px] font-bold rounded-full">
                    {tab.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </nav>

      {/* EDIT PROFILE MODAL */}
      {showEditProfileModal && (
        <div className="fixed inset-0 bg-slate-950/60 backdrop-blur-md z-50 flex items-center justify-center p-4 animate-in fade-in duration-200">
          <div className="bg-white border border-slate-200/90 shadow-2xl rounded-3xl p-6 sm:p-7 max-w-md w-full space-y-5 animate-in zoom-in-95 duration-200">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5 text-slate-900 font-extrabold text-base">
                <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shadow-md shadow-blue-500/20">
                  <Pencil className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-base font-extrabold text-slate-900 leading-tight">Edit Student Profile</h3>
                  <p className="text-[11px] text-slate-500 font-medium leading-none mt-0.5">Update your identity details</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowEditProfileModal(false)}
                className="w-7 h-7 rounded-full bg-slate-100 hover:bg-slate-200 text-slate-500 flex items-center justify-center text-xs cursor-pointer transition-all"
              >
                ✕
              </button>
            </div>

            {/* Dynamic Avatar Live Preview */}
            <div className="p-3.5 rounded-2xl bg-gradient-to-r from-blue-50/70 to-indigo-50/70 border border-blue-100 flex items-center gap-3.5">
              <div className="w-12 h-12 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center font-black text-base shadow-md shadow-blue-500/20 shrink-0">
                {editName ? editName.trim().slice(0, 2).toUpperCase() : (studentName ? studentName.slice(0, 2).toUpperCase() : "ST")}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-bold text-slate-900 truncate">
                  {editName || studentName || "Student Name"}
                </p>
                <div className="flex items-center gap-1.5 mt-0.5">
                  <span className="text-[10px] font-mono font-bold text-blue-700 bg-white/90 px-1.5 py-0.2 rounded border border-blue-200">
                    {editRollNo || rollNo || "ROLL"}
                  </span>
                  <span className="text-[10px] text-slate-500 truncate">
                    IIIT Naya Raipur
                  </span>
                </div>
              </div>
            </div>

            {profileSaveSuccess && (
              <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-2xl flex items-center gap-2 text-emerald-800 text-xs font-bold animate-in fade-in">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>{profileSaveSuccess}</span>
              </div>
            )}

            <form onSubmit={handleSaveProfile} className="space-y-3.5">
              {/* Full Name Field */}
              <div>
                <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Full Name
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <User className="w-4 h-4" />
                  </div>
                  <input
                    type="text"
                    required
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    placeholder="e.g. Archana Prajapati"
                    className="w-full pl-10 pr-3.5 py-2.5 bg-slate-50/80 border border-slate-200 focus:border-blue-500 focus:bg-white focus:ring-3 focus:ring-blue-500/15 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none transition-all"
                  />
                </div>
              </div>

              {/* Roll Number Field */}
              <div>
                <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Roll Number / Student ID
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <Hash className="w-4 h-4" />
                  </div>
                  <input
                    type="text"
                    required
                    value={editRollNo}
                    onChange={(e) => setEditRollNo(e.target.value.toUpperCase())}
                    placeholder="e.g. 89 or 26CS101"
                    className="w-full pl-10 pr-3.5 py-2.5 bg-slate-50/80 border border-slate-200 focus:border-blue-500 focus:bg-white focus:ring-3 focus:ring-blue-500/15 rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none transition-all uppercase"
                  />
                </div>
              </div>

              {/* Email Address Field */}
              <div>
                <label className="block text-[11px] font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Email Address
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <Mail className="w-4 h-4" />
                  </div>
                  <input
                    type="email"
                    value={editEmail}
                    onChange={(e) => setEditEmail(e.target.value)}
                    placeholder="e.g. archanaprajapati917@gmail.com"
                    className="w-full pl-10 pr-3.5 py-2.5 bg-slate-50/80 border border-slate-200 focus:border-blue-500 focus:bg-white focus:ring-3 focus:ring-blue-500/15 rounded-xl text-xs font-medium text-slate-900 focus:outline-none transition-all"
                  />
                </div>
              </div>

              {/* Form Action Buttons */}
              <div className="pt-2 flex gap-2.5">
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
                  className="flex-1 py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 active:scale-[0.98] disabled:opacity-50 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-500/25 transition-all cursor-pointer flex items-center justify-center gap-1.5"
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
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-white border border-slate-200 shadow-2xl rounded-3xl p-6 sm:p-7 max-w-md w-full space-y-5 animate-in zoom-in-95">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-base">
                <ShieldCheck className="w-5 h-5 text-blue-600" />
                <span>Anti-Proxy Device Lock</span>
              </div>
              <button
                type="button"
                onClick={() => setShowUnbindModal(false)}
                className="text-slate-400 hover:text-slate-700 text-xs px-2 py-1 rounded-lg bg-slate-100 cursor-pointer"
              >
                ✕ Close
              </button>
            </div>

            <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
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
                  {boundDevice?.status === "PENDING_UNBIND" ? "Pending Teacher Approval" : "ACTIVE (Locked)"}
                </span>
              </div>
            </div>

            <div className="text-xs text-slate-600 space-y-3">
              <p>
                <b>Anti-Proxy Policy:</b> Your account is hardware-locked to this device to prevent proxy attendance. To switch phones, request an unbind from faculty.
              </p>
              {boundDevice?.status === "PENDING_UNBIND" ? (
                <div className="p-3 rounded-xl bg-amber-50 border border-amber-300 text-amber-800 text-xs">
                  ⏳ <b>Request Pending:</b> Unbind request sent to faculty. Once approved by your professor, you can bind a new phone.
                </div>
              ) : (
                <div className="space-y-3">
                  <div>
                    <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">
                      Reason for Unbind:
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
                    className="w-full py-3 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-bold shadow-md shadow-amber-600/20 transition-all cursor-pointer"
                  >
                    {unbindSubmitting ? "Submitting..." : "Request Device Unbind from Teacher →"}
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
    </div>
  );
}

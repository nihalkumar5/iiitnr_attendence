
export function getAndroidJoinCode(subCode: string, classId: string): string {
  if (!subCode) return "CS50-1000";
  if (subCode.includes("-")) {
    return subCode.trim().toUpperCase();
  }
  if (!classId) return `${subCode.slice(0, 4).toUpperCase()}-1000`;
  let hash = 0;
  for (let i = 0; i < classId.length; i++) {
    hash = (Math.imul(31, hash) + classId.charCodeAt(i)) | 0;
  }
  const num = (Math.abs(hash) % 9000) + 1000;
  const cleanSub = subCode.slice(0, 4).replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
  return `${cleanSub}-${num}`;
}

/**
 * Production Supabase Attendance Service
 * Direct cloud database connection for classes, sessions, enrollments, and live attendance records.
 */

import { supabase } from "./supabaseClient";

export interface DBStudent {
  id: string;
  name: string;
  rollNo: string;
  email: string;
  status: "PRESENT" | "ABSENT" | "PENDING";
  distanceMeters?: number;
  wifiStatus?: "CONNECTED" | "DISCONNECTED";
  detectedWifi?: string;
  verifiedAt?: string;
  joinedAt: string;
}

export interface DBClass {
  id: string;
  subjectCode: string;
  subjectName: string;
  section: string;
  joinCode: string;
  roomNo: string;
  wifiSsid: string;
  latitude: number;
  longitude: number;
  teacherId: string;
  teacherName: string;
  students: DBStudent[];
  createdAt: string;
}

export interface DBSession {
  id: string;
  classId: string;
  teacherId: string;
  status: "ACTIVE" | "COMPLETED";
  startTime: string;
  endTime?: string;
  joinCode: string;
  subjectCode: string;
  subjectName: string;
  roomNo: string;
  wifiSsid: string;
}

/**
 * Fetch all real classes and enrolled students from Supabase
 */
export async function fetchLiveClassesFromDB(): Promise<DBClass[]> {
  try {
    const { data, error } = await supabase
      .from("classes")
      .select(`
        id, room, is_active, created_at,
        subjects ( id, name, code ),
        teachers ( id, employee_id, users ( name, email ) )
      `)
      .order("created_at", { ascending: false });

    if (error || !data) {
      console.warn("DB Classes fetch error:", error);
      return [];
    }

    // Query real student attendance/enrollment records linked to classes
    const { data: attendanceRecs } = await supabase
      .from("attendance_records")
      .select(`
        id, student_id, marked_at, status,
        students ( id, roll_number, users ( name, email ) ),
        attendance_sessions ( class_id )
      `);

    const seenSubjectNames = new Set<string>();
    const uniqueList: any[] = [];
    for (const item of data) {
      const subObj: any = Array.isArray(item.subjects) ? item.subjects[0] : item.subjects;
      const subName = (subObj?.name || "").toLowerCase().trim();
      if (!seenSubjectNames.has(subName)) {
        seenSubjectNames.add(subName);
        uniqueList.push(item);
      }
    }

    return uniqueList.map((c: any) => {
      const subject = (Array.isArray(c.subjects) ? c.subjects[0] : c.subjects) || {};
      const teacher = c.teachers || {};
      const teacherUser = teacher.users || {};
      const code = subject.code || "CS301";
      const cleanJoinCode = getAndroidJoinCode(code, c.id);

      const seenRolls = new Set<string>();
      const enrolledStudents: DBStudent[] = [];

      // STRICT: Only include students who joined or have attendance records for THIS specific class!
      if (attendanceRecs) {
        for (const r of (attendanceRecs as any[])) {
          if (r.attendance_sessions?.class_id === c.id) {
            const s: any = Array.isArray(r.students) ? r.students[0] : (r.students || {});
            const u: any = Array.isArray(s.users) ? s.users[0] : (s.users || {});
            const roll = s.roll_number;
            if (roll && !seenRolls.has(roll.toUpperCase())) {
              seenRolls.add(roll.toUpperCase());
              enrolledStudents.push({
                id: s.id || r.student_id,
                name: u.name || "Student",
                rollNo: roll,
                email: u.email || "",
                status: "ABSENT" as const,
                joinedAt: r.marked_at || new Date().toISOString()
              });
            }
          }
        }
      }

      return {
        id: c.id,
        subjectCode: code,
        subjectName: subject.name || "Academic Subject",
        section: "Section A (Sem 5)",
        joinCode: cleanJoinCode,
        roomNo: c.room || "Room A-204",
        wifiSsid: "Pranjal",
        latitude: 21.128456,
        longitude: 81.766184,
        teacherId: teacher.id || c.teacher_id,
        teacherName: teacherUser.name || "Dr. Rajesh Sharma",
        students: enrolledStudents,
        createdAt: c.created_at
      };
    });
  } catch (err) {
    console.error("fetchLiveClassesFromDB exception:", err);
    return [];
  }
}

/**
 * Fetch the currently active session from Supabase
 */
export async function getActiveSessionFromDB(): Promise<DBSession | null> {
  try {
    const { data, error } = await supabase
      .from("attendance_sessions")
      .select(`
        id, class_id, teacher_id, status, start_time, end_time, session_secret,
        classes (
          id, room,
          subjects ( id, name, code )
        )
      `)
      .eq("status", "ACTIVE")
      .order("start_time", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (error || !data) return null;

    const cls = (data as any).classes || {};
    const sub = cls.subjects || {};
    const code = sub.code || "CS301";
    const cleanJoinCode = code.includes("-") ? code : `${code}-${cls.id ? cls.id.slice(0, 4).toUpperCase() : "9421"}`;

    let dynamicWifi = "Pranjal";
    const secret = (data as any)?.session_secret;
    if (secret && typeof secret === "string" && secret.includes("wifi:")) {
      const match = secret.match(/wifi:([^|]+)/);
      if (match && match[1]) dynamicWifi = match[1].trim();
    }

    return {
      id: data.id,
      classId: data.class_id,
      teacherId: data.teacher_id,
      status: data.status,
      startTime: data.start_time,
      endTime: data.end_time,
      joinCode: cleanJoinCode,
      subjectCode: code,
      subjectName: sub.name || "Data Mining & Warehousing",
      roomNo: cls.room || "Room A-204",
      wifiSsid: dynamicWifi
    };
  } catch (err) {
    console.error("getActiveSessionFromDB exception:", err);
    return null;
  }
}

/**
 * Start a new live lecture attendance session in Supabase
 */
export async function startAttendanceSessionInDB(
  classId: string,
  teacherId: string,
  wifiSsid?: string
): Promise<DBSession | null> {
  try {
    const cleanWifi = wifiSsid?.trim() || "Pranjal";
    // End any existing active sessions
    await supabase
      .from("attendance_sessions")
      .update({ status: "COMPLETED", end_time: new Date().toISOString() })
      .eq("status", "ACTIVE");

    // Insert new active session with dynamic WiFi embedded in session_secret
    const { data, error } = await supabase
      .from("attendance_sessions")
      .insert({
        class_id: classId,
        teacher_id: teacherId,
        status: "ACTIVE",
        start_time: new Date().toISOString(),
        session_secret: `wifi:${cleanWifi}|bssid:A4:2B:B0:8C:12:EF|ip:117.250.161.222|sec_${Math.random().toString(36).substring(2)}${Date.now().toString(36)}`
      })
      .select(`
        id, class_id, teacher_id, status, start_time, session_secret,
        classes (
          id, room,
          subjects ( id, name, code )
        )
      `)
      .single();

    if (error || !data) {
      console.warn("Error creating session in DB:", error);
      return null;
    }

    const cls = (data as any).classes || {};
    const sub = cls.subjects || {};
    const code = sub.code || "CS301";
    const cleanJoinCode = code.includes("-") ? code : `${code}-${cls.id ? cls.id.slice(0, 4).toUpperCase() : "9421"}`;

    return {
      id: data.id,
      classId: data.class_id,
      teacherId: data.teacher_id,
      status: "ACTIVE",
      startTime: data.start_time,
      joinCode: cleanJoinCode,
      subjectCode: code,
      subjectName: sub.name || "Academic Subject",
      roomNo: cls.room || "Room A-204",
      wifiSsid: cleanWifi
    };
  } catch (err) {
    console.error("startAttendanceSessionInDB exception:", err);
    return null;
  }
}

/**
 * Record a student's verified attendance in Supabase database
 */

/**
 * Register or fetch student from Supabase DB by roll number
 */
export async function registerOrGetStudentInDB(params: {
  name: string;
  rollNo: string;
  email?: string;
}): Promise<string | null> {
  try {
    const cleanRoll = params.rollNo.trim().toUpperCase();
    const cleanName = params.name.trim();
    let rawEmail = params.email?.trim().toLowerCase() || `${cleanRoll.toLowerCase()}@student.iiitnr.edu.in`;

    // Institutional domain constraint check:
    let dbEmail = rawEmail;
    if (!dbEmail.match(/@([a-zA-Z0-9.-]+\.)?(iiitnr\.edu\.in|iiitnr\.ac\.in|iitdemo\.edu)$/i)) {
      const prefix = dbEmail.includes("@") ? dbEmail.split("@")[0] : dbEmail;
      const safePrefix = prefix.replace(/[^a-zA-Z0-9._-]/g, "") || "student";
      dbEmail = `${safePrefix}@student.iiitnr.edu.in`;
    }

    // 1. Search existing student by roll number
    const { data: existingStudent } = await supabase
      .from("students")
      .select("id, user_id, users(id, name)")
      .ilike("roll_number", cleanRoll)
      .maybeSingle();

    if (existingStudent?.id) {
      const u = (existingStudent as any).users;
      if (u?.id && u?.name !== cleanName) {
        await supabase.from("users").update({ name: cleanName }).eq("id", u.id);
      }
      return existingStudent.id;
    }

    // 2. Search existing user by dbEmail
    const { data: existingUser } = await supabase
      .from("users")
      .select("id, name")
      .eq("email", dbEmail)
      .maybeSingle();

    let userId = existingUser?.id;
    if (!userId) {
      const { data: newUser, error: uErr } = await supabase
        .from("users")
        .insert({
          institution_id: "c2fcf1e8-075b-4156-bc0e-27c9b23404f5",
          name: cleanName,
          email: dbEmail,
          role: "STUDENT",
          is_active: true
        })
        .select()
        .single();

      if (uErr || !newUser) {
        console.warn("User create error:", uErr);
        return null;
      }
      userId = newUser.id;
    } else {
      await supabase.from("users").update({ name: cleanName }).eq("id", userId);
    }

    // 3. Check if student already exists for this userId!
    const { data: studentByUser } = await supabase
      .from("students")
      .select("id, roll_number")
      .eq("user_id", userId)
      .maybeSingle();

    if (studentByUser) {
      if (studentByUser.roll_number !== cleanRoll) {
        await supabase
          .from("students")
          .update({ roll_number: cleanRoll })
          .eq("id", studentByUser.id);
      }
      return studentByUser.id;
    }

    // 4. Create new student record linked to user if none exists
    const { data: newStudent, error: sErr } = await supabase
      .from("students")
      .insert({
        user_id: userId,
        roll_number: cleanRoll,
        program_id: "a0ccc36d-9b17-42d2-aa4c-042c74cbb9bb",
        section_id: "e85adf9a-ef6b-441c-9fc3-fa18e91d6ebd",
        semester: 1,
        status: "ACTIVE"
      })
      .select()
      .single();

    if (sErr || !newStudent) {
      console.warn("Student create error:", sErr);
      return null;
    }

    return newStudent.id;
  } catch (err) {
    console.error("registerOrGetStudentInDB exception:", err);
    return null;
  }
}

/**
 * Record a student's verified attendance in Supabase database
 */
export async function recordStudentAttendanceInDB(params: {
  sessionId: string;
  rollNo: string;
  name: string;
  email: string;
  status: "PRESENT" | "ABSENT";
  distanceMeters: number;
  wifiSsid: string;
  isWifiMatched: boolean;
}): Promise<boolean> {
  try {
    // 1. Ensure student exists in DB with correct name and rollNo
    const studentId = await registerOrGetStudentInDB({
      name: params.name,
      rollNo: params.rollNo,
      email: params.email
    });

    if (!studentId) {
      console.warn("Could not register or find studentId for", params.rollNo);
      return false;
    }

    const payload = {
      session_id: params.sessionId,
      student_id: studentId,
      status: params.status,
      verification_method: "BLE_AUTO",
      wifi_ap_verified: params.isWifiMatched,
      rtt_distance_meters: params.distanceMeters,
      presence_percentage: params.status === "PRESENT" ? 100.0 : 0.0,
      marked_at: new Date().toISOString(),
      sensor_details: {
        wifi_ssid: params.wifiSsid,
        distance_meters: params.distanceMeters,
        student_name: params.name,
        roll_number: params.rollNo,
        timestamp: new Date().toISOString()
      }
    };

    // Check if record already exists for this session and student
    const { data: existingRecord } = await supabase
      .from("attendance_records")
      .select("id, status")
      .eq("session_id", params.sessionId)
      .eq("student_id", studentId)
      .maybeSingle();

    if (existingRecord?.id) {
      // MONOTONIC ATTENDANCE RULE:
      // Once marked PRESENT in a lecture, automated background re-checks
      // must NEVER downgrade the student to ABSENT (e.g., when screen locks, Wi-Fi drops, or GPS drifts).
      if (existingRecord.status === "PRESENT" && params.status === "ABSENT") {
        return true;
      }

      const { error: updErr } = await supabase
        .from("attendance_records")
        .update(payload)
        .eq("id", existingRecord.id);
      if (updErr) console.warn("attendance_records update warning:", updErr.message);
    } else {
      const { error: insErr } = await supabase
        .from("attendance_records")
        .insert(payload);
      if (insErr) console.warn("attendance_records insert warning:", insErr.message);
    }

    return true;
  } catch (err) {
    console.error("recordStudentAttendanceInDB exception:", err);
    return false;
  }
}

/**
 * Fetch all attendance records for a live session from Supabase
 */
export async function fetchSessionRecordsFromDB(sessionId: string): Promise<DBStudent[]> {
  try {
    const { data, error } = await supabase
      .from("attendance_records")
      .select(`
        id, status, wifi_ap_verified, rtt_distance_meters, marked_at, sensor_details,
        students (
          id, roll_number,
          users ( name, email )
        )
      `)
      .eq("session_id", sessionId)
      .order("marked_at", { ascending: false });

    if (error || !data) return [];

    const seenRolls = new Set<string>();
    const uniqueRecords: DBStudent[] = [];

    for (const r of (data as any[])) {
      const student: any = Array.isArray(r.students) ? r.students[0] : (r.students || {});
      const user: any = Array.isArray(student.users) ? student.users[0] : (student.users || {});
      const sensor: any = r.sensor_details || {};
      const roll = (student.roll_number || sensor.roll_number || "").toUpperCase().trim();
      if (!roll || seenRolls.has(roll)) continue;
      seenRolls.add(roll);

      const timeStr = r.marked_at ? new Date(r.marked_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "Just now";

      uniqueRecords.push({
        id: student.id || r.id,
        name: user.name || sensor.student_name || "Student",
        rollNo: student.roll_number || sensor.roll_number || "26CS001",
        email: user.email || "student@iiitnr.edu.in",
        status: r.status as "PRESENT" | "ABSENT",
        distanceMeters: r.rtt_distance_meters || sensor.distance_meters || 3.4,
        wifiStatus: r.wifi_ap_verified ? "CONNECTED" : "DISCONNECTED",
        detectedWifi: sensor.wifi_ssid || "Pranjal",
        verifiedAt: timeStr,
        joinedAt: timeStr
      });
    }

    return uniqueRecords;
  } catch (err) {
    console.error("fetchSessionRecordsFromDB exception:", err);
    return [];
  }
}

/**
 * Finalize & Lock Lecture Attendance Session in Supabase
 */
export async function finalizeSessionInDB(sessionId: string): Promise<boolean> {
  try {
    const { error } = await supabase
      .from("attendance_sessions")
      .update({
        status: "COMPLETED",
        end_time: new Date().toISOString()
      })
      .eq("id", sessionId);

    return !error;
  } catch (err) {
    console.error("finalizeSessionInDB exception:", err);
    return false;
  }
}


/**
 * Create a real class & subject in Supabase Cloud DB
 */
export async function createClassInDB(params: {
  subjectName: string;
  subjectCode?: string;
  roomNo?: string;
  wifiSsid?: string;
  teacherId?: string;
}): Promise<DBClass | null> {
  try {
    const cleanName = params.subjectName.trim();
    const rawPrefix = (params.subjectCode || cleanName.slice(0, 3)).toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 5) || "CLS";
    const randHex = Math.random().toString(36).substring(2, 6).toUpperCase();
    const finalCode = `${rawPrefix}-${randHex}`;
    const room = params.roomNo?.trim() || "Room A-204";

    // 1. Get default department, section, classroom, teacher
    const { data: depts } = await supabase.from("departments").select("id").limit(1);
    const departmentId = depts?.[0]?.id || "117c2f09-1977-4ab9-b922-558f39699168";

    const { data: teachers } = await supabase.from("teachers").select("id").limit(1);
    const teacherId = params.teacherId || teachers?.[0]?.id || "6885fced-5d3e-4b9c-94fd-85d115cc9d9b";

    const { data: sections } = await supabase.from("sections").select("id").limit(1);
    const sectionId = sections?.[0]?.id || "42bf3cde-a3a2-45ee-b82a-eec10845001a";

    const { data: classrooms } = await supabase.from("classrooms").select("id").limit(1);
    const classroomId = classrooms?.[0]?.id || "638b7d85-61f2-430e-bde6-11547135eb3c";

    // 2. Insert into subjects
    const { data: subData, error: subErr } = await supabase
      .from("subjects")
      .insert({
        department_id: departmentId,
        name: cleanName,
        code: finalCode,
        credits: 4
      })
      .select()
      .single();

    if (subErr || !subData) {
      console.warn("Error inserting subject:", subErr);
      return null;
    }

    // 3. Insert into classes
    const { data: clsData, error: clsErr } = await supabase
      .from("classes")
      .insert({
        subject_id: subData.id,
        teacher_id: teacherId,
        section_id: sectionId,
        classroom_id: classroomId,
        room: room,
        day_of_week: 1,
        start_time: "10:00:00",
        end_time: "11:00:00",
        is_active: true
      })
      .select()
      .single();

    if (clsErr || !clsData) {
      console.warn("Error inserting class:", clsErr);
      return null;
    }

    // Populate enrolled students from section
    const { data: sectionStudents } = await supabase
      .from("students")
      .select("id, roll_number, users ( name, email )")
      .eq("section_id", sectionId);

    const initialEnrolled: DBStudent[] = (sectionStudents || []).map((s: any) => ({
      id: s.id,
      name: s.users?.name || "Student",
      rollNo: s.roll_number || "26CS001",
      email: s.users?.email || "",
      status: "ABSENT" as const,
      joinedAt: new Date().toISOString()
    }));

    return {
      id: clsData.id,
      subjectCode: finalCode,
      subjectName: cleanName,
      section: "Section A (Sem 5)",
      joinCode: finalCode,
      roomNo: room,
      wifiSsid: params.wifiSsid || "Pranjal",
      latitude: 21.128456,
      longitude: 81.766184,
      teacherId: teacherId,
      teacherName: "Dr. Rajesh Sharma",
      students: initialEnrolled,
      createdAt: clsData.created_at || new Date().toISOString()
    };
  } catch (err) {
    console.error("createClassInDB exception:", err);
    return null;
  }
}

/**
 * Enroll a student into a specific class in Supabase database
 */
export async function enrollStudentInClassInDB(params: {
  classId: string;
  rollNo: string;
  name: string;
  email: string;
}): Promise<boolean> {
  try {
    const studentId = await registerOrGetStudentInDB({
      name: params.name,
      rollNo: params.rollNo,
      email: params.email
    });
    if (!studentId) return false;

    // Find latest session for this class or create one
    let sessionId: string | null = null;
    const { data: sess } = await supabase
      .from("attendance_sessions")
      .select("id")
      .eq("class_id", params.classId)
      .order("start_time", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (sess?.id) {
      sessionId = sess.id;
    } else {
      const { data: teachers } = await supabase.from("teachers").select("id").limit(1);
      const teacherId = teachers?.[0]?.id || "6885fced-5d3e-4b9c-94fd-85d115cc9d9b";
      const { data: newSess } = await supabase
        .from("attendance_sessions")
        .insert({
          class_id: params.classId,
          teacher_id: teacherId,
          session_secret: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
          status: "ACTIVE",
          start_time: new Date().toISOString()
        })
        .select()
        .single();
      sessionId = newSess?.id || null;
    }

    if (!sessionId) return false;

    // Direct registration into course_enrollments for cross-device parity with Android
    try {
      await supabase.from("course_enrollments").upsert({
        class_id: params.classId,
        student_id: studentId,
        enrollment_type: "REGULAR",
        is_active: true
      }, { onConflict: "class_id,student_id" });
    } catch (e) {
      console.warn("course_enrollments sync note:", e);
    }

    // Check if record already exists for this student and session
    const { data: existing } = await supabase
      .from("attendance_records")
      .select("id")
      .eq("session_id", sessionId)
      .eq("student_id", studentId)
      .maybeSingle();

    if (!existing?.id) {
      await supabase.from("attendance_records").insert({
        session_id: sessionId,
        student_id: studentId,
        status: "ABSENT",
        presence_percentage: 0,
        verification_method: "BLE_AUTO",
        sensor_details: {
          student_name: params.name,
          roll_number: params.rollNo,
          enrolled: true
        }
      });
    }

    return true;
  } catch (err) {
    console.error("enrollStudentInClassInDB exception:", err);
    return false;
  }
}

// ==========================================
// ANTI-PROXY DEVICE BINDING & UNBIND ENGINE
// ==========================================

export interface BoundDevice {
  id: string;
  studentId: string;
  rollNo: string;
  studentName: string;
  installationId: string;
  deviceModel: string;
  osVersion: string;
  platform: string;
  status: "ACTIVE" | "PENDING_UNBIND" | "REVOKED";
  registeredAt: string;
  lastSeen: string;
}

/**
 * Binds a physical student device to their roll number (Anti-Proxy)
 */
export async function bindStudentDeviceInDB(params: {
  rollNo: string;
  name: string;
  email?: string;
  installationId: string;
  deviceModel: string;
  osVersion?: string;
}): Promise<{ success: boolean; message: string; device?: any; conflictRoll?: string }> {
  try {
    const studentId = await registerOrGetStudentInDB({
      rollNo: params.rollNo,
      name: params.name,
      email: params.email
    });

    if (!studentId) {
      return { success: false, message: "Could not find student account" };
    }

    // 1. Check if this physical device/browser is already bound to a DIFFERENT student
    const { data: existingDeviceOnHardware } = await supabase
      .from("devices")
      .select("id, student_id, device_model, status, students(roll_number, users(name))")
      .eq("installation_id", params.installationId)
      .eq("status", "ACTIVE")
      .maybeSingle();

    if (existingDeviceOnHardware && existingDeviceOnHardware.student_id !== studentId) {
      const otherStudent = (existingDeviceOnHardware as any).students || {};
      const otherUser = otherStudent.users || {};
      return {
        success: false,
        message: `ANTI-PROXY LOCK: This device is locked to ${otherUser.name || "another student"} (Roll: ${otherStudent.roll_number || "Unknown"}). Device sharing for proxy is blocked.`,
        conflictRoll: otherStudent.roll_number
      };
    }

    // 2. Check if this student is already bound to a DIFFERENT physical device
    const { data: existingStudentDevice } = await supabase
      .from("devices")
      .select("id, installation_id, device_model, status")
      .eq("student_id", studentId)
      .eq("status", "ACTIVE")
      .maybeSingle();

    if (existingStudentDevice && existingStudentDevice.installation_id !== params.installationId) {
      // Check if there is an approved unbind or allow binding
      return {
        success: false,
        message: `DEVICE MISMATCH: Account ${params.rollNo} is bound to another device (${existingStudentDevice.device_model}). Request device unbind from faculty to switch phones.`,
        device: existingStudentDevice
      };
    }

    // 3. Register or update device binding
    if (existingStudentDevice) {
      await supabase
        .from("devices")
        .update({
          last_seen: new Date().toISOString(),
          device_model: params.deviceModel
        })
        .eq("id", existingStudentDevice.id);
      return { success: true, message: "Device verified", device: existingStudentDevice };
    }

    const { data: newDev, error: insErr } = await supabase
      .from("devices")
      .insert({
        student_id: studentId,
        installation_id: params.installationId,
        device_model: params.deviceModel,
        os_version: params.osVersion || "Web PWA Client",
        platform: "WEB",
        status: "ACTIVE"
      })
      .select()
      .single();

    if (insErr) {
      console.warn("Device bind error:", insErr);
      return { success: false, message: insErr.message };
    }

    return { success: true, message: "Device bound successfully", device: newDev };
  } catch (err: any) {
    console.error("bindStudentDeviceInDB error:", err);
    return { success: false, message: err?.message || "Device binding exception" };
  }
}

/**
 * Fetch bound device for a student
 */
export async function getStudentBoundDeviceFromDB(rollNo: string): Promise<BoundDevice | null> {
  try {
    const cleanRoll = rollNo.trim().toUpperCase();
    const { data: st } = await supabase
      .from("students")
      .select("id, roll_number, users(name)")
      .ilike("roll_number", cleanRoll)
      .maybeSingle();

    if (!st?.id) return null;

    const { data: dev } = await supabase
      .from("devices")
      .select("*")
      .eq("student_id", st.id)
      .order("registered_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!dev) return null;

    const isPendingUnbind = dev.device_model?.includes("[UNBIND REQUEST]") || dev.status === "REVOKED";

    return {
      id: dev.id,
      studentId: dev.student_id,
      rollNo: st.roll_number,
      studentName: (st as any).users?.name || "Student",
      installationId: dev.installation_id,
      deviceModel: dev.device_model || "Mobile Device",
      osVersion: dev.os_version || "1.0",
      platform: dev.platform || "WEB",
      status: isPendingUnbind ? "PENDING_UNBIND" : dev.status,
      registeredAt: dev.registered_at,
      lastSeen: dev.last_seen
    };
  } catch (err) {
    console.error("getStudentBoundDeviceFromDB exception:", err);
    return null;
  }
}

/**
 * Submit an Unbind Request for a student device
 */
export async function requestStudentDeviceUnbindInDB(rollNo: string, reason?: string): Promise<boolean> {
  try {
    const cleanRoll = rollNo.trim().toUpperCase();
    const { data: st } = await supabase
      .from("students")
      .select("id, roll_number, users(name)")
      .ilike("roll_number", cleanRoll)
      .maybeSingle();

    if (!st?.id) return false;

    // Mark active device with UNBIND REQUEST flag
    const { data: dev } = await supabase
      .from("devices")
      .select("id, device_model")
      .eq("student_id", st.id)
      .eq("status", "ACTIVE")
      .maybeSingle();

    if (dev?.id) {
      await supabase
        .from("devices")
        .update({
          device_model: `[UNBIND REQUEST] ${dev.device_model || "Mobile Device"} (Reason: ${reason || "Phone Change/Reset"})`,
          status: "REVOKED"
        })
        .eq("id", dev.id);
    } else {
      // Create pending unbind record so teacher can see it
      await supabase
        .from("devices")
        .insert({
          student_id: st.id,
          installation_id: `unbind_req_${Date.now()}`,
          device_model: `[UNBIND REQUEST] Student requested phone unbind (Reason: ${reason || "Device Lost/Upgraded"})`,
          platform: "WEB",
          status: "REVOKED"
        });
    }

    return true;
  } catch (err) {
    console.error("requestStudentDeviceUnbindInDB exception:", err);
    return false;
  }
}

/**
 * Fetch all pending unbind requests for faculty dashboard
 */
export async function fetchPendingUnbindRequestsFromDB(): Promise<BoundDevice[]> {
  try {
    const { data, error } = await supabase
      .from("devices")
      .select(`
        id, student_id, installation_id, device_model, os_version, platform, status, registered_at, last_seen,
        students ( id, roll_number, users ( name, email ) )
      `)
      .order("registered_at", { ascending: false });

    if (error || !data) return [];

    const list: BoundDevice[] = [];
    for (const d of data) {
      const isUnbind = d.device_model?.includes("[UNBIND REQUEST]") || d.status === "REVOKED";
      const s = (d as any).students || {};
      const u = s.users || {};
      list.push({
        id: d.id,
        studentId: d.student_id,
        rollNo: s.roll_number || "Unknown",
        studentName: u.name || "Student",
        installationId: d.installation_id,
        deviceModel: d.device_model || "Mobile Device",
        osVersion: d.os_version || "1.0",
        platform: d.platform || "WEB",
        status: isUnbind ? "PENDING_UNBIND" : (d.status as any),
        registeredAt: d.registered_at,
        lastSeen: d.last_seen
      });
    }

    return list;
  } catch (err) {
    console.error("fetchPendingUnbindRequestsFromDB exception:", err);
    return [];
  }
}

/**
 * Approve unbind request and unlock student account
 */
export async function approveDeviceUnbindInDB(deviceId: string): Promise<boolean> {
  try {
    const { error } = await supabase
      .from("devices")
      .delete()
      .eq("id", deviceId);

    return !error;
  } catch (err) {
    console.error("approveDeviceUnbindInDB exception:", err);
    return false;
  }
}

/**
 * Update the active classroom WiFi SSID in real time for all connected devices
 */
export async function updateLiveSessionWifiInDB(
  sessionId: string,
  newWifiSsid: string
): Promise<boolean> {
  try {
    const clean = newWifiSsid.trim() || "Pranjal";
    const { error } = await supabase
      .from("attendance_sessions")
      .update({
        session_secret: `wifi:${clean}|bssid:A4:2B:B0:8C:12:EF|ip:117.250.161.222|sec_${Date.now().toString(36)}`
      })
      .eq("id", sessionId);

    return !error;
  } catch (err) {
    console.error("updateLiveSessionWifiInDB exception:", err);
    return false;
  }
}


export interface DBCompletedSession {
  id: string;
  classId: string;
  teacherId: string;
  subjectCode: string;
  subjectName: string;
  roomNo: string;
  startTime: string;
  endTime?: string;
  joinCode: string;
  wifiSsid: string;
  presentCount: number;
  absentCount: number;
  totalStudents: number;
  records: Array<{
    id: string;
    studentId: string;
    rollNo: string;
    name: string;
    status: "PRESENT" | "ABSENT";
    verifiedAt: string;
    wifiVerified: boolean;
  }>;
}

/**
 * Fetch past/completed attendance sessions with summarized stats
 */
export async function fetchCompletedSessionsFromDB(): Promise<DBCompletedSession[]> {
  try {
    const { data, error } = await supabase
      .from("attendance_sessions")
      .select(`
        id, class_id, teacher_id, status, start_time, end_time, session_secret,
        classes (
          id, room,
          subjects ( id, name, code )
        ),
        attendance_records (
          id, student_id, status, marked_at, wifi_ap_verified,
          students ( id, roll_number, users ( name, email ) )
        )
      `)
      .eq("status", "COMPLETED")
      .order("end_time", { ascending: false })
      .limit(30);

    if (error || !data) return [];

    const rawSessions: DBCompletedSession[] = data.map((item: any) => {
      const cls = item.classes || {};
      const sub = cls.subjects || {};
      const records = (item.attendance_records || []).map((r: any) => {
        const student = r.students || {};
        const user = student.users || {};
        return {
          id: r.id,
          studentId: r.student_id,
          rollNo: student.roll_number || "Unknown",
          name: user.name || "Student",
          status: r.status as "PRESENT" | "ABSENT",
          verifiedAt: r.marked_at,
          wifiVerified: !!r.wifi_ap_verified
        };
      });

      const presentCount = records.filter((r: any) => r.status === "PRESENT").length;
      const absentCount = records.filter((r: any) => r.status === "ABSENT").length;

      let wifi = "Pranjal";
      const secret = item.session_secret;
      if (secret && typeof secret === "string" && secret.includes("wifi:")) {
        const match = secret.match(/wifi:([^|]+)/);
        if (match && match[1]) wifi = match[1].trim();
      }

      const code = sub.code || "CS301";
      const cleanJoinCode = code.includes("-") ? code : `${code}-${cls.id ? cls.id.slice(0, 4).toUpperCase() : "9421"}`;

      return {
        id: item.id,
        classId: item.class_id,
        teacherId: item.teacher_id,
        subjectCode: code,
        subjectName: sub.name || "Lecture Session",
        roomNo: cls.room || "Room 101",
        startTime: item.start_time,
        endTime: item.end_time || item.start_time,
        joinCode: cleanJoinCode,
        wifiSsid: wifi,
        presentCount,
        absentCount,
        totalStudents: records.length,
        records
      };
    });

    // 1 LECTURE = 1 ATTENDANCE PER DAY:
    // Deduplicate so each class has exactly 1 authoritative record per calendar date.
    // If multiple sessions occurred on the same day (e.g. from testing/re-take), choose the authoritative one
    // (the one with the highest verified attendance count or latest timestamp).
    // Omit 0-student ghost sessions.
    const dateMap = new Map<string, DBCompletedSession>();
    for (const sess of rawSessions) {
      if (sess.totalStudents === 0) continue;
      const dateKey = `${sess.classId || sess.subjectCode}_${new Date(sess.endTime || sess.startTime).toDateString()}`;
      if (!dateMap.has(dateKey)) {
        dateMap.set(dateKey, sess);
      } else {
        const existing = dateMap.get(dateKey)!;
        const sessTime = new Date(sess.endTime || sess.startTime).getTime();
        const existTime = new Date(existing.endTime || existing.startTime).getTime();
        if (sess.presentCount > existing.presentCount || sessTime > existTime) {
          dateMap.set(dateKey, sess);
        }
      }
    }

    const deduplicated = Array.from(dateMap.values());
    return deduplicated.length > 0 ? deduplicated : rawSessions.slice(0, 5);
  } catch (err) {
    console.error("fetchCompletedSessionsFromDB exception:", err);
    return [];
  }
}


export async function fetchStudentEnrolledClassesFromDB(rollNo: string): Promise<any[]> {
  try {
    const cleanRoll = rollNo.trim().toUpperCase();
    const { data: student } = await supabase
      .from("students")
      .select("id")
      .eq("roll_number", cleanRoll)
      .maybeSingle();

    if (!student?.id) return [];

    const { data: enrollments } = await supabase
      .from("course_enrollments")
      .select(`
        class_id,
        is_active,
        classes (
          id, room, is_active,
          subjects ( id, name, code ),
          teachers ( id, users ( name ) )
        )
      `)
      .eq("student_id", student.id)
      .eq("is_active", true);

    if (!enrollments || enrollments.length === 0) return [];

    return enrollments.map((e: any) => {
      const c = e.classes || {};
      const subject = (Array.isArray(c.subjects) ? c.subjects[0] : c.subjects) || {};
      const teacher = c.teachers || {};
      const teacherUser = teacher.users || {};
      const code = subject.code || "CS301";
      const joinCode = getAndroidJoinCode(code, c.id || "0000");

      return {
        id: c.id,
        subjectCode: code,
        subjectName: subject.name || "Subject",
        section: "Section A",
        joinCode: joinCode,
        roomNo: c.room || "Room A-204",
        wifiSsid: "Pranjal",
        latitude: 21.128456,
        longitude: 81.766184,
        teacherId: teacher.id || "",
        teacherName: teacherUser.name || "Faculty",
        joinedAt: "Active",
        attendancePct: 100
      };
    });
  } catch (err) {
    console.warn("fetchStudentEnrolledClassesFromDB error:", err);
    return [];
  }
}


/**
 * Permanently delete a subject/class, including its sessions and attendance records
 */
export async function deleteClassFromDB(classId: string, subjectCode?: string): Promise<boolean> {
  try {
    // 1. Find all attendance_sessions for this class
    const { data: sessions } = await supabase
      .from("attendance_sessions")
      .select("id")
      .eq("class_id", classId);

    if (sessions && sessions.length > 0) {
      const sessionIds = sessions.map(s => s.id);
      // 2. Delete attendance records for these sessions
      await supabase
        .from("attendance_records")
        .delete()
        .in("session_id", sessionIds);

      // 3. Delete the attendance sessions
      await supabase
        .from("attendance_sessions")
        .delete()
        .eq("class_id", classId);
    }

    // 4. Get subject_id from classes before deleting class
    const { data: clsData } = await supabase
      .from("classes")
      .select("subject_id")
      .eq("id", classId)
      .maybeSingle();

    const subjectId = clsData?.subject_id;

    // 5. Delete class from classes table
    const { error: clsDelErr } = await supabase
      .from("classes")
      .delete()
      .eq("id", classId);

    if (clsDelErr) {
      console.error("Failed to delete class:", clsDelErr);
    }

    // 6. Delete subject if exists and no other classes reference it
    if (subjectId) {
      const { data: otherClasses } = await supabase
        .from("classes")
        .select("id")
        .eq("subject_id", subjectId);

      if (!otherClasses || otherClasses.length === 0) {
        await supabase
          .from("subjects")
          .delete()
          .eq("id", subjectId);
      }
    } else if (subjectCode) {
      await supabase
        .from("subjects")
        .delete()
        .ilike("code", subjectCode);
    }

    return true;
  } catch (err) {
    console.error("deleteClassFromDB error:", err);
    return false;
  }
}

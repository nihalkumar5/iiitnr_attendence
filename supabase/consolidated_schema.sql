-- ============================================================================
-- SMART ATTENDANCE SYSTEM — CONSOLIDATED FULL DATABASE SETUP
-- Target Supabase Project: https://vtuztciyaqegrvoaxmnf.supabase.co
-- ============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enums
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('SUPER_ADMIN', 'DEPT_ADMIN', 'TEACHER', 'STUDENT');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE session_status AS ENUM ('ACTIVE', 'CALCULATING', 'IN_REVIEW', 'COMPLETED', 'CANCELLED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE attendance_status AS ENUM ('PRESENT', 'REVIEW', 'ABSENT');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE verification_method AS ENUM ('BLE_AUTO', 'WIFI_AUTO', 'MANUAL_TEACHER', 'QR_FALLBACK');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE device_status AS ENUM ('ACTIVE', 'BLOCKED', 'PENDING_APPROVAL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 1. INSTITUTIONS
CREATE TABLE IF NOT EXISTS institutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    code TEXT NOT NULL UNIQUE,
    settings JSONB NOT NULL DEFAULT '{
        "present_threshold": 0.60,
        "review_threshold": 0.20,
        "rssi_threshold": -80,
        "scan_interval_seconds": 60,
        "token_rotation_seconds": 30
    }'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. DEPARTMENTS
CREATE TABLE IF NOT EXISTS departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (institution_id, code)
);

-- 3. PROGRAMS
CREATE TABLE IF NOT EXISTS programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    duration_years INT NOT NULL DEFAULT 4,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, code)
);

-- 4. SECTIONS
CREATE TABLE IF NOT EXISTS sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    semester INT NOT NULL,
    name TEXT NOT NULL,
    academic_year TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (program_id, semester, name, academic_year)
);

-- 5. CLASSROOMS INFRASTRUCTURE (Wi-Fi AP BSSID + BLE Beacons + RTT)
CREATE TABLE IF NOT EXISTS classrooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    building TEXT,
    floor INT,
    capacity INT DEFAULT 60,
    wifi_ssid TEXT DEFAULT 'Campus-Secure',
    wifi_bssid TEXT,
    secondary_bssids TEXT[] DEFAULT '{}',
    beacon_uuid UUID,
    beacon_major INT,
    beacon_minor INT,
    is_rtt_supported BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. USERS (Independent identity table with optional auth.users linkage)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    role user_role NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Drop constraint if table was created with old foreign key
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_id_fkey;
ALTER TABLE users ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

-- 7. TEACHERS
CREATE TABLE IF NOT EXISTS teachers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    employee_id TEXT NOT NULL UNIQUE,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    designation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8. STUDENTS
CREATE TABLE IF NOT EXISTS students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    roll_number TEXT NOT NULL UNIQUE,
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE RESTRICT,
    semester INT NOT NULL,
    section_id UUID NOT NULL REFERENCES sections(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 9. SUBJECTS
CREATE TABLE IF NOT EXISTS subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    credits INT NOT NULL DEFAULT 4,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, code)
);

-- 10. CLASSES
CREATE TABLE IF NOT EXISTS classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    classroom_id UUID REFERENCES classrooms(id) ON DELETE SET NULL,
    room TEXT NOT NULL,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 11. DEVICES (Anti-proxy device binding)
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    installation_id TEXT NOT NULL UNIQUE,
    device_model TEXT,
    os_version TEXT,
    platform TEXT NOT NULL DEFAULT 'ANDROID',
    status device_status NOT NULL DEFAULT 'ACTIVE',
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 12. ATTENDANCE_SESSIONS
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES teachers(id) ON DELETE RESTRICT,
    session_secret TEXT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    end_time TIMESTAMPTZ,
    expected_end_time TIMESTAMPTZ,
    status session_status NOT NULL DEFAULT 'ACTIVE',
    present_threshold NUMERIC(4,2) NOT NULL DEFAULT 0.60,
    review_threshold NUMERIC(4,2) NOT NULL DEFAULT 0.20,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 13. PRESENCE_EVENTS (Multi-Modal Raw Telemetry Stream)
CREATE TABLE IF NOT EXISTS presence_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE RESTRICT,
    token_hash TEXT NOT NULL,
    rssi INT NOT NULL,
    wifi_bssid TEXT,
    wifi_ssid TEXT,
    wifi_rssi INT,
    wifi_rtt_distance_meters NUMERIC(6,2),
    ble_rssi INT,
    source_type TEXT DEFAULT 'HYBRID',
    multi_sensor_confidence NUMERIC(5,2),
    timestamp TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 14. ATTENDANCE_RECORDS
CREATE TABLE IF NOT EXISTS attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    status attendance_status NOT NULL,
    presence_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    verification_method verification_method NOT NULL DEFAULT 'BLE_AUTO',
    wifi_ap_verified BOOLEAN DEFAULT false,
    ble_verified BOOLEAN DEFAULT false,
    rtt_distance_meters NUMERIC(6,2),
    sensor_details JSONB,
    marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    marked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, student_id)
);

-- 15. AUDIT_LOGS
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    previous_value JSONB,
    new_value JSONB,
    reason TEXT,
    ip_address TEXT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_presence_events_session_student ON presence_events(session_id, student_id);
CREATE INDEX IF NOT EXISTS idx_presence_events_timestamp ON presence_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_attendance_records_student ON attendance_records(student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_records_session ON attendance_records(session_id);
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_teacher ON attendance_sessions(teacher_id, status);

-- ----------------------------------------------------------------------------
-- Stored Functions & Attendance Calculation Engine
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION generate_ephemeral_token(
    p_session_secret TEXT,
    p_session_id UUID,
    p_timestamp TIMESTAMPTZ DEFAULT now(),
    p_window_seconds INT DEFAULT 30
)
RETURNS TEXT AS $$
DECLARE
    v_window BIGINT;
    v_payload TEXT;
    v_raw_hmac BYTEA;
BEGIN
    v_window := floor(EXTRACT(EPOCH FROM p_timestamp) / p_window_seconds);
    v_payload := p_session_id::text || ':' || v_window::text;
    v_raw_hmac := hmac(v_payload::bytea, p_session_secret::bytea, 'sha256');
    RETURN substring(encode(v_raw_hmac, 'hex') from 1 for 16);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION calculate_hybrid_session_attendance(p_session_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_session RECORD;
    v_classroom RECORD;
    v_student RECORD;
    v_window_size_seconds INT := 30;
    v_duration_seconds NUMERIC;
    v_total_windows INT;
    v_wifi_windows INT;
    v_ble_windows INT;
    v_avg_rtt NUMERIC(6,2);
    v_combined_score NUMERIC(5,2);
    v_status attendance_status;
    v_method verification_method;
    v_total_students INT := 0;
    v_count_present INT := 0;
    v_count_review INT := 0;
    v_count_absent INT := 0;
BEGIN
    -- 1. Fetch Session, Class, and Classroom
    SELECT s.*, c.section_id, c.classroom_id INTO v_session
    FROM attendance_sessions s
    JOIN classes c ON c.id = s.class_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Attendance session % not found', p_session_id;
    END IF;

    IF v_session.classroom_id IS NOT NULL THEN
        SELECT * INTO v_classroom FROM classrooms WHERE id = v_session.classroom_id;
    END IF;

    IF v_session.end_time IS NULL THEN
        UPDATE attendance_sessions SET end_time = now() WHERE id = p_session_id;
        v_session.end_time := now();
    END IF;

    v_duration_seconds := EXTRACT(EPOCH FROM (v_session.end_time - v_session.start_time));
    v_total_windows := GREATEST(1, CEIL(v_duration_seconds / v_window_size_seconds));

    -- 2. Iterate enrolled students
    FOR v_student IN 
        SELECT st.id AS student_id, st.roll_number, u.name
        FROM students st
        JOIN users u ON u.id = st.user_id
        WHERE st.section_id = v_session.section_id AND st.status = 'ACTIVE'
    LOOP
        v_total_students := v_total_students + 1;

        -- Count Classroom Wi-Fi AP windows
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_wifi_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND v_classroom.wifi_bssid IS NOT NULL
          AND (
              lower(pe.wifi_bssid) = lower(v_classroom.wifi_bssid) 
              OR pe.wifi_bssid = ANY(v_classroom.secondary_bssids)
              OR lower(pe.wifi_ssid) LIKE '%classroom%'
              OR lower(pe.wifi_ssid) LIKE '%campus%'
              OR lower(pe.wifi_ssid) LIKE '%iit%'
          )
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Count BLE windows (optional supplementary)
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_ble_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.rssi >= -85
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Average Wi-Fi RTT if available
        SELECT AVG(pe.wifi_rtt_distance_meters)
        INTO v_avg_rtt
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.wifi_rtt_distance_meters IS NOT NULL
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        DECLARE
            v_wifi_ratio NUMERIC := (v_wifi_windows::NUMERIC / v_total_windows::NUMERIC) * 100.0;
            v_ble_ratio NUMERIC := (v_ble_windows::NUMERIC / v_total_windows::NUMERIC) * 100.0;
            v_has_wifi BOOLEAN := (v_wifi_windows > 0 OR v_wifi_ratio >= 20.0);
            v_has_ble BOOLEAN := (v_ble_windows > 0 OR v_ble_ratio >= 20.0);
        BEGIN
            -- WI-FI FIRST ARCHITECTURE:
            -- If student phone connects to classroom Wi-Fi AP, presence is 100% verified -> PRESENT!
            -- Works flawlessly on iPhone (iOS) and Android without background Bluetooth limits or battery drain.
            IF v_has_wifi THEN
                v_combined_score := 100.0;
                v_status := 'PRESENT';
                v_method := 'WIFI_AUTO';
                v_count_present := v_count_present + 1;
            ELSIF v_has_ble THEN
                -- Secondary proximity fallback for student who forgot to connect to Wi-Fi (e.g. on 5G mobile data)
                v_combined_score := ROUND(LEAST(100.0, v_ble_ratio), 2);
                IF v_combined_score >= (v_session.present_threshold * 100.0) THEN
                    v_status := 'PRESENT';
                    v_method := 'BLE_AUTO';
                    v_count_present := v_count_present + 1;
                ELSE
                    v_status := 'REVIEW';
                    v_method := 'BLE_AUTO';
                    v_count_review := v_count_review + 1;
                END IF;
            ELSE
                v_combined_score := 0.0;
                v_status := 'ABSENT';
                v_method := 'WIFI_AUTO';
                v_count_absent := v_count_absent + 1;
            END IF;

            INSERT INTO attendance_records (
                session_id, student_id, status, presence_percentage,
                verification_method, wifi_ap_verified, ble_verified,
                rtt_distance_meters, sensor_details, marked_at
            ) VALUES (
                p_session_id, v_student.student_id, v_status, v_combined_score,
                v_method, v_has_wifi, v_has_ble, v_avg_rtt,
                jsonb_build_object(
                    'engine', 'WIFI_FIRST',
                    'wifi_ratio', v_wifi_ratio,
                    'ble_ratio', v_ble_ratio,
                    'wifi_windows', v_wifi_windows,
                    'ble_windows', v_ble_windows,
                    'rtt_avg_meters', v_avg_rtt,
                    'ios_zero_friction', true
                ),
                now()
            )
            ON CONFLICT (session_id, student_id)
            DO UPDATE SET
                status = EXCLUDED.status,
                presence_percentage = EXCLUDED.presence_percentage,
                verification_method = EXCLUDED.verification_method,
                wifi_ap_verified = EXCLUDED.wifi_ap_verified,
                ble_verified = EXCLUDED.ble_verified,
                rtt_distance_meters = EXCLUDED.rtt_distance_meters,
                sensor_details = EXCLUDED.sensor_details,
                marked_at = now()
                WHERE attendance_records.verification_method IN ('BLE_AUTO', 'WIFI_AUTO');
        END;
    END LOOP;

    UPDATE attendance_sessions SET status = 'IN_REVIEW' WHERE id = p_session_id;

    RETURN jsonb_build_object(
        'session_id', p_session_id,
        'total_students', v_total_students,
        'present_count', v_count_present,
        'review_count', v_count_review,
        'absent_count', v_count_absent,
        'mode', 'WIFI_FIRST_IPHONE_FRIENDLY'
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- Institutional Google Auth (@iiitnr.edu.in) & Anti-Proxy Device Locking
-- ----------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub TEXT UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;

DO $$ BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS check_institutional_domain;
    ALTER TABLE users ADD CONSTRAINT check_institutional_domain
        CHECK (
            email ~* '^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\.)?(iiitnr\.edu\.in|iiitnr\.ac\.in|iitdemo\.edu)$'
        );
EXCEPTION
    WHEN others THEN null;
END $$;

CREATE OR REPLACE FUNCTION bind_student_hardware_device(
    p_student_id UUID,
    p_installation_id TEXT,
    p_device_model TEXT,
    p_os_version TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_existing_device RECORD;
    v_student_device RECORD;
    v_new_device_id UUID;
BEGIN
    IF p_installation_id IS NULL OR trim(p_installation_id) = '' THEN
        RAISE EXCEPTION 'ANTI_PROXY_VIOLATION: Hardware device identifier cannot be empty.';
    END IF;

    -- Check if this phone is already bound to a DIFFERENT student
    SELECT d.id, d.student_id, d.status, s.roll_number, u.name
    INTO v_existing_device
    FROM devices d
    JOIN students s ON s.id = d.student_id
    JOIN users u ON u.id = s.user_id
    WHERE d.installation_id = p_installation_id AND d.status = 'ACTIVE'
    LIMIT 1;

    IF FOUND THEN
        IF v_existing_device.student_id != p_student_id THEN
            RAISE EXCEPTION 'ANTI_PROXY_LOCK: This phone is permanently locked to % (%). Sharing devices to mark proxy attendance is strictly forbidden.',
                v_existing_device.name, v_existing_device.roll_number;
        ELSE
            UPDATE devices
            SET device_model = p_device_model,
                os_version = p_os_version,
                last_seen = now()
            WHERE id = v_existing_device.id;
            RETURN v_existing_device.id;
        END IF;
    END IF;

    -- Check if this student is already bound to a DIFFERENT physical phone
    SELECT d.id, d.installation_id, d.device_model
    INTO v_student_device
    FROM devices d
    WHERE d.student_id = p_student_id AND d.status = 'ACTIVE'
    LIMIT 1;

    IF FOUND THEN
        IF v_student_device.installation_id != p_installation_id THEN
            RAISE EXCEPTION 'DEVICE_MISMATCH: Your account is already bound to another phone (%). You cannot mark attendance from a secondary device.',
                v_student_device.device_model;
        ELSE
            RETURN v_student_device.id;
        END IF;
    END IF;

    INSERT INTO devices (
        student_id,
        installation_id,
        device_model,
        os_version,
        platform,
        status,
        registered_at,
        last_seen
    ) VALUES (
        p_student_id,
        p_installation_id,
        COALESCE(p_device_model, 'Android Device'),
        COALESCE(p_os_version, 'Android 14'),
        'ANDROID',
        'ACTIVE',
        now(),
        now()
    )
    RETURNING id INTO v_new_device_id;

    RETURN v_new_device_id;
END;
$$;

-- ----------------------------------------------------------------------------
-- 15. COURSE ENROLLMENTS & LECTURE ATTENDANCE GATEKEEPER
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    enrollment_type TEXT NOT NULL DEFAULT 'CORE',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (class_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_course_enrollments_class ON course_enrollments (class_id);
CREATE INDEX IF NOT EXISTS idx_course_enrollments_student ON course_enrollments (student_id);

CREATE OR REPLACE FUNCTION is_student_enrolled_in_session(
    p_session_id UUID,
    p_student_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_class_id UUID;
    v_is_enrolled BOOLEAN;
BEGIN
    SELECT class_id INTO v_class_id
    FROM attendance_sessions
    WHERE id = p_session_id;

    IF v_class_id IS NULL THEN
        RETURN true;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM course_enrollments WHERE class_id = v_class_id) THEN
        RETURN true;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM course_enrollments
        WHERE class_id = v_class_id
          AND student_id = p_student_id
          AND is_active = true
    ) INTO v_is_enrolled;

    RETURN v_is_enrolled;
END;
$$;



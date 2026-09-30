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
    CREATE TYPE verification_method AS ENUM ('BLE_AUTO', 'MANUAL_TEACHER', 'QR_FALLBACK');
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
RETURNS JSONB AS $$
DECLARE
    v_session RECORD;
    v_classroom RECORD;
    v_duration_seconds NUMERIC;
    v_window_size_seconds NUMERIC := 180;
    v_total_windows INT;
    v_student RECORD;
    v_wifi_windows INT;
    v_ble_windows INT;
    v_avg_rtt NUMERIC(6,2);
    v_combined_score NUMERIC(5,2);
    v_status attendance_status;
    v_total_students INT := 0;
    v_count_present INT := 0;
    v_count_review INT := 0;
    v_count_absent INT := 0;
BEGIN
    SELECT s.*, c.section_id, c.classroom_id INTO v_session
    FROM attendance_sessions s
    JOIN classes c ON c.id = s.class_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Session % not found', p_session_id;
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

    FOR v_student IN 
        SELECT st.id AS student_id, st.roll_number, u.name
        FROM students st
        JOIN users u ON u.id = st.user_id
        WHERE st.section_id = v_session.section_id AND st.status = 'ACTIVE'
    LOOP
        v_total_students := v_total_students + 1;

        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_wifi_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND v_classroom.wifi_bssid IS NOT NULL
          AND (lower(pe.wifi_bssid) = lower(v_classroom.wifi_bssid) OR pe.wifi_bssid = ANY(v_classroom.secondary_bssids))
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_ble_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.rssi >= -85
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

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
            v_has_wifi BOOLEAN := (v_wifi_ratio >= 40.0);
            v_has_ble BOOLEAN := (v_ble_ratio >= 40.0);
        BEGIN
            IF v_classroom.wifi_bssid IS NOT NULL THEN
                v_combined_score := (0.50 * v_wifi_ratio) + (0.50 * v_ble_ratio);
                IF v_avg_rtt IS NOT NULL AND v_avg_rtt <= 10.0 THEN
                    v_combined_score := LEAST(100.0, v_combined_score + 10.0);
                END IF;
            ELSE
                v_combined_score := v_ble_ratio;
            END IF;

            v_combined_score := ROUND(LEAST(100.0, v_combined_score), 2);

            IF v_combined_score >= (v_session.present_threshold * 100.0) THEN
                v_status := 'PRESENT';
                v_count_present := v_count_present + 1;
            ELSIF v_combined_score >= (v_session.review_threshold * 100.0) OR (v_has_wifi AND NOT v_has_ble) THEN
                v_status := 'REVIEW';
                v_count_review := v_count_review + 1;
            ELSE
                v_status := 'ABSENT';
                v_count_absent := v_count_absent + 1;
            END IF;

            INSERT INTO attendance_records (
                session_id, student_id, status, presence_percentage,
                verification_method, wifi_ap_verified, ble_verified,
                rtt_distance_meters, sensor_details, marked_at
            ) VALUES (
                p_session_id, v_student.student_id, v_status, v_combined_score,
                'BLE_AUTO', v_has_wifi, v_has_ble, v_avg_rtt,
                jsonb_build_object('wifi_ratio', v_wifi_ratio, 'ble_ratio', v_ble_ratio, 'rtt_avg_meters', v_avg_rtt),
                now()
            )
            ON CONFLICT (session_id, student_id)
            DO UPDATE SET
                status = EXCLUDED.status,
                presence_percentage = EXCLUDED.presence_percentage,
                wifi_ap_verified = EXCLUDED.wifi_ap_verified,
                ble_verified = EXCLUDED.ble_verified,
                rtt_distance_meters = EXCLUDED.rtt_distance_meters,
                sensor_details = EXCLUDED.sensor_details,
                marked_at = now()
                WHERE attendance_records.verification_method = 'BLE_AUTO';
        END;
    END LOOP;

    UPDATE attendance_sessions SET status = 'IN_REVIEW' WHERE id = p_session_id;

    RETURN jsonb_build_object(
        'session_id', p_session_id,
        'total_students', v_total_students,
        'present_count', v_count_present,
        'review_count', v_count_review,
        'absent_count', v_count_absent,
        'mode', 'HYBRID_WIFI_BLE_RTT'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- Smart Attendance System — Initial Schema Migration
-- Migration: 20260930000001_initial_schema.sql
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enums
CREATE TYPE user_role AS ENUM ('SUPER_ADMIN', 'DEPT_ADMIN', 'TEACHER', 'STUDENT');
CREATE TYPE session_status AS ENUM ('ACTIVE', 'CALCULATING', 'IN_REVIEW', 'COMPLETED', 'CANCELLED');
CREATE TYPE attendance_status AS ENUM ('PRESENT', 'REVIEW', 'ABSENT');
CREATE TYPE verification_method AS ENUM ('BLE_AUTO', 'MANUAL_TEACHER', 'QR_FALLBACK');
CREATE TYPE device_status AS ENUM ('ACTIVE', 'BLOCKED', 'PENDING_APPROVAL');

-- 1. INSTITUTIONS
CREATE TABLE institutions (
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
CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (institution_id, code)
);

-- 3. PROGRAMS
CREATE TABLE programs (
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
CREATE TABLE sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    semester INT NOT NULL,
    name TEXT NOT NULL, -- e.g. "Section A"
    academic_year TEXT NOT NULL, -- e.g. "2026-2027"
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (program_id, semester, name, academic_year)
);

-- 5. USERS (Profiles tied to auth.users)
CREATE TABLE users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    role user_role NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. TEACHERS
CREATE TABLE teachers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    employee_id TEXT NOT NULL UNIQUE,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    designation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. STUDENTS
CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    roll_number TEXT NOT NULL UNIQUE,
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE RESTRICT,
    semester INT NOT NULL,
    section_id UUID NOT NULL REFERENCES sections(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8. SUBJECTS
CREATE TABLE subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    credits INT NOT NULL DEFAULT 4,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, code)
);

-- 9. CLASSES (Scheduled Lecture Offerings)
CREATE TABLE classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    room TEXT NOT NULL,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1 = Monday
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 10. DEVICES (Anti-proxy device binding for students)
CREATE TABLE devices (
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

-- 11. ATTENDANCE_SESSIONS
CREATE TABLE attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES teachers(id) ON DELETE RESTRICT,
    session_secret TEXT NOT NULL, -- 256-bit cryptographically secure hex secret
    start_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    end_time TIMESTAMPTZ,
    expected_end_time TIMESTAMPTZ,
    status session_status NOT NULL DEFAULT 'ACTIVE',
    present_threshold NUMERIC(4,2) NOT NULL DEFAULT 0.60,
    review_threshold NUMERIC(4,2) NOT NULL DEFAULT 0.20,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 12. PRESENCE_EVENTS (Raw BLE observation timeline samples)
-- Note: Short-lived raw data; retention policy removes entries older than 30 days.
CREATE TABLE presence_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE RESTRICT,
    token_hash TEXT NOT NULL,
    rssi INT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 13. ATTENDANCE_RECORDS (Finalized status per student per session)
CREATE TABLE attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    status attendance_status NOT NULL,
    presence_percentage NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    verification_method verification_method NOT NULL DEFAULT 'BLE_AUTO',
    marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    marked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, student_id)
);

-- 14. AUDIT_LOGS (Immutable compliance log)
CREATE TABLE audit_logs (
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

-- ============================================================================
-- Indexes for High Performance Queries
-- ============================================================================
CREATE INDEX idx_presence_events_session_student ON presence_events(session_id, student_id);
CREATE INDEX idx_presence_events_timestamp ON presence_events(timestamp);
CREATE INDEX idx_attendance_records_student ON attendance_records(student_id);
CREATE INDEX idx_attendance_records_session ON attendance_records(session_id);
CREATE INDEX idx_attendance_sessions_teacher ON attendance_sessions(teacher_id, status);
CREATE INDEX idx_classes_teacher_day ON classes(teacher_id, day_of_week);
CREATE INDEX idx_classes_section ON classes(section_id);
CREATE INDEX idx_devices_student ON devices(student_id);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);

-- ============================================================================
-- Automatic updated_at Trigger
-- ============================================================================
CREATE OR REPLACE FUNCTION update_timestamp_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_timestamp_column();
CREATE TRIGGER trg_institutions_updated_at BEFORE UPDATE ON institutions FOR EACH ROW EXECUTE FUNCTION update_timestamp_column();
CREATE TRIGGER trg_departments_updated_at BEFORE UPDATE ON departments FOR EACH ROW EXECUTE FUNCTION update_timestamp_column();
CREATE TRIGGER trg_programs_updated_at BEFORE UPDATE ON programs FOR EACH ROW EXECUTE FUNCTION update_timestamp_column();
CREATE TRIGGER trg_sections_updated_at BEFORE UPDATE ON sections FOR EACH ROW EXECUTE FUNCTION update_timestamp_column();
CREATE TRIGGER trg_attendance_records_updated_at BEFORE UPDATE ON attendance_records FOR EACH ROW EXECUTE FUNCTION update_timestamp_column();

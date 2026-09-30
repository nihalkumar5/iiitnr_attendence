-- ============================================================================
-- Smart Attendance System — Row Level Security (RLS) Policies
-- Migration: 20260930000002_rls_policies.sql
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE institutions ENABLE ROW LEVEL SECURITY;
ALTER TABLE departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE programs ENABLE ROW LEVEL SECURITY;
ALTER TABLE sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE students ENABLE ROW LEVEL SECURITY;
ALTER TABLE subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE presence_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

-- Helper functions to extract current user info
CREATE OR REPLACE FUNCTION current_user_role()
RETURNS user_role AS $$
    SELECT role FROM users WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION current_user_institution()
RETURNS UUID AS $$
    SELECT institution_id FROM users WHERE id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION current_teacher_id()
RETURNS UUID AS $$
    SELECT id FROM teachers WHERE user_id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION current_student_id()
RETURNS UUID AS $$
    SELECT id FROM students WHERE user_id = auth.uid();
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- ----------------------------------------------------------------------------
-- 1. USERS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can read own profile"
    ON users FOR SELECT
    TO authenticated
    USING (id = auth.uid() OR current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN'));

CREATE POLICY "Super Admins can manage users in institution"
    ON users FOR ALL
    TO authenticated
    USING (institution_id = current_user_institution() AND current_user_role() = 'SUPER_ADMIN')
    WITH CHECK (institution_id = current_user_institution() AND current_user_role() = 'SUPER_ADMIN');

-- ----------------------------------------------------------------------------
-- 2. INSTITUTIONS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view their institution"
    ON institutions FOR SELECT
    TO authenticated
    USING (id = current_user_institution());

CREATE POLICY "Super Admins can update their institution"
    ON institutions FOR UPDATE
    TO authenticated
    USING (id = current_user_institution() AND current_user_role() = 'SUPER_ADMIN');

-- ----------------------------------------------------------------------------
-- 3. DEPARTMENTS & PROGRAMS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "View departments in institution"
    ON departments FOR SELECT
    TO authenticated
    USING (institution_id = current_user_institution());

CREATE POLICY "View programs in institution"
    ON programs FOR SELECT
    TO authenticated
    USING (department_id IN (SELECT id FROM departments WHERE institution_id = current_user_institution()));

-- ----------------------------------------------------------------------------
-- 4. SECTIONS & CLASSES POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "View sections"
    ON sections FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Teachers can view assigned classes"
    ON classes FOR SELECT
    TO authenticated
    USING (
        teacher_id = current_teacher_id() 
        OR current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN')
        OR section_id IN (SELECT section_id FROM students WHERE user_id = auth.uid())
    );

-- ----------------------------------------------------------------------------
-- 5. STUDENTS & TEACHERS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "View student profiles"
    ON students FOR SELECT
    TO authenticated
    USING (
        user_id = auth.uid()
        OR current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN')
        OR current_user_role() = 'TEACHER'
    );

CREATE POLICY "View teacher profiles"
    ON teachers FOR SELECT
    TO authenticated
    USING (true);

-- ----------------------------------------------------------------------------
-- 6. DEVICES POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Students can view own registered devices"
    ON devices FOR SELECT
    TO authenticated
    USING (student_id = current_student_id() OR current_user_role() = 'SUPER_ADMIN');

CREATE POLICY "Students can register own device"
    ON devices FOR INSERT
    TO authenticated
    WITH CHECK (student_id = current_student_id());

CREATE POLICY "Students can update own device last_seen"
    ON devices FOR UPDATE
    TO authenticated
    USING (student_id = current_student_id());

-- ----------------------------------------------------------------------------
-- 7. ATTENDANCE_SESSIONS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Teachers can create attendance sessions"
    ON attendance_sessions FOR INSERT
    TO authenticated
    WITH CHECK (teacher_id = current_teacher_id());

CREATE POLICY "Teachers can update own attendance sessions"
    ON attendance_sessions FOR UPDATE
    TO authenticated
    USING (teacher_id = current_teacher_id() OR current_user_role() = 'SUPER_ADMIN');

CREATE POLICY "Authorized users can view attendance sessions"
    ON attendance_sessions FOR SELECT
    TO authenticated
    USING (
        teacher_id = current_teacher_id()
        OR current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN')
        OR class_id IN (
            SELECT c.id FROM classes c 
            JOIN students s ON s.section_id = c.section_id 
            WHERE s.user_id = auth.uid()
        )
    );

-- ----------------------------------------------------------------------------
-- 8. PRESENCE_EVENTS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Students can insert own presence events"
    ON presence_events FOR INSERT
    TO authenticated
    WITH CHECK (
        student_id = current_student_id()
        AND device_id IN (SELECT id FROM devices WHERE student_id = current_student_id() AND status = 'ACTIVE')
    );

CREATE POLICY "Teachers can view presence events of their sessions"
    ON presence_events FOR SELECT
    TO authenticated
    USING (
        session_id IN (SELECT id FROM attendance_sessions WHERE teacher_id = current_teacher_id())
        OR current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN')
    );

-- ----------------------------------------------------------------------------
-- 9. ATTENDANCE_RECORDS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Students can view own attendance records"
    ON attendance_records FOR SELECT
    TO authenticated
    USING (
        student_id = current_student_id()
        OR session_id IN (SELECT id FROM attendance_sessions WHERE teacher_id = current_teacher_id())
        OR current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN')
    );

CREATE POLICY "Teachers can modify attendance records for their sessions"
    ON attendance_records FOR ALL
    TO authenticated
    USING (
        session_id IN (SELECT id FROM attendance_sessions WHERE teacher_id = current_teacher_id())
        OR current_user_role() = 'SUPER_ADMIN'
    )
    WITH CHECK (
        session_id IN (SELECT id FROM attendance_sessions WHERE teacher_id = current_teacher_id())
        OR current_user_role() = 'SUPER_ADMIN'
    );

-- ----------------------------------------------------------------------------
-- 10. AUDIT_LOGS POLICIES
-- ----------------------------------------------------------------------------
CREATE POLICY "Admins and teachers can view audit logs"
    ON audit_logs FOR SELECT
    TO authenticated
    USING (current_user_role() IN ('SUPER_ADMIN', 'DEPT_ADMIN', 'TEACHER'));

CREATE POLICY "System and users can insert audit logs"
    ON audit_logs FOR INSERT
    TO authenticated
    WITH CHECK (actor_id = auth.uid());

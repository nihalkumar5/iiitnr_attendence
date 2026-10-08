-- ============================================================================
-- Migration: 20261002000004_course_enrollments_and_gatekeeper.sql
-- Course-Level Student Registration & Lecture Attendance Gatekeeping
-- ============================================================================

-- 1. Course Enrollments Table
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

-- 2. Stored Procedure: Enroll student into course by roll number
CREATE OR REPLACE FUNCTION enroll_student_by_roll(
    p_class_id UUID,
    p_roll_number TEXT,
    p_name TEXT DEFAULT NULL,
    p_email TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_student_id UUID;
    v_user_id UUID;
    v_clean_roll TEXT := trim(upper(p_roll_number));
    v_clean_email TEXT := trim(lower(coalesce(p_email, v_clean_roll || '@student.iiitnr.edu.in')));
    v_enrollment_id UUID;
BEGIN
    -- Check if student already exists
    SELECT id, user_id INTO v_student_id, v_user_id
    FROM students
    WHERE upper(roll_number) = v_clean_roll;

    IF v_student_id IS NULL THEN
        -- Create user if needed
        SELECT id INTO v_user_id FROM users WHERE lower(email) = v_clean_email;
        IF v_user_id IS NULL THEN
            INSERT INTO users (institution_id, name, email, role, is_active)
            VALUES (
                (SELECT institution_id FROM classes c JOIN departments d ON c.subject_id = d.id LIMIT 1),
                coalesce(p_name, 'IIIT-NR Student'),
                v_clean_email,
                'STUDENT',
                true
            )
            RETURNING id INTO v_user_id;
        END IF;

        -- Create student record
        INSERT INTO students (user_id, roll_number, program_id, section_id, semester, status)
        VALUES (
            v_user_id,
            v_clean_roll,
            (SELECT program_id FROM sections LIMIT 1),
            (SELECT section_id FROM classes WHERE id = p_class_id),
            1,
            'ACTIVE'
        )
        RETURNING id INTO v_student_id;
    END IF;

    -- Upsert course enrollment
    INSERT INTO course_enrollments (class_id, student_id, enrollment_type, is_active)
    VALUES (p_class_id, v_student_id, 'CORE', true)
    ON CONFLICT (class_id, student_id)
    DO UPDATE SET is_active = true
    RETURNING id INTO v_enrollment_id;

    RETURN v_enrollment_id;
END;
$$;

-- 3. Stored Procedure: Check if student is authorized for session's class
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
        RETURN true; -- Session doesn't enforce specific class
    END IF;

    -- If no enrollments exist for this class at all, allow all students in section
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

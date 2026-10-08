-- ============================================================================
-- Migration: 20261008000001_class_join_code_and_student_enrollment.sql
-- Batch Join Code Generation for Faculty & Student Course Enrollment
-- ============================================================================

-- 1. Add join_code column to classes table if not exists
ALTER TABLE classes ADD COLUMN IF NOT EXISTS join_code TEXT UNIQUE;

-- 2. Function to generate clean 6-character alphanumeric batch join code
CREATE OR REPLACE FUNCTION generate_unique_join_code(p_prefix TEXT DEFAULT 'BATCH')
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_code TEXT;
    v_exists BOOLEAN;
    v_chars TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    v_random_suffix TEXT;
BEGIN
    LOOP
        v_random_suffix := '';
        FOR i IN 1..4 LOOP
            v_random_suffix := v_random_suffix || substr(v_chars, floor(random() * length(v_chars) + 1)::integer, 1);
        END LOOP;

        v_code := upper(trim(p_prefix)) || '-' || v_random_suffix;

        SELECT EXISTS(SELECT 1 FROM classes WHERE upper(join_code) = v_code) INTO v_exists;
        IF NOT v_exists THEN
            RETURN v_code;
        END IF;
    END LOOP;
END;
$$;

-- 3. Trigger to auto-assign unique join_code when new class is inserted
CREATE OR REPLACE FUNCTION set_class_join_code_trigger()
RETURNS TRIGGER AS $$
DECLARE
    v_sub_code TEXT;
BEGIN
    IF NEW.join_code IS NULL OR trim(NEW.join_code) = '' THEN
        SELECT code INTO v_sub_code FROM subjects WHERE id = NEW.subject_id;
        IF v_sub_code IS NULL OR trim(v_sub_code) = '' THEN
            v_sub_code := 'CLS';
        ELSE
            v_sub_code := regexp_replace(upper(v_sub_code), '[^A-Z0-9]', '', 'g');
            IF length(v_sub_code) > 4 THEN
                v_sub_code := substr(v_sub_code, 1, 4);
            END IF;
        END IF;
        NEW.join_code := generate_unique_join_code(v_sub_code);
    ELSE
        NEW.join_code := upper(trim(NEW.join_code));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_classes_join_code ON classes;
CREATE TRIGGER trg_classes_join_code
BEFORE INSERT ON classes
FOR EACH ROW
EXECUTE FUNCTION set_class_join_code_trigger();

-- 4. Backfill existing classes with unique join codes if null
DO $$
DECLARE
    r RECORD;
    v_sub_code TEXT;
BEGIN
    FOR r IN SELECT c.id, s.code FROM classes c LEFT JOIN subjects s ON c.subject_id = s.id WHERE c.join_code IS NULL LOOP
        v_sub_code := coalesce(regexp_replace(upper(r.code), '[^A-Z0-9]', '', 'g'), 'BATCH');
        IF length(v_sub_code) > 4 THEN
            v_sub_code := substr(v_sub_code, 1, 4);
        END IF;
        UPDATE classes SET join_code = generate_unique_join_code(v_sub_code) WHERE id = r.id;
    END LOOP;
END $$;

-- 5. Stored Procedure: Student Joins Course via Batch Code
CREATE OR REPLACE FUNCTION join_course_by_code(
    p_join_code TEXT,
    p_roll_number TEXT,
    p_student_name TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_clean_code TEXT := upper(trim(p_join_code));
    v_clean_roll TEXT := upper(trim(p_roll_number));
    v_class RECORD;
    v_student_id UUID;
    v_user_id UUID;
    v_enrollment_id UUID;
BEGIN
    IF v_clean_code IS NULL OR v_clean_code = '' THEN
        RAISE EXCEPTION 'INVALID_CODE: Please enter a valid Batch Join Code.';
    END IF;

    -- Find the class by join_code or subject code
    SELECT c.id AS class_id, c.join_code, c.room, s.name AS subject_name, s.code AS subject_code, u.name AS teacher_name, t.id AS teacher_id
    INTO v_class
    FROM classes c
    JOIN subjects s ON c.subject_id = s.id
    LEFT JOIN teachers t ON c.teacher_id = t.id
    LEFT JOIN users u ON t.user_id = u.id
    WHERE upper(c.join_code) = v_clean_code
       OR upper(s.code) = v_clean_code
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'BATCH_NOT_FOUND: No active course found for Batch Code "%". Please check the code with your faculty.', v_clean_code;
    END IF;

    -- Look up or create student
    SELECT id, user_id INTO v_student_id, v_user_id
    FROM students
    WHERE upper(roll_number) = v_clean_roll;

    IF v_student_id IS NULL THEN
        -- Create user profile if not exists
        SELECT id INTO v_user_id FROM users WHERE lower(email) = lower(v_clean_roll || '@student.iiitnr.edu.in');
        IF v_user_id IS NULL THEN
            INSERT INTO users (institution_id, name, email, role, is_active)
            VALUES (
                'c2fcf1e8-075b-4156-bc0e-27c9b23404f5'::uuid,
                coalesce(p_student_name, 'IIIT-NR Student'),
                lower(v_clean_roll || '@student.iiitnr.edu.in'),
                'STUDENT',
                true
            )
            RETURNING id INTO v_user_id;
        END IF;

        INSERT INTO students (user_id, roll_number, program_id, section_id, semester, status)
        VALUES (
            v_user_id,
            v_clean_roll,
            'a0b5cbed-5dad-4998-83f1-bab35271d01f'::uuid,
            'b7bd5c04-a4bf-478b-b822-1ca0982b55f4'::uuid,
            1,
            'ACTIVE'
        )
        RETURNING id INTO v_student_id;
    END IF;

    -- Upsert course enrollment
    INSERT INTO course_enrollments (class_id, student_id, enrollment_type, is_active)
    VALUES (v_class.class_id, v_student_id, 'CORE', true)
    ON CONFLICT (class_id, student_id)
    DO UPDATE SET is_active = true
    RETURNING id INTO v_enrollment_id;

    RETURN jsonb_build_object(
        'success', true,
        'class_id', v_class.class_id,
        'subject_name', v_class.subject_name,
        'subject_code', v_class.subject_code,
        'join_code', v_class.join_code,
        'teacher_name', coalesce(v_class.teacher_name, 'Faculty Member'),
        'room', v_class.room,
        'enrollment_id', v_enrollment_id
    );
END;
$$;

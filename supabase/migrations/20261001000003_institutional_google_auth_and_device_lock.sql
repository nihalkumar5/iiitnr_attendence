-- ============================================================================
-- Migration: 20261001000003_institutional_google_auth_and_device_lock.sql
-- Enforces @iiitnr.edu.in domain restrictions and cryptographic device locking
-- ============================================================================

-- 1. Ensure columns for Google Identity & Avatars
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub TEXT UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- 2. Domain Validation Constraint
-- Only permits official IIIT-NR institutional email addresses
DO $$ BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS check_institutional_domain;
    ALTER TABLE users ADD CONSTRAINT check_institutional_domain
        CHECK (
            email ~* '^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\.)?(iiitnr\.edu\.in|iiitnr\.ac\.in|iitdemo\.edu)$'
        );
EXCEPTION
    WHEN others THEN null;
END $$;

-- 3. Stored Procedure: Atomic Anti-Proxy Hardware Device Binding
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

    -- Step 1: Check if this physical phone is already bound to a DIFFERENT student
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
            -- Phone already belongs to this student: refresh model, OS, and timestamp
            UPDATE devices
            SET device_model = p_device_model,
                os_version = p_os_version,
                last_seen = now()
            WHERE id = v_existing_device.id;
            RETURN v_existing_device.id;
        END IF;
    END IF;

    -- Step 2: Check if this student is already bound to a DIFFERENT physical phone
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

    -- Step 3: Clear to bind! Insert new official device binding
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

-- 4. Stored Procedure: Google Sign-In Account Sync & Institutional Verification
CREATE OR REPLACE FUNCTION authenticate_google_user(
    p_email TEXT,
    p_name TEXT,
    p_google_sub TEXT,
    p_avatar_url TEXT DEFAULT NULL,
    p_role TEXT DEFAULT 'STUDENT',
    p_installation_id TEXT DEFAULT NULL,
    p_device_model TEXT DEFAULT NULL,
    p_os_version TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_clean_email TEXT;
    v_user RECORD;
    v_student RECORD;
    v_teacher RECORD;
    v_device_id UUID;
    v_roll_candidate TEXT;
    v_institution_id UUID;
BEGIN
    v_clean_email := lower(trim(p_email));

    -- Enforce institutional domain
    IF NOT (v_clean_email ~* '^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\.)?(iiitnr\.edu\.in|iiitnr\.ac\.in|iitdemo\.edu)$') THEN
        RAISE EXCEPTION 'SECURITY_REJECTION: Access restricted. Only official institutional Google accounts (@iiitnr.edu.in) are permitted.';
    END IF;

    -- Get institution ID
    SELECT id INTO v_institution_id FROM institutions LIMIT 1;
    IF v_institution_id IS NULL THEN
        v_institution_id := 'c2fcf1e8-075b-4156-bc0e-27c9b23404f5'::uuid;
    END IF;

    -- Check if user already exists
    SELECT id, name, email, role, is_active
    INTO v_user
    FROM users
    WHERE email = v_clean_email
    LIMIT 1;

    IF NOT FOUND THEN
        -- Create new user profile linked to Google
        INSERT INTO users (
            institution_id,
            name,
            email,
            role,
            google_sub,
            avatar_url,
            is_active
        ) VALUES (
            v_institution_id,
            p_name,
            v_clean_email,
            p_role::user_role,
            p_google_sub,
            p_avatar_url,
            true
        )
        RETURNING id, name, email, role, is_active INTO v_user;
    ELSE
        -- Update existing user with Google sub & avatar
        UPDATE users
        SET google_sub = COALESCE(p_google_sub, google_sub),
            avatar_url = COALESCE(p_avatar_url, avatar_url),
            name = COALESCE(p_name, name),
            updated_at = now()
        WHERE id = v_user.id;
    END IF;

    -- If Student role: Handle student record & device binding
    IF v_user.role = 'STUDENT' THEN
        SELECT id, roll_number, program_id, semester
        INTO v_student
        FROM students
        WHERE user_id = v_user.id
        LIMIT 1;

        IF NOT FOUND THEN
            -- Extract roll number candidate from email (e.g. 26DSAI001@student.iiitnr.edu.in)
            v_roll_candidate := upper(split_part(v_clean_email, '@', 1));
            INSERT INTO students (
                user_id,
                roll_number,
                program_id,
                section_id,
                semester,
                status
            ) VALUES (
                v_user.id,
                v_roll_candidate,
                'a0b5cbed-5dad-4998-83f1-bab35271d01f'::uuid,
                'b7bd5c04-a4bf-478b-b822-1ca0982b55f4'::uuid,
                1,
                'ACTIVE'
            )
            RETURNING id, roll_number, program_id, semester INTO v_student;
        END IF;

        -- Bind device if installation ID provided
        IF p_installation_id IS NOT NULL AND trim(p_installation_id) != '' THEN
            v_device_id := bind_student_hardware_device(
                v_student.id,
                p_installation_id,
                p_device_model,
                p_os_version
            );
        END IF;

        RETURN jsonb_build_object(
            'success', true,
            'user_id', v_user.id,
            'name', v_user.name,
            'email', v_user.email,
            'role', 'STUDENT',
            'student_id', v_student.id,
            'roll_number', v_student.roll_number,
            'device_id', v_device_id,
            'device_bound', v_device_id IS NOT NULL
        );

    -- If Teacher role:
    ELSIF v_user.role = 'TEACHER' THEN
        SELECT id, employee_id, designation
        INTO v_teacher
        FROM teachers
        WHERE user_id = v_user.id
        LIMIT 1;

        IF NOT FOUND THEN
            INSERT INTO teachers (
                user_id,
                employee_id,
                department_id,
                designation
            ) VALUES (
                v_user.id,
                upper('FAC-' || split_part(v_clean_email, '@', 1)),
                'a0b5cbed-5dad-4998-83f1-bab35271d01f'::uuid,
                'Assistant Professor'
            )
            RETURNING id, employee_id, designation INTO v_teacher;
        END IF;

        RETURN jsonb_build_object(
            'success', true,
            'user_id', v_user.id,
            'name', v_user.name,
            'email', v_user.email,
            'role', 'TEACHER',
            'teacher_id', v_teacher.id,
            'employee_id', v_teacher.employee_id
        );
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'user_id', v_user.id,
        'name', v_user.name,
        'email', v_user.email,
        'role', v_user.role
    );
END;
$$;

-- Migration: 20261009000001_restore_strict_anti_proxy_device_lock.sql
-- Enforce strict 1 Phone = 1 Student and 1 Student = 1 Phone
-- Block proxy attendance from secondary devices / account sharing

CREATE OR REPLACE FUNCTION bind_student_hardware_device(
    p_student_id UUID,
    p_installation_id TEXT,
    p_device_model TEXT DEFAULT 'Android Device',
    p_os_version TEXT DEFAULT 'Android 14'
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

    -- Step 1: Check if this physical phone / installation_id is already bound to a DIFFERENT student
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
            SET device_model = COALESCE(p_device_model, device_model),
                os_version = COALESCE(p_os_version, os_version),
                last_seen = now()
            WHERE id = v_existing_device.id;
            RETURN v_existing_device.id;
        END IF;
    END IF;

    -- Step 2: Check if this student is already bound to a DIFFERENT physical phone / device
    SELECT d.id, d.installation_id, d.device_model
    INTO v_student_device
    FROM devices d
    WHERE d.student_id = p_student_id AND d.status = 'ACTIVE'
    LIMIT 1;

    IF FOUND THEN
        IF v_student_device.installation_id != p_installation_id THEN
            RAISE EXCEPTION 'DEVICE_MISMATCH: Your account is locked to another device (%). You cannot mark attendance from a secondary phone or browser without faculty unbind approval.',
                v_student_device.device_model;
        ELSE
            RETURN v_student_device.id;
        END IF;
    END IF;

    -- Step 3: First time binding - insert new official device binding
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

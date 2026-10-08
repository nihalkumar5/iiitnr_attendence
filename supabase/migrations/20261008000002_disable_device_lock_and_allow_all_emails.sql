-- Migration: 20261008000002_disable_device_lock_and_allow_all_emails.sql
-- Drop domain restriction constraint
DO $$ BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS check_institutional_domain;
EXCEPTION
    WHEN others THEN null;
END $$;

-- Update bind_student_hardware_device to permit multi-device / device switching
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
    v_new_device_id UUID;
BEGIN
    IF p_installation_id IS NULL OR trim(p_installation_id) = '' THEN
        RETURN NULL;
    END IF;

    -- Check if this physical installation_id exists
    SELECT id, student_id, status
    INTO v_existing_device
    FROM devices
    WHERE installation_id = p_installation_id
    LIMIT 1;

    IF FOUND THEN
        -- Rebind / update device to this student
        UPDATE devices
        SET student_id = p_student_id,
            device_model = COALESCE(p_device_model, device_model),
            os_version = COALESCE(p_os_version, os_version),
            status = 'ACTIVE',
            last_seen = now()
        WHERE id = v_existing_device.id;
        RETURN v_existing_device.id;
    END IF;

    -- Check if student already has a registered device record
    SELECT id INTO v_existing_device
    FROM devices
    WHERE student_id = p_student_id AND status = 'ACTIVE'
    LIMIT 1;

    IF FOUND THEN
        UPDATE devices
        SET installation_id = p_installation_id,
            device_model = COALESCE(p_device_model, device_model),
            os_version = COALESCE(p_os_version, os_version),
            last_seen = now()
        WHERE id = v_existing_device.id;
        RETURN v_existing_device.id;
    END IF;

    -- Insert new device record
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

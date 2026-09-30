-- ============================================================================
-- Smart Attendance System — Attendance Calculation Engine & Functions
-- Migration: 20260930000003_attendance_engine_functions.sql
-- ============================================================================

-- Function to generate an ephemeral rotating token for an active session at timestamp t
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
    v_token TEXT;
BEGIN
    v_window := floor(EXTRACT(EPOCH FROM p_timestamp) / p_window_seconds);
    v_payload := p_session_id::text || ':' || v_window::text;
    v_raw_hmac := hmac(v_payload::bytea, p_session_secret::bytea, 'sha256');
    -- Truncate to 16 hex characters (64-bit entropy, compact for BLE payload)
    v_token := substring(encode(v_raw_hmac, 'hex') from 1 for 16);
    RETURN v_token;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to validate an ephemeral rotating token with +/- 1 window tolerance
CREATE OR REPLACE FUNCTION validate_ephemeral_token(
    p_session_id UUID,
    p_token TEXT,
    p_timestamp TIMESTAMPTZ DEFAULT now(),
    p_window_seconds INT DEFAULT 30
)
RETURNS BOOLEAN AS $$
DECLARE
    v_secret TEXT;
    v_session_status session_status;
    v_window BIGINT;
    v_w BIGINT;
    v_expected_token TEXT;
BEGIN
    SELECT session_secret, status INTO v_secret, v_session_status
    FROM attendance_sessions
    WHERE id = p_session_id;

    IF v_secret IS NULL OR v_session_status != 'ACTIVE' THEN
        RETURN FALSE;
    END IF;

    v_window := floor(EXTRACT(EPOCH FROM p_timestamp) / p_window_seconds);

    -- Check current, previous (-1), and next (+1) time windows
    FOR v_w IN (v_window - 1)..(v_window + 1) LOOP
        v_expected_token := substring(encode(hmac((p_session_id::text || ':' || v_w::text)::bytea, v_secret::bytea, 'sha256'), 'hex') from 1 for 16);
        IF lower(v_expected_token) = lower(p_token) THEN
            RETURN TRUE;
        END IF;
    END LOOP;

    RETURN FALSE;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

-- Function to calculate attendance coverage across timeline
CREATE OR REPLACE FUNCTION calculate_session_attendance(p_session_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_session RECORD;
    v_duration_seconds NUMERIC;
    v_window_size_seconds NUMERIC := 180; -- 3-minute discrete observation windows
    v_total_windows INT;
    v_student RECORD;
    v_observed_windows INT;
    v_coverage NUMERIC(5,2);
    v_status attendance_status;
    v_total_students INT := 0;
    v_count_present INT := 0;
    v_count_review INT := 0;
    v_count_absent INT := 0;
BEGIN
    -- 1. Fetch session metadata
    SELECT s.*, c.section_id INTO v_session
    FROM attendance_sessions s
    JOIN classes c ON c.id = s.class_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Attendance session % not found', p_session_id;
    END IF;

    -- Update end_time if not already marked
    IF v_session.end_time IS NULL THEN
        UPDATE attendance_sessions SET end_time = now() WHERE id = p_session_id;
        v_session.end_time := now();
    END IF;

    v_duration_seconds := EXTRACT(EPOCH FROM (v_session.end_time - v_session.start_time));
    -- Ensure at least 1 window
    v_total_windows := GREATEST(1, CEIL(v_duration_seconds / v_window_size_seconds));

    -- 2. Process each student enrolled in the section
    FOR v_student IN 
        SELECT st.id AS student_id, st.roll_number, u.name
        FROM students st
        JOIN users u ON u.id = st.user_id
        WHERE st.section_id = v_session.section_id AND st.status = 'ACTIVE'
    LOOP
        v_total_students := v_total_students + 1;

        -- Count how many distinct 3-minute windows had at least 1 valid presence event with RSSI >= -85 dBm
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_observed_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.rssi >= -85
          AND pe.timestamp >= v_session.start_time
          AND pe.timestamp <= v_session.end_time;

        v_coverage := LEAST(100.0, ROUND((v_observed_windows::NUMERIC / v_total_windows::NUMERIC) * 100.0, 2));

        -- Determine status based on thresholds
        IF v_coverage >= (v_session.present_threshold * 100.0) THEN
            v_status := 'PRESENT';
            v_count_present := v_count_present + 1;
        ELSIF v_coverage >= (v_session.review_threshold * 100.0) THEN
            v_status := 'REVIEW';
            v_count_review := v_count_review + 1;
        ELSE
            v_status := 'ABSENT';
            v_count_absent := v_count_absent + 1;
        END IF;

        -- Upsert record
        INSERT INTO attendance_records (
            session_id,
            student_id,
            status,
            presence_percentage,
            verification_method,
            marked_at
        ) VALUES (
            p_session_id,
            v_student.student_id,
            v_status,
            v_coverage,
            'BLE_AUTO',
            now()
        )
        ON CONFLICT (session_id, student_id)
        DO UPDATE SET
            status = EXCLUDED.status,
            presence_percentage = EXCLUDED.presence_percentage,
            marked_at = now()
            WHERE attendance_records.verification_method = 'BLE_AUTO'; -- Preserve manual teacher overrides if already set
    END LOOP;

    -- Update session status to IN_REVIEW
    UPDATE attendance_sessions
    SET status = 'IN_REVIEW'
    WHERE id = p_session_id;

    RETURN jsonb_build_object(
        'session_id', p_session_id,
        'total_students', v_total_students,
        'present_count', v_count_present,
        'review_count', v_count_review,
        'absent_count', v_count_absent,
        'total_windows', v_total_windows
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function for Teacher to submit finalized attendance
CREATE OR REPLACE FUNCTION submit_attendance_session(
    p_session_id UUID,
    p_teacher_id UUID
)
RETURNS JSONB AS $$
DECLARE
    v_record_counts RECORD;
BEGIN
    -- Verify teacher owns this session
    IF NOT EXISTS (
        SELECT 1 FROM attendance_sessions
        WHERE id = p_session_id AND teacher_id = p_teacher_id
    ) THEN
        RAISE EXCEPTION 'Unauthorized: teacher does not own session %', p_session_id;
    END IF;

    -- Set status to COMPLETED
    UPDATE attendance_sessions
    SET status = 'COMPLETED',
        end_time = COALESCE(end_time, now())
    WHERE id = p_session_id;

    -- Gather final summary
    SELECT 
        COUNT(*) AS total,
        COUNT(*) FILTER (WHERE status = 'PRESENT') AS present,
        COUNT(*) FILTER (WHERE status = 'REVIEW') AS review,
        COUNT(*) FILTER (WHERE status = 'ABSENT') AS absent
    INTO v_record_counts
    FROM attendance_records
    WHERE session_id = p_session_id;

    -- Audit log
    INSERT INTO audit_logs (
        actor_id,
        action,
        target_type,
        target_id,
        new_value
    ) VALUES (
        auth.uid(),
        'ATTENDANCE_SUBMITTED',
        'attendance_sessions',
        p_session_id::text,
        jsonb_build_object(
            'total', v_record_counts.total,
            'present', v_record_counts.present,
            'review', v_record_counts.review,
            'absent', v_record_counts.absent
        )
    );

    RETURN jsonb_build_object(
        'success', true,
        'session_id', p_session_id,
        'summary', jsonb_build_object(
            'total', v_record_counts.total,
            'present', v_record_counts.present,
            'review', v_record_counts.review,
            'absent', v_record_counts.absent
        )
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

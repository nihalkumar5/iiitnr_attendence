-- ============================================================================
-- Smart Attendance System — Hybrid Multi-Sensor Architecture (Wi-Fi + BLE + RTT)
-- Migration: 20260930000005_hybrid_presence_engine.sql
-- ============================================================================

-- 1. CLASSROOMS TABLE (Maps physical rooms to AP BSSIDs & Beacons)
CREATE TABLE IF NOT EXISTS classrooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL, -- e.g. "Room A-204"
    building TEXT,
    floor INT,
    capacity INT DEFAULT 60,
    -- Network Infrastructure
    wifi_ssid TEXT DEFAULT 'Campus-Secure',
    wifi_bssid TEXT, -- Primary Access Point BSSID (e.g. "00:14:22:01:23:45")
    secondary_bssids TEXT[] DEFAULT '{}', -- Secondary BSSIDs (e.g. 5GHz radio / mesh nodes)
    -- Fixed BLE Beacon (Optional - if deployed instead of Teacher Phone beacon)
    beacon_uuid UUID,
    beacon_major INT,
    beacon_minor INT,
    -- Wi-Fi RTT 802.11mc capability
    is_rtt_supported BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Link classes to classrooms
ALTER TABLE classes ADD COLUMN IF NOT EXISTS classroom_id UUID REFERENCES classrooms(id) ON DELETE SET NULL;

-- 3. Enhance presence_events with Multi-Modal Sensor Data
ALTER TABLE presence_events 
    ADD COLUMN IF NOT EXISTS wifi_bssid TEXT,
    ADD COLUMN IF NOT EXISTS wifi_ssid TEXT,
    ADD COLUMN IF NOT EXISTS wifi_rssi INT,
    ADD COLUMN IF NOT EXISTS wifi_rtt_distance_meters NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS ble_rssi INT,
    ADD COLUMN IF NOT EXISTS source_type TEXT DEFAULT 'BLE_ONLY',
    ADD COLUMN IF NOT EXISTS multi_sensor_confidence NUMERIC(5,2);

-- 4. Enhance attendance_records with Multi-Sensor Audit Breakdown
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS wifi_ap_verified BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS ble_verified BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS rtt_distance_meters NUMERIC(6,2),
    ADD COLUMN IF NOT EXISTS sensor_details JSONB;

-- 5. Stored Procedure for Hybrid Multi-Modal Attendance Calculation
CREATE OR REPLACE FUNCTION calculate_hybrid_session_attendance(p_session_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_session RECORD;
    v_classroom RECORD;
    v_duration_seconds NUMERIC;
    v_window_size_seconds NUMERIC := 180; -- 3-minute discrete observation windows
    v_total_windows INT;
    v_student RECORD;
    v_wifi_windows INT;
    v_ble_windows INT;
    v_rtt_windows INT;
    v_avg_rtt NUMERIC(6,2);
    v_combined_score NUMERIC(5,2);
    v_status attendance_status;
    v_verification_method verification_method;
    v_total_students INT := 0;
    v_count_present INT := 0;
    v_count_review INT := 0;
    v_count_absent INT := 0;
BEGIN
    -- 1. Fetch session and associated classroom info
    SELECT s.*, c.section_id, c.classroom_id INTO v_session
    FROM attendance_sessions s
    JOIN classes c ON c.id = s.class_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Session % not found', p_session_id;
    END IF;

    -- Fetch classroom infrastructure details
    IF v_session.classroom_id IS NOT NULL THEN
        SELECT * INTO v_classroom FROM classrooms WHERE id = v_session.classroom_id;
    END IF;

    IF v_session.end_time IS NULL THEN
        UPDATE attendance_sessions SET end_time = now() WHERE id = p_session_id;
        v_session.end_time := now();
    END IF;

    v_duration_seconds := EXTRACT(EPOCH FROM (v_session.end_time - v_session.start_time));
    v_total_windows := GREATEST(1, CEIL(v_duration_seconds / v_window_size_seconds));

    -- 2. Process each enrolled student
    FOR v_student IN 
        SELECT st.id AS student_id, st.roll_number, u.name
        FROM students st
        JOIN users u ON u.id = st.user_id
        WHERE st.section_id = v_session.section_id AND st.status = 'ACTIVE'
    LOOP
        v_total_students := v_total_students + 1;

        -- Count windows where student was connected to the registered Classroom AP BSSID
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_wifi_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND v_classroom.wifi_bssid IS NOT NULL
          AND (lower(pe.wifi_bssid) = lower(v_classroom.wifi_bssid) OR pe.wifi_bssid = ANY(v_classroom.secondary_bssids))
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Count windows where student detected valid BLE beacon within proximity (RSSI >= -85)
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_ble_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.rssi >= -85
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Check average Wi-Fi RTT distance if recorded
        SELECT AVG(pe.wifi_rtt_distance_meters)
        INTO v_avg_rtt
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.wifi_rtt_distance_meters IS NOT NULL
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Calculate Sensor Ratios (0.0 to 100.0)
        DECLARE
            v_wifi_ratio NUMERIC := (v_wifi_windows::NUMERIC / v_total_windows::NUMERIC) * 100.0;
            v_ble_ratio NUMERIC := (v_ble_windows::NUMERIC / v_total_windows::NUMERIC) * 100.0;
            v_has_wifi BOOLEAN := (v_wifi_ratio >= 40.0);
            v_has_ble BOOLEAN := (v_ble_ratio >= 40.0);
        BEGIN
            -- Multi-modal Fusion Weighting:
            -- If both Wi-Fi AP and BLE are active in the classroom: 50% Wi-Fi AP + 50% BLE
            -- Bonus +10% if RTT confirms distance <= 10m
            IF v_classroom.wifi_bssid IS NOT NULL THEN
                v_combined_score := (0.50 * v_wifi_ratio) + (0.50 * v_ble_ratio);
                IF v_avg_rtt IS NOT NULL AND v_avg_rtt <= 10.0 THEN
                    v_combined_score := LEAST(100.0, v_combined_score + 10.0);
                END IF;
            ELSE
                -- Pure BLE mode (if no room AP configured)
                v_combined_score := v_ble_ratio;
            END IF;

            v_combined_score := ROUND(LEAST(100.0, v_combined_score), 2);

            -- Classification Decision Matrix
            IF v_combined_score >= (v_session.present_threshold * 100.0) THEN
                v_status := 'PRESENT';
                v_count_present := v_count_present + 1;
                v_verification_method := 'BLE_AUTO';
            ELSIF v_combined_score >= (v_session.review_threshold * 100.0) OR (v_has_wifi AND NOT v_has_ble) THEN
                -- Flag as REVIEW if Wi-Fi connected to room AP but BLE missing (e.g. bluetooth disabled)
                v_status := 'REVIEW';
                v_count_review := v_count_review + 1;
                v_verification_method := 'BLE_AUTO';
            ELSE
                v_status := 'ABSENT';
                v_count_absent := v_count_absent + 1;
                v_verification_method := 'BLE_AUTO';
            END IF;

            -- Upsert into attendance_records with sensor breakdown
            INSERT INTO attendance_records (
                session_id,
                student_id,
                status,
                presence_percentage,
                verification_method,
                wifi_ap_verified,
                ble_verified,
                rtt_distance_meters,
                sensor_details,
                marked_at
            ) VALUES (
                p_session_id,
                v_student.student_id,
                v_status,
                v_combined_score,
                v_verification_method,
                v_has_wifi,
                v_has_ble,
                v_avg_rtt,
                jsonb_build_object(
                    'wifi_ratio', v_wifi_ratio,
                    'ble_ratio', v_ble_ratio,
                    'rtt_avg_meters', v_avg_rtt,
                    'wifi_windows', v_wifi_windows,
                    'ble_windows', v_ble_windows,
                    'total_windows', v_total_windows
                ),
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

    UPDATE attendance_sessions
    SET status = 'IN_REVIEW'
    WHERE id = p_session_id;

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

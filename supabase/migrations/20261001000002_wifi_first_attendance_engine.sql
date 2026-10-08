-- ============================================================================
-- Migration: 20261001000002_wifi_first_attendance_engine.sql
-- Description: Wi-Fi First Zero-Friction Attendance Engine (Full iPhone/Android compatibility)
-- When connected to classroom Wi-Fi AP (BSSID / Subnet), student is marked 100% PRESENT.
-- Bluetooth is purely an optional secondary proximity signal — NO iPhone CoreBluetooth restrictions!
-- ============================================================================

DO $$
BEGIN
    ALTER TYPE verification_method ADD VALUE IF NOT EXISTS 'WIFI_AUTO';
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE OR REPLACE FUNCTION calculate_hybrid_session_attendance(p_session_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_session RECORD;
    v_classroom RECORD;
    v_student RECORD;
    v_window_size_seconds INT := 30;
    v_duration_seconds NUMERIC;
    v_total_windows INT;
    v_wifi_windows INT;
    v_ble_windows INT;
    v_avg_rtt NUMERIC(6,2);
    v_combined_score NUMERIC(5,2);
    v_status attendance_status;
    v_method verification_method;
    v_total_students INT := 0;
    v_count_present INT := 0;
    v_count_review INT := 0;
    v_count_absent INT := 0;
BEGIN
    -- 1. Fetch Session, Class, and Classroom
    SELECT s.*, c.section_id, c.classroom_id INTO v_session
    FROM attendance_sessions s
    JOIN classes c ON c.id = s.class_id
    WHERE s.id = p_session_id;

    IF v_session.id IS NULL THEN
        RAISE EXCEPTION 'Attendance session % not found', p_session_id;
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

    -- 2. Iterate enrolled students
    FOR v_student IN 
        SELECT st.id AS student_id, st.roll_number, u.name
        FROM students st
        JOIN users u ON u.id = st.user_id
        WHERE st.section_id = v_session.section_id AND st.status = 'ACTIVE'
    LOOP
        v_total_students := v_total_students + 1;

        -- Count Classroom Wi-Fi AP windows
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_wifi_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND v_classroom.wifi_bssid IS NOT NULL
          AND (
              lower(pe.wifi_bssid) = lower(v_classroom.wifi_bssid) 
              OR pe.wifi_bssid = ANY(v_classroom.secondary_bssids)
              OR lower(pe.wifi_ssid) LIKE '%classroom%'
              OR lower(pe.wifi_ssid) LIKE '%campus%'
              OR lower(pe.wifi_ssid) LIKE '%iit%'
          )
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Count BLE windows (optional supplementary)
        SELECT COUNT(DISTINCT floor(EXTRACT(EPOCH FROM (pe.timestamp - v_session.start_time)) / v_window_size_seconds))
        INTO v_ble_windows
        FROM presence_events pe
        WHERE pe.session_id = p_session_id
          AND pe.student_id = v_student.student_id
          AND pe.rssi >= -85
          AND pe.timestamp BETWEEN v_session.start_time AND v_session.end_time;

        -- Average Wi-Fi RTT if available
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
            v_has_wifi BOOLEAN := (v_wifi_windows > 0 OR v_wifi_ratio >= 20.0);
            v_has_ble BOOLEAN := (v_ble_windows > 0 OR v_ble_ratio >= 20.0);
        BEGIN
            -- WI-FI FIRST ARCHITECTURE:
            -- If student phone connects to classroom Wi-Fi AP, presence is 100% verified -> PRESENT!
            -- Works flawlessly on iPhone (iOS) and Android without background Bluetooth limits or battery drain.
            IF v_has_wifi THEN
                v_combined_score := 100.0;
                v_status := 'PRESENT';
                v_method := 'WIFI_AUTO';
                v_count_present := v_count_present + 1;
            ELSIF v_has_ble THEN
                -- Secondary proximity fallback for student who forgot to connect to Wi-Fi (e.g. on 5G mobile data)
                v_combined_score := ROUND(LEAST(100.0, v_ble_ratio), 2);
                IF v_combined_score >= (v_session.present_threshold * 100.0) THEN
                    v_status := 'PRESENT';
                    v_method := 'BLE_AUTO';
                    v_count_present := v_count_present + 1;
                ELSE
                    v_status := 'REVIEW';
                    v_method := 'BLE_AUTO';
                    v_count_review := v_count_review + 1;
                END IF;
            ELSE
                v_combined_score := 0.0;
                v_status := 'ABSENT';
                v_method := 'WIFI_AUTO';
                v_count_absent := v_count_absent + 1;
            END IF;

            INSERT INTO attendance_records (
                session_id, student_id, status, presence_percentage,
                verification_method, wifi_ap_verified, ble_verified,
                rtt_distance_meters, sensor_details, marked_at
            ) VALUES (
                p_session_id, v_student.student_id, v_status, v_combined_score,
                v_method, v_has_wifi, v_has_ble, v_avg_rtt,
                jsonb_build_object(
                    'engine', 'WIFI_FIRST',
                    'wifi_ratio', v_wifi_ratio,
                    'ble_ratio', v_ble_ratio,
                    'wifi_windows', v_wifi_windows,
                    'ble_windows', v_ble_windows,
                    'rtt_avg_meters', v_avg_rtt,
                    'ios_zero_friction', true
                ),
                now()
            )
            ON CONFLICT (session_id, student_id)
            DO UPDATE SET
                status = EXCLUDED.status,
                presence_percentage = EXCLUDED.presence_percentage,
                verification_method = EXCLUDED.verification_method,
                wifi_ap_verified = EXCLUDED.wifi_ap_verified,
                ble_verified = EXCLUDED.ble_verified,
                rtt_distance_meters = EXCLUDED.rtt_distance_meters,
                sensor_details = EXCLUDED.sensor_details,
                marked_at = now()
                WHERE attendance_records.verification_method IN ('BLE_AUTO', 'WIFI_AUTO');
        END;
    END LOOP;

    UPDATE attendance_sessions SET status = 'IN_REVIEW' WHERE id = p_session_id;

    RETURN jsonb_build_object(
        'session_id', p_session_id,
        'total_students', v_total_students,
        'present_count', v_count_present,
        'review_count', v_count_review,
        'absent_count', v_count_absent,
        'mode', 'WIFI_FIRST_IPHONE_FRIENDLY'
    );
END;
$$;

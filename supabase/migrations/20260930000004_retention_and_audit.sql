-- ============================================================================
-- Smart Attendance System — Audit Logging & Retention Policy
-- Migration: 20260930000004_retention_and_audit.sql
-- ============================================================================

-- Automatic trigger to record manual modifications to attendance records
CREATE OR REPLACE FUNCTION trg_log_attendance_override()
RETURNS TRIGGER AS $$
BEGIN
    -- Log only when status or verification_method changes
    IF (OLD.status IS DISTINCT FROM NEW.status) OR (OLD.verification_method IS DISTINCT FROM NEW.verification_method) THEN
        INSERT INTO audit_logs (
            actor_id,
            action,
            target_type,
            target_id,
            previous_value,
            new_value,
            reason
        ) VALUES (
            auth.uid(),
            'MANUAL_ATTENDANCE_OVERRIDE',
            'attendance_records',
            NEW.id::text,
            jsonb_build_object(
                'student_id', OLD.student_id,
                'status', OLD.status,
                'verification_method', OLD.verification_method,
                'presence_percentage', OLD.presence_percentage
            ),
            jsonb_build_object(
                'student_id', NEW.student_id,
                'status', NEW.status,
                'verification_method', NEW.verification_method,
                'presence_percentage', NEW.presence_percentage
            ),
            NEW.notes
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_attendance_record_override
    AFTER UPDATE ON attendance_records
    FOR EACH ROW
    EXECUTE FUNCTION trg_log_attendance_override();

-- Retention policy function: Purges presence_events older than 30 days
CREATE OR REPLACE FUNCTION purge_expired_presence_events(p_retention_days INT DEFAULT 30)
RETURNS BIGINT AS $$
DECLARE
    v_deleted_count BIGINT;
BEGIN
    DELETE FROM presence_events
    WHERE timestamp < (now() - (p_retention_days || ' days')::INTERVAL);
    
    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    
    INSERT INTO audit_logs (
        actor_id,
        action,
        target_type,
        target_id,
        new_value
    ) VALUES (
        NULL,
        'DATA_RETENTION_PURGE',
        'presence_events',
        'bulk',
        jsonb_build_object('purged_records', v_deleted_count, 'retention_days', p_retention_days)
    );

    RETURN v_deleted_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

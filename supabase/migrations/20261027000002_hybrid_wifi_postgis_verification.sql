-- ============================================================================
-- Hybrid Wi-Fi Scan & PostGIS GPS Polygon Verification Engine
-- Migration: 20261027000002_hybrid_wifi_postgis_verification.sql
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Ensure classrooms table has wifi_bssids array & building polygon
ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS wifi_bssids TEXT[] DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS building_polygon GEOGRAPHY(Polygon, 4326);

-- Seed with User's Exact Main Academic Building Polygon (IIIT Naya Raipur)
UPDATE classrooms
SET building_polygon = ST_SetSRID(
    ST_MakePolygon(
        ST_GeomFromText('LINESTRING(81.765671 21.128551, 81.766056 21.128031, 81.766608 21.128317, 81.766258 21.128873, 81.765671 21.128551)')
    ), 4326
)::geography
WHERE building_polygon IS NULL;

-- 2. Create PostGIS Polygon Boundary Checking RPC Function
CREATE OR REPLACE FUNCTION check_polygon_boundary(
    room_id UUID,
    user_lat DOUBLE PRECISION,
    user_lng DOUBLE PRECISION,
    buffer_meters DOUBLE PRECISION DEFAULT 8.0
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    classroom_geom GEOGRAPHY;
    is_inside BOOLEAN := false;
    distance_val DOUBLE PRECISION := 0.0;
BEGIN
    SELECT building_polygon INTO classroom_geom
    FROM classrooms
    WHERE id = room_id;

    IF classroom_geom IS NULL THEN
        -- Fallback to default CS_BLOCK coordinates if specific room geom not set
        classroom_geom := ST_SetSRID(
            ST_MakePolygon(
                ST_GeomFromText('LINESTRING(81.765671 21.128551, 81.766056 21.128031, 81.766608 21.128317, 81.766258 21.128873, 81.765671 21.128551)')
            ), 4326
        )::geography;
    END IF;

    -- ST_DWithin tests if point is inside polygon or within buffer_meters of edge (handles indoor GPS drift)
    is_inside := ST_DWithin(classroom_geom, ST_SetSRID(ST_MakePoint(user_lng, user_lat), 4326)::geography, buffer_meters);
    distance_val := ST_Distance(classroom_geom, ST_SetSRID(ST_MakePoint(user_lng, user_lat), 4326)::geography);

    RETURN jsonb_build_object(
        'is_inside', is_inside,
        'distance_meters', distance_val,
        'buffer_applied', buffer_meters
    );
END;
$$;

-- 3. Attendance Logs Table for Hybrid Verification Records
CREATE TABLE IF NOT EXISTS attendance_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID,
    student_id UUID,
    verified_by VARCHAR(20) NOT NULL, -- 'WIFI' or 'GPS'
    marked_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb,
    UNIQUE(session_id, student_id)
);

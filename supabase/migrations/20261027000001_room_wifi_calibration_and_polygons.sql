-- ============================================================================
-- Smart Attendance System — Room Wi-Fi Calibration & Building Polygon Schema
-- Migration: 20261027000001_room_wifi_calibration_and_polygons.sql
-- ============================================================================

-- 1. Building Polygons Table (GeoJSON boundary drawn from geojson.io)
CREATE TABLE IF NOT EXISTS building_polygons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID REFERENCES institutions(id) ON DELETE CASCADE,
    name TEXT NOT NULL UNIQUE, -- e.g. "CS_BLOCK", "AC_COMPLEX", "ADMIN_BLOCK"
    description TEXT,
    geojson JSONB NOT NULL, -- GeoJSON Polygon coordinates e.g. {"type": "Polygon", "coordinates": [[[81.766, 21.128], ...]]}
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Enhance Classrooms Table with Wi-Fi Calibration & Polygon Link
ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS polygon_name TEXT DEFAULT 'CS_BLOCK',
    ADD COLUMN IF NOT EXISTS polygon_id UUID REFERENCES building_polygons(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS calibrated_aps JSONB DEFAULT '[]', -- Array of {bssid, ssid, rssi, frequencyMhz}
    ADD COLUMN IF NOT EXISTS is_calibrated BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS calibrated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS calibrated_by TEXT;

-- 3. Seed Default CS_BLOCK Polygon (IIIT Naya Raipur Computer Science Block rooftop)
INSERT INTO building_polygons (name, description, geojson)
VALUES (
    'CS_BLOCK',
    'Computer Science & Engineering Building Rooftop Polygon',
    '{
        "type": "Polygon",
        "coordinates": [
            [
                [81.765820, 21.128150],
                [81.766540, 21.128150],
                [81.766540, 21.128850],
                [81.765820, 21.128850],
                [81.765820, 21.128150]
            ]
        ]
    }'::jsonb
) ON CONFLICT (name) DO NOTHING;

-- 4. Ensure Room 101, Room A-204, and Room A-302 exist and link to CS_BLOCK
INSERT INTO classrooms (name, building, floor, capacity, polygon_name, wifi_ssid, wifi_bssid, secondary_bssids, is_calibrated)
VALUES
    ('Room 101', 'CS & IT Academic Block', 1, 60, 'CS_BLOCK', 'Campus-Secure', 'A4:2B:B0:8C:12:01', ARRAY['A4:2B:B0:8C:12:02', 'A4:2B:B0:8C:12:03'], false),
    ('Room A-204', 'Academic Complex Block A', 2, 60, 'CS_BLOCK', 'Campus-Secure', 'A4:2B:B0:8C:12:EF', ARRAY['A4:2B:B0:8C:12:F0', 'A4:2B:B0:8C:12:F1'], true),
    ('Room A-302', 'Academic Complex Block A', 3, 45, 'CS_BLOCK', 'Campus-Secure', 'A4:2B:B0:8C:13:10', ARRAY['A4:2B:B0:8C:13:11', 'A4:2B:B0:8C:13:12'], true)
ON CONFLICT DO NOTHING;

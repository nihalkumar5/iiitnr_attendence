-- ============================================================================
-- Smart Attendance System — Realistic Seed Data
-- ============================================================================

-- 1. Create Demo Institution
INSERT INTO institutions (id, name, code, settings)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'Indian Institute of Technology (Demo Campus)',
    'IIT-DEMO',
    '{
        "present_threshold": 0.60,
        "review_threshold": 0.20,
        "rssi_threshold": -80,
        "scan_interval_seconds": 60,
        "token_rotation_seconds": 30
    }'::jsonb
) ON CONFLICT DO NOTHING;

-- 2. Department
INSERT INTO departments (id, institution_id, name, code)
VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'Department of Computer Science & Engineering',
    'CSE'
) ON CONFLICT DO NOTHING;

-- 3. Program
INSERT INTO programs (id, department_id, name, code, duration_years)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001',
    'M.Tech in Data Science & AI',
    'MT-DSAI',
    2
) ON CONFLICT DO NOTHING;

-- 4. Section
INSERT INTO sections (id, program_id, semester, name, academic_year)
VALUES (
    'd0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    1,
    'Section A',
    '2026-2027'
) ON CONFLICT DO NOTHING;

-- 5. Subject
INSERT INTO subjects (id, department_id, name, code, credits)
VALUES (
    'e0000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001',
    'Data Structures & Algorithms',
    'CS501',
    4
),
(
    'e0000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000001',
    'Machine Learning',
    'CS502',
    4
) ON CONFLICT DO NOTHING;

-- 6. Users: Teacher Dr. Sharma and Admin
-- Note: auth.users entries would normally be created via supabase auth; for local db testing:
INSERT INTO auth.users (id, email, raw_user_meta_data, created_at, updated_at)
VALUES 
    ('10000000-0000-0000-0000-000000000001', 'sharma@iitdemo.edu', '{"name":"Dr. Sharma","role":"TEACHER"}'::jsonb, now(), now()),
    ('10000000-0000-0000-0000-000000000002', 'admin@iitdemo.edu', '{"name":"Admin Office","role":"SUPER_ADMIN"}'::jsonb, now(), now()),
    ('10000000-0000-0000-0000-000000000003', 'rahul@student.edu', '{"name":"Rahul Kumar","role":"STUDENT"}'::jsonb, now(), now()),
    ('10000000-0000-0000-0000-000000000004', 'aman@student.edu', '{"name":"Aman Singh","role":"STUDENT"}'::jsonb, now(), now()),
    ('10000000-0000-0000-0000-000000000005', 'priya@student.edu', '{"name":"Priya Sharma","role":"STUDENT"}'::jsonb, now(), now()),
    ('10000000-0000-0000-0000-000000000006', 'karan@student.edu', '{"name":"Karan Verma","role":"STUDENT"}'::jsonb, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, institution_id, name, email, role)
VALUES 
    ('10000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'Dr. Sharma', 'sharma@iitdemo.edu', 'TEACHER'),
    ('10000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', 'Prof. Narayan (Admin)', 'admin@iitdemo.edu', 'SUPER_ADMIN'),
    ('10000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'Rahul Kumar', 'rahul@student.edu', 'STUDENT'),
    ('10000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001', 'Aman Singh', 'aman@student.edu', 'STUDENT'),
    ('10000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000001', 'Priya Sharma', 'priya@student.edu', 'STUDENT'),
    ('10000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000001', 'Karan Verma', 'karan@student.edu', 'STUDENT')
ON CONFLICT (id) DO NOTHING;

-- 7. Teacher Record
INSERT INTO teachers (id, user_id, employee_id, department_id, designation)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'EMP-CSE-102',
    'b0000000-0000-0000-0000-000000000001',
    'Associate Professor'
) ON CONFLICT DO NOTHING;

-- 8. Students Records
INSERT INTO students (id, user_id, roll_number, program_id, semester, section_id)
VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003', '26DSAI001', 'c0000000-0000-0000-0000-000000000001', 1, 'd0000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004', '26DSAI002', 'c0000000-0000-0000-0000-000000000001', 1, 'd0000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000005', '26DSAI003', 'c0000000-0000-0000-0000-000000000001', 1, 'd0000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000006', '26DSAI004', 'c0000000-0000-0000-0000-000000000001', 1, 'd0000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- 9. Student Devices
INSERT INTO devices (id, student_id, installation_id, device_model, os_version, platform, status)
VALUES
    ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'inst-pixel8-rahul-001', 'Pixel 8 Pro', 'Android 15', 'ANDROID', 'ACTIVE'),
    ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', 'inst-s23-aman-002', 'Samsung Galaxy S23', 'Android 14', 'ANDROID', 'ACTIVE'),
    ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', 'inst-oneplus-priya-003', 'OnePlus 12', 'Android 14', 'ANDROID', 'ACTIVE'),
    ('40000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000004', 'inst-moto-karan-004', 'Motorola Edge 50', 'Android 14', 'ANDROID', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 10. Classrooms Infrastructure
INSERT INTO classrooms (id, institution_id, name, building, floor, capacity, wifi_ssid, wifi_bssid, secondary_bssids, beacon_uuid, beacon_major, beacon_minor, is_rtt_supported)
VALUES (
    '60000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'Room A-204',
    'Academic Complex Block A',
    2,
    60,
    'IIT-Campus-WiFi',
    'a4:2b:b0:12:34:56',
    ARRAY['a4:2b:b0:12:34:57'],
    '0000fd5a-0000-1000-8000-00805f9b34fb',
    10,
    204,
    true
) ON CONFLICT DO NOTHING;

-- 11. Class Timetable Slot
INSERT INTO classes (id, subject_id, teacher_id, section_id, classroom_id, room, day_of_week, start_time, end_time)
VALUES (
    '50000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000001',
    'Room A-204',
    3, -- Wednesday
    '10:00:00',
    '11:00:00'
) ON CONFLICT DO NOTHING;

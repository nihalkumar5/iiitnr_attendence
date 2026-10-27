import { NextResponse } from 'next/server';
import { supabase } from '@/lib/supabaseClient';

const DEV_MODE = process.env.DEV_MODE === 'true' || process.env.NODE_ENV !== 'production';

// Fallback Ray-Casting Algorithm for Main Academic Building (IIIT Naya Raipur)
const MAIN_BUILDING_COORDS: [number, number][] = [
  [81.765671, 21.128551],
  [81.766056, 21.128031],
  [81.766608, 21.128317],
  [81.766258, 21.128873],
  [81.765671, 21.128551]
];

function isInsidePolygon(lat: number, lng: number, poly: [number, number][]): boolean {
  let inside = false;
  let j = poly.length - 1;
  for (let i = 0; i < poly.length; i++) {
    const [xi, yi] = poly[i]; // xi = lng, yi = lat
    const [xj, yj] = poly[j];
    const intersect = ((yi > lat) !== (yj > lat)) &&
      (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
    if (intersect) inside = !inside;
    j = i;
  }
  return inside;
}

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const {
      student_id,
      session_id,
      pin,
      lat,
      lng,
      is_mocked,
      scanned_bssids
    } = body;

    // 1. Anti-Spoof Fake GPS Check
    if (is_mocked && !DEV_MODE) {
      return NextResponse.json(
        { error: "Fake GPS application detected! Attendance blocked." },
        { status: 400 }
      );
    }

    // 2. Query Session & Classroom from Supabase
    let classroom: any = null;
    let session: any = null;

    try {
      const { data: sessionData, error: sessionErr } = await supabase
        .from('sessions')
        .select('*, classrooms(*)')
        .eq('id', session_id)
        .single();

      if (!sessionErr && sessionData) {
        session = sessionData;
        classroom = sessionData.classrooms;
      }
    } catch (_e) {
      // Offline fallback if Supabase project paused
    }

    // Default classroom APs for offline fallback (Room 101 / CS Block)
    const knownClassroomBssids: string[] = classroom?.wifi_bssids?.map((b: string) => b.toLowerCase()) || [
      'f2:0a:f6:b1:c1:07',
      '7c:a9:6b:cd:bb:a5',
      '40:86:cb:81:b6:70',
      'a4:2b:b0:8c:12:01',
      'a4:2b:b0:8c:12:ef'
    ];

    // 3. HYBRID LOCATION VERIFICATION (Wi-Fi OR GPS Polygon)
    let verifiedBy: 'WIFI' | 'GPS' | null = null;

    // Check A: Wi-Fi BSSID Match (Indoor Zero-GPS Failure Check)
    const normalizedScanned: string[] = (scanned_bssids || []).map((b: string) => b.toLowerCase().trim());
    const hasWifiMatch = normalizedScanned.some((bssid: string) =>
      knownClassroomBssids.includes(bssid)
    );

    if (hasWifiMatch) {
      verifiedBy = 'WIFI';
    } else if (lat != null && lng != null) {
      // Check B: GPS Polygon Boundary Check (8-meter buffer applied)
      let isInside = false;

      try {
        if (classroom?.id) {
          const { data: polyRes } = await supabase.rpc('check_polygon_boundary', {
            room_id: classroom.id,
            user_lat: Number(lat),
            user_lng: Number(lng),
            buffer_meters: 8.0
          });
          if (polyRes?.is_inside) {
            isInside = true;
          }
        }
      } catch (_rpcErr) {
        // Fallback to local ray-casting if RPC unavailable
      }

      if (!isInside) {
        isInside = isInsidePolygon(Number(lat), Number(lng), MAIN_BUILDING_COORDS);
      }

      if (isInside) {
        verifiedBy = 'GPS';
      }
    }

    // If both Wi-Fi match and GPS Polygon fail -> Strict Rejection
    if (!verifiedBy) {
      return NextResponse.json(
        {
          error: "Location mismatch! Aap class ya campus ke andar nahi hain.",
          details: "Neither verified Room Wi-Fi BSSID nor Main Academic Building GPS Polygon matched."
        },
        { status: 403 }
      );
    }

    // 4. Record Attendance Log
    try {
      await supabase
        .from('attendance_logs')
        .insert([{
          session_id,
          student_id,
          verified_by: verifiedBy,
          marked_at: new Date().toISOString()
        }]);
    } catch (_insertErr) {
      // Silent on local offline mode
    }

    return NextResponse.json({
      success: true,
      verified_by: verifiedBy,
      message: `Attendance marked successfully via ${verifiedBy}!`
    });

  } catch (err: any) {
    return NextResponse.json(
      { error: err?.message || "Internal Server Error" },
      { status: 500 }
    );
  }
}

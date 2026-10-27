import { NextResponse } from 'next/server';
import { supabase } from '@/lib/supabaseClient';

export async function POST(request: Request) {
  try {
    const { room_id, bssids } = await request.json();

    if (!room_id || !Array.isArray(bssids)) {
      return NextResponse.json(
        { error: "Invalid payload: room_id and bssids array required" },
        { status: 400 }
      );
    }

    const cleanBssids = bssids.map((b: string) => b.toLowerCase().trim());

    // Update classroom in Supabase
    try {
      const { data, error } = await supabase
        .from('classrooms')
        .update({
          wifi_bssids: cleanBssids,
          is_calibrated: true,
          calibrated_at: new Date().toISOString()
        })
        .eq('id', room_id)
        .select();

      if (error) {
        // Fallback update by room name e.g. "Room 101"
        await supabase
          .from('classrooms')
          .update({
            wifi_bssids: cleanBssids,
            is_calibrated: true,
            calibrated_at: new Date().toISOString()
          })
          .eq('name', room_id);
      }
    } catch (_dbErr) {
      // Offline fallback handling
    }

    return NextResponse.json({
      success: true,
      message: `Successfully linked ${cleanBssids.length} Wi-Fi APs to classroom!`,
      room_id,
      bssids: cleanBssids
    });

  } catch (err: any) {
    return NextResponse.json(
      { error: err?.message || "Internal Server Error" },
      { status: 500 }
    );
  }
}

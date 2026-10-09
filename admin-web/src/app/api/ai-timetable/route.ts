import { NextResponse } from "next/server";

export interface ParsedLecture {
  subjectName: string;
  subjectCode: string;
  program: string;
  room: string;
  dayOfWeek: number; // 1 = Monday ... 6 = Saturday
  startTime: string; // HH:mm:ss
  endTime: string;   // HH:mm:ss
}

const DEFAULT_GEMINI_KEY = Buffer.from(
  "QVEuQWI4Uk42S1lac0ZQU3hLWnZ1SGJ0QkdzTUpsTUNncWNPM2M2ay1KQlVxQlpibGU0U2c=",
  "base64"
).toString("utf-8");

const SYSTEM_PROMPT = `
You are an expert academic timetable and syllabus schedule parser.
Your task is to accurately analyze the provided timetable image or text and extract all class/lab/lecture slots.

CRITICAL TIMETABLE GRID & TIME EXTRACTION RULES:
1. UNIVERSITY TIMINGS:
   - Lectures and labs take place during daytime between 08:00 (8:00 AM) and 18:00 (6:00 PM).
   - ALL times MUST be converted to 24-hour ISO format "HH:mm:ss" (e.g., "09:00:00", "11:55:00", "14:00:00", "15:55:00").
   - Morning slots: 08:00 to 11:59 (e.g. 10:00 - 11:00 AM is startTime: "10:00:00", endTime: "11:00:00").
   - Noon slots: 12:00 to 13:00 (startTime: "12:00:00", endTime: "13:00:00").
   - Afternoon slots (PM): College timetables commonly list slots as 1:00 - 2:00, 2:00 - 3:00, 2:00 - 3:55, 3:00 - 4:00, 4:00 - 5:00.
     You MUST convert these to 24-hour PM times:
     - 1:00 -> "13:00:00"
     - 2:00 -> "14:00:00"
     - 3:00 -> "15:00:00"
     - 3:55 -> "15:55:00"
     - 4:00 -> "16:00:00"
     - 5:00 -> "17:00:00"
     NEVER output early morning hours like "02:00:00" or "03:00:00" for afternoon classes.

2. PERIOD COLUMNS OR LABELS:
   - If columns list period times (e.g. "09:00-10:00", "10:00-11:00", "11:00-11:55", "12:00-01:00", "02:00-03:55", etc.), match the row (Day) and column (Time) intersection carefully.
   - If periods are labeled only as Period 1, 2, 3... without times, map them to standard university periods:
     Period 1: "09:00:00" - "10:00:00"
     Period 2: "10:00:00" - "11:00:00"
     Period 3: "11:00:00" - "12:00:00"
     Period 4: "12:00:00" - "13:00:00"
     Period 5: "14:00:00" - "15:00:00"
     Period 6: "15:00:00" - "16:00:00"
     Period 7: "16:00:00" - "17:00:00"

3. CONTINUOUS & LAB SLOTS:
   - If the same subject spans 2 continuous periods (e.g., Period 5 and 6, or 2:00 PM to 4:00 PM lab):
     MERGE into one continuous entry: startTime: "14:00:00", endTime: "16:00:00".

4. REQUIRED OUTPUT FIELDS FOR EACH CLASS:
   - subjectName: Full subject or course name (e.g. "Digital Transformation-I", "Data Structures and Algorithm Analysis", "Advanced Operating Systems").
   - subjectCode: Academic course code (e.g. "DT50-363", "DSA501", "CS302"). If not explicitly given, generate a clean 4-6 char uppercase code.
   - program: Department/branch/batch (e.g. "M.Tech I Semester DSAI", "B.Tech CSE Sem 5"). Default: "M.Tech I Semester DSAI".
   - room: Classroom/Lab number (e.g. "Room 319", "Room A-204", "Lab 3"). Default: "Room 319".
   - dayOfWeek: Integer representing the day of the week:
       1 = Monday
       2 = Tuesday
       3 = Wednesday
       4 = Thursday
       5 = Friday
       6 = Saturday
   - startTime: 24-hour "HH:mm:ss"
   - endTime: 24-hour "HH:mm:ss"

CRITICAL FORMAT REQUIREMENT:
Return ONLY a valid JSON array of lecture objects. No markdown ticks, no preamble, no commentary.
Example:
[
  {
    "subjectName": "Data Structures and Algorithm Analysis",
    "subjectCode": "DSA501",
    "program": "M.Tech I Semester DSAI",
    "room": "Room 319",
    "dayOfWeek": 5,
    "startTime": "11:00:00",
    "endTime": "11:55:00"
  },
  {
    "subjectName": "Digital Transformation-I",
    "subjectCode": "DT50-363",
    "program": "M.Tech I Semester DSAI",
    "room": "Room 319",
    "dayOfWeek": 5,
    "startTime": "14:00:00",
    "endTime": "15:55:00"
  }
]
`.trim();

function parseTimeMinutes(t: string): number {
  if (!t) return 0;
  const parts = t.split(":").map(Number);
  return (parts[0] || 0) * 60 + (parts[1] || 0);
}

function normalizeTime(t: any, defaultTime = "10:00:00"): string {
  if (!t || typeof t !== "string") return defaultTime;
  let clean = t.trim().toLowerCase().replace(".", ":");
  const isPm = clean.includes("pm");
  const isAm = clean.includes("am");
  clean = clean.replace(/[^\d:]/g, "");
  const parts = clean.split(":").map(Number);
  let h = parts[0] ?? 10;
  const m = parts[1] ?? 0;
  const s = parts[2] ?? 0;

  if (isPm && h < 12) {
    h += 12;
  } else if (isAm && h === 12) {
    h = 0;
  } else if (!isPm && !isAm) {
    // University timetable heuristic: 1 to 7 is PM (13:00 to 19:00)
    if (h >= 1 && h <= 7) {
      h += 12;
    }
  }
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function mergeConsecutiveLectures(lectures: ParsedLecture[]): ParsedLecture[] {
  if (lectures.length <= 1) return lectures;

  const grouped = new Map<number, ParsedLecture[]>();
  for (const lec of lectures) {
    const list = grouped.get(lec.dayOfWeek) || [];
    list.push(lec);
    grouped.set(lec.dayOfWeek, list);
  }

  const result: ParsedLecture[] = [];
  for (const [, dayList] of grouped) {
    dayList.sort((a, b) => parseTimeMinutes(a.startTime) - parseTimeMinutes(b.startTime));
    const merged: ParsedLecture[] = [];

    for (const curr of dayList) {
      if (merged.length === 0) {
        merged.push(curr);
        continue;
      }
      const prev = merged[merged.length - 1];
      const sameCode = prev.subjectCode.toUpperCase() === curr.subjectCode.toUpperCase();
      const sameName = prev.subjectName.toLowerCase().trim() === curr.subjectName.toLowerCase().trim();
      const prevEnd = parseTimeMinutes(prev.endTime);
      const currStart = parseTimeMinutes(curr.startTime);
      const currEnd = parseTimeMinutes(curr.endTime);

      if ((sameCode || sameName) && currStart <= prevEnd + 20 && currEnd > prevEnd) {
        merged[merged.length - 1] = {
          ...prev,
          endTime: curr.endTime,
          room: prev.room || curr.room,
          program: prev.program || curr.program,
        };
      } else {
        merged.push(curr);
      }
    }
    result.push(...merged);
  }

  return result.sort((a, b) => {
    if (a.dayOfWeek !== b.dayOfWeek) return a.dayOfWeek - b.dayOfWeek;
    return parseTimeMinutes(a.startTime) - parseTimeMinutes(b.startTime);
  });
}

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { rawText, base64Image, mimeType = "image/jpeg", apiKey, teacherName } = body;

    const key = (apiKey && apiKey.trim()) || process.env.GEMINI_API_KEY || DEFAULT_GEMINI_KEY;
    const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=${key}`;

    const parts: any[] = [];
    const facultyContext = teacherName ? `\nFaculty Member: "${teacherName}". Extract all scheduled lecture/lab sessions for this professor.` : "";

    if (base64Image) {
      const cleanData = base64Image.includes(",") ? base64Image.split(",")[1] : base64Image;
      parts.push({
        text: `${SYSTEM_PROMPT}${facultyContext}`,
      });
      parts.push({
        inline_data: {
          mime_type: mimeType,
          data: cleanData,
        },
      });
    } else if (rawText) {
      parts.push({
        text: `${SYSTEM_PROMPT}${facultyContext}\n\nTimetable Content to Analyze:\n${rawText}`,
      });
    } else {
      return NextResponse.json(
        { success: false, error: "Please provide either an image or timetable text to analyze." },
        { status: 400 }
      );
    }

    const payload = {
      contents: [{ parts }],
    };

    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const errText = await response.text();
      console.error("Gemini API Error:", response.status, errText);
      return NextResponse.json(
        { success: false, error: `Gemini AI Error (${response.status}): ${errText.slice(0, 200)}` },
        { status: 500 }
      );
    }

    const data = await response.json();
    const textPart = data.candidates?.[0]?.content?.parts?.[0]?.text || "";

    let cleaned = textPart.trim();
    if (cleaned.startsWith("```json")) {
      cleaned = cleaned.replace(/^```json\s*/i, "").replace(/\s*```$/, "").trim();
    } else if (cleaned.startsWith("```")) {
      cleaned = cleaned.replace(/^```\s*/, "").replace(/\s*```$/, "").trim();
    }

    const startIdx = cleaned.indexOf("[");
    const endIdx = cleaned.lastIndexOf("]");
    if (startIdx !== -1 && endIdx !== -1) {
      cleaned = cleaned.substring(startIdx, endIdx + 1);
    }

    let parsed: ParsedLecture[] = [];
    try {
      parsed = JSON.parse(cleaned);
    } catch (parseErr) {
      console.error("JSON parse error:", cleaned);
      return NextResponse.json(
        { success: false, error: "Could not parse timetable output from AI. Please try again." },
        { status: 500 }
      );
    }

    // Post-process, validate and normalize all timings
    parsed = parsed.map((lec) => {
      let start = normalizeTime(lec.startTime, "10:00:00");
      let end = normalizeTime(lec.endTime, "11:00:00");
      let startMins = parseTimeMinutes(start);
      let endMins = parseTimeMinutes(end);

      if (endMins <= startMins) {
        endMins = startMins + 55;
        const endH = Math.floor(endMins / 60);
        const endM = endMins % 60;
        end = `${String(endH).padStart(2, "0")}:${String(endM).padStart(2, "0")}:00`;
      }

      let day = Number(lec.dayOfWeek) || 1;
      if (day < 1 || day > 6) day = 1;

      return {
        subjectName: (lec.subjectName || "Subject Lecture").trim(),
        subjectCode: (lec.subjectCode || "SUB101").trim().toUpperCase(),
        program: (lec.program || "M.Tech I Semester DSAI").trim(),
        room: (lec.room || "Room 319").trim(),
        dayOfWeek: day,
        startTime: start,
        endTime: end,
      };
    });

    const merged = mergeConsecutiveLectures(parsed);

    return NextResponse.json({
      success: true,
      lectures: merged,
    });
  } catch (error: any) {
    console.error("ai-timetable API exception:", error);
    return NextResponse.json(
      { success: false, error: error?.message || "Internal server error" },
      { status: 500 }
    );
  }
}

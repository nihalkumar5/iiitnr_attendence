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
You are an academic timetable analyzer AI.
Analyze the provided timetable image or schedule text and extract all lecture/lab classes.

CRITICAL CONSECUTIVE / CONTINUOUS SLOTS MERGE RULE:
If the same subject has consecutive, back-to-back, or continuous periods/slots on the same day (for example, Period 1 from 10:00 to 11:00 and Period 2 from 11:00 to 12:00, or a 2-hour / 3-hour practical lab):
You MUST automatically MERGE them into ONE single continuous lecture slot with the overall start time of the first period and the final end time of the last period (e.g. startTime: "10:00:00", endTime: "12:00:00").
DO NOT return separate entries for continuous periods of the same course. Merge them together into one slot.

For each lecture, extract:
- subjectName: Full subject or course name (e.g., "Computer Networks", "Database Management", "Machine Learning Lab").
- subjectCode: Subject code (e.g., "CS301", "DS-502", "IT204"). If not explicitly mentioned, generate an appropriate 4-6 char code.
- program: Academic program/branch/semester (e.g., "B.Tech CSE - Sem 5", "M.Tech I Semester DSAI"). Default to "M.Tech I Semester DSAI" if not specified.
- room: Classroom/Lab/Hall (e.g., "Room 319", "Room A-302", "Lab 2"). Default to "Room 319" if not specified.
- dayOfWeek: Integer representing the day of the week:
    1 = Monday
    2 = Tuesday
    3 = Wednesday
    4 = Thursday
    5 = Friday
    6 = Saturday
- startTime: Start time in 24-hour ISO format "HH:mm:ss" (e.g., "09:00:00", "10:30:00", "14:00:00").
- endTime: End time in 24-hour ISO format "HH:mm:ss" (e.g., "10:00:00", "11:30:00", "15:55:00").

IMPORTANT INSTRUCTION:
Return ONLY a raw valid JSON array of lecture objects.
Do not enclose in markdown ticks, do not include comments, no conversational text.
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
  }
]
`.trim();

function parseTimeMinutes(t: string): number {
  const parts = t.split(":").map(Number);
  return (parts[0] || 0) * 60 + (parts[1] || 0);
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

      if ((sameCode || sameName) && currStart <= prevEnd + 15 && currEnd > prevEnd) {
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
    const { rawText, base64Image, mimeType = "image/jpeg", apiKey } = body;

    const key = (apiKey && apiKey.trim()) || process.env.GEMINI_API_KEY || DEFAULT_GEMINI_KEY;
    const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=${key}`;

    const parts: any[] = [];

    if (base64Image) {
      const cleanData = base64Image.includes(",") ? base64Image.split(",")[1] : base64Image;
      parts.push({
        text: SYSTEM_PROMPT,
      });
      parts.push({
        inline_data: {
          mime_type: mimeType,
          data: cleanData,
        },
      });
    } else if (rawText) {
      parts.push({
        text: `${SYSTEM_PROMPT}\n\nTimetable Content to Analyze:\n${rawText}`,
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

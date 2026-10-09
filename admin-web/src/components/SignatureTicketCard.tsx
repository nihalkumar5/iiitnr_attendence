"use client";

import React, { useState, useEffect } from "react";
import { Play, QrCode, Users, Copy, Check } from "lucide-react";

export interface SignatureTicketCardProps {
  subjectName: string;
  subjectCode: string;
  roomNo?: string;
  timeSlot?: string;
  enrolledStudentsCount?: number;
  joinCode?: string;
  wifiSsid?: string;
  isLive?: boolean;
  attendanceCount?: number;
  eyebrow?: string;
  eyebrowBadge?: string;
  buttonText?: string;
  onStartAttendance: () => void;
  isStarting?: boolean;
  className?: string;
}

export function SignatureTicketCard({
  subjectName,
  subjectCode,
  roomNo = "Room A-204",
  timeSlot = "Friday, 02:00 PM – 03:55 PM",
  enrolledStudentsCount = 0,
  joinCode,
  wifiSsid = "Pranjal",
  isLive = false,
  attendanceCount = 0,
  eyebrow = "TODAY'S LECTURE",
  eyebrowBadge,
  buttonText = "Start Attendance",
  onStartAttendance,
  isStarting = false,
  className = ""
}: SignatureTicketCardProps) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [copiedCode, setCopiedCode] = useState(false);

  useEffect(() => {
    if (!isLive) return;
    const interval = setInterval(() => {
      setElapsedSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [isLive]);

  const mins = Math.floor(elapsedSeconds / 60);
  const secs = elapsedSeconds % 60;
  const timerStr = `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!joinCode) return;
    navigator.clipboard.writeText(joinCode);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const badgeText = eyebrowBadge || timeSlot;

  return (
    <div
      onClick={onStartAttendance}
      className={`group relative overflow-hidden rounded-[26px] bg-[#0A0E17] border border-slate-800/90 text-white shadow-xl shadow-slate-950/25 transition-all duration-300 hover:border-slate-700 hover:shadow-2xl hover:shadow-slate-950/40 cursor-pointer ${className}`}
    >
      {/* Semicircular Ticket Edge Notches */}
      <div 
        aria-hidden="true" 
        className="absolute -left-3.5 bottom-[70px] w-7 h-7 rounded-full bg-[#F8FAFC] border border-slate-300/80 shadow-[inset_-2px_0_4px_rgba(0,0,0,0.06)] z-20 pointer-events-none" 
      />
      <div 
        aria-hidden="true" 
        className="absolute -right-3.5 bottom-[70px] w-7 h-7 rounded-full bg-[#F8FAFC] border border-slate-300/80 shadow-[inset_2px_0_4px_rgba(0,0,0,0.06)] z-20 pointer-events-none" 
      />

      {/* Subtle Perforated Dashed Line Connecting Notches */}
      <div 
        aria-hidden="true" 
        className="absolute left-4 right-4 bottom-[83px] border-t border-dashed border-slate-800/80 pointer-events-none z-10" 
      />

      {/* TOP TICKET BODY */}
      <div className="p-6 sm:p-7 pb-8 space-y-4">
        {/* Eyebrow Row: Status Tag (No duplicate "PENDING ATTENDANCE") & Time Badge */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-[3px] h-3.5 rounded-full bg-[#818CF8]" />
            <span className="text-[10px] sm:text-[11px] font-bold uppercase tracking-[0.25em] text-[#C4B5FD]">
              {isLive ? "LIVE SESSION" : eyebrow}
            </span>

            {isLive ? (
              <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-[10px] font-bold font-mono tracking-wider uppercase ml-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                LIVE NOW · {timerStr}
              </span>
            ) : null}
          </div>

          {badgeText && !isLive && (
            <span className="px-2.5 py-0.5 rounded-full bg-white/10 border border-white/10 text-slate-300 text-[10px] sm:text-[11px] font-medium tracking-normal truncate max-w-[200px]">
              {badgeText}
            </span>
          )}
        </div>

        {/* Large Subject Heading */}
        <div>
          <h2 className="text-2xl sm:text-[30px] tracking-tight text-white leading-[1.16] text-balance break-words pr-2 font-bold">
            {subjectName}
          </h2>
        </div>

        {/* Secondary Metadata: Subject Code & Room (time is in top badge, no duplicate!) */}
        <div className="space-y-0.5 pt-0.5">
          <div className="text-xs sm:text-[13px] font-medium text-[#C4B5FD] tracking-normal">
            {subjectCode} · {roomNo}
          </div>
        </div>

        {/* Bottom Supporting Row: Enrolled count + Join Code */}
        <div className="flex items-center justify-between text-[11px] text-slate-400 font-medium pt-1">
          <span className="flex items-center gap-1.5">
            <Users className="w-3.5 h-3.5 text-slate-500" />
            <span>{enrolledStudentsCount} students enrolled</span>
          </span>

          {joinCode && (
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-2 py-0.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 text-[11px] font-mono transition-colors"
            >
              <span>Code: {joinCode}</span>
              {copiedCode ? (
                <Check className="w-3.5 h-3.5 text-emerald-400" />
              ) : (
                <Copy className="w-3.5 h-3.5 text-slate-400" />
              )}
            </button>
          )}
        </div>
      </div>

      {/* BOTTOM TICKET STUB */}
      <div className="px-6 sm:px-7 py-3.5 bg-slate-950/60 border-t border-slate-800/40">
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onStartAttendance();
          }}
          disabled={isStarting}
          className={`w-full py-3 px-4 rounded-xl font-bold text-xs uppercase tracking-wider flex items-center justify-center gap-2 transition-all shadow-md cursor-pointer active:scale-[0.99] ${
            isLive
              ? "bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-600/20"
              : "bg-blue-600 hover:bg-blue-500 text-white shadow-blue-600/25"
          }`}
        >
          {isStarting ? (
            <span>Preparing Session...</span>
          ) : isLive ? (
            <>
              <QrCode className="w-4 h-4 text-white" />
              <span>Continue Live Attendance ({attendanceCount} Present)</span>
            </>
          ) : (
            <>
              <Play className="w-3.5 h-3.5 fill-white text-white" />
              <span>{buttonText}</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
}

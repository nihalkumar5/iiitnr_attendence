"use client";

import React, { useState, useEffect } from "react";
import { Play, QrCode, Users, Copy, Check, Clock, MapPin } from "lucide-react";

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
  roomNo = "Room 319",
  timeSlot = "02:00 PM – 03:55 PM",
  enrolledStudentsCount = 0,
  joinCode,
  wifiSsid,
  isLive = false,
  attendanceCount = 0,
  eyebrow,
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

  const statusLabel = isLive ? "LIVE SESSION" : (eyebrow || "READY TO START");
  const displayTime = eyebrowBadge || timeSlot;

  return (
    <div
      onClick={onStartAttendance}
      className={`group relative overflow-hidden rounded-3xl bg-[#0B0F19] border border-slate-800/90 text-white shadow-xl shadow-slate-950/25 transition-all duration-300 hover:border-slate-700 hover:shadow-2xl hover:shadow-slate-950/40 cursor-pointer ${className}`}
    >
      {/* Ambient gradient glow on top */}
      <div 
        aria-hidden="true"
        className="absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-blue-500/10 via-indigo-500/5 to-transparent pointer-events-none"
      />

      <div className="relative p-6 sm:p-7 space-y-4">
        {/* Top Header: Status Pill (Left) & Time Badge (Right) */}
        <div className="flex items-center justify-between gap-2">
          {isLive ? (
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-xs font-bold tracking-wide">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span>LIVE · {timerStr}</span>
            </div>
          ) : (
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-500/15 border border-indigo-500/25 text-indigo-300 text-xs font-semibold tracking-wide">
              <span className="w-1.5 h-1.5 rounded-full bg-indigo-400" />
              <span>{statusLabel}</span>
            </div>
          )}

          {displayTime && (
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/5 border border-white/10 text-slate-300 text-xs font-medium">
              <Clock className="w-3.5 h-3.5 text-slate-400" />
              <span>{displayTime}</span>
            </div>
          )}
        </div>

        {/* Subject Title */}
        <div className="pt-1">
          <h2 className="text-2xl sm:text-[26px] font-bold text-white tracking-tight leading-snug break-words">
            {subjectName}
          </h2>
        </div>

        {/* Course Code & Location Pill */}
        <div className="flex items-center gap-2.5 text-xs">
          <span className="px-2.5 py-0.5 rounded-lg bg-indigo-950/80 border border-indigo-700/50 text-indigo-200 font-mono font-bold tracking-wide">
            {subjectCode}
          </span>
          <span className="flex items-center gap-1 text-slate-400 font-medium">
            <MapPin className="w-3.5 h-3.5 text-slate-500" />
            <span>{roomNo}</span>
          </span>
        </div>

        {/* Separator Line */}
        <div className="border-t border-slate-800/80 pt-3 flex items-center justify-between text-xs text-slate-400">
          <span className="flex items-center gap-1.5">
            <Users className="w-4 h-4 text-slate-500" />
            <span>{enrolledStudentsCount} enrolled</span>
          </span>

          {joinCode && (
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 font-mono transition-all"
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

        {/* Primary Action Button */}
        <div className="pt-1">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onStartAttendance();
            }}
            disabled={isStarting}
            className={`w-full py-3.5 px-5 rounded-2xl font-bold text-sm tracking-wide flex items-center justify-center gap-2.5 transition-all shadow-lg cursor-pointer active:scale-[0.99] ${
              isLive
                ? "bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-600/25"
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
                <Play className="w-4 h-4 fill-white text-white" />
                <span>{buttonText}</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

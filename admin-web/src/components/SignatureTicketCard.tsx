"use client";

import React from "react";
import { ArrowUpRight, Play, Radio, Users, Wifi } from "lucide-react";

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
  onStartAttendance: () => void;
  isStarting?: boolean;
  className?: string;
}

export function SignatureTicketCard({
  subjectName,
  subjectCode,
  roomNo = "Room A-204",
  timeSlot = "Today · 10:00 – 11:00 AM",
  enrolledStudentsCount = 0,
  joinCode,
  wifiSsid = "Pranjal",
  isLive = false,
  attendanceCount = 0,
  onStartAttendance,
  isStarting = false,
  className = ""
}: SignatureTicketCardProps) {
  return (
    <div
      onClick={onStartAttendance}
      className={`group relative overflow-hidden rounded-[26px] bg-[#0A0E17] border border-slate-800/90 text-white shadow-xl shadow-slate-950/25 transition-all duration-300 hover:border-slate-700 hover:shadow-2xl hover:shadow-slate-950/40 cursor-pointer ${className}`}
    >
      {/* Semicircular Ticket Edge Notches */}
      <div 
        aria-hidden="true" 
        className="absolute -left-3.5 bottom-[70px] w-7 h-7 rounded-full bg-slate-50 border border-slate-300/80 shadow-[inset_-2px_0_4px_rgba(0,0,0,0.06)] z-20 pointer-events-none" 
      />
      <div 
        aria-hidden="true" 
        className="absolute -right-3.5 bottom-[70px] w-7 h-7 rounded-full bg-slate-50 border border-slate-300/80 shadow-[inset_2px_0_4px_rgba(0,0,0,0.06)] z-20 pointer-events-none" 
      />

      {/* Subtle Perforated Dashed Line Connecting Notches */}
      <div 
        aria-hidden="true" 
        className="absolute left-4 right-4 bottom-[83px] border-t border-dashed border-slate-800/80 pointer-events-none z-10" 
      />

      {/* TOP TICKET BODY */}
      <div className="p-6 sm:p-7 pb-8 space-y-4">
        {/* Eyebrow Row: NEXT CLASS & Circular Arrow Action */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-[10px] sm:text-[11px] font-bold uppercase tracking-[0.25em] text-[#C4B5FD]">
              NEXT CLASS
            </span>

            {isLive ? (
              <span className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-[10px] font-bold tracking-wider uppercase">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                Live ({attendanceCount})
              </span>
            ) : null}
          </div>

          {/* Restrained Lavender Circular Arrow Action Button */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onStartAttendance();
            }}
            disabled={isStarting}
            className="w-10 h-10 rounded-full bg-[#EDE9FE]/12 hover:bg-[#EDE9FE]/22 active:bg-[#EDE9FE]/30 border border-[#C4B5FD]/35 text-[#C4B5FD] flex items-center justify-center transition-all group-hover:scale-105 active:scale-95 shadow-sm cursor-pointer shrink-0"
            title={isLive ? "Continue Live Attendance" : "Start Live Attendance"}
          >
            <ArrowUpRight className="w-5 h-5 transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
          </button>
        </div>

        {/* Large Subject Heading (Dominant Typographic Element) */}
        <h2 className="text-2xl sm:text-[30px] font-extrabold tracking-tight text-white leading-[1.16] text-balance break-words pr-2">
          {subjectName || "Upcoming Class"}
        </h2>

        {/* Restrained Lavender Vertical Rule & Secondary Metadata */}
        <div className="flex items-stretch gap-3.5 pt-1 pb-1">
          <div className="w-[3px] rounded-full bg-[#C4B5FD] shrink-0" />
          <div className="space-y-0.5">
            <div className="text-xs sm:text-[13px] font-medium text-slate-300 tracking-normal">
              {timeSlot}
            </div>
            <div className="text-xs font-mono font-semibold text-slate-400">
              {subjectCode} · {roomNo}
            </div>
          </div>
        </div>

        {/* Small Supporting Information */}
        <div className="flex items-center gap-4 text-[11px] text-slate-400 font-medium pt-1">
          <span className="flex items-center gap-1.5">
            <Users className="w-3.5 h-3.5 text-slate-500" />
            <span>{enrolledStudentsCount} enrolled</span>
          </span>

          {wifiSsid && (
            <span className="flex items-center gap-1.5">
              <Wifi className="w-3.5 h-3.5 text-slate-500" />
              <span className="font-mono text-slate-400">{wifiSsid}</span>
            </span>
          )}

          {joinCode && (
            <span className="font-mono text-[10px] text-slate-500 ml-auto hidden sm:inline">
              Join: {joinCode}
            </span>
          )}
        </div>
      </div>

      {/* BOTTOM TICKET STUB (CTA ACTION ROW) */}
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
              <Radio className="w-4 h-4 text-white animate-pulse" />
              <span>Continue Live Attendance ({attendanceCount} Present)</span>
            </>
          ) : (
            <>
              <Play className="w-3.5 h-3.5 fill-white text-white" />
              <span>Start Live Attendance</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
}

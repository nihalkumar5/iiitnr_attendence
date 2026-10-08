"use client";

import Link from "next/link";
import { 
  GraduationCap, 
  BookOpen, 
  ArrowRight, 
  ShieldCheck, 
  CheckCircle2,
  Sparkles
} from "lucide-react";

export default function HomeGateway() {
  return (
    <main className="min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between p-6 font-sans">
      {/* Top Brand Bar */}
      <header className="max-w-4xl mx-auto w-full flex items-center justify-between py-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white font-bold text-base shadow-sm">
            SA
          </div>
          <div>
            <span className="font-bold text-base tracking-tight text-slate-900">Smart Attendance</span>
            <span className="text-xs block text-slate-500 font-medium">Institutional Campus Portal</span>
          </div>
        </div>

        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          System Active
        </div>
      </header>

      {/* Main 2-Portal Cards Selection */}
      <div className="max-w-3xl mx-auto w-full my-auto py-8">
        <div className="text-center space-y-2 mb-8">
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">
            Choose Your Portal
          </h1>
          <p className="text-slate-600 text-sm max-w-md mx-auto">
            Select your role to access attendance, subjects, and live class verification.
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          {/* Card 1: Faculty Portal */}
          <Link
            href="/teacher"
            className="group bg-white border border-slate-200 hover:border-blue-500 rounded-2xl p-6 sm:p-8 transition-all duration-200 hover:shadow-lg flex flex-col justify-between"
          >
            <div>
              <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 border border-blue-200 flex items-center justify-center mb-5 group-hover:scale-105 transition-transform">
                <BookOpen className="w-6 h-6" />
              </div>

              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs font-bold uppercase tracking-wider text-blue-600">
                  Faculty Portal
                </span>
              </div>

              <h2 className="text-xl font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                Faculty Console
              </h2>

              <p className="text-xs text-slate-600 mt-2 leading-relaxed">
                Manage subjects, generate 6-character joining codes, start live attendance sessions, and submit lecture records.
              </p>

              <div className="mt-5 space-y-2 text-xs text-slate-600">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Today's Scheduled & Active Classes</span>
                </div>
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Subject Creation & Join Code Generation</span>
                </div>
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Real-time Live Student Roll Call</span>
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between text-blue-600 font-semibold text-sm group-hover:translate-x-1 transition-transform">
              <span>Open Faculty Portal</span>
              <ArrowRight className="w-4 h-4" />
            </div>
          </Link>

          {/* Card 2: Student Portal */}
          <Link
            href="/student"
            className="group bg-white border border-slate-200 hover:border-blue-500 rounded-2xl p-6 sm:p-8 transition-all duration-200 hover:shadow-lg flex flex-col justify-between"
          >
            <div>
              <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 border border-blue-200 flex items-center justify-center mb-5 group-hover:scale-105 transition-transform">
                <GraduationCap className="w-6 h-6" />
              </div>

              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs font-bold uppercase tracking-wider text-blue-600">
                  Student Portal
                </span>
              </div>

              <h2 className="text-xl font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                Student Attendance
              </h2>

              <p className="text-xs text-slate-600 mt-2 leading-relaxed">
                View your enrolled subjects, enter subject codes to join classes, monitor your attendance percentage, and check history.
              </p>

              <div className="mt-5 space-y-2 text-xs text-slate-600">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Today's Classes & Live Presence</span>
                </div>
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Join Subjects via Faculty Join Code</span>
                </div>
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>Attendance Percentage & History</span>
                </div>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between text-blue-600 font-semibold text-sm group-hover:translate-x-1 transition-transform">
              <span>Open Student Portal</span>
              <ArrowRight className="w-4 h-4" />
            </div>
          </Link>
        </div>
      </div>

      {/* Footer Info */}
      <footer className="max-w-4xl mx-auto w-full text-center py-4 border-t border-slate-200 text-xs text-slate-500 flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <ShieldCheck className="w-4 h-4 text-emerald-600" />
          <span>Smart Attendance Management System</span>
        </div>
        <span>IIIT-NR Unified Mobile & Web Experience</span>
      </footer>
    </main>
  );
}

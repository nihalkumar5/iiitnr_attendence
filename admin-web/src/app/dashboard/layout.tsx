"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { 
  LayoutDashboard, 
  BookOpen, 
  Users, 
  GraduationCap, 
  FileCheck2, 
  BarChart3, 
  Wifi, 
  Settings, 
  LogOut,
  ShieldCheck,
  Sparkles
} from "lucide-react";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();

  // If on attendance screen, render clean mobile-parity faculty console without SaaS admin sidebar
  if (pathname === "/dashboard/attendance") {
    return <>{children}</>;
  }

  return (
    <div className="min-h-screen flex bg-canvas">
      {/* Sidebar */}
      <aside className="w-64 border-r border-subtle bg-white flex flex-col justify-between p-4 shrink-0">
        <div>
          {/* Institution Header */}
          <div className="flex items-center gap-3 px-2 py-3 mb-6 border-b border-subtle">
            <div className="w-9 h-9 rounded-lg bg-primary text-white font-bold flex items-center justify-center text-sm">
              IIT
            </div>
            <div>
              <h2 className="font-bold text-sm tracking-tight text-primary">Smart Attendance</h2>
              <p className="text-[11px] text-secondary">Demo Campus Portal</p>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1">
            <Link
              href="/dashboard/demo"
              className="flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-bold bg-primary text-white shadow-sm mb-3"
            >
              <div className="flex items-center gap-3">
                <Sparkles className="w-4 h-4 text-warning" />
                <span>Live Demo Studio</span>
              </div>
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/20 font-mono">NEW</span>
            </Link>

            <Link
              href="/dashboard"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <LayoutDashboard className="w-4 h-4" />
              <span>Overview</span>
            </Link>

            <Link
              href="/dashboard/attendance"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <FileCheck2 className="w-4 h-4" />
              <span>Live Attendance</span>
            </Link>

            <Link
              href="/dashboard/attendance?tab=report"
              className="flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <div className="flex items-center gap-3">
                <BarChart3 className="w-4 h-4 text-blue-600" />
                <span>Daily Attendance Report</span>
              </div>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-blue-50 text-blue-700 font-bold border border-blue-200">
                IN-CONSOLE
              </span>
            </Link>

            <div className="pt-4 pb-2 px-3 text-[11px] font-semibold text-secondary uppercase tracking-wider">
              Academic Setup
            </div>

            <Link
              href="/dashboard/classes"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <BookOpen className="w-4 h-4" />
              <span>Classes & Timetable</span>
            </Link>

            <Link
              href="/dashboard/infrastructure"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <Wifi className="w-4 h-4" />
              <span>Wi-Fi & BLE Beacons</span>
            </Link>

            <Link
              href="/dashboard/students"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <GraduationCap className="w-4 h-4" />
              <span>Students & Devices</span>
            </Link>

            <Link
              href="/dashboard/teachers"
              className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium text-secondary hover:text-primary hover:bg-pill/60 transition-colors"
            >
              <Users className="w-4 h-4" />
              <span>Faculty Directory</span>
            </Link>
          </nav>
        </div>

        {/* Footer Actions */}
        <div className="pt-4 border-t border-subtle space-y-1">
          <div className="px-3 py-2 flex items-center gap-2 text-xs text-secondary">
            <ShieldCheck className="w-4 h-4 text-success" />
            <span>Hybrid Wi-Fi + BLE Engine</span>
          </div>

          <Link
            href="/"
            className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-danger hover:bg-danger/10 transition-colors"
          >
            <LogOut className="w-4 h-4" />
            <span>Sign Out</span>
          </Link>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Top Navbar */}
        <header className="h-16 border-b border-subtle bg-white px-8 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <span className="text-xs px-2.5 py-1 rounded-full bg-pill text-secondary font-medium">
              Academic Year 2026–2027
            </span>
          </div>

          <div className="flex items-center gap-4">
            <div className="text-right">
              <p className="text-xs font-semibold text-primary">Prof. Narayan</p>
              <p className="text-[11px] text-secondary">Super Administrator</p>
            </div>
            <div className="w-9 h-9 rounded-full bg-pill border border-subtle flex items-center justify-center font-bold text-xs text-primary">
              PN
            </div>
          </div>
        </header>

        {/* Page Container */}
        <main className="flex-1 p-8">
          {children}
        </main>
      </div>
    </div>
  );
}

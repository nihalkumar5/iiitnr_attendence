import { Users, GraduationCap, BookOpen, CheckCircle, Wifi, Radio, ArrowUpRight } from "lucide-react";
import Link from "next/link";

export default function AdminOverviewPage() {
  const kpis = [
    { label: "Total Students", value: "1,248", change: "+12 enrolled", icon: GraduationCap },
    { label: "Total Faculty", value: "64", change: "4 departments", icon: Users },
    { label: "Lectures Scheduled", value: "86", change: "Today's classes", icon: BookOpen },
    { label: "Campus Attendance", value: "91.2%", change: "+2.4% vs last week", icon: CheckCircle },
  ];

  const activeSessions = [
    {
      id: "sess-01",
      subject: "Data Structures & Algorithms",
      code: "CS501",
      teacher: "Dr. Sharma",
      room: "Room A-204",
      apBssid: "a4:2b:b0:12:34:56",
      detected: 47,
      total: 50,
      status: "ACTIVE",
      mode: "HYBRID (Wi-Fi + BLE)",
    },
    {
      id: "sess-02",
      subject: "Machine Learning Foundations",
      code: "CS502",
      teacher: "Dr. Mehra",
      room: "Room A-302",
      apBssid: "a4:2b:b0:12:34:88",
      detected: 42,
      total: 45,
      status: "ACTIVE",
      mode: "HYBRID (Wi-Fi + BLE)",
    },
    {
      id: "sess-03",
      subject: "Database Management Systems",
      code: "CS503",
      teacher: "Prof. Gupta",
      room: "Room B-105",
      apBssid: "a4:2b:b0:12:35:12",
      detected: 53,
      total: 55,
      status: "COMPLETED",
      mode: "BLE ONLY",
    },
  ];

  return (
    <div className="space-y-8">
      {/* Page Title */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-primary">Campus Attendance Overview</h1>
        <p className="text-sm text-secondary mt-1">Real-time telemetry from classroom Wi-Fi APs and BLE proximity beacons</p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        {kpis.map((kpi, i) => {
          const Icon = kpi.icon;
          return (
            <div key={i} className="card-clean p-5">
              <div className="flex items-center justify-between text-secondary">
                <span className="text-xs font-semibold uppercase tracking-wider">{kpi.label}</span>
                <Icon className="w-4 h-4" />
              </div>
              <div className="mt-3">
                <span className="text-3xl font-bold text-primary">{kpi.value}</span>
              </div>
              <div className="mt-2 text-xs text-secondary flex items-center gap-1">
                <span>{kpi.change}</span>
              </div>
            </div>
          );
        })}
      </div>

      {/* Live Active Lectures Section */}
      <div className="card-clean p-6">
        <div className="flex items-center justify-between mb-5">
          <div>
            <h2 className="text-base font-bold text-primary">Active Classrooms & Lectures</h2>
            <p className="text-xs text-secondary mt-0.5">Live session proximity tracking across academic blocks</p>
          </div>
          <Link
            href="/dashboard/attendance"
            className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
          >
            <span>View All Sessions</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-subtle text-[11px] font-semibold uppercase tracking-wider text-secondary">
                <th className="pb-3 font-semibold">Subject & Code</th>
                <th className="pb-3 font-semibold">Teacher</th>
                <th className="pb-3 font-semibold">Classroom & AP</th>
                <th className="pb-3 font-semibold">Sensing Mode</th>
                <th className="pb-3 font-semibold">Presence Ratio</th>
                <th className="pb-3 font-semibold text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-subtle">
              {activeSessions.map((session) => (
                <tr key={session.id} className="hover:bg-canvas/50 transition-colors">
                  <td className="py-3.5">
                    <p className="font-semibold text-primary">{session.subject}</p>
                    <p className="text-xs text-secondary font-mono">{session.code}</p>
                  </td>
                  <td className="py-3.5 text-secondary">{session.teacher}</td>
                  <td className="py-3.5">
                    <p className="font-medium text-primary">{session.room}</p>
                    <p className="text-[11px] text-secondary font-mono">BSSID: {session.apBssid}</p>
                  </td>
                  <td className="py-3.5">
                    <span className="text-xs px-2.5 py-1 rounded-full bg-pill text-primary font-medium flex items-center gap-1.5 w-fit">
                      <Wifi className="w-3 h-3 text-success" />
                      <span>{session.mode}</span>
                    </span>
                  </td>
                  <td className="py-3.5">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-primary">{session.detected}/{session.total}</span>
                      <span className="text-xs text-secondary">({Math.round((session.detected / session.total) * 100)}%)</span>
                    </div>
                  </td>
                  <td className="py-3.5 text-right">
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-bold ${
                        session.status === "ACTIVE"
                          ? "bg-success/10 text-success"
                          : "bg-pill text-secondary"
                      }`}
                    >
                      {session.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Multi-Modal Architecture Architecture Status Banner */}
      <div className="card-clean p-6 bg-gradient-to-r from-white to-[#F6F7F3] border border-subtle">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-success"></span>
              <h3 className="font-bold text-sm text-primary">Classroom Multi-Modal Sensing Operational</h3>
            </div>
            <p className="text-xs text-secondary mt-1 max-w-xl">
              Tier 1 Wi-Fi AP BSSID verification handles scale for 100+ concurrent students, while Tier 2 BLE ephemeral tokens and Tier 3 Wi-Fi RTT ranging prevent corridor spoofing.
            </p>
          </div>
          <Link
            href="/dashboard/infrastructure"
            className="px-4 py-2 bg-primary text-white text-xs font-semibold rounded-lg hover:bg-black transition-colors"
          >
            Manage AP & Beacon Mappings
          </Link>
        </div>
      </div>
    </div>
  );
}

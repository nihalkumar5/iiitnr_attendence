import { Check, X, HelpCircle, Wifi, Bluetooth, ArrowUpDown, Filter } from "lucide-react";

export default function AttendanceSessionsPage() {
  const sessionData = {
    subject: "Data Structures & Algorithms (CS501)",
    program: "M.Tech Data Science & AI · Semester 1 · Section A",
    teacher: "Dr. Sharma",
    room: "Room A-204 (AP BSSID: a4:2b:b0:12:34:56)",
    timeSlot: "10:00 – 11:00 AM (September 30, 2026)",
    summary: {
      total: 50,
      present: 46,
      review: 2,
      absent: 2,
    },
  };

  const students = [
    {
      id: "st-1",
      roll: "26DSAI001",
      name: "Rahul Kumar",
      wifiAp: true,
      ble: true,
      rttDist: "4.2m",
      coverage: 92,
      status: "PRESENT",
      method: "BLE_AUTO",
      diagnosis: "Wi-Fi AP & BLE proximity verified continuously",
    },
    {
      id: "st-2",
      roll: "26DSAI002",
      name: "Aman Singh",
      wifiAp: true,
      ble: true,
      rttDist: "6.8m",
      coverage: 71,
      status: "PRESENT",
      method: "BLE_AUTO",
      diagnosis: "Continuous classroom presence verified",
    },
    {
      id: "st-3",
      roll: "26DSAI003",
      name: "Priya Sharma",
      wifiAp: true,
      ble: false,
      rttDist: "5.1m",
      coverage: 80,
      status: "REVIEW",
      method: "BLE_AUTO",
      diagnosis: "Connected to Room AP-204, but BLE signal absent (Bluetooth off)",
    },
    {
      id: "st-4",
      roll: "26DSAI004",
      name: "Vikram Patel",
      wifiAp: true,
      ble: true,
      rttDist: "14.5m",
      coverage: 12,
      status: "REVIEW",
      method: "BLE_AUTO",
      diagnosis: "Brief presence only (12% timeline coverage)",
    },
    {
      id: "st-5",
      roll: "26DSAI005",
      name: "Karan Verma",
      wifiAp: false,
      ble: false,
      rttDist: "N/A",
      coverage: 0,
      status: "ABSENT",
      method: "BLE_AUTO",
      diagnosis: "No classroom Wi-Fi or BLE evidence (Outside room)",
    },
  ];

  return (
    <div className="space-y-6">
      {/* Session Title & Metadata Card */}
      <div className="card-clean p-6">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-subtle pb-5">
          <div>
            <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-pill text-primary">
              Session Telemetry Review
            </span>
            <h1 className="text-xl font-bold text-primary mt-2">{sessionData.subject}</h1>
            <p className="text-xs text-secondary mt-0.5">{sessionData.program} · {sessionData.teacher}</p>
          </div>

          <div className="text-right">
            <p className="text-sm font-semibold text-primary">{sessionData.room}</p>
            <p className="text-xs text-secondary">{sessionData.timeSlot}</p>
          </div>
        </div>

        {/* Attendance Summary Pills */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-5">
          <div className="p-4 rounded-xl bg-canvas border border-subtle">
            <p className="text-xs text-secondary font-medium">Total Students</p>
            <p className="text-2xl font-bold text-primary mt-1">{sessionData.summary.total}</p>
          </div>

          <div className="p-4 rounded-xl bg-success/10 border border-success/20">
            <p className="text-xs text-success font-medium">Verified Present (≥60%)</p>
            <p className="text-2xl font-bold text-success mt-1">{sessionData.summary.present}</p>
          </div>

          <div className="p-4 rounded-xl bg-warning/10 border border-warning/20">
            <p className="text-xs text-warning font-medium">Requires Review</p>
            <p className="text-2xl font-bold text-warning mt-1">{sessionData.summary.review}</p>
          </div>

          <div className="p-4 rounded-xl bg-danger/10 border border-danger/20">
            <p className="text-xs text-danger font-medium">Absent (&lt;20%)</p>
            <p className="text-2xl font-bold text-danger mt-1">{sessionData.summary.absent}</p>
          </div>
        </div>
      </div>

      {/* Student Multi-Sensor Roster Table */}
      <div className="card-clean p-6">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-base font-bold text-primary">Student Presence Roster & Sensor Fusion Breakdown</h2>
          <div className="flex items-center gap-2">
            <button className="px-3 py-1.5 rounded-lg border border-subtle text-xs font-medium text-secondary hover:text-primary flex items-center gap-1.5">
              <Filter className="w-3.5 h-3.5" />
              <span>Filter Status</span>
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-subtle text-[11px] font-semibold uppercase tracking-wider text-secondary">
                <th className="pb-3">Student Name</th>
                <th className="pb-3">Roll Number</th>
                <th className="pb-3 text-center">Wi-Fi AP</th>
                <th className="pb-3 text-center">BLE Signal</th>
                <th className="pb-3 text-center">Wi-Fi RTT</th>
                <th className="pb-3">Presence %</th>
                <th className="pb-3">Audit Diagnosis</th>
                <th className="pb-3 text-right">Classification</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-subtle">
              {students.map((st) => (
                <tr key={st.id} className="hover:bg-canvas/40 transition-colors">
                  <td className="py-3.5 font-semibold text-primary">{st.name}</td>
                  <td className="py-3.5 text-secondary font-mono text-xs">{st.roll}</td>
                  <td className="py-3.5 text-center">
                    {st.wifiAp ? (
                      <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-success/15 text-success">
                        <Check className="w-3.5 h-3.5" />
                      </span>
                    ) : (
                      <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-danger/15 text-danger">
                        <X className="w-3.5 h-3.5" />
                      </span>
                    )}
                  </td>
                  <td className="py-3.5 text-center">
                    {st.ble ? (
                      <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-success/15 text-success">
                        <Check className="w-3.5 h-3.5" />
                      </span>
                    ) : (
                      <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-danger/15 text-danger">
                        <X className="w-3.5 h-3.5" />
                      </span>
                    )}
                  </td>
                  <td className="py-3.5 text-center font-mono text-xs text-secondary">{st.rttDist}</td>
                  <td className="py-3.5">
                    <span className="font-bold text-primary">{st.coverage}%</span>
                  </td>
                  <td className="py-3.5 text-xs text-secondary max-w-xs truncate" title={st.diagnosis}>
                    {st.diagnosis}
                  </td>
                  <td className="py-3.5 text-right">
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-bold ${
                        st.status === "PRESENT"
                          ? "bg-success/10 text-success"
                          : st.status === "REVIEW"
                          ? "bg-warning/10 text-warning"
                          : "bg-danger/10 text-danger"
                      }`}
                    >
                      {st.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

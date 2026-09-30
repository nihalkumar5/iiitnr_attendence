"use client";

import { useState } from "react";
import { Download, Filter, FileText, CheckCircle2, Calendar } from "lucide-react";

export default function AttendanceReportsPage() {
  const [selectedDept, setSelectedDept] = useState("CSE");
  const [selectedSemester, setSelectedSemester] = useState("1");

  const reportRows = [
    { roll: "26DSAI001", name: "Rahul Kumar", total: 34, attended: 32, rate: "94.1%", status: "Eligible" },
    { roll: "26DSAI002", name: "Aman Singh", total: 34, attended: 30, rate: "88.2%", status: "Eligible" },
    { roll: "26DSAI003", name: "Priya Sharma", total: 34, attended: 26, rate: "76.5%", status: "Eligible" },
    { roll: "26DSAI004", name: "Vikram Patel", total: 34, attended: 22, rate: "64.7%", status: "Warning (<75%)" },
    { roll: "26DSAI005", name: "Karan Verma", total: 34, attended: 11, rate: "32.4%", status: "Critical Shortage" },
  ];

  const handleExportCsv = () => {
    const headers = ["Roll Number", "Student Name", "Total Lectures", "Attended", "Attendance Rate", "Eligibility"];
    const csvContent = [
      headers.join(","),
      ...reportRows.map(r => `"${r.roll}","${r.name}",${r.total},${r.attended},"${r.rate}","${r.status}"`)
    ].join("\n");

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `attendance_report_${selectedDept}_sem${selectedSemester}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Title & Action Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary">Institutional Attendance Reports</h1>
          <p className="text-sm text-secondary mt-1">Export official university compliance rosters and shortage lists</p>
        </div>

        <button
          onClick={handleExportCsv}
          className="px-4 py-2.5 bg-primary text-white text-xs font-semibold rounded-lg hover:bg-black transition-colors flex items-center gap-2 shadow-sm"
        >
          <Download className="w-4 h-4" />
          <span>Export Official CSV</span>
        </button>
      </div>

      {/* Filter Toolbar */}
      <div className="card-clean p-5 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
        <div>
          <label className="block text-xs font-semibold text-secondary uppercase tracking-wider mb-1.5">
            Department
          </label>
          <select
            value={selectedDept}
            onChange={(e) => setSelectedDept(e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-subtle bg-canvas text-sm focus:outline-none"
          >
            <option value="CSE">Computer Science & Engineering</option>
            <option value="AI">Data Science & Artificial Intelligence</option>
            <option value="ECE">Electronics & Communication</option>
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-secondary uppercase tracking-wider mb-1.5">
            Program
          </label>
          <select className="w-full px-3 py-2 rounded-lg border border-subtle bg-canvas text-sm focus:outline-none">
            <option>M.Tech in Data Science & AI</option>
            <option>B.Tech Computer Science</option>
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-secondary uppercase tracking-wider mb-1.5">
            Semester
          </label>
          <select
            value={selectedSemester}
            onChange={(e) => setSelectedSemester(e.target.value)}
            className="w-full px-3 py-2 rounded-lg border border-subtle bg-canvas text-sm focus:outline-none"
          >
            <option value="1">Semester 1 (Autumn 2026)</option>
            <option value="2">Semester 2 (Spring 2027)</option>
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-secondary uppercase tracking-wider mb-1.5">
            Compliance Threshold
          </label>
          <select className="w-full px-3 py-2 rounded-lg border border-subtle bg-canvas text-sm focus:outline-none">
            <option>Standard Policy (≥ 75%)</option>
            <option>Condonation Allowed (≥ 65%)</option>
          </select>
        </div>
      </div>

      {/* Reports Table */}
      <div className="card-clean p-6">
        <div className="flex items-center justify-between mb-5">
          <div>
            <h2 className="text-base font-bold text-primary">Cumulative Student Attendance Ledger</h2>
            <p className="text-xs text-secondary mt-0.5">Automated telemetry verified through Multi-Sensor Hybrid Engine</p>
          </div>
          <span className="text-xs px-2.5 py-1 rounded-full bg-pill text-secondary font-medium">
            5 Students Enrolled
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-subtle text-[11px] font-semibold uppercase tracking-wider text-secondary">
                <th className="pb-3">Roll Number</th>
                <th className="pb-3">Student Name</th>
                <th className="pb-3">Lectures Held</th>
                <th className="pb-3">Lectures Attended</th>
                <th className="pb-3">Attendance Rate</th>
                <th className="pb-3 text-right">Eligibility Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-subtle">
              {reportRows.map((row) => (
                <tr key={row.roll} className="hover:bg-canvas/40 transition-colors">
                  <td className="py-3.5 font-mono text-xs font-semibold text-primary">{row.roll}</td>
                  <td className="py-3.5 font-medium text-primary">{row.name}</td>
                  <td className="py-3.5 text-secondary">{row.total}</td>
                  <td className="py-3.5 font-bold text-primary">{row.attended}</td>
                  <td className="py-3.5">
                    <span className="font-bold text-primary">{row.rate}</span>
                  </td>
                  <td className="py-3.5 text-right">
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-bold ${
                        row.status === "Eligible"
                          ? "bg-success/10 text-success"
                          : row.status.includes("Warning")
                          ? "bg-warning/10 text-warning"
                          : "bg-danger/10 text-danger"
                      }`}
                    >
                      {row.status}
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

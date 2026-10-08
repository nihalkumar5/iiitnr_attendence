"use client";

import React, { useState, useEffect } from "react";
import { supabase } from "@/lib/supabaseClient";
import {
  GraduationCap,
  Search,
  Plus,
  ShieldCheck,
  Smartphone,
  RotateCcw,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  X,
  UserCheck,
  UserX,
  Lock,
  Clock,
} from "lucide-react";

interface StudentRecord {
  id: string;
  roll_number: string;
  semester: number;
  status: string;
  users?: {
    id: string;
    name: string;
    email: string;
  };
  programs?: {
    name: string;
    code: string;
  };
  devices?: {
    id: string;
    installation_id: string;
    platform: string;
    device_model: string;
    status: string;
    registered_at?: string;
    last_seen: string;
  }[];
}

export default function StudentsManagementPage() {
  const [students, setStudents] = useState<StudentRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterDevice, setFilterDevice] = useState<"ALL" | "BOUND" | "PENDING" | "UNBOUND">("ALL");

  // Modal State
  const [showAddModal, setShowAddModal] = useState(false);
  const [nameInput, setNameInput] = useState("");
  const [rollInput, setRollInput] = useState("");
  const [emailInput, setEmailInput] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    fetchStudents();
  }, []);

  const fetchStudents = async () => {
    setLoading(true);
    try {
      const { data, error } = await supabase
        .from("students")
        .select(`
          id,
          roll_number,
          semester,
          status,
          users ( id, name, email ),
          programs ( name, code ),
          devices ( id, installation_id, platform, device_model, status, registered_at, last_seen )
        `)
        .order("roll_number", { ascending: true });

      if (error) throw error;
      setStudents((data as any) || []);
    } catch (err: any) {
      console.error("Error fetching students:", err);
      setStatusMessage({ type: "error", text: "Failed to load students: " + err.message });
    } finally {
      setLoading(false);
    }
  };

  const handleAddStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nameInput.trim() || !rollInput.trim()) {
      setStatusMessage({ type: "error", text: "Please enter both Name and Roll Number." });
      return;
    }

    setIsSubmitting(true);
    try {
      const cleanRoll = rollInput.trim().toUpperCase();
      const cleanName = nameInput.trim();
      const cleanEmail = emailInput.trim() || `${cleanRoll.toLowerCase().replace(/[^a-z0-9]/g, "")}@student.iiitnr.edu.in`;

      // 1. Create User
      const { data: newUser, error: uErr } = await supabase
        .from("users")
        .insert({
          institution_id: "49625912-3ad0-4f77-8b39-9f20dc53d086",
          name: cleanName,
          email: cleanEmail,
          role: "STUDENT",
          is_active: true,
        })
        .select()
        .single();

      if (uErr) throw uErr;

      // 2. Create Student
      const { error: sErr } = await supabase.from("students").insert({
        user_id: newUser.id,
        roll_number: cleanRoll,
        program_id: "ce93026c-bb1c-463e-b7eb-cc01d86e5f33",
        section_id: "42bf3cde-a3a2-45ee-b82a-eec10845001a",
        semester: 5,
        status: "ACTIVE",
      });

      if (sErr) throw sErr;

      setStatusMessage({ type: "success", text: `Successfully registered student: ${cleanName} (${cleanRoll})` });
      setShowAddModal(false);
      setNameInput("");
      setRollInput("");
      setEmailInput("");
      fetchStudents();
    } catch (err: any) {
      setStatusMessage({ type: "error", text: "Registration error: " + err.message });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Faculty approves a student's new device
  const handleApproveDevice = async (studentId: string, deviceId: string, studentName: string) => {
    try {
      // 1. Block all previous active devices for this student
      await supabase
        .from("devices")
        .update({ status: "BLOCKED" })
        .eq("student_id", studentId)
        .neq("id", deviceId);

      // 2. Check the pending device record
      const { data: targetDev } = await supabase
        .from("devices")
        .select("id, installation_id")
        .eq("id", deviceId)
        .single();

      if (targetDev && targetDev.installation_id.startsWith("REQ-REBIND")) {
        // Unbind placeholder request: delete it so student's new phone can freshly bind as ACTIVE
        await supabase.from("devices").delete().eq("id", deviceId);
      } else {
        // Real new phone installation_id: activate it directly
        await supabase
          .from("devices")
          .update({ status: "ACTIVE" })
          .eq("id", deviceId);
      }

      setStatusMessage({
        type: "success",
        text: `✓ Approved device change for ${studentName}. Student is now permitted to use their new phone.`
      });
      fetchStudents();
    } catch (err: any) {
      setStatusMessage({ type: "error", text: "Failed to approve device: " + err.message });
    }
  };

  // Faculty rejects a device switch request
  const handleRejectDevice = async (deviceId: string, studentName: string) => {
    try {
      await supabase.from("devices").delete().eq("id", deviceId);
      setStatusMessage({
        type: "success",
        text: `Device change request for ${studentName} was rejected.`
      });
      fetchStudents();
    } catch (err: any) {
      setStatusMessage({ type: "error", text: "Failed to reject device: " + err.message });
    }
  };

  // Faculty manually unbinds a phone and optionally allows immediate rebind
  const handleUnbindDevice = async (deviceId: string, studentId: string, studentName: string, allowImmediateRebind: boolean = false) => {
    const actionText = allowImmediateRebind
      ? `Unbind and immediately permit ${studentName} to bind a new phone?`
      : `Unbind phone from ${studentName}? (Student will require faculty approval to bind a new phone).`;

    if (!confirm(actionText)) return;

    try {
      if (allowImmediateRebind) {
        // Delete or block old device so slate is clean
        await supabase.from("devices").update({ status: "BLOCKED" }).eq("id", deviceId);
        setStatusMessage({
          type: "success",
          text: `Device unbound for ${studentName}. Student can immediately bind their new phone.`
        });
      } else {
        // Block current device and insert a pending request record so admin approval is required
        await supabase.from("devices").update({ status: "BLOCKED" }).eq("id", deviceId);
        await supabase.from("devices").insert({
          student_id: studentId,
          installation_id: `REQ-REBIND-${Date.now()}`,
          device_model: "Faculty Initiated Unbind (Pending Permission)",
          platform: "WEB_PWA",
          status: "PENDING_APPROVAL"
        });
        setStatusMessage({
          type: "success",
          text: `Device unbound for ${studentName}. Binding a new device will require faculty approval.`
        });
      }
      fetchStudents();
    } catch (err: any) {
      setStatusMessage({ type: "error", text: "Failed to unbind device: " + err.message });
    }
  };

  const pendingStudents = students.filter((s) =>
    s.devices && s.devices.some((d) => d.status === "PENDING_APPROVAL")
  );

  const filtered = students.filter((s) => {
    const nameMatch = s.users?.name?.toLowerCase().includes(searchQuery.toLowerCase());
    const rollMatch = s.roll_number?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesSearch = nameMatch || rollMatch;

    const hasActiveDevice = s.devices && s.devices.some((d) => d.status === "ACTIVE");
    const hasPendingDevice = s.devices && s.devices.some((d) => d.status === "PENDING_APPROVAL");

    if (filterDevice === "BOUND") return matchesSearch && hasActiveDevice;
    if (filterDevice === "PENDING") return matchesSearch && hasPendingDevice;
    if (filterDevice === "UNBOUND") return matchesSearch && !hasActiveDevice && !hasPendingDevice;
    return matchesSearch;
  });

  const totalStudents = students.length;
  const boundStudents = students.filter((s) => s.devices && s.devices.some((d) => d.status === "ACTIVE")).length;
  const pendingCount = pendingStudents.length;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-primary flex items-center gap-2.5">
            <GraduationCap className="w-7 h-7 text-blue-600" />
            <span>Students & Device Binding</span>
          </h1>
          <p className="text-sm text-secondary mt-1">
            Manage student rosters, hardware device locks, and faculty approvals for phone re-binding
          </p>
        </div>

        <button
          onClick={() => {
            setStatusMessage(null);
            setShowAddModal(true);
          }}
          className="px-4 py-2.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition flex items-center gap-2 shadow-sm"
        >
          <Plus className="w-4 h-4" />
          <span>Register New Student</span>
        </button>
      </div>

      {/* Status Alerts */}
      {statusMessage && (
        <div
          className={`p-3.5 rounded-xl border flex items-center justify-between text-xs font-medium animate-in fade-in duration-200 ${
            statusMessage.type === "success"
              ? "bg-emerald-50 border-emerald-200 text-emerald-800"
              : "bg-red-50 border-red-200 text-red-800"
          }`}
        >
          <div className="flex items-center gap-2">
            {statusMessage.type === "success" ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            ) : (
              <AlertTriangle className="w-4 h-4 text-red-600" />
            )}
            <span>{statusMessage.text}</span>
          </div>
          <button onClick={() => setStatusMessage(null)} className="text-gray-400 hover:text-gray-600">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* PENDING DEVICE APPROVALS ALERT BANNER */}
      {pendingCount > 0 && (
        <div className="bg-amber-50/90 border border-amber-200/90 rounded-2xl p-4 shadow-sm animate-in fade-in duration-300">
          <div className="flex items-center justify-between pb-3 border-b border-amber-200/70">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-amber-500 text-white flex items-center justify-center font-bold text-xs shadow-xs animate-pulse">
                {pendingCount}
              </div>
              <div>
                <h3 className="font-bold text-sm text-amber-950">
                  Pending Device Change Approval{pendingCount > 1 ? "s" : ""} ({pendingCount})
                </h3>
                <p className="text-[11px] text-amber-800">
                  These students have unbound their phone or requested faculty permission to bind a new device
                </p>
              </div>
            </div>
            <span className="text-[10px] font-mono uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-amber-100 text-amber-800 border border-amber-300">
              Anti-Proxy Lock
            </span>
          </div>

          <div className="mt-3 divide-y divide-amber-200/60">
            {pendingStudents.map((st) => {
              const pendingDevice = st.devices?.find((d) => d.status === "PENDING_APPROVAL");
              if (!pendingDevice) return null;

              return (
                <div key={st.id} className="py-2.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-amber-200 text-amber-900 font-bold flex items-center justify-center text-xs">
                      {st.users?.name?.slice(0, 2).toUpperCase() || "ST"}
                    </div>
                    <div>
                      <div className="text-xs font-bold text-gray-900 flex items-center gap-2">
                        <span>{st.users?.name || "Student"}</span>
                        <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-900 border border-amber-200">
                          {st.roll_number}
                        </span>
                      </div>
                      <div className="text-[11px] text-amber-800 font-mono mt-0.5">
                        {pendingDevice.device_model || "New Device Request"} · {pendingDevice.installation_id}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 self-end sm:self-auto">
                    <button
                      onClick={() => handleRejectDevice(pendingDevice.id, st.users?.name || st.roll_number)}
                      className="px-2.5 py-1 text-[11px] font-semibold text-gray-700 bg-white hover:bg-gray-100 border border-gray-300 rounded-lg transition"
                    >
                      Reject
                    </button>
                    <button
                      onClick={() => handleApproveDevice(st.id, pendingDevice.id, st.users?.name || st.roll_number)}
                      className="px-3 py-1 text-[11px] font-bold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg transition shadow-xs flex items-center gap-1.5"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Approve New Device</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="bg-white border border-subtle rounded-xl p-4 shadow-xs">
          <div className="text-xs text-secondary font-medium">Total Registered</div>
          <div className="text-2xl font-bold text-primary mt-1">{totalStudents}</div>
          <div className="text-[11px] text-muted mt-1">Enrolled students</div>
        </div>

        <div className="bg-white border border-subtle rounded-xl p-4 shadow-xs">
          <div className="text-xs text-secondary font-medium">Hardware Bound (Locked)</div>
          <div className="text-2xl font-bold text-emerald-600 mt-1">{boundStudents}</div>
          <div className="text-[11px] text-emerald-700 font-medium mt-1">Anti-proxy verified</div>
        </div>

        <div className={`border rounded-xl p-4 shadow-xs ${pendingCount > 0 ? "bg-amber-50/80 border-amber-200" : "bg-white border-subtle"}`}>
          <div className="text-xs text-secondary font-medium">Pending Approvals</div>
          <div className={`text-2xl font-bold mt-1 ${pendingCount > 0 ? "text-amber-600" : "text-gray-400"}`}>
            {pendingCount}
          </div>
          <div className="text-[11px] text-muted mt-1">Device switch requests</div>
        </div>

        <div className="bg-white border border-subtle rounded-xl p-4 shadow-xs">
          <div className="text-xs text-secondary font-medium">Floating / Unbound</div>
          <div className="text-2xl font-bold text-gray-600 mt-1">{totalStudents - boundStudents - pendingCount}</div>
          <div className="text-[11px] text-muted mt-1">Ready for first sign-in</div>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-white border border-subtle rounded-xl p-4 shadow-xs flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative w-full md:w-96">
          <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search by student name or roll number..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-xs border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex items-center gap-2 self-start md:self-auto flex-wrap">
          <button
            onClick={() => setFilterDevice("ALL")}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              filterDevice === "ALL" ? "bg-primary text-white" : "bg-gray-100 text-secondary hover:bg-gray-200"
            }`}
          >
            All ({students.length})
          </button>
          <button
            onClick={() => setFilterDevice("BOUND")}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              filterDevice === "BOUND" ? "bg-emerald-600 text-white" : "bg-gray-100 text-secondary hover:bg-gray-200"
            }`}
          >
            Bound ({boundStudents})
          </button>
          <button
            onClick={() => setFilterDevice("PENDING")}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
              filterDevice === "PENDING" ? "bg-amber-600 text-white" : "bg-gray-100 text-secondary hover:bg-gray-200"
            }`}
          >
            <span>Pending Approvals</span>
            {pendingCount > 0 && (
              <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${filterDevice === "PENDING" ? "bg-white text-amber-700" : "bg-amber-500 text-white"}`}>
                {pendingCount}
              </span>
            )}
          </button>
          <button
            onClick={() => setFilterDevice("UNBOUND")}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              filterDevice === "UNBOUND" ? "bg-gray-700 text-white" : "bg-gray-100 text-secondary hover:bg-gray-200"
            }`}
          >
            Unbound ({totalStudents - boundStudents})
          </button>
          <button
            onClick={fetchStudents}
            className="p-2 border border-gray-200 rounded-lg text-gray-500 hover:text-gray-900 hover:bg-gray-50 transition"
            title="Refresh List"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* Students Table */}
      <div className="bg-white border border-subtle rounded-xl shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-subtle bg-canvas text-[11px] font-bold text-secondary uppercase tracking-wider">
                <th className="py-3 px-4">Student</th>
                <th className="py-3 px-4">Roll Number</th>
                <th className="py-3 px-4">Program / Sem</th>
                <th className="py-3 px-4">Hardware Device Lock</th>
                <th className="py-3 px-4">Lock Status</th>
                <th className="py-3 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-subtle text-xs">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-secondary">
                    <RefreshCw className="w-5 h-5 animate-spin mx-auto text-blue-600 mb-2" />
                    Loading enrolled students ledger...
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-secondary">
                    No students matched the search criteria.
                  </td>
                </tr>
              ) : (
                filtered.map((st) => {
                  const activeDevice = st.devices?.find((d) => d.status === "ACTIVE");
                  const pendingDevice = st.devices?.find((d) => d.status === "PENDING_APPROVAL");
                  const blockedDevice = st.devices?.find((d) => d.status === "BLOCKED");

                  return (
                    <tr key={st.id} className="hover:bg-pill/30 transition-colors">
                      {/* Name & Email */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <div className={`w-8 h-8 rounded-full font-bold flex items-center justify-center text-xs ${
                            pendingDevice ? "bg-amber-100 text-amber-800" : "bg-blue-100 text-blue-700"
                          }`}>
                            {st.users?.name
                              ?.split(" ")
                              .map((n) => n[0])
                              .join("")
                              .substring(0, 2) || "ST"}
                          </div>
                          <div>
                            <div className="font-bold text-primary">{st.users?.name || "Unknown Student"}</div>
                            <div className="text-[11px] text-muted">{st.users?.email || "No email"}</div>
                          </div>
                        </div>
                      </td>

                      {/* Roll Number */}
                      <td className="py-3 px-4">
                        <span className="font-mono font-semibold px-2 py-0.5 rounded bg-gray-100 text-gray-800 border border-gray-200 text-[11px]">
                          {st.roll_number}
                        </span>
                      </td>

                      {/* Program */}
                      <td className="py-3 px-4">
                        <div className="text-primary font-medium">{st.programs?.name || "B.Tech DSAI"}</div>
                        <div className="text-[10px] text-secondary">Semester {st.semester || 5}</div>
                      </td>

                      {/* Hardware Device */}
                      <td className="py-3 px-4">
                        {pendingDevice ? (
                          <div className="font-mono text-[11px] text-amber-900">
                            <div className="flex items-center gap-1.5 font-bold">
                              <Smartphone className="w-3.5 h-3.5 text-amber-600" />
                              <span>{pendingDevice.device_model || "New Device"}</span>
                            </div>
                            <div className="text-[10px] text-amber-700 truncate max-w-[170px]">
                              {pendingDevice.installation_id}
                            </div>
                          </div>
                        ) : activeDevice ? (
                          <div className="font-mono text-[11px] text-gray-800">
                            <div className="flex items-center gap-1.5">
                              <Smartphone className="w-3.5 h-3.5 text-emerald-600" />
                              <span>{activeDevice.device_model || "Mobile Device"}</span>
                            </div>
                            <div className="text-[10px] text-gray-500 truncate max-w-[170px]">
                              {activeDevice.installation_id}
                            </div>
                          </div>
                        ) : (
                          <span className="text-secondary text-[11px] italic">No device bound</span>
                        )}
                      </td>

                      {/* Status */}
                      <td className="py-3 px-4">
                        {pendingDevice ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-300 animate-pulse">
                            <Clock className="w-3 h-3 text-amber-600" />
                            Awaiting Faculty Approval
                          </span>
                        ) : activeDevice ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            <ShieldCheck className="w-3 h-3" />
                            Locked (Anti-Proxy)
                          </span>
                        ) : blockedDevice ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
                            Blocked
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-gray-50 text-gray-600 border border-gray-200">
                            Unbound
                          </span>
                        )}
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        {pendingDevice ? (
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => handleRejectDevice(pendingDevice.id, st.users?.name || st.roll_number)}
                              className="px-2 py-1 text-[11px] font-semibold text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-md border border-gray-200 transition"
                            >
                              Reject
                            </button>
                            <button
                              onClick={() => handleApproveDevice(st.id, pendingDevice.id, st.users?.name || st.roll_number)}
                              className="px-2.5 py-1 text-[11px] font-bold text-white bg-emerald-600 hover:bg-emerald-700 rounded-md transition shadow-xs flex items-center gap-1"
                            >
                              <CheckCircle2 className="w-3 h-3" />
                              <span>Approve</span>
                            </button>
                          </div>
                        ) : activeDevice ? (
                          <button
                            onClick={() => handleUnbindDevice(activeDevice.id, st.id, st.users?.name || st.roll_number, false)}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 border border-rose-200 rounded-lg transition"
                            title="Unbind phone (re-binding will require faculty permission)"
                          >
                            <RotateCcw className="w-3 h-3" />
                            <span>Unbind Phone</span>
                          </button>
                        ) : (
                          <button
                            onClick={() => handleUnbindDevice(blockedDevice?.id || "", st.id, st.users?.name || st.roll_number, true)}
                            className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium text-blue-600 hover:underline"
                          >
                            Permit Next Device
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* ADD STUDENT MODAL */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-gray-100">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center">
                  <GraduationCap className="w-4 h-4" />
                </div>
                <h3 className="font-bold text-sm text-primary">Register New Student</h3>
              </div>
              <button onClick={() => setShowAddModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddStudent} className="mt-4 space-y-3.5">
              <div>
                <label className="block text-xs font-bold text-gray-700 mb-1">
                  Student Full Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Harshit Sharma"
                  value={nameInput}
                  onChange={(e) => setNameInput(e.target.value)}
                  className="w-full px-3 py-2 text-xs border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 mb-1">
                  Roll Number <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. 261020401"
                  value={rollInput}
                  onChange={(e) => setRollInput(e.target.value)}
                  className="w-full px-3 py-2 text-xs font-mono border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 uppercase"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-700 mb-1">Institutional Email</label>
                <input
                  type="email"
                  placeholder={`${rollInput ? rollInput.toLowerCase() : "roll"}@student.iiitnr.edu.in`}
                  value={emailInput}
                  onChange={(e) => setEmailInput(e.target.value)}
                  className="w-full px-3 py-2 text-xs border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-800 flex items-start gap-2">
                <ShieldCheck className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
                <span>
                  Once registered, this student will be able to log in on Android or iOS PWA and bind their hardware phone.
                </span>
              </div>

              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg transition shadow-xs flex items-center gap-1.5"
                >
                  {isSubmitting ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
                  <span>{isSubmitting ? "Enrolling..." : "Enroll Student"}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

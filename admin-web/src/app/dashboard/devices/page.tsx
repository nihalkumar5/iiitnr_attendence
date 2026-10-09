"use client";

import { useState, useEffect } from "react";
import { 
  ShieldCheck, 
  Smartphone, 
  RotateCcw, 
  CheckCircle2, 
  XCircle, 
  Clock, 
  Search, 
  RefreshCw, 
  AlertTriangle,
  KeyRound,
  Filter,
  User,
  Info
} from "lucide-react";
import { supabase } from "@/lib/supabaseClient";
import { 
  fetchPendingUnbindRequestsFromDB, 
  approveDeviceUnbindInDB, 
  rejectDeviceUnbindInDB,
  BoundDevice 
} from "@/lib/attendanceService";

export default function CentralDeviceManagementPage() {
  const [pendingRequests, setPendingRequests] = useState<BoundDevice[]>([]);
  const [allDevices, setAllDevices] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "PENDING" | "ACTIVE">("ALL");
  const [actingId, setActingId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  const loadData = async () => {
    setIsLoading(true);
    try {
      // 1. Fetch pending unbind requests
      const pending = await fetchPendingUnbindRequestsFromDB();
      setPendingRequests(pending);

      // 2. Fetch all registered devices across campus
      const { data, error } = await supabase
        .from("devices")
        .select(`
          id, student_id, installation_id, device_model, platform, status, registered_at, last_seen,
          students (
            id, roll_number, semester,
            users ( name, email )
          )
        `)
        .order("registered_at", { ascending: false });

      if (!error && data) {
        setAllDevices(data);
      }
    } catch (e: any) {
      console.error("Error loading devices data:", e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 8000);
    return () => clearInterval(interval);
  }, []);

  const handleApprove = async (req: BoundDevice) => {
    setActingId(req.id);
    try {
      const ok = await approveDeviceUnbindInDB(req.id);
      if (ok) {
        showToast(`✓ Device unbind approved for ${req.studentName} (${req.rollNo}). Hardware lock released.`);
        await loadData();
      } else {
        showToast("Failed to approve unbind request.");
      }
    } catch (e: any) {
      showToast("Error: " + e.message);
    } finally {
      setActingId(null);
    }
  };

  const handleReject = async (req: BoundDevice) => {
    setActingId(req.id);
    try {
      const ok = await rejectDeviceUnbindInDB(req.id);
      if (ok) {
        showToast(`Request rejected for ${req.studentName} (${req.rollNo}).`);
        await loadData();
      } else {
        showToast("Failed to reject request.");
      }
    } catch (e: any) {
      showToast("Error: " + e.message);
    } finally {
      setActingId(null);
    }
  };

  const handleForceUnbind = async (deviceId: string, studentName: string, roll: string) => {
    if (!confirm(`Are you sure you want to force unbind the device for ${studentName} (${roll})? They will be prompted to bind a new hardware device on next sign in.`)) {
      return;
    }
    setActingId(deviceId);
    try {
      const { error } = await supabase.from("devices").delete().eq("id", deviceId);
      if (!error) {
        showToast(`✓ Hardware lock removed for ${studentName} (${roll}).`);
        await loadData();
      } else {
        showToast("Failed to remove device lock: " + error.message);
      }
    } catch (e: any) {
      showToast("Error: " + e.message);
    } finally {
      setActingId(null);
    }
  };

  const filteredDevices = allDevices.filter((d) => {
    const s = d.students || {};
    const u = s.users || {};
    const query = searchQuery.toLowerCase();
    const matchesSearch = 
      (u.name || "").toLowerCase().includes(query) ||
      (s.roll_number || "").toLowerCase().includes(query) ||
      (d.device_model || "").toLowerCase().includes(query) ||
      (d.installation_id || "").toLowerCase().includes(query);

    const isPending = d.device_model?.includes("[UNBIND REQUEST]") || d.status === "PENDING_APPROVAL";
    if (statusFilter === "PENDING") return matchesSearch && isPending;
    if (statusFilter === "ACTIVE") return matchesSearch && d.status === "ACTIVE" && !isPending;
    return matchesSearch;
  });

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6">
      {/* Toast Alert */}
      {toastMessage && (
        <div className="fixed top-6 right-6 z-50 bg-slate-900 text-white px-4 py-3 rounded-2xl shadow-2xl border border-slate-700 flex items-center gap-3 animate-in fade-in slide-in-from-top-4">
          <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
          <span className="text-xs font-semibold">{toastMessage}</span>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-gray-100">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-200 text-blue-600 flex items-center justify-center">
              <KeyRound className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-slate-900 tracking-tight">
                Institutional Hardware Security & Device Approvals
              </h1>
              <p className="text-xs text-slate-500 font-medium">
                Centralized Single-Admin Console • 1 Student = 1 Device Anti-Proxy Control
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={loadData}
            disabled={isLoading}
            className="px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl transition shadow-xs flex items-center gap-1.5 cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? "animate-spin" : ""}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-2xl bg-white border border-slate-200 shadow-xs flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-xl bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center shrink-0">
            <Clock className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Pending Requests</span>
            <div className="text-2xl font-black text-slate-900 font-mono mt-0.5">
              {pendingRequests.length}
            </div>
            <p className="text-[11px] text-amber-700 font-medium mt-0.5">Awaiting central admin authorization</p>
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-white border border-slate-200 shadow-xs flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-600 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Total Bound Devices</span>
            <div className="text-2xl font-black text-slate-900 font-mono mt-0.5">
              {allDevices.filter(d => d.status === "ACTIVE" && !d.device_model?.includes("[UNBIND REQUEST]")).length}
            </div>
            <p className="text-[11px] text-emerald-700 font-medium mt-0.5">Protected by anti-proxy hardware lock</p>
          </div>
        </div>

        <div className="p-4 rounded-2xl bg-white border border-slate-200 shadow-xs flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-xl bg-blue-50 border border-blue-200 text-blue-600 flex items-center justify-center shrink-0">
            <Smartphone className="w-6 h-6" />
          </div>
          <div>
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Campus Coverage</span>
            <div className="text-2xl font-black text-slate-900 font-mono mt-0.5">
              {allDevices.length}
            </div>
            <p className="text-[11px] text-slate-500 font-medium mt-0.5">Hardware profiles registered</p>
          </div>
        </div>
      </div>

      {/* SECTION 1: PENDING UNBIND REQUESTS (Priority Action Queue) */}
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xs overflow-hidden">
        <div className="p-4 sm:p-5 border-b border-slate-100 flex items-center justify-between bg-amber-50/40">
          <div className="flex items-center gap-2">
            <div className="w-2.5 h-2.5 rounded-full bg-amber-500 animate-pulse" />
            <h2 className="text-sm font-bold text-slate-900">
              Pending Unbind Approval Requests
            </h2>
            <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-mono">
              {pendingRequests.length} action required
            </span>
          </div>
          <span className="text-[11px] text-slate-500 font-medium hidden sm:inline">
            Centralized IT Admin Approval
          </span>
        </div>

        {pendingRequests.length === 0 ? (
          <div className="p-10 text-center space-y-2">
            <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto" />
            <h3 className="text-sm font-bold text-slate-900">Queue is Clear</h3>
            <p className="text-xs text-slate-500 max-w-sm mx-auto">
              No pending student hardware unbind requests. All student device bindings are verified and compliant.
            </p>
          </div>
        ) : (
          <div className="divide-y divide-slate-100">
            {pendingRequests.map((req) => {
              const reasonMatch = req.deviceModel.match(/\(Reason:\s*(.+?)\)/i);
              const reason = reasonMatch ? reasonMatch[1] : "Phone reset / hardware upgrade";
              const cleanModel = req.deviceModel.replace(/\[UNBIND REQUEST\]/gi, "").replace(/\(Reason:.+?\)/gi, "").trim() || "Mobile Device";

              return (
                <div key={req.id} className="p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 hover:bg-slate-50/70 transition-colors">
                  <div className="space-y-1.5 flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-sm text-slate-900">{req.studentName}</span>
                      <span className="px-2 py-0.5 rounded-md bg-blue-50 text-blue-700 font-mono font-bold text-xs border border-blue-200">
                        {req.rollNo}
                      </span>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
                        Pending Admin Approval
                      </span>
                    </div>

                    <div className="flex items-center gap-3 text-xs text-slate-600">
                      <span className="flex items-center gap-1 font-medium">
                        <Smartphone className="w-3.5 h-3.5 text-slate-400" />
                        {cleanModel} ({req.platform})
                      </span>
                      <span>·</span>
                      <span className="text-slate-400">
                        Requested: {req.registeredAt ? new Date(req.registeredAt).toLocaleString() : "Recently"}
                      </span>
                    </div>

                    <div className="p-2.5 rounded-xl bg-amber-50/70 border border-amber-200/60 text-xs text-amber-900 flex items-start gap-2 max-w-2xl">
                      <Info className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                      <span>
                        <strong>Student Reason:</strong> {reason}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      disabled={actingId === req.id}
                      onClick={() => handleReject(req)}
                      className="px-3.5 py-2 rounded-xl border border-slate-200 hover:bg-slate-100 text-slate-600 font-semibold text-xs transition cursor-pointer disabled:opacity-50"
                    >
                      Reject
                    </button>
                    <button
                      type="button"
                      disabled={actingId === req.id}
                      onClick={() => handleApprove(req)}
                      className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs shadow-md shadow-emerald-600/20 flex items-center gap-1.5 transition cursor-pointer disabled:opacity-50"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      <span>{actingId === req.id ? "Authorizing..." : "Approve Unbind"}</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* SECTION 2: INSTITUTIONAL DEVICE REGISTRY */}
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xs overflow-hidden">
        <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-slate-900">Campus Hardware Registry</h2>
            <p className="text-xs text-slate-500 font-medium">Audit registered hardware tokens and reset proxy locks</p>
          </div>

          <div className="flex items-center gap-2.5">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search student, roll, device..."
                className="pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:border-blue-500 w-48 sm:w-60"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e: any) => setStatusFilter(e.target.value)}
              className="px-2.5 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl font-medium text-slate-700 focus:outline-none"
            >
              <option value="ALL">All Status</option>
              <option value="PENDING">Pending Unbind</option>
              <option value="ACTIVE">Locked / Active</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50/80 border-b border-slate-200 text-slate-500 font-semibold uppercase tracking-wider text-[10px]">
                <th className="py-3 px-4">Student</th>
                <th className="py-3 px-4">Roll Number</th>
                <th className="py-3 px-4">Device Model</th>
                <th className="py-3 px-4">Hardware Token (Installation ID)</th>
                <th className="py-3 px-4">Security Status</th>
                <th className="py-3 px-4 text-right">Admin Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredDevices.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-400">
                    No devices match the search criteria.
                  </td>
                </tr>
              ) : (
                filteredDevices.map((dev) => {
                  const s = dev.students || {};
                  const u = s.users || {};
                  const isPending = dev.device_model?.includes("[UNBIND REQUEST]") || dev.status === "PENDING_APPROVAL";

                  return (
                    <tr key={dev.id} className="hover:bg-slate-50/60 transition-colors">
                      <td className="py-3 px-4 font-bold text-slate-900">
                        {u.name || "Student"}
                      </td>
                      <td className="py-3 px-4 font-mono font-semibold text-blue-600">
                        {s.roll_number || "—"}
                      </td>
                      <td className="py-3 px-4 text-slate-700">
                        <span className="font-medium truncate max-w-[200px] block">
                          {dev.device_model?.replace(/\[UNBIND REQUEST\].+/gi, "[Pending Unbind]").slice(0, 40) || "Mobile Device"}
                        </span>
                        <span className="text-[10px] text-slate-400 font-mono block">
                          Platform: {dev.platform}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-500">
                        {dev.installation_id?.slice(0, 16)}...
                      </td>
                      <td className="py-3 px-4">
                        {isPending ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                            <Clock className="w-3 h-3 text-amber-600" />
                            Awaiting Unbind Approval
                          </span>
                        ) : dev.status === "ACTIVE" ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            <ShieldCheck className="w-3 h-3 text-emerald-600" />
                            Locked (Anti-Proxy)
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
                            Blocked
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <button
                          type="button"
                          onClick={() => handleForceUnbind(dev.id, u.name || "Student", s.roll_number || "—")}
                          disabled={actingId === dev.id}
                          className="px-2.5 py-1 text-[11px] font-semibold text-rose-600 hover:text-rose-700 hover:bg-rose-50 border border-rose-200 rounded-lg transition cursor-pointer"
                        >
                          Force Unbind
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

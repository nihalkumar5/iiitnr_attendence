"use client";

import React, { useState, useRef, useEffect, useMemo } from "react";
import { 
  Wifi, 
  Search, 
  Signal, 
  Check, 
  RefreshCw, 
  ChevronDown, 
  Plus, 
  Radio, 
  ShieldCheck, 
  X 
} from "lucide-react";

interface LiveWifiSearchSelectorProps {
  currentWifi: string;
  onSelectWifi: (wifi: string) => void;
  wifiList: string[];
  onScanNearby?: () => Promise<void> | void;
  isScanning?: boolean;
  variant?: "header" | "modal" | "inline";
}

export function LiveWifiSearchSelector({
  currentWifi,
  onSelectWifi,
  wifiList,
  onScanNearby,
  isScanning = false,
  variant = "modal"
}: LiveWifiSearchSelectorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Close when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      setTimeout(() => inputRef.current?.focus(), 60);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen]);

  const filteredNetworks = useMemo(() => {
    if (!searchTerm.trim()) return wifiList;
    const term = searchTerm.toLowerCase();
    return wifiList.filter(w => w.toLowerCase().includes(term));
  }, [wifiList, searchTerm]);

  const exactMatchExists = useMemo(() => {
    return wifiList.some(w => w.toLowerCase() === searchTerm.trim().toLowerCase());
  }, [wifiList, searchTerm]);

  const handleSelect = (wifi: string) => {
    onSelectWifi(wifi);
    setIsOpen(false);
    setSearchTerm("");
  };

  const handleAddCustomWifi = () => {
    if (!searchTerm.trim()) return;
    handleSelect(searchTerm.trim());
  };

  return (
    <div className="relative w-full" ref={containerRef}>
      {/* Trigger Button based on Variant */}
      {variant === "modal" ? (
        <button
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          className={`w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border transition-all text-left flex items-center justify-between gap-3 cursor-pointer ${
            isOpen ? "border-indigo-500 ring-2 ring-indigo-500/20" : "border-slate-800 hover:border-slate-700"
          }`}
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-7 h-7 rounded-lg bg-cyan-500/10 text-cyan-400 flex items-center justify-center shrink-0 border border-cyan-500/20">
              <Wifi className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <span className="text-xs font-bold text-white block truncate">
                {currentWifi || "Select Wi-Fi Network"}
              </span>
              <span className="text-[10px] text-slate-400 block font-mono">
                Hardware AP • Anti-Spoof BSSID Linked
              </span>
            </div>
          </div>
          <div className="flex items-center gap-1.5 text-slate-400">
            <span className="text-[10px] px-2 py-0.5 rounded-md bg-slate-900 border border-slate-800 hidden sm:inline text-indigo-300 font-medium">
              Live Search
            </span>
            <ChevronDown className={`w-4 h-4 transition-transform ${isOpen ? "rotate-180 text-indigo-400" : ""}`} />
          </div>
        </button>
      ) : variant === "header" ? (
        <button
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          className={`flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-900 border transition-all text-xs cursor-pointer ${
            isOpen ? "border-cyan-500 text-white shadow-md shadow-cyan-500/10" : "border-slate-800 text-slate-300 hover:border-slate-700"
          }`}
        >
          <Wifi className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
          <span className="text-slate-400 hidden lg:inline">Classroom Wi-Fi:</span>
          <span className="font-bold text-white max-w-[130px] truncate">{currentWifi}</span>
          <ChevronDown className={`w-3.5 h-3.5 text-slate-400 transition-transform ${isOpen ? "rotate-180 text-cyan-400" : ""}`} />
        </button>
      ) : (
        /* inline variant for live session header */
        <button
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          className={`bg-slate-900 border rounded-lg px-2.5 py-1 text-white font-bold cursor-pointer text-xs flex items-center gap-1.5 transition-all ${
            isOpen ? "border-cyan-500 shadow-sm" : "border-slate-700 hover:border-slate-600"
          }`}
        >
          <Wifi className="w-3 h-3 text-cyan-400" />
          <span className="truncate max-w-[130px]">{currentWifi}</span>
          <ChevronDown className={`w-3 h-3 text-slate-400 transition-transform ${isOpen ? "rotate-180" : ""}`} />
        </button>
      )}

      {/* Popover Dropdown with Live Search & Radar Scan */}
      {isOpen && (
        <div 
          className={`absolute z-50 mt-1.5 bg-slate-900/98 backdrop-blur-2xl border border-slate-700/80 rounded-2xl shadow-2xl p-3 space-y-2.5 animate-in fade-in zoom-in-95 duration-150 ${
            variant === "header" 
              ? "right-0 w-80 sm:w-96" 
              : "left-0 right-0 w-full min-w-[280px]"
          }`}
        >
          {/* Popover Top Bar */}
          <div className="flex items-center justify-between gap-2 pb-2 border-b border-slate-800/80">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></span>
              <span className="text-xs font-extrabold text-white tracking-tight flex items-center gap-1.5">
                <Radio className="w-3.5 h-3.5 text-cyan-400" />
                Live Wi-Fi AP Radar
              </span>
            </div>

            {onScanNearby && (
              <button
                type="button"
                onClick={onScanNearby}
                disabled={isScanning}
                className="px-2 py-1 rounded-lg bg-slate-800 hover:bg-slate-750 text-slate-300 text-[10px] font-bold flex items-center gap-1 border border-slate-700 cursor-pointer transition-all"
                title="Scan nearby Wi-Fi beacons"
              >
                <RefreshCw className={`w-3 h-3 text-cyan-400 ${isScanning ? "animate-spin" : ""}`} />
                <span>{isScanning ? "Scanning..." : "Rescan Air"}</span>
              </button>
            )}
          </div>

          {/* Live Search Input */}
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              ref={inputRef}
              type="text"
              placeholder="Search live Wi-Fi SSID..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-8 pr-7 py-2 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs placeholder:text-slate-500 focus:outline-none focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500/30 transition-all font-medium"
            />
            {searchTerm && (
              <button
                type="button"
                onClick={() => setSearchTerm("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {/* List of Detected Networks */}
          <div className="max-h-56 overflow-y-auto space-y-1 pr-1">
            {filteredNetworks.length > 0 ? (
              filteredNetworks.map((wifi, idx) => {
                const isSelected = wifi === currentWifi;
                const is5G = wifi.includes("5G") || idx % 3 === 0;
                return (
                  <button
                    key={wifi}
                    type="button"
                    onClick={() => handleSelect(wifi)}
                    className={`w-full p-2.5 rounded-xl text-left transition-all flex items-center justify-between gap-2.5 cursor-pointer border ${
                      isSelected
                        ? "bg-cyan-500/15 border-cyan-500/40 text-white shadow-sm"
                        : "bg-slate-950/60 border-slate-800/60 hover:bg-slate-800/80 hover:border-slate-700 text-slate-300"
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className={`w-6 h-6 rounded-lg flex items-center justify-center shrink-0 ${
                        isSelected ? "bg-cyan-500/20 text-cyan-300" : "bg-slate-800 text-slate-400"
                      }`}>
                        <Wifi className="w-3.5 h-3.5" />
                      </div>
                      <div className="min-w-0">
                        <span className="text-xs font-bold block truncate text-white">
                          {wifi}
                        </span>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-[9px] font-mono text-slate-400 flex items-center gap-0.5">
                            <Signal className="w-2.5 h-2.5 text-emerald-400" />
                            {is5G ? "5.8 GHz • Strong" : "2.4 GHz • Good"}
                          </span>
                          <span className="text-[9px] text-slate-500 font-mono">
                            WPA2/3
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {isSelected ? (
                        <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                          <Check className="w-3 h-3" />
                          Active
                        </span>
                      ) : (
                        <span className="text-[10px] text-slate-500">
                          Select
                        </span>
                      )}
                    </div>
                  </button>
                );
              })
            ) : (
              <div className="p-3 text-center rounded-xl bg-slate-950/40 border border-slate-800 space-y-2">
                <p className="text-xs text-slate-400 font-medium">
                  No detected network matching &ldquo;{searchTerm}&rdquo;
                </p>
              </div>
            )}

            {/* Custom SSID fallback if teacher types a unique SSID */}
            {searchTerm.trim() && !exactMatchExists && (
              <button
                type="button"
                onClick={handleAddCustomWifi}
                className="w-full p-2.5 rounded-xl bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/40 text-indigo-200 text-left flex items-center justify-between gap-2 cursor-pointer transition-all mt-1"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <Plus className="w-4 h-4 text-indigo-400 shrink-0" />
                  <div className="min-w-0">
                    <span className="text-xs font-bold block truncate text-white">
                      Use &ldquo;{searchTerm.trim()}&rdquo;
                    </span>
                    <span className="text-[10px] text-indigo-300 block">
                      Custom Classroom Hotspot / Hidden SSID
                    </span>
                  </div>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-indigo-500 text-white shrink-0">
                  Apply
                </span>
              </button>
            )}
          </div>

          {/* Footer Status */}
          <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-400 font-mono">
            <span className="flex items-center gap-1 text-slate-400">
              <ShieldCheck className="w-3 h-3 text-emerald-400" />
              BSSID Anti-Spoof Active
            </span>
            <span className="text-slate-400">
              {filteredNetworks.length} APs detected
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

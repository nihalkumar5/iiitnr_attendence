"use client";

import React, { useState, useRef } from "react";
import {
  Sparkles,
  X,
  Upload,
  FileText,
  Check,
  Key,
  AlertCircle,
  Loader2,
  Calendar,
  Clock,
  MapPin,
  BookOpen,
  ArrowRight,
  RotateCcw,
  Pencil
} from "lucide-react";
import { ParsedLecture } from "@/app/api/ai-timetable/route";
import { createClassInDB, replaceTeacherClassesInDB, DBClass } from "@/lib/attendanceService";

interface AiTimetableModalProps {
  isOpen: boolean;
  onClose: () => void;
  onImportComplete: (createdClasses: DBClass[]) => void;
  teacherName?: string;
  teacherId?: string;
  existingClasses?: DBClass[];
}

export function AiTimetableModal({
  isOpen,
  onClose,
  onImportComplete,
  teacherName = "Prof. Nihal26302",
  teacherId,
  existingClasses = [],
}: AiTimetableModalProps) {
  const [activeTab, setActiveTab] = useState<"image" | "text">("image");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [pastedText, setPastedText] = useState("");
  const [apiKey, setApiKey] = useState(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("gemini_api_key_override") || "";
    }
    return "";
  });
  const [showKeyInput, setShowKeyInput] = useState(false);

  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const [parsedLectures, setParsedLectures] = useState<ParsedLecture[] | null>(null);
  const [selectedIndices, setSelectedIndices] = useState<number[]>([]);
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState("");

  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setAnalysisError(null);
      const url = URL.createObjectURL(file);
      setPreviewUrl(url);
    }
  };

  const handleSaveApiKey = (keyVal: string) => {
    setApiKey(keyVal);
    if (typeof window !== "undefined") {
      localStorage.setItem("gemini_api_key_override", keyVal.trim());
    }
    setShowKeyInput(false);
  };

  const handleExtract = async () => {
    setIsAnalyzing(true);
    setAnalysisError(null);

    try {
      let body: any = { teacherName: teacherName.trim() };
      if (apiKey) body.apiKey = apiKey;

      if (activeTab === "image") {
        if (!selectedFile) {
          setAnalysisError("Please select a timetable image or document first.");
          setIsAnalyzing(false);
          return;
        }

        // Compress large camera photos to max 1600px 85% JPEG to prevent payload/timeout errors
        const { base64, mimeType } = await new Promise<{ base64: string; mimeType: string }>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = reject;
          reader.onload = () => {
            const img = new Image();
            img.onerror = () => resolve({ base64: reader.result as string, mimeType: selectedFile.type || "image/jpeg" });
            img.onload = () => {
              const MAX_DIM = 1600;
              let width = img.width;
              let height = img.height;
              if (width > MAX_DIM || height > MAX_DIM) {
                if (width > height) {
                  height = Math.round((height * MAX_DIM) / width);
                  width = MAX_DIM;
                } else {
                  width = Math.round((width * MAX_DIM) / height);
                  height = MAX_DIM;
                }
              }
              const canvas = document.createElement("canvas");
              canvas.width = width;
              canvas.height = height;
              const ctx = canvas.getContext("2d");
              if (!ctx) {
                resolve({ base64: reader.result as string, mimeType: selectedFile.type || "image/jpeg" });
                return;
              }
              ctx.drawImage(img, 0, 0, width, height);
              const compressed = canvas.toDataURL("image/jpeg", 0.85);
              resolve({ base64: compressed, mimeType: "image/jpeg" });
            };
            img.src = reader.result as string;
          };
          reader.readAsDataURL(selectedFile);
        });

        body.base64Image = base64;
        body.mimeType = mimeType;
      } else {
        if (!pastedText.trim()) {
          setAnalysisError("Please paste your timetable text or syllabus schedule.");
          setIsAnalyzing(false);
          return;
        }
        body.rawText = pastedText.trim();
      }

      const res = await fetch("/api/ai-timetable", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.error || "Failed to analyze timetable with AI.");
      }

      const lectures: ParsedLecture[] = data.lectures || [];
      if (lectures.length === 0) {
        throw new Error("No classes could be extracted. Please check your image or text format.");
      }

      setParsedLectures(lectures);
      setSelectedIndices(lectures.map((_, i) => i)); // Default select all
    } catch (err: any) {
      console.error("AI extraction error:", err);
      setAnalysisError(err.message || "An unexpected error occurred during extraction.");
    } finally {
      setIsAnalyzing(false);
    }
  };



  const handleUpdateLecture = (idx: number, updatedFields: Partial<ParsedLecture>) => {
    if (!parsedLectures) return;
    setParsedLectures(prev => {
      if (!prev) return null;
      return prev.map((item, i) => i === idx ? { ...item, ...updatedFields } : item);
    });
  };

  const handleImportClasses = async () => {
    if (!parsedLectures || selectedIndices.length === 0) return;

    setIsImporting(true);
    setImportProgress("Replacing previous timetable classes...");

    // 1. Purge previous timetable classes completely
    if (existingClasses && existingClasses.length > 0) {
      await replaceTeacherClassesInDB(teacherId, existingClasses);
    }

    const created: DBClass[] = [];
    const chosen = selectedIndices.map((i) => parsedLectures[i]);

    for (let idx = 0; idx < chosen.length; idx++) {
      const lec = chosen[idx];
      setImportProgress(`Saving ${idx + 1} of ${chosen.length}: ${lec.subjectName}...`);

      const newCls = await createClassInDB({
        subjectName: lec.subjectName,
        subjectCode: lec.subjectCode,
        roomNo: lec.room || "Room 319",
        teacherId,
        dayOfWeek: lec.dayOfWeek,
        startTime: lec.startTime,
        endTime: lec.endTime,
        program: lec.program || "M.Tech I Semester DSAI",
      });

      if (newCls) {
        created.push(newCls);
      }
    }

    setIsImporting(false);
    onImportComplete(created);
    onClose();
  };

  const formatTimeSlot = (start: string, end: string) => {
    const to12 = (t: string) => {
      const [h, m] = t.split(":").map(Number);
      const isPm = h >= 12;
      const hour12 = h % 12 || 12;
      return `${String(hour12).padStart(2, "0")}:${String(m).padStart(2, "0")} ${isPm ? "PM" : "AM"}`;
    };
    return `${to12(start)} – ${to12(end)}`;
  };

  const getDayName = (day: number) => {
    const map = ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
    return map[day] || "Monday";
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-3 sm:p-4 animate-in fade-in">
      <div className="w-full max-w-lg bg-white border border-slate-200 rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        
        {/* Header */}
        <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-2xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-600 shadow-2xs">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900 tracking-tight leading-tight">
                AI Timetable Scanner
              </h2>
              <p className="text-[11px] font-medium text-blue-600">
                Gemini AI Vision & Automatic Class Scheduling
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setShowKeyInput(!showKeyInput)}
              title="Custom Gemini API Key"
              className="p-1.5 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
            >
              <Key className="w-4 h-4" />
            </button>

            <button
              type="button"
              onClick={onClose}
              disabled={isAnalyzing || isImporting}
              className="p-1.5 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Custom API Key input drawer */}
        {showKeyInput && (
          <div className="p-3 bg-amber-50/70 border-b border-amber-200/60 text-xs space-y-1.5 animate-in slide-in-from-top-1">
            <div className="flex items-center justify-between text-amber-900 font-semibold">
              <span>Gemini API Key Override</span>
              <span className="text-[10px] text-amber-700">Optional</span>
            </div>
            <div className="flex gap-2">
              <input
                type="password"
                placeholder="AIzaSy... (leave blank to use system key)"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                className="flex-1 px-2.5 py-1.5 bg-white border border-amber-300 rounded-lg text-xs text-slate-800 focus:outline-none focus:border-blue-500 font-mono"
              />
              <button
                type="button"
                onClick={() => handleSaveApiKey(apiKey)}
                className="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-lg font-bold text-xs shadow-2xs"
              >
                Save
              </button>
            </div>
          </div>
        )}

        {/* Modal Body */}
        <div className="p-5 flex-1 overflow-y-auto space-y-4 text-xs">
          {parsedLectures ? (
            /* ==================================================================== */
            /* VIEW 2: EXTRACTED RESULTS REVIEW */
            /* ==================================================================== */
            <div className="space-y-3.5 animate-in fade-in">
              <div className="p-2.5 rounded-xl bg-amber-50 border border-amber-200/80 text-[11px] text-amber-800 flex items-center justify-between">
                <span>⚡ Notice: Importing will replace your previous timetable so only the new schedule is active.</span>
                <span className="font-bold text-amber-900 uppercase text-[10px] bg-amber-100 px-2 py-0.5 rounded-md">Clean Replace</span>
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Extracted {parsedLectures.length} Lecture Slots
                  </h3>
                  <p className="text-[11px] text-slate-500">
                    Review or tweak timings before importing into your schedule
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    if (selectedIndices.length === parsedLectures.length) {
                      setSelectedIndices([]);
                    } else {
                      setSelectedIndices(parsedLectures.map((_, i) => i));
                    }
                  }}
                  className="text-xs font-semibold text-blue-600 hover:text-blue-800"
                >
                  {selectedIndices.length === parsedLectures.length ? "Deselect All" : "Select All"}
                </button>
              </div>

              {/* Cards List */}
              <div className="space-y-2 max-h-[340px] overflow-y-auto pr-0.5">
                {parsedLectures.map((lec, idx) => {
                  const isChecked = selectedIndices.includes(idx);
                  return (
                    <div
                      key={idx}
                      onClick={() => {
                        if (isChecked) {
                          setSelectedIndices(selectedIndices.filter((i) => i !== idx));
                        } else {
                          setSelectedIndices([...selectedIndices, idx]);
                        }
                      }}
                      className={`p-3.5 rounded-2xl border transition-all cursor-pointer flex items-start gap-3 ${
                        isChecked
                          ? "bg-blue-50/50 border-blue-500/80 shadow-2xs"
                          : "bg-white border-slate-200/90 hover:border-slate-300"
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => {}}
                        className="mt-0.5 w-4 h-4 rounded text-blue-600 border-slate-300 focus:ring-0 cursor-pointer"
                      />

                      <div className="flex-1 space-y-1.5 min-w-0">
                        {editingIndex === idx ? (
                          <div className="space-y-2 p-2 bg-white rounded-xl border border-blue-200" onClick={(e) => e.stopPropagation()}>
                            <div className="grid grid-cols-2 gap-2">
                              <div>
                                <label className="text-[10px] font-bold text-slate-600">Subject Name</label>
                                <input
                                  type="text"
                                  value={lec.subjectName}
                                  onChange={(e) => handleUpdateLecture(idx, { subjectName: e.target.value })}
                                  className="w-full px-2 py-1 border border-slate-200 rounded text-xs"
                                />
                              </div>
                              <div>
                                <label className="text-[10px] font-bold text-slate-600">Course Code</label>
                                <input
                                  type="text"
                                  value={lec.subjectCode}
                                  onChange={(e) => handleUpdateLecture(idx, { subjectCode: e.target.value })}
                                  className="w-full px-2 py-1 border border-slate-200 rounded text-xs"
                                />
                              </div>
                            </div>
                            <div className="grid grid-cols-3 gap-2">
                              <div>
                                <label className="text-[10px] font-bold text-slate-600">Day</label>
                                <select
                                  value={lec.dayOfWeek}
                                  onChange={(e) => handleUpdateLecture(idx, { dayOfWeek: Number(e.target.value) })}
                                  className="w-full px-1.5 py-1 border border-slate-200 rounded text-xs"
                                >
                                  <option value={1}>Monday</option>
                                  <option value={2}>Tuesday</option>
                                  <option value={3}>Wednesday</option>
                                  <option value={4}>Thursday</option>
                                  <option value={5}>Friday</option>
                                  <option value={6}>Saturday</option>
                                </select>
                              </div>
                              <div>
                                <label className="text-[10px] font-bold text-slate-600">Start (HH:mm:ss)</label>
                                <input
                                  type="text"
                                  value={lec.startTime}
                                  onChange={(e) => handleUpdateLecture(idx, { startTime: e.target.value })}
                                  className="w-full px-2 py-1 border border-slate-200 rounded text-xs"
                                />
                              </div>
                              <div>
                                <label className="text-[10px] font-bold text-slate-600">End (HH:mm:ss)</label>
                                <input
                                  type="text"
                                  value={lec.endTime}
                                  onChange={(e) => handleUpdateLecture(idx, { endTime: e.target.value })}
                                  className="w-full px-2 py-1 border border-slate-200 rounded text-xs"
                                />
                              </div>
                            </div>
                            <div className="flex items-center justify-between pt-1">
                              <input
                                type="text"
                                placeholder="Room (e.g. Room 319)"
                                value={lec.room}
                                onChange={(e) => handleUpdateLecture(idx, { room: e.target.value })}
                                className="px-2 py-1 border border-slate-200 rounded text-xs w-1/2"
                              />
                              <button
                                type="button"
                                onClick={() => setEditingIndex(null)}
                                className="px-3 py-1 bg-blue-600 text-white rounded text-[11px] font-bold hover:bg-blue-700"
                              >
                                Done
                              </button>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div className="flex items-start justify-between gap-2">
                              <h4 className="font-bold text-slate-900 text-[13px] leading-tight">
                                {lec.subjectName}
                              </h4>
                              <div className="flex items-center gap-1.5 shrink-0">
                                <span className="px-2 py-0.5 rounded-md bg-blue-100 text-blue-800 font-mono font-bold text-[10px]">
                                  {lec.subjectCode}
                                </span>
                                <button
                                  type="button"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setEditingIndex(idx);
                                  }}
                                  title="Edit slot timing"
                                  className="p-1 hover:bg-slate-200/70 rounded-md text-slate-400 hover:text-slate-700 transition-colors cursor-pointer"
                                >
                                  <Pencil className="w-3 h-3" />
                                </button>
                              </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-slate-500 pt-0.5">
                              <span className="flex items-center gap-1 font-semibold text-slate-700">
                                <Calendar className="w-3 h-3 text-slate-400" />
                                <span>{getDayName(lec.dayOfWeek)}</span>
                              </span>

                              <span className="flex items-center gap-1 text-slate-600 font-medium">
                                <Clock className="w-3 h-3 text-slate-400" />
                                <span>{formatTimeSlot(lec.startTime, lec.endTime)}</span>
                              </span>

                              <span className="flex items-center gap-1 text-slate-500">
                                <MapPin className="w-3 h-3 text-slate-400" />
                                <span>{lec.room || "Room 319"}</span>
                              </span>
                            </div>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ) : (
            /* ==================================================================== */
            /* VIEW 1: UPLOAD / PASTE TIMETABLE */
            /* ==================================================================== */
            <div className="space-y-4">
              {/* Tab Selector: Upload Image | Paste Text */}
              <div className="flex bg-slate-100 p-1 rounded-2xl">
                <button
                  type="button"
                  onClick={() => setActiveTab("image")}
                  className={`flex-1 py-2 rounded-xl font-bold text-xs transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
                    activeTab === "image"
                      ? "bg-white text-slate-900 shadow-2xs"
                      : "text-slate-500 hover:text-slate-800"
                  }`}
                >
                  <Upload className="w-3.5 h-3.5" />
                  <span>Timetable Image / Document</span>
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab("text")}
                  className={`flex-1 py-2 rounded-xl font-bold text-xs transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
                    activeTab === "text"
                      ? "bg-white text-slate-900 shadow-2xs"
                      : "text-slate-500 hover:text-slate-800"
                  }`}
                >
                  <FileText className="w-3.5 h-3.5" />
                  <span>Paste Text Schedule</span>
                </button>
              </div>

              {activeTab === "image" ? (
                <div className="space-y-3">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*,.pdf"
                    onChange={handleFileChange}
                    className="hidden"
                  />

                  {previewUrl ? (
                    <div className="relative border border-slate-200 rounded-2xl p-3 bg-slate-50 text-center space-y-2">
                      <img
                        src={previewUrl}
                        alt="Timetable Preview"
                        className="max-h-48 mx-auto rounded-xl object-contain shadow-xs border border-slate-200"
                      />
                      <div className="flex items-center justify-between px-2 pt-1">
                        <span className="text-[11px] font-medium text-slate-600 truncate max-w-[200px]">
                          {selectedFile?.name}
                        </span>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedFile(null);
                            setPreviewUrl(null);
                          }}
                          className="text-xs font-semibold text-rose-600 hover:underline"
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div
                      onClick={() => fileInputRef.current?.click()}
                      className="border-2 border-dashed border-slate-200 hover:border-blue-400 hover:bg-blue-50/20 rounded-2xl p-8 text-center space-y-2.5 transition-all cursor-pointer group"
                    >
                      <div className="w-12 h-12 rounded-2xl bg-slate-100 group-hover:bg-blue-100 group-hover:text-blue-600 text-slate-400 flex items-center justify-center mx-auto transition-colors">
                        <Upload className="w-6 h-6" />
                      </div>
                      <div>
                        <div className="font-bold text-slate-800 text-xs">
                          Click to select timetable photo or screenshot
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5">
                          Supports PNG, JPG, JPEG documents
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label className="font-bold text-slate-700 text-xs">
                      Timetable Text or Syllabus:
                    </label>
                    <button
                      type="button"
                      onClick={() => {
                        setPastedText(
                          "Monday:\n10:00 - 11:00 AM: Operating Systems (CS302) Room A-302\n11:00 - 12:00 PM: Computer Networks (CS304) Room A-302\n\nFriday:\n02:00 - 03:55 PM: Digital Transformation-I (DT501) Room 319\n11:00 - 11:55 AM: Data Structures and Algorithm Analysis (DSA501) Room 319"
                        );
                      }}
                      className="text-[11px] text-blue-600 font-semibold hover:underline"
                    >
                      Load Sample
                    </button>
                  </div>
                  <textarea
                    rows={6}
                    placeholder={`e.g.\nFriday:\n11:00 - 11:55 AM: Data Structures & Algorithms (DSA501) Room 319\n02:00 - 03:55 PM: Digital Transformation-I (DT501) Room 319`}
                    value={pastedText}
                    onChange={(e) => setPastedText(e.target.value)}
                    className="w-full p-3 rounded-2xl bg-slate-50 border border-slate-200 text-slate-900 text-xs focus:outline-none focus:border-blue-500 font-mono leading-relaxed"
                  />
                </div>
              )}

              {/* Error message */}
              {analysisError && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-2 text-rose-700 text-xs">
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <span>{analysisError}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center justify-between gap-3">
          {parsedLectures ? (
            <>
              <button
                type="button"
                disabled={isImporting}
                onClick={() => {
                  setParsedLectures(null);
                  setSelectedIndices([]);
                }}
                className="py-2.5 px-4 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 font-bold text-xs flex items-center gap-1.5 transition-all cursor-pointer"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Scan Another</span>
              </button>

              <button
                type="button"
                disabled={isImporting || selectedIndices.length === 0}
                onClick={handleImportClasses}
                className="flex-1 py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-md shadow-blue-600/20 transition-all cursor-pointer"
              >
                {isImporting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>{importProgress || "Saving to Schedule..."}</span>
                  </>
                ) : (
                  <>
                    <Check className="w-4 h-4" />
                    <span>Import {selectedIndices.length} Classes to Schedule</span>
                  </>
                )}
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                disabled={isAnalyzing}
                onClick={onClose}
                className="py-2.5 px-4 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 font-bold text-xs transition-all cursor-pointer"
              >
                Cancel
              </button>

              <button
                type="button"
                disabled={isAnalyzing || (activeTab === "image" && !selectedFile) || (activeTab === "text" && !pastedText.trim())}
                onClick={handleExtract}
                className="flex-1 py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-md shadow-blue-600/20 transition-all cursor-pointer"
              >
                {isAnalyzing ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Analyzing Timetable with Gemini...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="w-4 h-4" />
                    <span>Extract Schedule with Gemini AI</span>
                  </>
                )}
              </button>
            </>
          )}
        </div>

      </div>
    </div>
  );
}

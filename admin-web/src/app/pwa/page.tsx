import dynamic from "next/dynamic";

// Disable SSR entirely for the student PWA page.
// This prevents ALL hydration mismatches caused by device-specific checks
// (isMobilePhone, GPS geofence, crypto.subtle, localStorage) that differ
// between server render and client render.
const StudentPwaClient = dynamic(() => import("./StudentPwaClient"), {
  ssr: false,
  loading: () => (
    <div className="min-h-screen flex items-center justify-center bg-white">
      <div className="flex flex-col items-center gap-3">
        <div className="w-8 h-8 rounded-full border-2 border-blue-600 border-t-transparent animate-spin" />
        <p className="text-xs text-gray-500 font-medium">IIIT Naya Raipur · Loading...</p>
      </div>
    </div>
  ),
});

export default function PwaPage() {
  return <StudentPwaClient />;
}

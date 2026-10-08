import type { Metadata, Viewport } from "next";

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: "cover",
  themeColor: "#2563EB",
};

export const metadata: Metadata = {
  title: "Smart Attendance · Student Portal",
  description: "Cryptographic Anti-Proxy Student Attendance Portal for IIIT Naya Raipur",
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Attendance",
  },
  icons: {
    icon: "/icon.svg",
    apple: "/icon.svg",
  },
};

export default function PwaLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-[#F8F9FA] text-[#111827] flex flex-col items-center justify-start antialiased select-none pb-safe">
      <div className="w-full max-w-md min-h-screen flex flex-col relative bg-[#F8F9FA] shadow-2xl">
        {children}
      </div>
    </div>
  );
}

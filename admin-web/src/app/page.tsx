import Link from "next/link";
import { ArrowRight, ShieldCheck } from "lucide-react";

export default function LoginPage() {
  return (
    <main className="min-h-screen flex items-center justify-center p-6 bg-canvas">
      <div className="w-full max-w-md card-clean p-8 bg-white shadow-sm">
        <div className="flex items-center gap-2 mb-2">
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center text-white font-bold text-sm">
            SA
          </div>
          <span className="font-semibold text-lg tracking-tight">Smart Attendance</span>
        </div>
        <h1 className="text-2xl font-bold text-primary mt-4">Institutional Portal</h1>
        <p className="text-secondary text-sm mt-1">Sign in with your university administrator credentials</p>

        <form className="mt-6 space-y-4" action="/dashboard">
          <div>
            <label className="block text-xs font-semibold text-secondary uppercase tracking-wider mb-1.5">
              Work Email / Username
            </label>
            <input
              type="email"
              defaultValue="admin@iitdemo.edu"
              placeholder="admin@university.edu"
              className="w-full px-3.5 py-2.5 rounded-lg border border-subtle focus:outline-none focus:ring-2 focus:ring-primary/20 text-sm"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-xs font-semibold text-secondary uppercase tracking-wider">
                Password
              </label>
              <a href="#" className="text-xs text-secondary hover:text-primary underline">Forgot?</a>
            </div>
            <input
              type="password"
              defaultValue="••••••••••••"
              className="w-full px-3.5 py-2.5 rounded-lg border border-subtle focus:outline-none focus:ring-2 focus:ring-primary/20 text-sm"
            />
          </div>

          <Link
            href="/dashboard"
            className="w-full h-11 bg-primary text-white rounded-lg flex items-center justify-center font-medium text-sm hover:bg-black transition-colors gap-2 mt-6"
          >
            <span>Enter Admin Dashboard</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </form>

        <div className="mt-8 pt-6 border-t border-subtle flex items-center gap-2 text-xs text-secondary justify-center">
          <ShieldCheck className="w-4 h-4 text-success" />
          <span>Protected by Hardware-Backed Retaining Tokens & RLS</span>
        </div>
      </div>
    </main>
  );
}

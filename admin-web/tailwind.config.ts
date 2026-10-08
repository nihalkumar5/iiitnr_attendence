import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        canvas: "#F8FAFC",
        card: "#FFFFFF",
        surface: "#F1F5F9",
        "surface-subtle": "#F8FAFC",
        "border-subtle": "#E2E8F0",
        "border-highlight": "#CBD5E1",
        "text-primary": "#0F172A",
        "text-secondary": "#475569",
        "text-muted": "#94A3B8",
        brand: {
          DEFAULT: "#2563EB",
          hover: "#1D4ED8",
          subtle: "#EFF6FF",
          border: "#BFDBFE",
        },
        present: {
          DEFAULT: "#059669",
          glow: "#10B981",
          bg: "#ECFDF5",
          border: "#A7F3D0",
        },
        review: {
          DEFAULT: "#D97706",
          bg: "#FFFBEB",
          border: "#FDE68A",
        },
        absent: {
          DEFAULT: "#DC2626",
          bg: "#FEF2F2",
          border: "#FECACA",
        },
      },
      borderRadius: {
        "2xl": "16px",
        "xl": "12px",
        "lg": "8px",
        "full": "9999px",
      },
      fontFamily: {
        sans: ["Inter", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "Helvetica Neue", "Arial", "sans-serif"],
      },
    },
  },
  plugins: [],
};
export default config;

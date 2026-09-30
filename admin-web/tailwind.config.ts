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
        canvas: "#F8F8F6",
        primary: "#111111",
        secondary: "#666666",
        subtle: "#E5E5E5",
        success: "#16803C",
        warning: "#B7791F",
        danger: "#C53030",
        pill: "#EFEFE9",
      },
    },
  },
  plugins: [],
};
export default config;

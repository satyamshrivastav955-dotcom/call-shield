import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        cyber: {
          bg: "#06080e",
          chassis: "#0a0f1d",
          surface: "#0f172a",
          "surface-elevated": "#162032",
          border: "#1c2638",
          "border-highlight": "#2d3e58",
          blue: "#0284c7",
          "blue-light": "#38bdf8",
          green: "#059669",
          "green-light": "#10b981",
          amber: "#d97706",
          "amber-light": "#f59e0b",
          red: "#dc2626",
          "red-light": "#ef4444",
          muted: "#64748b",
          text: "#f8fafc",
          "text-muted": "#94a3b8",
        },
        risk: {
          safe: "#059669",
          "safe-bg": "rgba(5, 150, 105, 0.12)",
          verify: "#d97706",
          "verify-bg": "rgba(217, 119, 6, 0.12)",
          critical: "#dc2626",
          "critical-bg": "rgba(220, 38, 38, 0.14)",
          unavailable: "#475569",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "Inter", "-apple-system", "system-ui", "sans-serif"],
        mono: [
          "var(--font-mono)",
          "JetBrains Mono",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "Consolas",
          "monospace",
        ],
      },
      animation: {
        "radar-sweep": "radarSweep 4s linear infinite",
        "telemetry-blink": "telemetryBlink 1.5s ease-in-out infinite",
        "terminal-scanline": "scanline 8s linear infinite",
        "pulse-slow": "pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite",
      },
      keyframes: {
        radarSweep: {
          "0%": { transform: "rotate(0deg)" },
          "100%": { transform: "rotate(360deg)" },
        },
        telemetryBlink: {
          "0%, 100%": { opacity: "1" },
          "50%": { opacity: "0.3" },
        },
        scanline: {
          "0%": { transform: "translateY(-100%)" },
          "100%": { transform: "translateY(100%)" },
        },
      },
    },
  },
  plugins: [],
};

export default config;

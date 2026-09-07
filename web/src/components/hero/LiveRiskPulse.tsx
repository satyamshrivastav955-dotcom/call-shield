"use client";

import { useEffect, useState } from "react";
import { siteContent } from "@/content/content";

type RiskState = "safe" | "verify" | "critical";

export function LiveRiskPulse() {
  const [activeState, setActiveState] = useState<RiskState>("safe");
  const [isAutoCycling, setIsAutoCycling] = useState(true);

  // Auto cycle every 3.8 seconds if not paused
  useEffect(() => {
    if (!isAutoCycling) return;
    const interval = setInterval(() => {
      setActiveState((current) => {
        if (current === "safe") return "verify";
        if (current === "verify") return "critical";
        return "safe";
      });
    }, 3800);
    return () => clearInterval(interval);
  }, [isAutoCycling]);

  const guide = siteContent.hero.pulseGuide;
  const currentData =
    activeState === "safe"
      ? guide.safe
      : activeState === "verify"
      ? guide.verify
      : guide.critical;

  // Arc length calculations for 180° half circle (r=72)
  const radius = 72;
  const arcLength = Math.PI * radius; // ~226.2
  const scorePercent = currentData.score / 100;
  const strokeDashoffset = arcLength * (1 - scorePercent);

  const colors = {
    safe: {
      stroke: "#059669",
      text: "text-emerald-400",
      bg: "bg-emerald-950/20",
      border: "border-emerald-700/40",
      badge: "bg-emerald-500/10 text-emerald-300 border-emerald-500/40",
      halo: "rgba(5, 150, 105, 0.25)",
      statusText: "NORMAL TELECOM TRAFFIC",
    },
    verify: {
      stroke: "#d97706",
      text: "text-amber-400",
      bg: "bg-amber-950/20",
      border: "border-amber-700/40",
      badge: "bg-amber-500/10 text-amber-300 border-amber-500/40",
      halo: "rgba(217, 119, 6, 0.25)",
      statusText: "BIOMETRIC DRIFT ADVISORY",
    },
    critical: {
      stroke: "#dc2626",
      text: "text-red-400",
      bg: "bg-red-950/30",
      border: "border-red-700/60",
      badge: "bg-red-500/15 text-red-300 border-red-500/50",
      halo: "rgba(220, 38, 38, 0.35)",
      statusText: "THREAT BREACH // TRANSACTION HOLD",
    },
  }[activeState];

  return (
    <div className="relative mx-auto w-full max-w-lg rounded-xl border border-cyber-border bg-cyber-chassis/95 p-5 shadow-2xl backdrop-blur-xl corner-bracket">
      {/* Module Header Bar */}
      <div className="flex items-center justify-between border-b border-cyber-border pb-3 font-mono">
        <div className="flex items-center gap-2">
          <span className="relative flex h-2 w-2">
            <span
              className="absolute inline-flex h-full w-full rounded-full opacity-75 animate-ping"
              style={{ backgroundColor: colors.stroke }}
            />
            <span
              className="relative inline-flex h-2 w-2 rounded-full"
              style={{ backgroundColor: colors.stroke }}
            />
          </span>
          <span className="text-[11px] font-bold text-slate-200 uppercase tracking-wider">
            RADAR TELEMETRY // THREAT GAUGE
          </span>
        </div>

        {/* State selector pills */}
        <div className="flex items-center gap-1 bg-black/60 p-1 rounded border border-cyber-border text-[10px]">
          {(["safe", "verify", "critical"] as RiskState[]).map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => {
                setActiveState(st);
                setIsAutoCycling(false);
              }}
              className={`rounded px-2 py-0.5 uppercase tracking-wider transition-all ${
                activeState === st
                  ? "bg-cyber-surface-elevated text-cyber-blue-light font-bold border border-cyber-border-highlight shadow"
                  : "text-slate-500 hover:text-slate-300"
              }`}
            >
              {st}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setIsAutoCycling(!isAutoCycling)}
            title={isAutoCycling ? "Pause cycle" : "Resume auto-cycling"}
            className="text-slate-500 hover:text-cyber-blue-light px-1"
          >
            {isAutoCycling ? "❚❚" : "▶"}
          </button>
        </div>
      </div>

      {/* Military-Spec Circular Threat Meter */}
      <div className="flex flex-col items-center justify-center pt-5 pb-2">
        <div className="relative flex items-center justify-center">
          <svg className="w-64 h-36 overflow-visible" viewBox="-10 -10 180 100">
            {/* Outer Tick Marks for Radar Calibration */}
            <g stroke="#1c2638" strokeWidth="1.2">
              <line x1="20" y1="85" x2="12" y2="85" />
              <line x1="30" y1="50" x2="22" y2="47" />
              <line x1="55" y1="25" x2="50" y2="18" />
              <line x1="90" y1="15" x2="90" y2="7" />
              <line x1="125" y1="25" x2="130" y2="18" />
              <line x1="150" y1="50" x2="158" y2="47" />
              <line x1="160" y1="85" x2="168" y2="85" />
            </g>

            {/* Base Gray Track Arc */}
            <path
              d="M 20 85 A 72 72 0 0 1 160 85"
              fill="none"
              stroke="#0f172a"
              strokeWidth="11"
              strokeLinecap="round"
            />
            {/* Pulsing Active Threat Arc */}
            <path
              d="M 20 85 A 72 72 0 0 1 160 85"
              fill="none"
              stroke={colors.stroke}
              strokeWidth="11"
              strokeLinecap="round"
              strokeDasharray={arcLength.toFixed(1)}
              strokeDashoffset={strokeDashoffset.toFixed(1)}
              className="transition-all duration-700 ease-out"
              style={{ filter: `drop-shadow(0 0 8px ${colors.halo})` }}
            />
          </svg>

          {/* Centered Monospace Telemetry Score */}
          <div className="absolute inset-x-0 bottom-1 flex flex-col items-center justify-center text-center">
            <div className="flex items-baseline gap-1 font-mono">
              <span className={`text-5xl font-black tracking-tight ${colors.text} transition-colors duration-500`}>
                {currentData.score}
              </span>
              <span className="text-xs font-bold text-slate-500">/100</span>
            </div>
            <span
              className={`mt-1 inline-block rounded border px-2.5 py-0.5 text-[9px] font-bold font-mono tracking-widest uppercase ${colors.badge}`}
            >
              {currentData.band}
            </span>
          </div>
        </div>

        {/* Threat State Description */}
        <div className="mt-4 text-center px-2">
          <div className="text-xs font-mono font-bold text-white tracking-wide uppercase">
            {currentData.title}
          </div>
          <p className="mt-1 text-[11px] text-slate-400 max-w-sm leading-relaxed font-sans">
            {currentData.description}
          </p>
        </div>
      </div>

      {/* Emergency Freeze Hold Banner (active during critical risk) */}
      {activeState === "critical" ? (
        <div className="mt-4 rounded border border-red-600 bg-red-950/40 p-3 shadow-lg glow-red animate-pulse-slow">
          <div className="flex items-center gap-2.5 font-mono">
            <span className="flex h-2.5 w-2.5 relative flex-shrink-0">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500"></span>
            </span>
            <div className="flex-1 text-[10px]">
              <div className="font-bold text-red-200 uppercase tracking-wider">
                HOLD DIRECTIVE ENFORCED // IN-FLIGHT MEMORY LOCK
              </div>
              <p className="text-red-300/80 mt-0.5">
                Target Action: ₹8,50,000 NEFT Transfer frozen pending biometric proof.
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="mt-4 flex items-center justify-between rounded border border-cyber-border bg-black/40 px-3 py-2 text-[11px] font-mono text-slate-400">
          <span className="text-slate-500 uppercase tracking-wider">DEFENSE POSTURE</span>
          <span className="text-emerald-400 font-bold">{colors.statusText}</span>
        </div>
      )}

      {/* Signal Oscillogram / Telemetry Strip */}
      <div className="mt-4 grid grid-cols-3 gap-2 border-t border-cyber-border pt-3 text-[10px] font-mono">
        <div className="rounded border border-cyber-border bg-black/40 p-2 text-center">
          <span className="text-slate-500 uppercase text-[8px] tracking-wider block">AST Spectral</span>
          <div className={`mt-0.5 font-black ${activeState === "critical" ? "text-red-400" : "text-emerald-400"}`}>
            {activeState === "critical" ? "0.9412" : activeState === "verify" ? "0.4820" : "0.1205"}
          </div>
        </div>
        <div className="rounded border border-cyber-border bg-black/40 p-2 text-center">
          <span className="text-slate-500 uppercase text-[8px] tracking-wider block">ECAPA Cosine</span>
          <div className={`mt-0.5 font-black ${activeState === "critical" ? "text-red-400" : activeState === "verify" ? "text-amber-400" : "text-emerald-400"}`}>
            {activeState === "critical" ? "0.0810" : activeState === "verify" ? "0.6240" : "0.9620"}
          </div>
        </div>
        <div className="rounded border border-cyber-border bg-black/40 p-2 text-center">
          <span className="text-slate-500 uppercase text-[8px] tracking-wider block">Threat Urgency</span>
          <div className={`mt-0.5 font-black ${activeState === "critical" ? "text-red-400" : activeState === "verify" ? "text-amber-400" : "text-emerald-400"}`}>
            {activeState === "critical" ? "96/100" : activeState === "verify" ? "60/100" : "10/100"}
          </div>
        </div>
      </div>
    </div>
  );
}

"use client";

interface RiskGaugeProps {
  score: number | null;
  band: string;
  verifyThreshold?: number;
  criticalThreshold?: number;
}

export function RiskGauge({
  score,
  band,
  verifyThreshold = 50,
  criticalThreshold = 70,
}: RiskGaugeProps) {
  const currentRisk = score ?? 0;
  const isAvailable = score !== null;

  // Colors based on scenario thresholds
  const isCritical = currentRisk >= criticalThreshold;
  const isVerify = currentRisk >= verifyThreshold && !isCritical;

  const color = !isAvailable
    ? "#475569" // slate
    : isCritical
    ? "#dc2626" // red
    : isVerify
    ? "#d97706" // amber
    : "#059669"; // green

  const statusLabel = !isAvailable
    ? "NO ACTIVE STREAM // AWAITING AUDIO"
    : isCritical
    ? "CRITICAL THREAT // ACTION HOLD ENFORCED"
    : isVerify
    ? "THREAT ADVISORY // VERIFICATION MANDATORY"
    : "NORMAL CONVERSATIONAL TELEMETRY";

  // Arc math for half circle (center at 90, 85, radius 70)
  const radius = 70;
  const arcLength = Math.PI * radius; // ~219.9
  const clampedFraction = Math.min(100, Math.max(0, currentRisk)) / 100;
  const strokeOffset = arcLength * (1 - clampedFraction);

  // Threshold marker calculation (angle from 180° to 0°)
  const threshAngle = Math.PI * (1 - verifyThreshold / 100);
  const cx = 90;
  const cy = 85;
  const mx1 = cx + radius * Math.cos(Math.PI - threshAngle);
  const my1 = cy - radius * Math.sin(Math.PI - threshAngle);
  const mx2 = cx + (radius - 14) * Math.cos(Math.PI - threshAngle);
  const my2 = cy - (radius - 14) * Math.sin(Math.PI - threshAngle);

  return (
    <div className="flex flex-col items-center justify-center p-3 font-mono">
      <div className="relative flex items-center justify-center">
        <svg className="w-56 h-32 overflow-visible" viewBox="0 0 180 100">
          {/* Calibrated Tick Marks */}
          <g stroke="#1c2638" strokeWidth="1.2">
            <line x1="20" y1="85" x2="12" y2="85" />
            <line x1="28" y1="52" x2="21" y2="49" />
            <line x1="52" y1="28" x2="47" y2="21" />
            <line x1="90" y1="15" x2="90" y2="7" />
            <line x1="128" y1="28" x2="133" y2="21" />
            <line x1="152" y1="52" x2="159" y2="49" />
            <line x1="160" y1="85" x2="168" y2="85" />
          </g>

          {/* Background Arc */}
          <path
            d="M 20 85 A 70 70 0 0 1 160 85"
            fill="none"
            stroke="#0f172a"
            strokeWidth="11"
            strokeLinecap="round"
          />
          {/* Active Risk Value Arc */}
          <path
            d="M 20 85 A 70 70 0 0 1 160 85"
            fill="none"
            stroke={color}
            strokeWidth="11"
            strokeLinecap="round"
            strokeDasharray={arcLength.toFixed(1)}
            strokeDashoffset={strokeOffset.toFixed(1)}
            className="transition-all duration-500 ease-out"
            style={{ filter: `drop-shadow(0 0 6px ${color}88)` }}
          />
          {/* Scenario Threshold Marker Needle */}
          <line
            x1={mx1.toFixed(1)}
            y1={my1.toFixed(1)}
            x2={mx2.toFixed(1)}
            y2={my2.toFixed(1)}
            stroke="#f59e0b"
            strokeWidth="2.5"
            strokeLinecap="round"
            opacity="0.85"
          />
        </svg>

        {/* Center Monospace Score */}
        <div className="absolute inset-x-0 bottom-0 flex flex-col items-center justify-center text-center">
          <div className="flex items-baseline gap-1">
            <span
              className="text-4xl font-black tracking-tight transition-colors duration-300"
              style={{ color }}
            >
              {isAvailable ? Math.round(currentRisk) : "—"}
            </span>
            <span className="text-xs font-bold text-slate-500 font-sans">/ 100</span>
          </div>
          <span
            className="mt-0.5 text-[9px] font-bold uppercase tracking-wider"
            style={{ color }}
          >
            {band?.toUpperCase() || "MONITORING"}
          </span>
        </div>
      </div>

      {/* Threshold Needle Legend */}
      <div className="flex items-center gap-1.5 mt-2 text-[9px] text-slate-500">
        <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
        <span>Verify needle at {verifyThreshold} · Critical at {criticalThreshold}</span>
      </div>

      {/* Threat Status Alert Bar */}
      <div
        className={`w-full mt-3 rounded py-1.5 text-center text-[10px] font-bold tracking-wider transition-colors duration-300 border ${
          !isAvailable
            ? "border-slate-800 bg-slate-900/60 text-slate-400"
            : isCritical
            ? "border-red-600/70 bg-red-950/60 text-red-200 glow-red animate-pulse-slow"
            : isVerify
            ? "border-amber-600/60 bg-amber-950/50 text-amber-200 glow-amber"
            : "border-emerald-600/60 bg-emerald-950/40 text-emerald-200"
        }`}
      >
        {statusLabel}
      </div>
    </div>
  );
}

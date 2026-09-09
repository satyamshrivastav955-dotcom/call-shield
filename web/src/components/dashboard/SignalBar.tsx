"use client";

interface SignalBarProps {
  label: string;
  sublabel?: string;
  value: number | null | undefined; // 0..1 or 0..100 depending on isScore100
  isScore100?: boolean;
  isFlagged?: boolean;
  flagThreshold?: number;
  reverseColor?: boolean; // if high is good (e.g. speaker match)
}

export function SignalBar({
  label,
  sublabel,
  value,
  isScore100 = false,
  isFlagged = false,
  reverseColor = false,
}: SignalBarProps) {
  const isAvailable = value !== null && value !== undefined;
  const numericValue = isAvailable ? (isScore100 ? (value as number) : (value as number) * 100) : 0;

  // Determine color based on threshold & reverseColor
  let barColor = "#475569"; // muted
  let textColor = "text-slate-500";

  if (isAvailable) {
    if (reverseColor) {
      // High value is good (e.g., voiceprint similarity)
      if (numericValue >= 70) {
        barColor = "#059669"; // green
        textColor = "text-emerald-400";
      } else if (numericValue >= 50) {
        barColor = "#d97706"; // amber
        textColor = "text-amber-400";
      } else {
        barColor = "#dc2626"; // red
        textColor = "text-red-400";
      }
    } else {
      // High value is bad (e.g. synthetic probability, urgency)
      if (numericValue >= 70 || isFlagged) {
        barColor = "#dc2626"; // red
        textColor = "text-red-400";
      } else if (numericValue >= 45) {
        barColor = "#d97706"; // amber
        textColor = "text-amber-400";
      } else {
        barColor = "#059669"; // green
        textColor = "text-emerald-400";
      }
    }
  }

  const displayValue = !isAvailable
    ? "—"
    : isScore100
    ? `${Math.round(value as number)}/100`
    : `${(Math.round((value as number) * 1000) / 10).toFixed(1)}%`;

  return (
    <div
      className={`rounded border p-2.5 transition-all duration-200 font-mono ${
        isFlagged
          ? "border-red-600/70 bg-red-950/25 shadow-[0_0_10px_rgba(220,38,38,0.25)]"
          : "border-cyber-border bg-black/40 hover:bg-cyber-surface/60"
      }`}
    >
      <div className="flex items-center justify-between">
        <div>
          <span className="text-[9px] font-bold uppercase tracking-wider text-slate-400 block">
            {label}
          </span>
          {sublabel && (
            <p className="text-[10px] text-slate-500 truncate max-w-[200px] sm:max-w-[280px]">
              {sublabel}
            </p>
          )}
        </div>
        <div className={`text-sm font-black ${textColor}`}>
          {displayValue}
        </div>
      </div>

      <div className="mt-2 h-1 w-full rounded-full bg-slate-900 overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500 ease-out"
          style={{
            width: isAvailable ? `${Math.min(100, Math.max(0, numericValue))}%` : "0%",
            backgroundColor: barColor,
          }}
        />
      </div>
    </div>
  );
}

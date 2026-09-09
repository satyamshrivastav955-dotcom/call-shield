"use client";

interface SparklineChartProps {
  history: number[];
  verifyThreshold?: number;
  criticalThreshold?: number;
}

export function SparklineChart({
  history,
  verifyThreshold = 50,
  criticalThreshold = 70,
}: SparklineChartProps) {
  const W = 600;
  const H = 90;

  // Transform history array into SVG points
  const points =
    history.length > 0
      ? history.map((val, idx) => {
          const step = history.length > 1 ? W / (history.length - 1) : W;
          const x = idx * step;
          const y = H - (Math.min(100, Math.max(0, val)) / 100) * H;
          return `${x.toFixed(1)},${y.toFixed(1)}`;
        })
      : [];

  const polylinePoints = points.join(" ");

  // Fill polygon points
  const fillPolygon =
    points.length > 0
      ? `${polylinePoints} ${points[points.length - 1].split(",")[0]},${H} 0,${H}`
      : "";

  const verifyY = (H - (verifyThreshold / 100) * H).toFixed(1);
  const criticalY = (H - (criticalThreshold / 100) * H).toFixed(1);

  return (
    <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-4 space-y-2 font-mono">
      <div className="flex items-center justify-between border-b border-cyber-border pb-2 text-xs">
        <span className="font-bold uppercase tracking-wider text-slate-300 text-[11px]">
          THREAT TIMELINE OSCILLOGRAM (LAST 120 TICKS)
        </span>
        <div className="flex items-center gap-3 text-[9px] text-slate-400">
          <span className="flex items-center gap-1">
            <span className="h-1 w-2.5 bg-amber-400 rounded-none" />
            ADVISORY ({verifyThreshold})
          </span>
          <span className="flex items-center gap-1">
            <span className="h-1 w-2.5 bg-red-400 rounded-none" />
            HOLD ({criticalThreshold})
          </span>
        </div>
      </div>

      <div className="relative w-full h-24 pt-1">
        <svg
          className="w-full h-full overflow-visible"
          viewBox={`0 0 ${W} ${H}`}
          preserveAspectRatio="none"
        >
          <defs>
            <linearGradient id="cyberSparkGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#dc2626" stopOpacity="0.4" />
              <stop offset="50%" stopColor="#d97706" stopOpacity="0.15" />
              <stop offset="100%" stopColor="#0284c7" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Sub-pixel Grid Lines */}
          <line x1="0" y1="22" x2={W} y2="22" stroke="#1c2638" strokeWidth="0.8" />
          <line x1="0" y1="45" x2={W} y2="45" stroke="#1c2638" strokeWidth="0.8" />
          <line x1="0" y1="67" x2={W} y2="67" stroke="#1c2638" strokeWidth="0.8" />

          {/* Threshold guide lines */}
          <line
            x1="0"
            y1={verifyY}
            x2={W}
            y2={verifyY}
            stroke="#f59e0b"
            strokeDasharray="4 4"
            strokeWidth="1.2"
            opacity="0.75"
          />
          <line
            x1="0"
            y1={criticalY}
            x2={W}
            y2={criticalY}
            stroke="#ef4444"
            strokeDasharray="4 4"
            strokeWidth="1.2"
            opacity="0.75"
          />

          {/* Gradient Fill under line */}
          {fillPolygon && <polygon points={fillPolygon} fill="url(#cyberSparkGrad)" />}

          {/* Active Risk Polyline */}
          {polylinePoints && (
            <polyline
              points={polylinePoints}
              fill="none"
              stroke="#38bdf8"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="transition-all duration-250"
            />
          )}
        </svg>

        {history.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center text-[11px] text-slate-600 italic">
            &gt; AWAITING INGESTION STREAM TICKS... &lt;
          </div>
        )}
      </div>
    </div>
  );
}

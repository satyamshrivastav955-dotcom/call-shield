"use client";

import { useEffect, useRef } from "react";

interface TranscriptTickerProps {
  lines: Array<{ text: string; time: string }>;
  asrFailures?: number;
}

export function TranscriptTicker({ lines, asrFailures = 0 }: TranscriptTickerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [lines]);

  return (
    <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-4 space-y-2.5 flex flex-col h-64 font-mono">
      <div className="flex items-center justify-between border-b border-cyber-border pb-2 flex-shrink-0 text-xs">
        <span className="font-bold uppercase tracking-wider text-slate-300 text-[11px]">
          FORENSIC SPEECH TRANSCRIPT (ASR TAP)
        </span>
        <div className="flex items-center gap-3 text-[10px] text-slate-500">
          {asrFailures > 0 && <span className="text-amber-400">FAILS: {asrFailures}</span>}
          <span>{lines.length} CHUNKS</span>
        </div>
      </div>

      <div
        ref={containerRef}
        className="flex-1 overflow-y-auto space-y-1.5 pr-1 text-[11px] leading-relaxed"
      >
        {lines.length > 0 ? (
          lines.map((item, idx) => (
            <div
              key={idx}
              className="border-b border-cyber-border/40 pb-1 flex items-start gap-2"
            >
              <span className="text-slate-500 text-[9px] flex-shrink-0">[{item.time}]</span>
              <span className="text-slate-200">{item.text}</span>
            </div>
          ))
        ) : (
          <div className="flex h-full items-center justify-center text-[11px] text-slate-600 italic">
            &gt; AWAITING INBOUND SPEECH PACKETS... &lt;
          </div>
        )}
      </div>
    </div>
  );
}

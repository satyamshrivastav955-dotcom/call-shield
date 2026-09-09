"use client";

import { DEMO_CONTEXTS } from "@/lib/demoData";

interface CallerContextCardProps {
  scenario: string;
}

export function CallerContextCard({ scenario }: CallerContextCardProps) {
  const ctx = DEMO_CONTEXTS[scenario] || DEMO_CONTEXTS.high_value_txn;

  return (
    <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-4 space-y-3 font-mono text-xs">
      <div className="flex items-center justify-between border-b border-cyber-border pb-2.5">
        <span className="font-bold uppercase tracking-wider text-slate-300 text-[11px]">
          SESSION TARGET PROFILE
        </span>
        <span className="rounded border border-amber-500/40 bg-amber-500/10 px-1.5 py-0.2 text-[8px] font-bold text-amber-300 uppercase">
          SIMULATED DEMO PROFILE
        </span>
      </div>

      {/* Target Avatar & Details */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded border border-cyber-blue/60 bg-cyber-surface text-cyber-blue-light font-black text-sm">
          {ctx.initial}
        </div>
        <div>
          <h3 className="font-bold text-white text-sm tracking-tight">{ctx.name}</h3>
          <p className="text-[10px] text-slate-500">{ctx.phone} {"//"} INBOUND CHANNEL</p>
        </div>
      </div>

      {/* Transaction & Risk Rows */}
      <div className="space-y-1.5 border-t border-cyber-border pt-2 text-[11px]">
        <div className="flex justify-between py-0.5 border-b border-cyber-border/40">
          <span className="text-slate-500">REQUEST KIND:</span>
          <span className="text-slate-200 font-bold">{ctx.txn}</span>
        </div>
        <div className="flex justify-between py-0.5 border-b border-cyber-border/40">
          <span className="text-slate-500">EXPOSURE SUM:</span>
          <span className={`font-black ${ctx.amount ? "text-red-400 font-mono" : "text-slate-500"}`}>
            {ctx.amount || "N/A"}
          </span>
        </div>
        <div className="flex justify-between py-0.5 border-b border-cyber-border/40">
          <span className="text-slate-500">ASSERTED ROLE:</span>
          <span className="text-slate-300">{ctx.claimed}</span>
        </div>
        <div className="flex justify-between py-0.5">
          <span className="text-slate-500">THREAT REPUTATION:</span>
          <span className={`font-bold ${ctx.hist.includes("flag") ? "text-amber-400" : "text-emerald-400"}`}>
            {ctx.hist}
          </span>
        </div>
      </div>

      <p className="text-[9px] text-slate-600 italic">
        Target profile values above are simulated for evaluation clarity. Real-time acoustic scores below are computed live by models.
      </p>
    </div>
  );
}

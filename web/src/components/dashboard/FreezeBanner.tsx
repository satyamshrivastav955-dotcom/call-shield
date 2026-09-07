"use client";

import { useState } from "react";
import { decideFreeze } from "@/lib/api";

interface FreezeBannerProps {
  freezeId?: number;
  requestType?: string;
  amount?: string | null;
  riskScore?: number | null;
  onResolved?: (decision: string) => void;
}

export function FreezeBanner({
  freezeId = 101,
  requestType = "money",
  amount = "₹8,50,000",
  riskScore = 87,
  onResolved,
}: FreezeBannerProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [resolvedStatus, setResolvedStatus] = useState<string | null>(null);

  const handleDecision = async (decision: "confirm" | "override") => {
    setIsSubmitting(true);
    const res = await decideFreeze({
      freeze_id: freezeId,
      decision,
    });
    setIsSubmitting(false);
    setResolvedStatus(decision === "confirm" ? "APPROVED_PROCEED" : "REJECTED_BLOCKED");
    if (onResolved) onResolved(decision);
  };

  if (resolvedStatus) {
    return (
      <div
        className={`rounded border p-3.5 text-xs font-mono flex items-center justify-between shadow-lg ${
          resolvedStatus === "APPROVED_PROCEED"
            ? "border-emerald-600/60 bg-emerald-950/40 text-emerald-300"
            : "border-slate-700 bg-slate-900/80 text-slate-400"
        }`}
      >
        <div className="flex items-center gap-2">
          <span className="h-1.5 w-1.5 rounded-full bg-current" />
          <span>
            {resolvedStatus === "APPROVED_PROCEED"
              ? "INTERCEPT OVERRIDDEN BY OPERATOR // TRANSACTION RELEASED"
              : "HOLD ENFORCED // FRAUDULENT ACTION PERMANENTLY BLOCKED"}
          </span>
        </div>
        <button
          type="button"
          onClick={() => setResolvedStatus(null)}
          className="text-slate-400 hover:text-white underline text-[10px]"
        >
          [ DISMISS ]
        </button>
      </div>
    );
  }

  return (
    <div className="relative overflow-hidden rounded border-2 border-red-600 bg-red-950/80 p-4 shadow-2xl glow-red animate-pulse-slow font-mono">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        {/* Warning Copy */}
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-red-300">
            <span className="flex h-2.5 w-2.5 relative flex-shrink-0">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500"></span>
            </span>
            <span className="text-xs font-black uppercase tracking-widest text-red-100">
              IN-FLIGHT TRANSACTION INTERCEPT ACTIVE
            </span>
            <span className="rounded border border-red-700 bg-red-900/80 px-1.5 py-0.2 text-[9px] text-red-200">
              RISK: {riskScore}/100
            </span>
          </div>

          <p className="text-[11px] text-red-100 leading-relaxed font-sans">
            High-risk synthetic clone coupled with an active{" "}
            <span className="font-bold underline font-mono text-white">
              {requestType.toUpperCase()} ({amount || "ACTION"})
            </span>
            . In-app button locked. Dual-key confirmation required to release hold.
          </p>
        </div>

        {/* Dual-Key Action Buttons */}
        <div className="flex items-center gap-2 flex-shrink-0 text-xs">
          <button
            type="button"
            disabled={isSubmitting}
            onClick={() => handleDecision("override")}
            className="rounded border border-red-500 bg-red-600 hover:bg-red-500 disabled:opacity-40 px-3.5 py-2 font-bold text-white shadow transition-all"
          >
            {isSubmitting ? "[ ... ]" : "[ ENFORCE PERMANENT BLOCK ]"}
          </button>
          <button
            type="button"
            disabled={isSubmitting}
            onClick={() => handleDecision("confirm")}
            className="rounded border border-red-400/60 bg-black/60 hover:bg-black/90 disabled:opacity-40 px-3 py-2 font-semibold text-red-200 transition-all"
          >
            Authenticate Override
          </button>
        </div>
      </div>
    </div>
  );
}

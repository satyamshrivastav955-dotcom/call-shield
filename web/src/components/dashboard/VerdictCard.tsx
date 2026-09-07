"use client";

interface VerdictCardProps {
  recommendation?: string | null;
  reasons?: string[];
  band?: string;
  decision?: string;
}

export function VerdictCard({
  recommendation,
  reasons = [],
  band = "passive",
  decision = "log",
}: VerdictCardProps) {
  const isCritical = band === "critical" || decision === "freeze";
  const isVerify = band === "verify" || decision === "verify";

  const actionSteps = isCritical
    ? [
        { label: "MANDATORY CALL DISCONNECT", detail: "Terminate inbound session immediately; do not negotiate." },
        { label: "OUT-OF-BAND DIRECTORY CALLBACK", detail: "Verify caller via pre-registered branch/corporate directory." },
        { label: "FLAG FORENSIC HASH TO FRAUD DESK", detail: "Transmit session payload hash to central threat feed." },
      ]
    : isVerify
    ? [
        { label: "CHALLENGE-RESPONSE VERIFICATION", detail: "Enforce biometric or pre-shared secret authentication." },
        { label: "RESTRICT IN-APP OTP SHARING", detail: "Do not approve or verbalize 6-digit authentication tokens." },
      ]
    : [
        { label: "PASSIVE REPUTATION MONITORING", detail: "Acoustic spectrum within baseline natural parameters." },
      ];

  return (
    <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-4 space-y-3.5 font-mono text-xs">
      <div className="flex items-center justify-between border-b border-cyber-border pb-2.5">
        <span className="font-bold uppercase tracking-wider text-slate-300 text-[11px]">
          FORENSIC ARBITRATION DIRECTIVE
        </span>
        <span
          className={`rounded border px-2 py-0.2 text-[9px] font-bold uppercase ${
            isCritical
              ? "border-red-500/50 bg-red-500/20 text-red-300"
              : isVerify
              ? "border-amber-500/50 bg-amber-500/20 text-amber-300"
              : "border-emerald-500/50 bg-emerald-500/20 text-emerald-300"
          }`}
        >
          {band.toUpperCase()}
        </span>
      </div>

      {/* Main Guidance Text */}
      <div
        className={`rounded border p-3 text-[11px] leading-relaxed ${
          isCritical
            ? "border-red-600/60 bg-red-950/30 text-red-200"
            : isVerify
            ? "border-amber-600/60 bg-amber-950/30 text-amber-200"
            : "border-emerald-600/60 bg-emerald-950/20 text-emerald-200"
        }`}
      >
        <div className="font-bold uppercase tracking-wider text-[9px] mb-1 text-slate-400">
          SOC OPERATOR ADVISORY:
        </div>
        <p className="font-sans text-xs">
          {recommendation || "Awaiting audio stream evaluation to generate actionable guidance."}
        </p>
      </div>

      {/* Ordered Protocol Steps */}
      <div className="space-y-1.5">
        <span className="text-[9px] uppercase tracking-wider text-slate-500 font-bold block">
          MANDATORY INCIDENT PROTOCOL:
        </span>
        <div className="space-y-1">
          {actionSteps.map((step, idx) => (
            <div
              key={step.label}
              className="flex items-start gap-2 rounded border border-cyber-border bg-black/40 p-2 text-[10px]"
            >
              <span
                className={`flex h-4 w-4 flex-shrink-0 items-center justify-center rounded text-[9px] font-black ${
                  isCritical
                    ? "bg-red-600 text-white"
                    : isVerify
                    ? "bg-amber-600 text-white"
                    : "bg-emerald-600 text-white"
                }`}
              >
                {idx + 1}
              </span>
              <div>
                <div className="font-bold text-white uppercase">{step.label}</div>
                <div className="text-slate-400 font-sans text-[11px] mt-0.5">{step.detail}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Active Evidence List */}
      <div className="border-t border-cyber-border pt-2.5 space-y-1.5">
        <span className="text-[9px] uppercase tracking-wider text-slate-500 font-bold block">
          ACTIVATED FORENSIC CUES:
        </span>
        {reasons.length > 0 ? (
          <ul className="space-y-1 text-[11px]">
            {reasons.map((r, i) => (
              <li key={i} className="flex items-start gap-1.5 text-slate-300">
                <span
                  className={`mt-1.5 h-1.5 w-1.5 rounded-full flex-shrink-0 ${
                    isCritical ? "bg-red-400" : isVerify ? "bg-amber-400" : "bg-emerald-400"
                  }`}
                />
                <span>{r}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[10px] text-slate-600 italic">No elevated threat signals flagged.</p>
        )}
      </div>
    </div>
  );
}

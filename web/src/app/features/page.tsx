import Link from "next/link";
import { siteContent } from "@/content/content";

export default function FeaturesPage() {
  const { features, scenarios } = siteContent;

  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-12 space-y-16">
      {/* Header */}
      <div className="max-w-3xl space-y-3">
        <span className="inline-flex items-center gap-1.5 rounded border border-cyber-border bg-cyber-surface px-3 py-1 font-mono text-xs text-cyber-blue-light">
          OPERATIONAL CAPABILITIES SPECIFICATION
        </span>
        <h1 className="text-3xl sm:text-5xl font-black text-white font-sans uppercase tracking-tight">
          Adversarial Threat Surface Defeat
        </h1>
        <p className="text-xs sm:text-sm text-slate-300 font-sans leading-relaxed">
          Technical specifications for real-time speech biometrics, self-supervised acoustic anomaly detection, speaker embedding verification, and deterministic policy enforcement.
        </p>
      </div>

      {/* Feature Deep Dive Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 font-mono">
        {features.map((item) => (
          <div
            key={item.id}
            id={item.id}
            className="rounded-xl border border-cyber-border bg-cyber-chassis p-6 space-y-4 transition-all hover:border-cyber-border-highlight corner-bracket"
          >
            <div className="flex items-center justify-between text-xs">
              <span className="rounded border border-cyber-border bg-black/40 px-2.5 py-1 text-[10px] font-bold uppercase text-cyber-blue-light">
                {item.tag}
              </span>
              <span className="rounded bg-cyber-surface px-2 py-0.5 font-bold text-emerald-400 border border-cyber-border">
                {item.metric}
              </span>
            </div>

            <h2 className="text-lg font-bold text-white font-sans tracking-tight">{item.title}</h2>

            <p className="text-xs text-slate-300 leading-relaxed font-sans">{item.description}</p>

            <div className="rounded border border-cyber-border bg-black/50 p-3 text-[11px] text-slate-400 space-y-1">
              <div className="text-[9px] uppercase tracking-wider text-slate-500 font-bold">
                ENGINE IMPLEMENTATION PROVENANCE
              </div>
              <p className="text-emerald-400 font-semibold">
                &gt; Verified in `server/src/antai/orchestration/nodes.py` (Zero-Trust Gating)
              </p>
            </div>
          </div>
        ))}
      </div>

      {/* Scenario Threshold Matrix */}
      <div className="rounded-xl border border-cyber-border bg-cyber-surface p-6 sm:p-8 space-y-6">
        <div className="space-y-1">
          <span className="text-xs font-mono font-bold uppercase tracking-widest text-cyber-blue-light">
            ADAPTIVE SECURITY POLICY MATRIX
          </span>
          <h2 className="text-xl sm:text-2xl font-bold text-white font-sans uppercase">
            Contextual Threat Thresholds
          </h2>
          <p className="text-xs text-slate-400 font-sans">
            Threshold cutoffs fetched from <code className="font-mono text-cyber-blue-light bg-black px-1.5 py-0.5 rounded border border-cyber-border">GET /api/scenarios</code>:
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse font-mono text-xs">
            <thead>
              <tr className="border-b border-cyber-border text-slate-500 uppercase text-[10px]">
                <th className="py-2.5 px-3">Scenario Profile</th>
                <th className="py-2.5 px-3">Protected Context</th>
                <th className="py-2.5 px-3 text-center">Verify Boundary</th>
                <th className="py-2.5 px-3 text-center">Critical Freeze</th>
                <th className="py-2.5 px-3 text-right">Policy Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-cyber-border">
              {Object.entries(scenarios).map(([key, sc]) => (
                <tr key={key} className="hover:bg-cyber-surface-elevated/40 transition-colors">
                  <td className="py-3 px-3 font-bold text-white flex items-center gap-2">
                    <span className="h-1.5 w-1.5 rounded-full bg-cyber-blue" />
                    {sc.label}
                  </td>
                  <td className="py-3 px-3 text-slate-300 font-sans">{sc.description}</td>
                  <td className="py-3 px-3 text-center">
                    <span className="rounded border border-amber-500/40 bg-amber-500/10 text-amber-300 px-2 py-0.5 font-bold">
                      &ge; {sc.verify_at}
                    </span>
                  </td>
                  <td className="py-3 px-3 text-center">
                    <span className="rounded border border-red-500/40 bg-red-500/10 text-red-300 px-2 py-0.5 font-bold">
                      &ge; {sc.critical_at}
                    </span>
                  </td>
                  <td className="py-3 px-3 text-right text-slate-300">
                    {sc.critical_at <= 70 ? "IMMEDIATE MEMORY HOLD" : "ESCALATE TO CONTACT"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Zero-Knowledge Ephemeral Media Retention */}
      <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-6 sm:p-8 space-y-4 font-mono">
        <div className="text-xs text-cyber-blue-light font-bold uppercase tracking-wider">
          PRIVACY &amp; CRYPTOGRAPHIC HYGIENE POSTURE
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs font-sans text-slate-300">
          <div className="rounded border border-cyber-border bg-black/40 p-4 space-y-1.5">
            <div className="font-bold text-white font-mono uppercase text-[11px]">1. Zero Media Persistence</div>
            <p className="text-slate-400 text-xs leading-relaxed">
              No raw audio is written to disk. Frames are processed strictly in RAM circular buffers and purged immediately on stream completion.
            </p>
          </div>
          <div className="rounded border border-cyber-border bg-black/40 p-4 space-y-1.5">
            <div className="font-bold text-white font-mono uppercase text-[11px]">2. Salt-Hashed Identifiers</div>
            <p className="text-slate-400 text-xs leading-relaxed">
              Phone numbers and participant profiles are cryptographically salted with SHA-256 before collective reputation lookups.
            </p>
          </div>
          <div className="rounded border border-cyber-border bg-black/40 p-4 space-y-1.5">
            <div className="font-bold text-white font-mono uppercase text-[11px]">3. Air-Gapped Fallback</div>
            <p className="text-slate-400 text-xs leading-relaxed">
              When operating in on-device mode, all model inference executes locally with zero external API calls or outbound telemetry.
            </p>
          </div>
        </div>
      </div>

      <div className="text-center pt-2">
        <Link
          href="/dashboard-live"
          className="inline-flex items-center gap-2 rounded border border-cyber-blue bg-cyber-blue/20 hover:bg-cyber-blue hover:text-white px-6 py-2.5 font-mono text-xs font-bold text-cyber-blue-light transition-all shadow"
        >
          [ ACCESS SOC LIVE CONSOLE &rarr; ]
        </Link>
      </div>
    </div>
  );
}

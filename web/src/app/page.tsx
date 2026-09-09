import Link from "next/link";
import { siteContent } from "@/content/content";
import { LiveRiskPulse } from "@/components/hero/LiveRiskPulse";

export default function HomePage() {
  const { hero, features, howItWorks } = siteContent;

  return (
    <div className="space-y-20 pb-20">
      {/* ── C2 Operations Command Hero ── */}
      <section className="relative pt-10 md:pt-16 border-b border-cyber-border pb-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          {/* Top Operational Status Bar */}
          <div className="mb-8 flex flex-wrap items-center justify-between gap-3 border border-cyber-border bg-cyber-chassis/80 px-4 py-2 rounded font-mono text-xs text-slate-400">
            <div className="flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-white font-bold tracking-wider uppercase">DEFENSE RUNTIME ACTIVE</span>
              <span className="text-slate-600">|</span>
              <span className="text-slate-400">POLICY: DETERMINISTIC ZERO-TRUST</span>
            </div>
            <div className="flex items-center gap-4 text-[11px] text-slate-500">
              <span>LATENCY SLA: &lt; 1800ms</span>
              <span className="text-cyber-blue-light">{siteContent.meta.nodeId}</span>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
            {/* Left Operational Copy */}
            <div className="lg:col-span-7 space-y-6 text-left">
              <div className="inline-flex items-center gap-2 rounded border border-cyber-border bg-cyber-surface px-3 py-1 font-mono text-[11px] text-cyber-blue-light">
                <span className="h-1.5 w-1.5 rounded-full bg-cyber-blue animate-ping" />
                {siteContent.meta.badge}
              </div>

              <h1 className="text-3xl sm:text-5xl lg:text-6xl font-black tracking-tight text-white font-sans uppercase leading-[1.1]">
                Real-time voice-clone{" "}
                <span className="text-cyber-blue-light underline decoration-cyber-blue/50 underline-offset-8">
                  interception
                </span>{" "}
                and transaction freeze.
              </h1>

              <p className="text-sm sm:text-base text-slate-300 max-w-2xl leading-relaxed font-sans">
                {hero.subhead}
              </p>

              {/* Action Directives */}
              <div className="flex flex-wrap items-center gap-3 pt-2 font-mono text-xs">
                <Link
                  href="/dashboard-live"
                  className="rounded border border-cyber-blue bg-cyber-blue hover:bg-cyber-blue-light hover:text-black px-6 py-3 font-bold text-white shadow-lg glow-blue transition-all"
                >
                  [ {hero.ctaPrimary.toUpperCase()} ]
                </Link>
                <Link
                  href="/dataflow"
                  className="rounded border border-cyber-border bg-cyber-surface hover:bg-cyber-surface-elevated px-6 py-3 font-semibold text-slate-200 transition-all"
                >
                  {hero.ctaSecondary}
                </Link>
              </div>

              {/* High-Assurance Telemetry Strip */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-6 border-t border-cyber-border font-mono">
                {hero.telemetry.map((item) => (
                  <div key={item.label} className="rounded border border-cyber-border bg-black/40 p-3 space-y-1">
                    <div className="text-[10px] text-slate-500 uppercase tracking-wider">{item.label}</div>
                    <div className="text-lg sm:text-xl font-black text-white">{item.value}</div>
                    <div className="text-[9px] text-slate-400">{item.sub}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Right Tactical Radial Threat Radar */}
            <div className="lg:col-span-5 w-full flex justify-center">
              <LiveRiskPulse />
            </div>
          </div>
        </div>
      </section>

      {/* ── Threat Capabilities / Defense Modules Grid ── */}
      <section className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="max-w-3xl space-y-2 mb-10">
          <span className="text-xs font-mono font-bold uppercase tracking-widest text-cyber-blue-light">
            DEFENSIVE ARCHITECTURE MODULES
          </span>
          <h2 className="text-2xl sm:text-3xl font-black text-white font-sans uppercase tracking-tight">
            Hardened voice biometrics and fraud mitigation
          </h2>
          <p className="text-xs sm:text-sm text-slate-400">
            Multi-spectral acoustic verification combined with deterministic policy decision logic.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 font-mono">
          {features.map((feat) => (
            <div
              key={feat.id}
              className="rounded-xl border border-cyber-border bg-cyber-chassis p-5 space-y-3 transition-all hover:border-cyber-border-highlight hover:bg-cyber-surface/60 corner-bracket"
            >
              <div className="flex items-center justify-between text-[10px]">
                <span className="rounded border border-cyber-border bg-black/40 px-2 py-0.5 text-cyber-blue-light font-bold">
                  {feat.tag}
                </span>
                <span className="text-slate-400 font-bold">{feat.metric}</span>
              </div>
              <h3 className="text-sm font-bold text-white font-sans">
                {feat.title}
              </h3>
              <p className="text-xs text-slate-400 leading-relaxed font-sans">
                {feat.description}
              </p>
              <div className="pt-2 border-t border-cyber-border text-[10px] text-slate-500">
                {"//"} {feat.metricLabel}
              </div>
            </div>
          ))}
        </div>

        <div className="mt-8 text-center">
          <Link
            href="/features"
            className="inline-flex items-center gap-2 text-xs font-mono font-bold text-cyber-blue-light hover:underline uppercase tracking-wider"
          >
            Inspect full benchmark specifications &amp; threat matrix &rarr;
          </Link>
        </div>
      </section>

      {/* ── 5-Stage Ingestion Pipeline ── */}
      <section className="border-y border-cyber-border bg-cyber-chassis/60 py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="max-w-3xl space-y-2 mb-10">
            <span className="text-xs font-mono font-bold uppercase tracking-widest text-emerald-400">
              FORENSIC TELEMETRY CHAIN
            </span>
            <h2 className="text-2xl sm:text-3xl font-black text-white font-sans uppercase tracking-tight">
              {howItWorks.title}
            </h2>
            <p className="text-xs sm:text-sm text-slate-400 font-sans">{howItWorks.subhead}</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-5 gap-3 font-mono">
            {howItWorks.steps.map((s, idx) => (
              <div
                key={s.step}
                className="rounded-lg border border-cyber-border bg-cyber-surface p-4 space-y-2 flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-black text-cyber-blue-light text-sm">STAGE {s.step}</span>
                    <span className="rounded border border-cyber-border bg-black/40 px-1.5 py-0.5 text-[10px] text-slate-400">
                      {s.time}
                    </span>
                  </div>
                  <h3 className="mt-2 font-bold text-xs text-white font-sans">{s.name}</h3>
                  <p className="mt-1 text-[11px] text-slate-400 leading-relaxed font-sans">{s.summary}</p>
                </div>
                <div className="text-[9px] text-slate-500 border-t border-cyber-border pt-2">
                  PIPELINE ORDER #{idx + 1}
                </div>
              </div>
            ))}
          </div>

          <div className="mt-10 flex flex-wrap gap-4 font-mono text-xs">
            <Link
              href="/how-it-works"
              className="rounded border border-cyber-border bg-cyber-surface-elevated hover:bg-slate-800 px-4 py-2 font-semibold text-white transition-colors"
            >
              [ Technical Ingestion Specification ]
            </Link>
            <Link
              href="/dataflow"
              className="rounded border border-cyber-blue bg-cyber-blue/20 hover:bg-cyber-blue hover:text-white px-4 py-2 font-bold text-cyber-blue-light transition-colors"
            >
              [ Interactive Architecture DAG ]
            </Link>
          </div>
        </div>
      </section>

      {/* ── Direct Deployment Notice ── */}
      <section className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8">
        <div className="rounded-xl border border-cyber-border bg-cyber-surface p-8 sm:p-10 space-y-5 corner-bracket">
          <div className="flex items-center gap-2 font-mono text-xs text-cyber-blue-light font-bold uppercase tracking-wider">
            <span className="h-2 w-2 rounded-full bg-cyber-blue animate-pulse" />
            OPERATIONAL DEPLOYMENT // ZERO EXTERNAL TELEMETRY
          </div>
          <h2 className="text-2xl sm:text-3xl font-black text-white font-sans uppercase tracking-tight">
            Ready to monitor live voice streams and intercept fraud?
          </h2>
          <p className="text-xs sm:text-sm text-slate-300 max-w-2xl leading-relaxed font-sans">
            Connect directly to the local FastAPI stream at <code className="font-mono text-cyber-blue-light bg-black px-2 py-0.5 rounded border border-cyber-border">/api/stream/ws</code> or execute automated attack injection in our forensic lab.
          </p>
          <div className="flex flex-wrap gap-3 pt-2 font-mono text-xs">
            <Link
              href="/dashboard-live"
              className="rounded border border-cyber-blue bg-cyber-blue hover:bg-cyber-blue-light hover:text-black px-5 py-2.5 font-bold text-white shadow glow-blue transition-all"
            >
              [ LAUNCH SOC CONSOLE ]
            </Link>
            <Link
              href="/demo"
              className="rounded border border-cyber-border bg-black hover:bg-cyber-surface px-5 py-2.5 font-semibold text-white transition-all"
            >
              [ ACCESS FORENSIC LAB ]
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}

import Link from "next/link";
import { siteContent } from "@/content/content";

export default function HowItWorksPage() {
  const { howItWorks } = siteContent;

  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-12 space-y-12">
      {/* Header */}
      <div className="max-w-3xl space-y-3">
        <span className="inline-flex items-center gap-1.5 rounded border border-cyber-border bg-cyber-surface px-3 py-1 font-mono text-xs text-cyber-blue-light">
          SYSTEM INGESTION ARCHITECTURE
        </span>
        <h1 className="text-3xl sm:text-5xl font-black text-white font-sans uppercase tracking-tight">
          How antAI Intercepts Audio Fraud
        </h1>
        <p className="text-xs sm:text-sm text-slate-300 font-sans leading-relaxed">
          The step-by-step telemetry loop: from the initial WebRTC read-only stream tap to parallelized GPU inference, LangGraph dead-zone fusion, and in-flight action hold enforcement.
        </p>
      </div>

      {/* 5-Step Detailed Vertical Timeline */}
      <div className="max-w-4xl space-y-6 font-mono">
        {howItWorks.steps.map((item, index) => (
          <div
            key={item.step}
            className="rounded-xl border border-cyber-border bg-cyber-chassis p-6 space-y-3 transition-all hover:border-cyber-border-highlight corner-bracket"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="flex h-9 w-9 items-center justify-center rounded border border-cyber-blue/60 bg-cyber-surface text-cyber-blue-light font-black text-sm">
                  {item.step}
                </span>
                <div>
                  <h2 className="text-base font-bold text-white font-sans uppercase tracking-tight">
                    {item.name}
                  </h2>
                  <span className="text-[10px] text-slate-500 font-mono uppercase">
                    STAGE #{index + 1} OF 5
                  </span>
                </div>
              </div>
              <span className="rounded border border-cyber-border bg-black/40 px-2.5 py-1 text-xs font-semibold text-emerald-400">
                LATENCY SLA: {item.time}
              </span>
            </div>

            <p className="text-xs font-semibold text-slate-200 font-sans">{item.summary}</p>
            <p className="text-xs text-slate-400 leading-relaxed font-sans">{item.detail}</p>

            {/* Architecture code snippet per stage */}
            <div className="rounded border border-cyber-border bg-black/60 p-3 text-[10px] text-slate-300 overflow-x-auto">
              {index === 0 && (
                <code>
                  [INGRESS] Android WebRTC PeerConnection &rarr; SFU Media Relay &rarr; Read-only Audio Ingestor Tap [16kHz mono float32]
                </code>
              )}
              {index === 1 && (
                <code>
                  [BUFFER] AudioIngestor &rarr; Silero VAD (0.4–2.0s speech slices) &rarr; Streaming ASR Worker (Deepgram / Whisper)
                </code>
              )}
              {index === 2 && (
                <code>
                  [FAN-OUT] Parallel Inference: [AST-ASVspoof5 (95M) | wav2vec2-large | ECAPA-TDNN 192-dim | MiniLM NLP | SyncNet]
                </code>
              )}
              {index === 3 && (
                <code>
                  [FUSION] LangGraph DAG: Hard (+60 max) + Soft (+22) dead-zone ramps &rarr; Corroboration Guard (Cap @ 39 if uncorroborated)
                </code>
              )}
              {index === 4 && (
                <code>
                  [MITIGATION] Decision Node &rarr; (Risk &ge; 70 + Money Ask) &rarr; HOLD DIRECTIVE DISPATCH &rarr; LLM Explainer
                </code>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Direct Link to Interactive DAG */}
      <div className="max-w-4xl rounded-xl border border-cyber-border bg-cyber-surface p-6 flex flex-col sm:flex-row items-center justify-between gap-4 font-mono">
        <div>
          <h3 className="text-sm font-bold text-white uppercase font-sans">
            Need to inspect the full 10-node execution graph?
          </h3>
          <p className="text-xs text-slate-400 font-sans mt-0.5">
            Open the interactive schematic showing individual failure modes, model weights, and live packet injection.
          </p>
        </div>
        <Link
          href="/dataflow"
          className="rounded border border-cyber-blue bg-cyber-blue hover:bg-cyber-blue-light hover:text-black px-5 py-2 text-xs font-bold text-white transition-all shadow flex-shrink-0"
        >
          [ INSPECT ARCHITECTURE DAG &rarr; ]
        </Link>
      </div>
    </div>
  );
}

"use client";

import { ModelDebugResponse } from "@/lib/types";

interface ModelStatusStripProps {
  debugData?: ModelDebugResponse | null;
  isLive?: boolean;
}

export function ModelStatusStrip({ debugData, isLive = true }: ModelStatusStripProps) {
  const models = debugData?.models || {};
  const providers = debugData?.providers;
  const llm = providers?.llm;

  const fallbackChain = llm?.fallback_chain || ["groq", "gemini", "openrouter", "local_qwen"];
  const activeBackend = llm?.last_backend_used || llm?.configured || "groq";

  return (
    <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-3.5 space-y-2.5 font-mono text-xs">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-cyber-border pb-2">
        <div className="flex items-center gap-2">
          <span className="font-bold uppercase tracking-wider text-slate-300 text-[11px]">
            ENGINE CLUSTER TELEMETRY (GET /api/debug/models)
          </span>
          <span
            className={`rounded border px-1.5 py-0.2 text-[9px] font-bold uppercase ${
              isLive
                ? "border-emerald-700/60 bg-emerald-950/40 text-emerald-300"
                : "border-amber-700/60 bg-amber-950/40 text-amber-300"
            }`}
          >
            {isLive ? "LIVE CLUSTER" : "SIMULATED ENGINES"}
          </span>
        </div>

        {/* LLM Fallback Chain */}
        <div className="flex items-center gap-1.5 text-[10px] text-slate-400">
          <span className="text-slate-500">LLM FALLBACK CHAIN:</span>
          {fallbackChain.map((backend, idx) => {
            const isCurrent = backend === activeBackend;
            return (
              <span key={backend} className="flex items-center gap-1">
                <span
                  className={`px-1.5 py-0.2 rounded border text-[9px] font-bold uppercase ${
                    isCurrent
                      ? "border-cyber-blue bg-cyber-blue text-black shadow-sm"
                      : "border-cyber-border bg-black/40 text-slate-500"
                  }`}
                  title={isCurrent ? "Active LLM backend serving verdicts" : "Fallback backend in reserve"}
                >
                  {backend}
                </span>
                {idx < fallbackChain.length - 1 && <span className="text-slate-600">&rarr;</span>}
              </span>
            );
          })}
        </div>
      </div>

      {/* Model Engine Status Grid */}
      <div className="flex flex-wrap gap-1.5">
        {Object.entries(models).map(([name, info]) => {
          const isReady = info.ready;
          return (
            <div
              key={name}
              className={`flex items-center gap-1.5 rounded border px-2 py-0.5 text-[10px] transition-colors ${
                isReady
                  ? "border-emerald-800/60 bg-emerald-950/20 text-emerald-300"
                  : "border-red-800/60 bg-red-950/20 text-red-300"
              }`}
              title={isReady ? `${name}: Ready (${info.device || "cpu"})` : `${name}: Load failed (${info.reason || "missing weights"})`}
            >
              <span
                className={`h-1.5 w-1.5 rounded-full ${
                  isReady ? "bg-emerald-400" : "bg-red-400"
                }`}
              />
              <span className="font-semibold uppercase">{name.replace(/_/g, " ")}</span>
              {info.device && (
                <span className="text-[8px] text-slate-500 uppercase font-mono">[{info.device}]</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

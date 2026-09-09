import Link from "next/link";
import { siteContent } from "@/content/content";

export function Footer() {
  return (
    <footer className="border-t border-cyber-border bg-cyber-chassis text-slate-400 font-mono text-xs">
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Col 1: System Identification */}
          <div className="md:col-span-2 space-y-3">
            <div className="flex items-center gap-2">
              <div className="flex h-6 w-6 items-center justify-center rounded border border-cyber-blue/60 bg-cyber-surface text-cyber-blue-light font-black text-xs">
                A
              </div>
              <span className="font-bold text-white text-sm tracking-tight font-sans">
                antAI Guardian Defense Runtime
              </span>
              <span className="rounded bg-cyber-surface px-1.5 py-0.5 text-[9px] text-cyber-blue-light border border-cyber-border">
                {siteContent.meta.nodeId}
              </span>
            </div>
            <p className="text-slate-400 text-xs max-w-lg leading-relaxed font-sans">
              High-assurance defensive runtime for enterprise voice and high-stakes banking. Evaluates 14 acoustic, biometric, and linguistic models in sub-2s latency, enforces in-flight transaction holds, and defeats adversarial social engineering.
            </p>
            <div className="text-[10px] text-slate-500 space-y-0.5">
              <div>PROJECT: Smart India Hackathon 2026 // Problem Statement PS26104</div>
              <div>COMPLIANCE: Ephemeral In-Memory Only // Salted SHA-256 Identifiers</div>
            </div>
          </div>

          {/* Col 2: Operational Modules */}
          <div>
            <h4 className="font-bold text-slate-200 uppercase text-[11px] tracking-wider mb-3">
              Operational Modules
            </h4>
            <ul className="space-y-1.5 text-[11px]">
              {siteContent.navigation.links.map((link) => (
                <li key={link.href}>
                  <Link href={link.href} className="hover:text-cyber-blue-light transition-colors">
                    &gt; {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Col 3: Verified Inference Engines */}
          <div>
            <h4 className="font-bold text-slate-200 uppercase text-[11px] tracking-wider mb-3">
              Engine Roster (14 Nodes)
            </h4>
            <ul className="space-y-1 text-[10px] text-slate-400">
              <li>[OK] AST-ASVspoof5 (95M Transformer)</li>
              <li>[OK] wav2vec2-large-xlsr (90M)</li>
              <li>[OK] ECAPA-TDNN (192-dim Speaker)</li>
              <li>[OK] Silero VAD (0.4–2.0s Segments)</li>
              <li>[OK] LangGraph Deterministic DAG</li>
              <li>[OK] LLM Reasoner (Groq/Gemini/Qwen)</li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar with Verification Tags */}
        <div className="pt-4 border-t border-cyber-border/70 flex flex-col sm:flex-row items-center justify-between gap-3 text-[10px] text-slate-500">
          <div>
            SECURITY PROTOCOL: ZERO PERSISTENCE // ALL MEDIA TRANSIENT (TTL 0s)
          </div>
          <div className="flex items-center gap-4">
            <span className="text-emerald-400">● 14/14 ENGINES VERIFIED</span>
            <span>REST: :8765</span>
            <span>WS: /api/stream/ws</span>
          </div>
        </div>
      </div>
    </footer>
  );
}

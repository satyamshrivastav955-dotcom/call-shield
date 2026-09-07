"use client";

import { siteContent } from "@/content/content";

interface NodeDetailsPanelProps {
  nodeId: string;
}

export function NodeDetailsPanel({ nodeId }: NodeDetailsPanelProps) {
  const node =
    siteContent.pipelineNodes.find((n) => n.id === nodeId) ||
    siteContent.pipelineNodes[0];

  return (
    <div className="corner-bracket border border-cyber-border bg-cyber-chassis/95 p-6 space-y-5 shadow-2xl backdrop-blur-md">
      <div className="flex items-center justify-between border-b border-cyber-border pb-3 font-mono">
        <span className="border border-cyber-border bg-cyber-surface px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-cyber-blue">
          SYS_NODE {"//"} {node.category}
        </span>
        <span className="text-xs font-bold text-cyber-green flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-cyber-green animate-pulse" />
          BUDGET: {node.latencyBudget}
        </span>
      </div>

      <div>
        <h3 className="text-xl font-bold text-white tracking-tight">{node.label}</h3>
        <p className="mt-2 text-xs text-slate-300 leading-relaxed font-mono">{node.description}</p>
      </div>

      {/* Latency Budget Box */}
      <div className="border border-cyber-border bg-cyber-surface/60 p-3.5 space-y-1 font-mono">
        <span className="text-[10px] uppercase tracking-wider text-slate-400 font-bold block">
          Latency Budget Target
        </span>
        <div className="flex items-baseline gap-2">
          <span className="text-lg font-black text-white">{node.latencyBudget}</span>
          <span className="text-xs text-slate-400">enforced via asyncio timeouts</span>
        </div>
      </div>

      {/* Active Model / Architecture Box */}
      <div className="border border-cyber-border bg-cyber-surface/60 p-3.5 space-y-1 font-mono">
        <span className="text-[10px] uppercase tracking-wider text-slate-400 font-bold block">
          Model Engine Architecture & Weights
        </span>
        <div className="text-xs text-cyber-blue font-semibold">
          {node.modelInfo}
        </div>
      </div>

      {/* Failure Behavior Box */}
      <div className="border border-cyber-red/30 bg-cyber-red/5 p-3.5 space-y-1 font-mono">
        <span className="text-[10px] uppercase tracking-wider text-cyber-red font-bold flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-cyber-red" />
          Failure & Degradation Protocol
        </span>
        <p className="text-xs text-slate-300 leading-relaxed font-sans">
          {node.failureBehavior}
        </p>
      </div>
    </div>
  );
}

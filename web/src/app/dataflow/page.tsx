"use client";

import { useState } from "react";
import { PipelineGraphSvg } from "@/components/dataflow/PipelineGraphSvg";
import { NodeDetailsPanel } from "@/components/dataflow/NodeDetailsPanel";
import { TraceScamAnimation } from "@/components/dataflow/TraceScamAnimation";
import { siteContent } from "@/content/content";

export default function DataflowPage() {
  const [selectedNodeId, setSelectedNodeId] = useState<string>("voice_deepfake_ensemble");
  const [activePacketNodeId, setActivePacketNodeId] = useState<string | null>(null);

  const handleActiveNodeChange = (nodeId: string | null) => {
    setActivePacketNodeId(nodeId);
    if (nodeId) {
      setSelectedNodeId(nodeId);
    }
  };

  return (
    <div className="mx-auto max-w-[1520px] px-4 sm:px-6 lg:px-8 py-10 space-y-10">
      {/* Page Header */}
      <div className="text-center max-w-3xl mx-auto space-y-3 font-mono">
        <span className="inline-flex items-center gap-1.5 border border-cyber-border bg-cyber-surface px-3 py-1 text-xs text-cyber-blue uppercase tracking-wider">
          <span className="h-1.5 w-1.5 rounded-full bg-cyber-blue animate-pulse" />
          TOPOLOGY SCHEMATIC // ZERO-TRUST PIPELINE
        </span>
        <h1 className="text-3xl sm:text-5xl font-black text-white tracking-tight font-sans uppercase">
          LangGraph Multi-Engine Pipeline Architecture
        </h1>
        <p className="text-xs sm:text-sm text-slate-400 font-mono leading-relaxed">
          Trace how an inbound WebRTC call stream flows through Silero VAD segmentation, 14 parallel inference engines, LangGraph dead-zone fusion, and deterministic action intercept.
        </p>
      </div>

      {/* Interactive Packet Tracer Bar */}
      <TraceScamAnimation onActiveNodeChange={handleActiveNodeChange} />

      {/* Main Interactive Diagram & Inspector Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* SVG Flowchart (8 cols) */}
        <div className="lg:col-span-8">
          <PipelineGraphSvg
            selectedNodeId={selectedNodeId}
            onSelectNode={setSelectedNodeId}
            activePacketNodeId={activePacketNodeId}
          />
        </div>

        {/* Node Inspector Side Panel (4 cols) */}
        <div className="lg:col-span-4">
          <NodeDetailsPanel nodeId={selectedNodeId} />
        </div>
      </div>

      {/* Complete Node Roster Table */}
      <div className="corner-bracket border border-cyber-border bg-cyber-chassis/95 p-6 sm:p-8 space-y-6 shadow-2xl backdrop-blur-md">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-cyber-border pb-4 font-mono">
          <div>
            <h2 className="text-lg font-bold text-white tracking-tight uppercase flex items-center gap-2">
              <span className="h-2 w-2 bg-cyber-blue" />
              Full Pipeline Node Reference Matrix
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Complete latency budgets, model weights, and resilience characteristics from <code className="text-cyber-blue">server/src/antai/orchestration/</code>.
            </p>
          </div>
          <span className="text-xs text-slate-400 border border-cyber-border bg-cyber-surface px-2.5 py-1">
            10 PRIMARY NODES // 14 ENGINES
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="border-b border-cyber-border text-slate-400 font-mono uppercase text-[10px]">
                <th className="py-3 px-3">Node Identifier</th>
                <th className="py-3 px-3">Subsystem Class</th>
                <th className="py-3 px-3 text-center">Budget SLA</th>
                <th className="py-3 px-3">Engine Topology / Weights</th>
                <th className="py-3 px-3">Fault Tolerant Degradation</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-cyber-border/60 font-mono">
              {siteContent.pipelineNodes.map((node) => {
                const isSelected = selectedNodeId === node.id;
                return (
                  <tr
                    key={node.id}
                    onClick={() => setSelectedNodeId(node.id)}
                    className={`cursor-pointer transition-colors ${
                      isSelected
                        ? "bg-cyber-surface text-white border-l-2 border-cyber-blue font-bold"
                        : "hover:bg-cyber-surface/40 text-slate-300"
                    }`}
                  >
                    <td className="py-3 px-3 flex items-center gap-2">
                      <span
                        className={`h-2 w-2 ${
                          isSelected ? "bg-cyber-blue" : "bg-slate-600"
                        }`}
                      />
                      {node.label}
                    </td>
                    <td className="py-3 px-3 text-slate-400 uppercase text-[11px]">{node.category}</td>
                    <td className="py-3 px-3 text-center text-cyber-green font-semibold">{node.latencyBudget}</td>
                    <td className="py-3 px-3 text-[11px] text-slate-300 font-sans">{node.modelInfo}</td>
                    <td className="py-3 px-3 text-[11px] text-slate-400 font-sans">{node.failureBehavior}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

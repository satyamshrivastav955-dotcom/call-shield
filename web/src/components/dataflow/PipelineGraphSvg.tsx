"use client";

import { siteContent } from "@/content/content";

interface PipelineGraphSvgProps {
  selectedNodeId: string;
  onSelectNode: (nodeId: string) => void;
  activePacketNodeId?: string | null;
}

export function PipelineGraphSvg({
  selectedNodeId,
  onSelectNode,
  activePacketNodeId,
}: PipelineGraphSvgProps) {
  const nodes = siteContent.pipelineNodes;

  // Node position map in SVG coordinates (width=960, height=540)
  const layoutMap: Record<string, { x: number; y: number; w: number; h: number }> = {
    phone_webrtc: { x: 30, y: 220, w: 140, h: 65 },
    sfu_relay: { x: 195, y: 220, w: 120, h: 65 },
    vad_segmentation: { x: 340, y: 220, w: 135, h: 65 },
    // Parallel detectors
    asr_engine: { x: 505, y: 40, w: 145, h: 60 },
    voice_deepfake_ensemble: { x: 505, y: 120, w: 145, h: 60 },
    speaker_verify: { x: 505, y: 200, w: 145, h: 60 },
    video_detectors: { x: 505, y: 280, w: 145, h: 60 },
    text_classifiers: { x: 505, y: 360, w: 145, h: 60 },
    // Post-detection orchestration
    langgraph_fusion: { x: 685, y: 200, w: 135, h: 65 },
    decision_node: { x: 840, y: 200, w: 120, h: 65 },
    llm_reasoner: { x: 840, y: 310, w: 120, h: 65 },
    freeze_controller: { x: 685, y: 310, w: 135, h: 65 },
    storage_reporting: { x: 505, y: 450, w: 145, h: 55 },
  };

  return (
    <div className="relative w-full overflow-x-auto corner-bracket border border-cyber-border bg-cyber-chassis/95 p-5 shadow-2xl backdrop-blur-md">
      <div className="min-w-[960px]">
        <svg
          viewBox="0 0 980 520"
          className="w-full h-auto select-none font-mono"
          aria-label="Tactical Pipeline DAG Graph"
        >
          <defs>
            {/* Arrow Marker */}
            <marker
              id="arrow"
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#2a3854" />
            </marker>
            <marker
              id="arrow-active"
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#38bdf8" />
            </marker>
            <marker
              id="arrow-freeze"
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 1.5 L 8 5 L 0 8.5 z" fill="#dc2626" />
            </marker>
          </defs>

          {/* Connectors Lines */}
          <g stroke="#1c2638" strokeWidth="2" fill="none" markerEnd="url(#arrow)">
            {/* Phone -> SFU */}
            <path d="M 170 252 L 195 252" />
            {/* SFU -> VAD */}
            <path d="M 315 252 L 340 252" />
            {/* VAD -> Parallel Detectors */}
            <path d="M 475 252 C 490 252, 490 70, 505 70" />
            <path d="M 475 252 C 490 252, 490 150, 505 150" />
            <path d="M 475 252 L 505 230" />
            <path d="M 475 252 C 490 252, 490 310, 505 310" />
            <path d="M 475 252 C 490 252, 490 390, 505 390" />

            {/* ASR -> Text Classifiers */}
            <path d="M 577 100 L 577 360" strokeDasharray="3 3" stroke="#223249" />

            {/* Parallel Detectors -> Fusion Node */}
            <path d="M 650 70 C 665 70, 665 232, 685 232" />
            <path d="M 650 150 C 665 150, 665 232, 685 232" />
            <path d="M 650 230 L 685 232" />
            <path d="M 650 310 C 665 310, 665 232, 685 232" />
            <path d="M 650 390 C 665 390, 665 232, 685 232" />

            {/* Fusion -> Decision Node */}
            <path d="M 820 232 L 840 232" />

            {/* Decision -> LLM Reasoner */}
            <path d="M 900 265 L 900 310" />

            {/* Decision -> Freeze Controller (direct intercept) */}
            <path
              d="M 840 252 C 800 252, 800 320, 820 342 L 685 342"
              strokeDasharray="4 4"
              stroke="#dc2626"
              markerEnd="url(#arrow-freeze)"
            />

            {/* LLM -> Freeze Controller */}
            <path d="M 840 342 L 820 342" />

            {/* Freeze Controller -> Storage & Reporting */}
            <path d="M 752 375 C 752 477, 660 477, 650 477" />
          </g>

          {/* Interactive Nodes */}
          {nodes.map((n) => {
            const pos = layoutMap[n.id];
            if (!pos) return null;

            const isSelected = selectedNodeId === n.id;
            const isPacketActive = activePacketNodeId === n.id;

            return (
              <g
                key={n.id}
                onClick={() => onSelectNode(n.id)}
                className="cursor-pointer transition-all duration-150"
                tabIndex={0}
                role="button"
                aria-label={`Node ${n.label}`}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") onSelectNode(n.id);
                }}
              >
                {/* Active Glowing Packet Halo */}
                {isPacketActive && (
                  <rect
                    x={pos.x - 4}
                    y={pos.y - 4}
                    width={pos.w + 8}
                    height={pos.h + 8}
                    rx="2"
                    fill="none"
                    stroke="#38bdf8"
                    strokeWidth="2"
                    className="animate-pulse"
                    style={{ filter: "drop-shadow(0 0 10px rgba(56, 189, 248, 0.6))" }}
                  />
                )}

                {/* Node Chassis Box */}
                <rect
                  x={pos.x}
                  y={pos.y}
                  width={pos.w}
                  height={pos.h}
                  rx="1"
                  fill={
                    isSelected
                      ? "#0e1a2f"
                      : isPacketActive
                      ? "#0a192c"
                      : "#080c16"
                  }
                  stroke={
                    isSelected
                      ? "#38bdf8"
                      : isPacketActive
                      ? "#10b981"
                      : "#1c2638"
                  }
                  strokeWidth={isSelected || isPacketActive ? "2" : "1"}
                  className="transition-colors hover:fill-cyber-surface"
                />

                {/* Node Status Indicator pip */}
                <circle
                  cx={pos.x + 10}
                  cy={pos.y + 14}
                  r="2.5"
                  fill={isSelected ? "#38bdf8" : isPacketActive ? "#10b981" : "#475569"}
                />

                {/* Category Pill Inside Node */}
                <text
                  x={pos.x + 18}
                  y={pos.y + 17}
                  fontSize="8.5"
                  fontWeight="bold"
                  fill={isSelected ? "#38bdf8" : "#64748b"}
                  textAnchor="start"
                  letterSpacing="0.8"
                >
                  {n.category.toUpperCase()}
                </text>

                {/* Node Title */}
                <text
                  x={pos.x + 10}
                  y={pos.y + 35}
                  fontSize="10.5"
                  fontWeight="bold"
                  fontFamily="sans-serif"
                  fill="#f8fafc"
                  textAnchor="start"
                >
                  {n.label.length > 18 ? `${n.label.substring(0, 17)}…` : n.label}
                </text>

                {/* Latency Budget Badge */}
                <text
                  x={pos.x + 10}
                  y={pos.y + 51}
                  fontSize="9"
                  fill="#0ea5e9"
                  textAnchor="start"
                  letterSpacing="0.5"
                >
                  ⏱ {n.latencyBudget}
                </text>
              </g>
            );
          })}
        </svg>
      </div>

      <div className="mt-4 flex flex-col sm:flex-row items-center justify-between gap-2 border-t border-cyber-border/80 pt-3 text-xs text-slate-400 font-mono">
        <div className="flex items-center gap-2">
          <span className="inline-block h-2 w-2 bg-cyber-blue" />
          <span>INSPECT NODE: Select any processing block to review weights, budget & fail-safe rules.</span>
        </div>
        <span className="flex items-center gap-2 text-cyber-blue font-semibold">
          <span className="h-1.5 w-1.5 rounded-full bg-cyber-blue animate-ping" />
          SYNCHRONOUS DAG ACTIVE
        </span>
      </div>
    </div>
  );
}

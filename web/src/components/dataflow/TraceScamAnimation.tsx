"use client";

import { useEffect, useState } from "react";

interface TraceScamAnimationProps {
  onActiveNodeChange: (nodeId: string | null) => void;
}

interface TraceStep {
  nodeId: string;
  label: string;
  risk: number;
  log: string;
  isFreeze?: boolean;
}

const STEPS: TraceStep[] = [
  {
    nodeId: "phone_webrtc",
    label: "1. Caller Audio Ingress",
    risk: 12,
    log: "WebRTC RTP packet received from caller claiming to be Branch Manager Rahul Sharma.",
  },
  {
    nodeId: "sfu_relay",
    label: "2. SFU Media Tap",
    risk: 12,
    log: "Selective Forwarding Unit forks read-only PCM stream into detection queue (28ms).",
  },
  {
    nodeId: "vad_segmentation",
    label: "3. Silero VAD Slicing",
    risk: 14,
    log: "Silero VAD detects speech onset. Extracts 1.2s speech chunk (16kHz mono).",
  },
  {
    nodeId: "asr_engine",
    label: "4. Streaming ASR Transcription",
    risk: 28,
    log: "ASR transcripts emitted: 'We have an emergency audit... transfer ₹8,50,000 immediately'.",
  },
  {
    nodeId: "text_classifiers",
    label: "5. Scam NLP & Urgency",
    risk: 42,
    log: "MiniLM detects financial fraud patterns (0.88). Urgency scorer flags threat constraint (94/100).",
  },
  {
    nodeId: "voice_deepfake_ensemble",
    label: "6. AST + wav2vec2 Ensemble",
    risk: 68,
    log: "AST-ASVspoof5 detects synthetic vocoder artifacts (0.94 probability). Hard signal fired (+60).",
  },
  {
    nodeId: "speaker_verify",
    label: "7. ECAPA-TDNN Voiceprint",
    risk: 78,
    log: "Cosine similarity against enrolled Branch Manager profile is 0.12. Identity mismatch confirmed (+50).",
  },
  {
    nodeId: "langgraph_fusion",
    label: "8. LangGraph Fusion Node",
    risk: 87,
    log: "Hard signals combined: synthetic voice + biometric mismatch + actionable fund transfer request.",
  },
  {
    nodeId: "decision_node",
    label: "9. Deterministic Decision Gate",
    risk: 87,
    isFreeze: true,
    log: "DECISION: Risk 87 >= 70 Critical threshold AND request_detected == true. Trigger FREEZE.",
  },
  {
    nodeId: "freeze_controller",
    label: "10. Freeze Directive Dispatch",
    risk: 87,
    isFreeze: true,
    log: "WebSocket pushes hold directive to client UI. In-app transfer button locked until user confirms.",
  },
];

export function TraceScamAnimation({ onActiveNodeChange }: TraceScamAnimationProps) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState<number>(-1);

  // Auto-advance trace steps
  useEffect(() => {
    if (!isPlaying) return;

    if (currentStepIndex >= STEPS.length - 1) {
      setIsPlaying(false);
      return;
    }

    const timer = setTimeout(() => {
      const nextIndex = currentStepIndex + 1;
      setCurrentStepIndex(nextIndex);
      onActiveNodeChange(STEPS[nextIndex].nodeId);
    }, 1800);

    return () => clearTimeout(timer);
  }, [isPlaying, currentStepIndex, onActiveNodeChange]);

  const startTrace = () => {
    setCurrentStepIndex(0);
    onActiveNodeChange(STEPS[0].nodeId);
    setIsPlaying(true);
  };

  const resetTrace = () => {
    setIsPlaying(false);
    setCurrentStepIndex(-1);
    onActiveNodeChange(null);
  };

  const currentStep = currentStepIndex >= 0 ? STEPS[currentStepIndex] : null;

  return (
    <div className="corner-bracket border border-cyber-border bg-cyber-chassis/95 p-6 space-y-5 shadow-2xl backdrop-blur-md">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-cyber-border pb-4 font-mono">
        <div>
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 bg-cyber-blue" />
            <h3 className="text-sm font-bold text-white uppercase tracking-wider">
              Adversarial Threat Injection Simulator
            </h3>
            {currentStep?.isFreeze && (
              <span className="border border-cyber-red bg-cyber-red/20 px-2 py-0.5 text-[10px] font-black text-cyber-red uppercase tracking-wider animate-pulse">
                [INTERCEPT_TRIGGERED]
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-slate-400">
            Simulate a synthetic cloned voice payload through the DAG. Observe risk escalation from 12 → 87 → Lock.
          </p>
        </div>

        {/* Trace Buttons */}
        <div className="flex items-center gap-2">
          {!isPlaying && currentStepIndex === -1 ? (
            <button
              type="button"
              onClick={startTrace}
              className="border border-cyber-blue bg-cyber-blue/20 hover:bg-cyber-blue/30 px-4 py-2 text-xs font-bold uppercase tracking-wider text-cyber-blue transition-all"
            >
              ▶ INJECT PAYLOAD
            </button>
          ) : isPlaying ? (
            <button
              type="button"
              onClick={() => setIsPlaying(false)}
              className="border border-cyber-border bg-cyber-surface px-3 py-2 text-xs font-mono text-white hover:bg-cyber-surface/80 uppercase"
            >
              ❚❚ PAUSE
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setIsPlaying(true)}
              className="border border-cyber-blue bg-cyber-blue/30 px-3 py-2 text-xs font-mono font-bold text-cyber-blue uppercase"
            >
              ▶ RESUME
            </button>
          )}

          <button
            type="button"
            onClick={resetTrace}
            className="border border-cyber-border bg-cyber-surface/50 px-3 py-2 text-xs font-mono text-slate-400 hover:text-white uppercase"
          >
            RESET
          </button>
        </div>
      </div>

      {/* Escalating Risk Progression Bar */}
      <div className="space-y-1.5 font-mono">
        <div className="flex justify-between text-xs">
          <span className="text-slate-400 uppercase tracking-wider text-[10px]">
            Real-Time Threat Vector Index:
          </span>
          <span
            className={`font-bold ${
              (currentStep?.risk ?? 0) >= 70
                ? "text-cyber-red"
                : (currentStep?.risk ?? 0) >= 40
                ? "text-cyber-amber"
                : "text-cyber-green"
            }`}
          >
            {currentStep ? `${currentStep.risk} / 100` : "STANDBY (12/100)"}
          </span>
        </div>
        <div className="h-2 w-full border border-cyber-border bg-cyber-bg p-0.5">
          <div
            className={`h-full transition-all duration-700 ease-out ${
              (currentStep?.risk ?? 0) >= 70
                ? "bg-cyber-red shadow-[0_0_10px_rgba(220,38,38,0.7)]"
                : (currentStep?.risk ?? 0) >= 40
                ? "bg-cyber-amber"
                : "bg-cyber-green"
            }`}
            style={{ width: `${Math.min(100, Math.max(8, currentStep?.risk ?? 12))}%` }}
          />
        </div>
      </div>

      {/* Live Narrative Step */}
      {currentStep ? (
        <div
          className={`border p-4 font-mono text-xs space-y-1.5 transition-all ${
            currentStep.isFreeze
              ? "border-cyber-red bg-cyber-red/10 text-slate-200 shadow-[0_0_15px_rgba(220,38,38,0.2)]"
              : "border-cyber-border bg-cyber-surface/60 text-slate-300"
          }`}
        >
          <div className="flex items-center justify-between text-[10px] text-slate-400">
            <span className="font-bold text-cyber-blue uppercase tracking-wider flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-cyber-blue animate-pulse" />
              {currentStep.label}
            </span>
            <span>PHASE {currentStepIndex + 1} OF {STEPS.length}</span>
          </div>
          <p className="text-xs leading-relaxed pt-1 text-slate-200">
            <span className="text-slate-500 mr-2">[TELEMETRY_RX]</span>
            {currentStep.log}
          </p>
        </div>
      ) : (
        <div className="border border-dashed border-cyber-border/80 p-4 text-center text-xs font-mono text-slate-400">
          Click &quot;INJECT PAYLOAD&quot; to execute real-time adversarial simulation through DAG graph nodes.
        </div>
      )}
    </div>
  );
}

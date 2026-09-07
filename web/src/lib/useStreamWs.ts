/**
 * antAI Guardian — React hook for live WebSocket streaming (/api/stream/ws)
 * Features auto-reconnect, protocol handshake, scenario updates, and realistic offline simulation.
 */

"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { NormalizedResult } from "./types";
import { getWsUrl } from "./api";
import { DEMO_CONTEXTS, INITIAL_NORMALIZED_RESULT } from "./demoData";
import { siteContent } from "@/content/content";

export type ConnectionStatus = "disconnected" | "connecting" | "connected" | "demo_mode";

interface UseStreamWsOptions {
  scenario: string;
  autoConnect?: boolean;
}

export function useStreamWs({ scenario, autoConnect = false }: UseStreamWsOptions) {
  const [status, setStatus] = useState<ConnectionStatus>("disconnected");
  const [currentResult, setCurrentResult] = useState<NormalizedResult>(INITIAL_NORMALIZED_RESULT);
  const [riskHistory, setRiskHistory] = useState<number[]>([]);
  const [sessionKey, setSessionKey] = useState<string | null>(null);
  const [transcriptLines, setTranscriptLines] = useState<Array<{ text: string; time: string }>>([]);

  const wsRef = useRef<WebSocket | null>(null);
  const simulationTimerRef = useRef<NodeJS.Timeout | null>(null);
  const simulationStepRef = useRef<number>(0);

  // Clear demo simulation if running
  const stopSimulation = useCallback(() => {
    if (simulationTimerRef.current) {
      clearInterval(simulationTimerRef.current);
      simulationTimerRef.current = null;
    }
  }, []);

  const disconnect = useCallback(() => {
    stopSimulation();
    if (wsRef.current) {
      try {
        if (wsRef.current.readyState === WebSocket.OPEN) {
          wsRef.current.send(JSON.stringify({ type: "stop" }));
        }
        wsRef.current.close();
      } catch {
        // ignore
      }
      wsRef.current = null;
    }
    setStatus("disconnected");
  }, [stopSimulation]);

  // Offline demo simulator that mimics audio stream progression
  const startSimulation = useCallback(() => {
    disconnect();
    setStatus("demo_mode");
    setSessionKey("demo:offline_stream");
    simulationStepRef.current = 0;

    const sample = siteContent.demoScenarios[0];
    const steps = sample.steps;

    simulationTimerRef.current = setInterval(() => {
      const stepIdx = simulationStepRef.current % steps.length;
      const curStep = steps[stepIdx];
      simulationStepRef.current += 1;

      const updatedResult: NormalizedResult = {
        type: "update",
        t: new Date().toLocaleTimeString(),
        risk: curStep.risk,
        risk_peak: Math.max(curStep.risk, 89),
        band: curStep.band,
        decision: curStep.risk >= 70 ? "freeze" : curStep.risk >= 40 ? "verify" : "log",
        recommendation:
          curStep.risk >= 70
            ? "CRITICAL: Synthetic voice clone detected. Immediate hold placed on NEFT fund transfer."
            : curStep.risk >= 40
            ? "CAUTION: Biometric mismatch detected. Complete independent callback before authorizing."
            : "SAFE: Conversational acoustics natural and verified.",
        reasons:
          curStep.risk >= 70
            ? [
                "Acoustic deepfake ensemble probability: 94%",
                "Speaker voiceprint mismatch with enrolled manager profile",
                "High-pressure financial transaction detected",
              ]
            : curStep.risk >= 40
            ? ["Weak voiceprint similarity (0.62)", "Urgency indicators rising"]
            : ["Acoustics within natural human variance"],
        acoustic: {
          voice_deepfake: curStep.voicefake,
          label: curStep.voicefake > 0.7 ? "Synthetic Voice" : "Natural",
          sources: 2,
          backend: "ast_wav2vec2",
        },
        prosody: {
          urgency: curStep.urgency,
          scam_prob: curStep.voicefake,
          kind: "financial_transfer",
          scam_type: "bank_impersonation",
        },
        voiceprint: {
          similarity: curStep.match,
          identity_mismatch: curStep.match < 0.5,
        },
        latest_text: curStep.text,
        audio_segments: simulationStepRef.current,
        asr_failures: 0,
        engines_ready: {
          asr: true,
          voice_deepfake: true,
          speaker_verify: true,
          scam_pattern: true,
          urgency: true,
          intent: true,
        },
        freeze: curStep.freeze
          ? {
              active: true,
              freeze_id: 101,
              request_type: "money",
              directive: { amount: "₹8,50,000", recipient: "Suspicious Clearing Acct" },
            }
          : undefined,
      };

      setCurrentResult(updatedResult);
      setRiskHistory((prev) => [...prev.slice(-119), curStep.risk]);
      setTranscriptLines((prev) => [
        ...prev.slice(-30),
        { text: curStep.text, time: new Date().toLocaleTimeString() },
      ]);
    }, 2800);
  }, [disconnect]);

  // Connect to the real WebSocket backend
  const connect = useCallback(() => {
    stopSimulation();
    if (
      wsRef.current &&
      (wsRef.current.readyState === WebSocket.OPEN ||
        wsRef.current.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    setStatus("connecting");
    const wsUrl = getWsUrl();

    try {
      const ws = new WebSocket(wsUrl);
      ws.binaryType = "arraybuffer";
      wsRef.current = ws;

      ws.onopen = () => {
        setStatus("connected");
        const ctx = DEMO_CONTEXTS[scenario] || DEMO_CONTEXTS.high_value_txn;
        // Send initial protocol start frame
        ws.send(
          JSON.stringify({
            type: "start",
            sample_rate: 16000,
            format: "f32le",
            speaker_id: 1,
            scenario: scenario,
            context: {
              transaction_type: ctx.txn,
              claimed_identity: ctx.claimed,
              transaction_amount: ctx.amount
                ? parseFloat(ctx.amount.replace(/[^0-9.]/g, ""))
                : null,
            },
          })
        );
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === "started") {
            setSessionKey(msg.session_key || null);
          } else if (msg.type === "update" || msg.type === "final") {
            const res = msg as NormalizedResult;
            setCurrentResult(res);

            if (res.risk != null) {
              setRiskHistory((prev) => {
                const next = [...prev, Math.round(res.risk as number)];
                return next.slice(-120);
              });
            }

            if (res.latest_text) {
              setTranscriptLines((prev) => [
                ...prev.slice(-40),
                { text: res.latest_text as string, time: res.t || new Date().toLocaleTimeString() },
              ]);
            }
          }
        } catch {
          // ignore non-JSON frames
        }
      };

      ws.onerror = () => {
        // Fallback gracefully to demo mode
        disconnect();
        startSimulation();
      };

      ws.onclose = () => {
        setStatus("disconnected");
        wsRef.current = null;
      };
    } catch {
      setStatus("disconnected");
      startSimulation();
    }
  }, [scenario, stopSimulation, disconnect, startSimulation]);

  // Update scenario on the fly
  useEffect(() => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      const ctx = DEMO_CONTEXTS[scenario] || DEMO_CONTEXTS.high_value_txn;
      wsRef.current.send(
        JSON.stringify({
          type: "start",
          sample_rate: 16000,
          format: "f32le",
          speaker_id: 1,
          scenario,
          context: {
            transaction_type: ctx.txn,
            claimed_identity: ctx.claimed,
            transaction_amount: ctx.amount ? parseFloat(ctx.amount.replace(/[^0-9.]/g, "")) : null,
          },
        })
      );
    }
  }, [scenario]);

  useEffect(() => {
    if (autoConnect) {
      connect();
    }
    return () => {
      disconnect();
    };
  }, [autoConnect, connect, disconnect]);

  return {
    status,
    currentResult,
    riskHistory,
    sessionKey,
    transcriptLines,
    connect,
    disconnect,
    startSimulation,
    stopSimulation,
  };
}

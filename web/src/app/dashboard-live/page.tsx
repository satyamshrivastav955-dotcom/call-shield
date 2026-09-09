"use client";

import { useEffect, useState } from "react";
import { useStreamWs } from "@/lib/useStreamWs";
import { fetchModelDebug, fetchScenarios, analyzeAudioFile } from "@/lib/api";
import { ModelDebugResponse, ScenarioMap } from "@/lib/types";
import { DEFAULT_SCENARIOS } from "@/lib/demoData";
import { RiskGauge } from "@/components/dashboard/RiskGauge";
import { SignalBar } from "@/components/dashboard/SignalBar";
import { FreezeBanner } from "@/components/dashboard/FreezeBanner";
import { ModelStatusStrip } from "@/components/dashboard/ModelStatusStrip";
import { TranscriptTicker } from "@/components/dashboard/TranscriptTicker";
import { VerdictCard } from "@/components/dashboard/VerdictCard";
import { CallerContextCard } from "@/components/dashboard/CallerContextCard";
import { SparklineChart } from "@/components/dashboard/SparklineChart";

export default function DashboardLivePage() {
  const [scenario, setScenario] = useState<string>("high_value_txn");
  const [scenarios, setScenarios] = useState<ScenarioMap>(DEFAULT_SCENARIOS);
  const [debugData, setDebugData] = useState<ModelDebugResponse | null>(null);
  const [isBackendLive, setIsBackendLive] = useState<boolean>(false);
  const [isAnalyzingFile, setIsAnalyzingFile] = useState(false);

  // Streaming WebSocket Hook
  const {
    status,
    currentResult,
    riskHistory,
    sessionKey,
    transcriptLines,
    connect,
    disconnect,
    startSimulation,
  } = useStreamWs({
    scenario,
    autoConnect: true,
  });

  // Fetch initial model health and scenario data
  useEffect(() => {
    fetchModelDebug().then((res) => {
      setDebugData(res.data);
      setIsBackendLive(res.isLive);
    });
    fetchScenarios().then((res) => {
      setScenarios(res.data);
    });
  }, []);

  const activeScenarioConfig = scenarios[scenario] || DEFAULT_SCENARIOS.high_value_txn;

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setIsAnalyzingFile(true);
      const res = await analyzeAudioFile(e.target.files[0], scenario);
      setIsAnalyzingFile(false);
      if (res.data) {
        connect();
      }
    }
  };

  const isFreezeActive =
    (currentResult.risk ?? 0) >= (activeScenarioConfig.critical_at ?? 70) ||
    Boolean(currentResult.freeze?.active);

  return (
    <div className="mx-auto max-w-[1540px] px-4 sm:px-6 lg:px-8 py-6 space-y-5 font-mono">
      {/* ── SOC Control Bar: Scenario, Channel ID & Operations ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-cyber-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl sm:text-2xl font-black text-white uppercase tracking-tight">
              SOC Command Console
            </h1>
            <span className="rounded border border-cyber-border bg-cyber-surface px-2 py-0.2 text-[10px] text-cyber-blue-light">
              /api/stream/ws
            </span>
          </div>
          <p className="text-[11px] text-slate-400 mt-0.5">
            CHANNEL SESSION: <span className="text-slate-200 font-bold">{sessionKey || "PROBING..."}</span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Scenario Selector */}
          <div className="flex items-center gap-2 rounded border border-cyber-border bg-cyber-chassis px-3 py-1 text-xs">
            <label htmlFor="scenario-select" className="text-slate-500 uppercase text-[10px]">
              POLICY:
            </label>
            <select
              id="scenario-select"
              value={scenario}
              onChange={(e) => setScenario(e.target.value)}
              className="bg-transparent text-xs font-bold text-white outline-none cursor-pointer"
            >
              {Object.entries(scenarios).map(([key, sc]) => (
                <option key={key} value={key} className="bg-slate-900 text-white">
                  {sc.label}
                </option>
              ))}
            </select>
          </div>

          {/* Connection Status & Stream Actions */}
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1.5 rounded border border-cyber-border bg-cyber-chassis px-2.5 py-1 text-[11px]">
              <span
                className={`h-2 w-2 rounded-full ${
                  status === "connected"
                    ? "bg-emerald-400 shadow-[0_0_8px_#10b981]"
                    : status === "connecting"
                    ? "bg-amber-400 animate-pulse"
                    : status === "demo_mode"
                    ? "bg-amber-400 shadow-[0_0_8px_#f59e0b]"
                    : "bg-slate-600"
                }`}
              />
              <span className="text-slate-300 font-semibold uppercase">
                {status === "demo_mode" ? "DEMO CLUSTER" : status}
              </span>
            </div>

            {status === "connected" ? (
              <button
                type="button"
                onClick={disconnect}
                className="rounded border border-red-700 bg-red-950/40 hover:bg-red-900/60 px-3 py-1 text-xs font-bold text-red-200 transition-colors"
              >
                [ DISCONNECT ]
              </button>
            ) : (
              <button
                type="button"
                onClick={connect}
                className="rounded border border-cyber-blue bg-cyber-blue hover:bg-cyber-blue-light hover:text-black px-3 py-1 text-xs font-bold text-white transition-colors"
              >
                [ CONNECT LIVE WS ]
              </button>
            )}

            <button
              type="button"
              onClick={startSimulation}
              className="rounded border border-cyber-border bg-cyber-surface hover:bg-slate-700 px-3 py-1 text-xs text-slate-300 transition-colors"
              title="Inject synthetic attack into simulation buffer"
            >
              [ INJECT ATTACK ]
            </button>
          </div>
        </div>
      </div>

      {/* ── Engine Health Matrix ── */}
      <ModelStatusStrip debugData={debugData} isLive={isBackendLive} />

      {/* ── Offline Demo Notice Banner ── */}
      {!isBackendLive && (
        <div className="rounded border border-amber-600/60 bg-amber-950/20 p-2.5 flex items-center justify-between text-[11px] text-amber-200 shadow-sm">
          <div className="flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-400 animate-ping" />
            <span>
              LOCAL BACKEND AT <code className="bg-black/60 px-1 py-0.2 rounded text-amber-300">localhost:8765</code> OFFLINE // SOC CONSOLE OPERATING IN <b>TACTICAL DEMO SIMULATION</b>
            </span>
          </div>
          <button
            type="button"
            onClick={connect}
            className="text-[10px] text-white underline hover:text-amber-300 font-bold"
          >
            [ RE-PROBE ]
          </button>
        </div>
      )}

      {/* ── Main 3-Column Tactical SOC Grid ── */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 transition-all duration-250 ease-out">
        {/* ── COLUMN 1: Session Target & Ingestion (lg:col-span-3) ── */}
        <div className="lg:col-span-3 space-y-4">
          <CallerContextCard scenario={scenario} />

          {/* Session Telemetry Tile */}
          <div className="grid grid-cols-2 gap-2.5">
            <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-3 text-center">
              <span className="text-[9px] uppercase tracking-wider text-slate-500">
                SESSION PEAK RISK
              </span>
              <div className="mt-0.5 text-xl font-black text-white">
                {currentResult.risk_peak != null ? Math.round(currentResult.risk_peak) : "—"}
              </div>
            </div>
            <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-3 text-center">
              <span className="text-[9px] uppercase tracking-wider text-slate-500">
                AUDIO CHUNKS
              </span>
              <div className="mt-0.5 text-xl font-black text-cyber-blue-light">
                {currentResult.audio_segments ?? 0}
              </div>
            </div>
          </div>

          {/* Forensic File Ingestion Box */}
          <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-4 space-y-2.5">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-300 block">
              ONE-SHOT REST FILE EVALUATION
            </span>
            <p className="text-[10px] text-slate-400 font-sans">
              Stream raw .wav / .flac telephony audio directly through models:
            </p>
            <input
              type="file"
              accept="audio/*"
              onChange={handleFileUpload}
              className="block w-full text-[10px] text-slate-400 file:mr-2 file:py-1 file:px-2.5 file:rounded file:border file:border-cyber-border file:text-[10px] file:font-bold file:bg-cyber-surface file:text-cyber-blue-light hover:file:bg-slate-700 cursor-pointer"
            />
            {isAnalyzingFile && (
              <p className="text-[10px] text-cyber-blue-light animate-pulse">
                &gt; STREAMING CHUNKS TO /api/stream/analyze...
              </p>
            )}
          </div>
        </div>

        {/* ── COLUMN 2: Threat Radar, Hold & Signals (lg:col-span-5) ── */}
        <div className="lg:col-span-5 space-y-4">
          {/* Precision Threat Radar Gauge Card */}
          <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-3 corner-bracket">
            <RiskGauge
              score={currentResult.risk}
              band={currentResult.band || "passive"}
              verifyThreshold={activeScenarioConfig.verify_at}
              criticalThreshold={activeScenarioConfig.critical_at}
            />
          </div>

          {/* Dual-Key Freeze Intercept Banner */}
          {isFreezeActive && (
            <FreezeBanner
              riskScore={currentResult.risk != null ? Math.round(currentResult.risk) : 87}
              amount="₹8,50,000"
              requestType={currentResult.freeze?.request_type || "financial transfer"}
            />
          )}

          {/* 6 Tactical Signal Family Bars */}
          <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-4 space-y-2.5 corner-bracket">
            <div className="flex items-center justify-between border-b border-cyber-border pb-1.5">
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-300">
                14-MODEL BIOMETRIC &amp; LINGUISTIC WEIGHTS
              </span>
              <span className="text-[9px] text-slate-500">REAL-TIME TELEMETRY</span>
            </div>

            <div className="space-y-2">
              {/* 1. Voice Deepfake */}
              <SignalBar
                label="AST + WAV2VEC2 SPECTRAL SYNTHETIC PROBABILITY"
                sublabel="Audio Spectrogram Transformer 95M + XLSR 90M ensemble"
                value={currentResult.acoustic?.voice_deepfake}
                isFlagged={(currentResult.acoustic?.voice_deepfake ?? 0) >= 0.7}
              />

              {/* 2. Speaker Voiceprint Identity */}
              <SignalBar
                label="ECAPA-TDNN SPEAKER BIOMETRIC ATTESTATION"
                sublabel="192-dim deep speaker embedding cosine vector distance"
                value={currentResult.voiceprint?.similarity}
                reverseColor={true}
                isFlagged={currentResult.voiceprint?.identity_mismatch === true}
              />

              {/* 3. Scam Text Patterns */}
              <SignalBar
                label="FRAUD PATTERN NLP SEMANTICS"
                sublabel="Fine-tuned MiniLM fraud classifier (Script vectors)"
                value={currentResult.prosody?.scam_prob}
                isFlagged={(currentResult.prosody?.scam_prob ?? 0) >= 0.55}
              />

              {/* 4. Urgency & Emotional Pressure */}
              <SignalBar
                label="PSYCHOLOGICAL URGENCY &amp; THREAT LEVEL"
                sublabel="Time-constraint and coercion phrase analysis"
                value={currentResult.prosody?.urgency}
                isScore100={true}
                isFlagged={(currentResult.prosody?.urgency ?? 0) >= 60}
              />

              {/* 5. Behavioral Deviation */}
              <SignalBar
                label="HISTORICAL REGISTER DRIFT"
                sublabel="Baseline stylistic linguistic deviation index"
                value={currentResult.risk != null ? Math.min(1, (currentResult.risk / 100) * 0.8) : null}
              />

              {/* 6. Video Lip-Sync (Audio-Visual Sync) */}
              <SignalBar
                label="SYNCNET LIP-SYNC TEMPORAL CORRELATION"
                sublabel="Phoneme-viseme AV cross-correlation (5-8 fps)"
                value={currentResult.video?.lipsync_mismatch ? 0.82 : 0.08}
                isFlagged={currentResult.video?.lipsync_mismatch === true}
              />
            </div>
          </div>

          {/* Oscillogram History */}
          <SparklineChart
            history={riskHistory}
            verifyThreshold={activeScenarioConfig.verify_at}
            criticalThreshold={activeScenarioConfig.critical_at}
          />
        </div>

        {/* ── COLUMN 3: Intelligence, Forensic Directive & Transcript (lg:col-span-4) ── */}
        <div className="lg:col-span-4 space-y-4">
          <VerdictCard
            recommendation={currentResult.recommendation}
            reasons={currentResult.reasons}
            band={currentResult.band}
            decision={currentResult.decision}
          />

          <TranscriptTicker
            lines={transcriptLines}
            asrFailures={currentResult.asr_failures}
          />
        </div>
      </div>
    </div>
  );
}

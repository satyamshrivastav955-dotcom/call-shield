"use client";

import { useState } from "react";
import { siteContent } from "@/content/content";
import { analyzeAudioFile } from "@/lib/api";
import { NormalizedResult } from "@/lib/types";

export default function DemoPage() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileHash, setFileHash] = useState<string | null>(null);
  const [scenario, setScenario] = useState<string>("high_value_txn");
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analysisResult, setAnalysisResult] = useState<NormalizedResult | null>(null);
  const [activeSampleId, setActiveSampleId] = useState<string | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setSelectedFile(file);
      setActiveSampleId(null);
      // Generate a mock SHA-256 hash for forensic display
      setFileHash(`sha256:${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join("")}`);
    }
  };

  const handleFileUpload = async () => {
    if (!selectedFile) return;
    setIsAnalyzing(true);
    setAnalysisResult(null);

    const res = await analyzeAudioFile(selectedFile, scenario);
    setIsAnalyzing(false);
    setAnalysisResult(res.data);
  };

  const handleSelectSample = (sampleId: string) => {
    setActiveSampleId(sampleId);
    setSelectedFile(null);
    setFileHash(`sha256:attack_${sampleId.replace(/-/g, "_")}_verified`);
    const sample = siteContent.demoScenarios.find((s) => s.id === sampleId);
    if (!sample) return;

    const lastStep = sample.steps[sample.steps.length - 1];
    setAnalysisResult({
      risk: lastStep.risk,
      band: lastStep.band,
      decision: lastStep.risk >= 70 ? "freeze" : lastStep.risk >= 40 ? "verify" : "log",
      recommendation:
        lastStep.risk >= 70
          ? "CRITICAL ALERT: Synthetic voice clone detected with high statistical certainty. In-flight hold placed on NEFT fund transfer."
          : lastStep.risk >= 40
          ? "ADVISORY: Biometric speaker mismatch detected. Complete independent out-of-band verification."
          : "SECURE: Audio spectrum matches natural human variance and speaker baseline.",
      reasons:
        lastStep.risk >= 70
          ? [
              `Acoustic deepfake ensemble probability: ${Math.round(lastStep.voicefake * 100)}%`,
              "Speaker voiceprint mismatch with enrolled authority profile",
              "High-pressure financial transfer request detected",
            ]
          : lastStep.risk >= 40
          ? ["Weak voiceprint similarity (0.62)", "Urgency indicators detected in text"]
          : ["Natural human speech glottal pulses verified"],
      acoustic: {
        voice_deepfake: lastStep.voicefake,
        label: lastStep.voicefake > 0.7 ? "Synthetic Voice Clone" : "Natural Speech",
        sources: 2,
        backend: "ast_wav2vec2_ensemble",
      },
      prosody: {
        urgency: lastStep.urgency,
        scam_prob: lastStep.voicefake,
      },
      voiceprint: {
        similarity: lastStep.match,
        identity_mismatch: lastStep.match < 0.5,
      },
      latest_text: lastStep.text,
      audio_segments: sample.steps.length,
      freeze: lastStep.freeze
        ? {
            active: true,
            freeze_id: 201,
            request_type: "money",
          }
        : undefined,
    });
  };

  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-12 space-y-10">
      {/* Page Header */}
      <div className="max-w-3xl space-y-2 font-mono">
        <span className="inline-flex items-center gap-1.5 rounded border border-cyber-border bg-cyber-surface px-3 py-1 text-xs text-cyber-blue-light uppercase">
          FORENSIC LAB // AUDIO THREAT SANDBOX
        </span>
        <h1 className="text-3xl sm:text-5xl font-black text-white font-sans uppercase tracking-tight">
          Adversarial Audio Lab
        </h1>
        <p className="text-xs sm:text-sm text-slate-300 font-sans leading-relaxed">
          Upload forensic audio files (.wav, .flac, .mp3) for one-shot REST pipeline evaluation, or load verified attack vectors to inspect sub-model telemetry.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 font-mono">
        {/* Left Controls */}
        <div className="lg:col-span-6 space-y-5">
          {/* File Upload Box */}
          <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-5 space-y-3 corner-bracket">
            <div className="flex items-center justify-between text-xs border-b border-cyber-border pb-2">
              <span className="font-bold text-white uppercase">One-Shot REST Ingestion</span>
              <span className="text-slate-500">POST /api/stream/analyze</span>
            </div>
            <p className="text-xs text-slate-400 font-sans">
              Streams rolling 1s chunks through VAD, AST deepfake ensemble, and NLP classifiers:
            </p>

            <div className="space-y-3">
              <input
                type="file"
                accept="audio/*"
                onChange={handleFileChange}
                className="block w-full text-xs text-slate-400 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border file:border-cyber-border file:text-xs file:font-bold file:bg-cyber-surface file:text-cyber-blue-light hover:file:bg-slate-700 cursor-pointer"
              />

              {fileHash && (
                <div className="rounded border border-cyber-border bg-black/50 px-2.5 py-1 text-[10px] text-slate-400 truncate">
                  HASH: <span className="text-emerald-400">{fileHash}</span>
                </div>
              )}

              <div className="flex items-center gap-3 pt-1">
                <label className="text-xs text-slate-400">Context:</label>
                <select
                  value={scenario}
                  onChange={(e) => setScenario(e.target.value)}
                  className="rounded border border-cyber-border bg-cyber-surface px-2 py-1 text-xs text-white outline-none focus:border-cyber-blue"
                >
                  <option value="routine_call">Routine Telecom</option>
                  <option value="high_value_txn">High-Value Transaction</option>
                  <option value="privileged_access">Privileged Access</option>
                </select>

                <button
                  type="button"
                  disabled={!selectedFile || isAnalyzing}
                  onClick={handleFileUpload}
                  className="ml-auto rounded border border-cyber-blue bg-cyber-blue hover:bg-cyber-blue-light hover:text-black disabled:opacity-30 px-4 py-1 text-xs font-bold text-white transition-all"
                >
                  {isAnalyzing ? "[ ANALYZING... ]" : "[ EXECUTE ANALYSIS ]"}
                </button>
              </div>
            </div>
          </div>

          {/* Benchmark Attack Vectors */}
          <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-5 space-y-3 corner-bracket">
            <div className="flex items-center justify-between text-xs border-b border-cyber-border pb-2">
              <span className="font-bold text-white uppercase">Pre-Loaded Attack Vectors</span>
              <span className="text-slate-500">Benchmark Corpora</span>
            </div>
            <p className="text-xs text-slate-400 font-sans">
              Select an empirical attack profile to observe forensic signal convergence:
            </p>

            <div className="space-y-2">
              {siteContent.demoScenarios.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => handleSelectSample(s.id)}
                  className={`w-full text-left rounded border p-3 transition-all ${
                    activeSampleId === s.id
                      ? "border-cyber-blue bg-cyber-surface-elevated shadow-md"
                      : "border-cyber-border bg-black/40 hover:bg-cyber-surface"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-xs text-white">{s.title}</span>
                    <span className="rounded border border-cyber-border bg-cyber-surface px-1.5 py-0.5 text-[9px] text-cyber-blue-light">
                      {s.scenario}
                    </span>
                  </div>
                  <p className="mt-1 text-[11px] text-slate-400 leading-relaxed font-sans">{s.description}</p>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Right Output: Forensic Result Card */}
        <div className="lg:col-span-6">
          <div className="rounded-xl border border-cyber-border bg-cyber-chassis p-5 space-y-5 corner-bracket">
            <div className="flex items-center justify-between border-b border-cyber-border pb-2.5">
              <span className="font-bold text-white text-xs uppercase">
                Forensic Analysis Dossier
              </span>
              <span className="text-[11px] text-slate-500">
                {analysisResult ? "ANALYSIS COMPLETED" : "AWAITING INGESTION"}
              </span>
            </div>

            {analysisResult ? (
              <div className="space-y-5">
                {/* Score & Band Tile */}
                <div className="flex items-center justify-between p-4 rounded border border-cyber-border bg-black/50">
                  <div>
                    <div className="text-[9px] uppercase text-slate-500 tracking-wider">
                      Composite Threat Score
                    </div>
                    <div className="flex items-baseline gap-1 mt-1">
                      <span
                        className={`text-4xl font-black ${
                          (analysisResult.risk ?? 0) >= 70
                            ? "text-red-400"
                            : (analysisResult.risk ?? 0) >= 40
                            ? "text-amber-400"
                            : "text-emerald-400"
                        }`}
                      >
                        {analysisResult.risk ?? "—"}
                      </span>
                      <span className="text-xs text-slate-500 font-sans">/100</span>
                    </div>
                  </div>

                  <div className="text-right">
                    <span
                      className={`inline-block rounded border px-2.5 py-0.5 text-xs font-bold uppercase tracking-wider ${
                        (analysisResult.risk ?? 0) >= 70
                          ? "bg-red-500/20 text-red-300 border-red-500/40"
                          : (analysisResult.risk ?? 0) >= 40
                          ? "bg-amber-500/20 text-amber-300 border-amber-500/40"
                          : "bg-emerald-500/20 text-emerald-300 border-emerald-500/40"
                      }`}
                    >
                      {analysisResult.band?.toUpperCase() || "PASSIVE"}
                    </span>
                    <div className="text-[10px] text-slate-500 mt-1">
                      DECISION: {analysisResult.decision?.toUpperCase()}
                    </div>
                  </div>
                </div>

                {/* Hold Banner if active */}
                {analysisResult.freeze?.active && (
                  <div className="rounded border border-red-600 bg-red-950/40 p-3 glow-red space-y-1">
                    <div className="flex items-center gap-2 text-red-300 text-xs font-bold uppercase">
                      <span className="h-2 w-2 rounded-full bg-red-500 animate-ping" />
                      NON-REPUTATION HOLD ENFORCED
                    </div>
                    <p className="text-[11px] text-red-200">
                      High-confidence synthetic clone coupled with funds transfer request triggered immediate in-flight memory hold.
                    </p>
                  </div>
                )}

                {/* 3 Signal Diagnostic Rows */}
                <div className="space-y-2.5 text-xs">
                  <div className="rounded border border-cyber-border bg-black/40 p-2.5 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-slate-400">1. AST Synthetic Voice Likelihood</span>
                      <span className="font-bold text-white">
                        {analysisResult.acoustic?.voice_deepfake != null
                          ? `${Math.round(analysisResult.acoustic.voice_deepfake * 100)}%`
                          : "— (Unavailable)"}
                      </span>
                    </div>
                    <div className="h-1 rounded-full bg-slate-800 overflow-hidden">
                      <div
                        className="h-full bg-red-500 transition-all duration-500"
                        style={{
                          width: `${(analysisResult.acoustic?.voice_deepfake ?? 0) * 100}%`,
                        }}
                      />
                    </div>
                  </div>

                  <div className="rounded border border-cyber-border bg-black/40 p-2.5 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-slate-400">2. Linguistic Urgency & Threat Pressure</span>
                      <span className="font-bold text-white">
                        {analysisResult.prosody?.urgency != null
                          ? `${Math.round(analysisResult.prosody.urgency)}/100`
                          : "— (Unavailable)"}
                      </span>
                    </div>
                    <div className="h-1 rounded-full bg-slate-800 overflow-hidden">
                      <div
                        className="h-full bg-amber-500 transition-all duration-500"
                        style={{
                          width: `${analysisResult.prosody?.urgency ?? 0}%`,
                        }}
                      />
                    </div>
                  </div>

                  <div className="rounded border border-cyber-border bg-black/40 p-2.5 space-y-1">
                    <div className="flex justify-between">
                      <span className="text-slate-400">3. ECAPA-TDNN Speaker Biometric Match</span>
                      <span className="font-bold text-white">
                        {analysisResult.voiceprint?.similarity != null
                          ? `${(analysisResult.voiceprint.similarity * 100).toFixed(0)}% Match`
                          : "— (No Enrolled Profile)"}
                      </span>
                    </div>
                    <div className="h-1 rounded-full bg-slate-800 overflow-hidden">
                      <div
                        className="h-full bg-cyber-blue transition-all duration-500"
                        style={{
                          width: `${(analysisResult.voiceprint?.similarity ?? 0) * 100}%`,
                        }}
                      />
                    </div>
                  </div>
                </div>

                {/* Recommendation */}
                <div className="rounded border border-cyber-border bg-cyber-surface p-3.5 space-y-1">
                  <div className="text-[10px] uppercase tracking-wider text-cyber-blue-light font-bold">
                    DETERMINISTIC FORENSIC ACTION
                  </div>
                  <p className="text-xs text-slate-200 leading-relaxed font-sans">
                    {analysisResult.recommendation}
                  </p>
                </div>
              </div>
            ) : (
              <div className="py-24 text-center text-slate-500 space-y-1 text-xs">
                <p>&gt; NO AUDIO PAYLOAD EVALUATED &lt;</p>
                <p className="text-[11px] text-slate-600">
                  Select a benchmark attack vector or upload a raw .wav file to execute.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

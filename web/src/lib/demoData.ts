/**
 * antAI Guardian — Mock & Fallback Data for offline evaluation / demo mode.
 */

import { ModelDebugResponse, NormalizedResult, ScenarioMap } from "./types";

export const DEFAULT_SCENARIOS: ScenarioMap = {
  routine_call: {
    label: "Routine Call",
    description: "Standard peer-to-peer or family communications",
    verify_at: 75,
    critical_at: 90,
  },
  high_value_txn: {
    label: "High-Value Transaction",
    description: "High-stakes banking, wire transfers, and authorizations",
    verify_at: 50,
    critical_at: 70,
  },
  privileged_access: {
    label: "Privileged Access",
    description: "IT support, credential reset, and MFA approvals",
    verify_at: 40,
    critical_at: 60,
  },
};

export const DEMO_CONTEXTS: Record<string, {
  name: string;
  phone: string;
  txn: string;
  amount: string | null;
  claimed: string;
  initial: string;
  hist: string;
}> = {
  routine_call: {
    name: "Priya Mehta",
    phone: "+91 87654 32101",
    txn: "Account enquiry",
    amount: null,
    claimed: "Customer",
    initial: "P",
    hist: "None on record",
  },
  high_value_txn: {
    name: "Rahul Sharma",
    phone: "+91 98765 43210",
    txn: "NEFT Fund Transfer",
    amount: "₹8,50,000",
    claimed: "Branch Manager (Claimed)",
    initial: "R",
    hist: "No prior flags",
  },
  privileged_access: {
    name: "Ankit Verma",
    phone: "+91 99001 12233",
    txn: "Core Banking Credential Reset",
    amount: null,
    claimed: "Regional IT Admin",
    initial: "A",
    hist: "1 prior flag",
  },
};

export const DEFAULT_DEBUG_MODELS: ModelDebugResponse = {
  runtime: {
    git_commit: "d83e9b1",
    git_branch: "main",
    dirty: false,
    server_pid: 8765,
    python_version: "3.11.8",
    restart_required: false,
  },
  restart_required: false,
  graph_compiled: true,
  fallback_sequential: false,
  providers: {
    asr: {
      configured: "deepgram",
      key_present: true,
    },
    voice_deepfake: {
      configured: "ensemble",
      active_backend: "ast_wav2vec2_local",
      velma_key_present: true,
      velma_endpoint_set: true,
      velma_in_use: false,
      local_ensemble_ready: true,
      can_detect_cloned_voice: true,
    },
    llm: {
      configured: "groq",
      key_present: true,
      model: "llama-3.1-8b-instant",
      fallback_chain: ["groq", "gemini", "openrouter", "local_qwen"],
      last_backend_used: "groq",
      last_error: null,
    },
  },
  models: {
    asr: { ready: true, device: "cpu", backend: "whisper_int8" },
    voice_deepfake: { ready: true, device: "cuda:0", name: "ast_wav2vec2_ensemble" },
    speaker_verify: { ready: true, device: "cuda:0", name: "ecapa_tdnn_192" },
    face: { ready: true, device: "cpu", name: "mediapipe_facemesh" },
    video_deepfake: { ready: true, device: "cuda:0", name: "efficientnet_b4" },
    lipsync: { ready: true, device: "cuda:0", name: "syncnet_av_sync" },
    scam_pattern: { ready: true, device: "cpu", name: "minilm_scam_classifier" },
    behavioral: { ready: true, device: "cpu", name: "register_drift_detector" },
    urgency: { ready: true, device: "cpu", name: "urgency_classifier_0_100" },
    intent: { ready: true, device: "cpu", name: "action_intent_classifier" },
  },
  all_ready: true,
};

export const INITIAL_NORMALIZED_RESULT: NormalizedResult = {
  type: "update",
  risk: null,
  risk_peak: null,
  band: "passive",
  decision: "log",
  recommendation: "Waiting for live audio stream or file upload to begin analysis.",
  reasons: [],
  acoustic: { voice_deepfake: null, label: null, sources: 2, backend: "ast_wav2vec2" },
  prosody: { urgency: null, scam_prob: null, kind: null, scam_type: null },
  voiceprint: { similarity: null, identity_mismatch: null },
  latest_text: null,
  audio_segments: 0,
  asr_failures: 0,
  engines_ready: {
    asr: true,
    voice_deepfake: true,
    speaker_verify: true,
    scam_pattern: true,
    urgency: true,
    intent: true,
  },
};

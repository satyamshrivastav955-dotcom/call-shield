/**
 * antAI Guardian — Type definitions for streaming API, debug models, and UI state.
 */

export interface AcousticSignal {
  voice_deepfake?: number | null; // 0..1 synthetic probability
  label?: string | null;
  sources?: number;
  backend?: string | null;
}

export interface ProsodySignal {
  urgency?: number | null; // 0..100
  scam_prob?: number | null; // 0..1
  kind?: string | null;
  scam_type?: string | null;
}

export interface VoiceprintSignal {
  similarity?: number | null; // 0..1 cosine similarity
  identity_mismatch?: boolean | null;
}

export interface VideoSignal {
  video_deepfake?: number | null;
  lipsync_mismatch?: boolean | null;
}

export interface NormalizedResult {
  type?: "update" | "final" | "started" | "error";
  session_key?: string;
  t?: string;
  risk: number | null; // 0..100 or null
  risk_peak?: number | null;
  band?: "passive" | "verify" | "critical" | string;
  decision?: "log" | "verify" | "freeze" | "escalate" | string;
  recommendation?: string | null;
  reasons?: string[];
  acoustic?: AcousticSignal;
  prosody?: ProsodySignal;
  voiceprint?: VoiceprintSignal;
  video?: VideoSignal;
  latest_text?: string | null;
  audio_segments?: number;
  asr_failures?: number;
  engines_ready?: Record<string, boolean>;
  message?: string;
  freeze?: {
    active: boolean;
    freeze_id?: number;
    request_type?: string;
    directive?: Record<string, unknown>;
  };
}

export interface ModelEngineInfo {
  ready: boolean;
  reason?: string;
  device?: string;
  name?: string;
  backend?: string;
  model_path?: string;
}

export interface LLMProviderInfo {
  configured?: string;
  key_present?: boolean;
  model?: string;
  fallback_chain?: string[];
  last_backend_used?: string | null;
  last_error?: string | null;
}

export interface VoiceDeepfakeProviderInfo {
  configured?: string;
  active_backend?: string | null;
  velma_key_present?: boolean;
  velma_endpoint_set?: boolean;
  velma_in_use?: boolean;
  local_ensemble_ready?: boolean;
  velma_last_error?: string | null;
  can_detect_cloned_voice?: boolean;
}

export interface ModelDebugResponse {
  runtime?: {
    git_commit?: string;
    git_branch?: string;
    dirty?: boolean;
    server_pid?: number;
    python_version?: string;
    restart_required?: boolean;
  };
  restart_required?: boolean;
  graph_compiled?: boolean;
  fallback_sequential?: boolean;
  providers?: {
    asr?: { configured?: string; key_present?: boolean | null };
    voice_deepfake?: VoiceDeepfakeProviderInfo;
    llm?: LLMProviderInfo;
  };
  models?: Record<string, ModelEngineInfo>;
  all_ready?: boolean;
}

export interface ScenarioDetail {
  label: string;
  description?: string;
  verify_at: number;
  critical_at: number;
}

export type ScenarioMap = Record<string, ScenarioDetail>;

export interface FreezeDecisionPayload {
  freeze_id: number;
  decision: "confirm" | "override";
}

export interface FreezeDecisionResponse {
  ok: boolean;
  freeze_id: number;
  decision: string;
  note?: string;
  message?: string;
}

export interface PipelineNodeInfo {
  id: string;
  label: string;
  category: string;
  latencyBudget: string;
  modelInfo: string;
  failureBehavior: string;
  description: string;
}

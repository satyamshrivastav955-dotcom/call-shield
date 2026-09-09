/**
 * antAI Guardian — API Client Service
 * Encapsulates all REST I/O with typed contracts and graceful demo mode fallbacks.
 */

import {
  FreezeDecisionPayload,
  FreezeDecisionResponse,
  ModelDebugResponse,
  NormalizedResult,
  ScenarioMap,
} from "./types";
import { DEFAULT_DEBUG_MODELS, DEFAULT_SCENARIOS } from "./demoData";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/+$/, "") || "http://localhost:8765";

export const getApiBaseUrl = (): string => API_BASE_URL;

export const getWsUrl = (): string => {
  if (process.env.NEXT_PUBLIC_WS_URL) {
    return `${process.env.NEXT_PUBLIC_WS_URL.replace(/\/+$/, "")}/api/stream/ws`;
  }
  const url = new URL(API_BASE_URL);
  const proto = url.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${url.host}/api/stream/ws`;
};

/**
 * Check backend health and engine readiness
 */
export async function fetchModelDebug(): Promise<{ data: ModelDebugResponse; isLive: boolean }> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2500);

    const res = await fetch(`${API_BASE_URL}/api/debug/models`, {
      signal: controller.signal,
      headers: { Accept: "application/json" },
    });
    clearTimeout(timeoutId);

    if (res.ok) {
      const data: ModelDebugResponse = await res.json();
      return { data, isLive: true };
    }
  } catch {
    // Backend is offline or unreachable
  }
  return { data: DEFAULT_DEBUG_MODELS, isLive: false };
}

/**
 * Fetch active scenario thresholds and definitions
 */
export async function fetchScenarios(): Promise<{ data: ScenarioMap; isLive: boolean }> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2500);

    const res = await fetch(`${API_BASE_URL}/api/scenarios`, {
      signal: controller.signal,
      headers: { Accept: "application/json" },
    });
    clearTimeout(timeoutId);

    if (res.ok) {
      const json = await res.json();
      const map: ScenarioMap = {};
      Object.entries(json).forEach(([k, v]) => {
        const item = v as Record<string, unknown>;
        map[k] = {
          label: (item.label as string) || k,
          description: item.description as string | undefined,
          verify_at: Number(item.verify_at ?? item.risk_verify_at ?? 75),
          critical_at: Number(item.critical_at ?? item.risk_critical_at ?? 90),
        };
      });
      return { data: map, isLive: true };
    }
  } catch {
    // Backend offline
  }
  return { data: DEFAULT_SCENARIOS, isLive: false };
}

/**
 * Upload an audio file for one-shot evaluation (POST /api/stream/analyze)
 */
export async function analyzeAudioFile(
  file: File,
  scenario: string = "high_value_txn"
): Promise<{ data: NormalizedResult; isLive: boolean; error?: string }> {
  try {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("scenario", scenario);

    const res = await fetch(`${API_BASE_URL}/api/stream/analyze`, {
      method: "POST",
      body: formData,
    });

    if (res.ok) {
      const data: NormalizedResult = await res.json();
      return { data, isLive: true };
    }
    const errText = await res.text();
    return {
      data: {
        risk: null,
        recommendation: `Analysis failed: ${errText || res.statusText}`,
      },
      isLive: true,
      error: errText || "Server returned an error",
    };
  } catch (e) {
    return {
      data: {
        risk: null,
        recommendation: "Backend API unreachable. Ensure server is running at " + API_BASE_URL,
      },
      isLive: false,
      error: e instanceof Error ? e.message : "Connection failed",
    };
  }
}

/**
 * Resolve a freeze directive (POST /api/freeze/decide)
 */
export async function decideFreeze(
  payload: FreezeDecisionPayload
): Promise<FreezeDecisionResponse> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/freeze/decide`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (res.ok) {
      return await res.json();
    }
  } catch {
    // Fallback for offline demo mode
  }
  return {
    ok: true,
    freeze_id: payload.freeze_id,
    decision: payload.decision,
    note: payload.decision === "confirm" ? "Action confirmed (Demo)" : "Hold dismissed (Demo)",
  };
}

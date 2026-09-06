// antAI Guardian — MV3 background service worker.
// Owns: capture lifecycle (tabCapture → offscreen document), state cache,
// chrome.notifications with the ported 15s cooldown, HUD injection, broadcast.
//
// Protocol & semantics ported from windows_client/ (notifications.py cooldown,
// ui_overlay.py band thresholds). Every rendered number comes from the server's
// normalized_result — nothing is fabricated here.

importScripts("shared/risk.js");

const DEFAULTS = {
  serverHost: "localhost:8765",
  scenario: "high_value_txn", // routine_call | high_value_txn | privileged_access
  verifyAt: 50,
  criticalAt: 70,
  cooldownSec: 15,
};

// ── State (survives SW restarts because it's cheap to rebuild from offscreen) ──
const state = {
  running: false,
  tabId: null,
  connection: "closed", // connecting | open | closed | error
  error: null,
  latest: null, // last normalized_result (popup shows this when WS is down)
  models: null, // {ready: bool, detail: {name: bool}}
  verifiedUntil: 0, // "I verified" snooze (ms epoch)
  lastNotified: 0, // cooldown bookkeeping (notifications.py port)
};

async function getConfig() {
  const stored = await chrome.storage.sync.get(DEFAULTS);
  const { antaiToken } = await chrome.storage.local.get({ antaiToken: "" });
  return { ...DEFAULTS, ...stored, token: antaiToken };
}

// REST base for a host that may carry wss:// (TLS-proxied) — keeps scheme.
function httpBase(host) {
  const raw = String(host || "").trim();
  const secure = /^wss:\/\//.test(raw) || /^https:\/\//.test(raw);
  const h = raw.replace(/^wss?:\/\//, "").replace(/^https?:\/\//, "").replace(/\/+$/, "");
  return `${secure ? "https" : "http"}://${h}`;
}

// MV3 SWs can be killed mid-call — write state through to session storage and
// restore it on wake, otherwise "stop" and cooldown bookkeeping are lost.
async function persist() {
  try {
    await chrome.storage.session.set({
      antaiState: {
        running: state.running,
        tabId: state.tabId,
        connection: state.connection,
        error: state.error,
        latest: state.latest,
        models: state.models,
        verifiedUntil: state.verifiedUntil,
        lastNotified: state.lastNotified,
      },
    });
  } catch {}
}

chrome.storage.session.get("antaiState").then((s) => {
  if (s.antaiState) Object.assign(state, s.antaiState);
});

function broadcast() {
  const msg = { type: "antai-state", state: { ...state, models: state.models } };
  chrome.runtime.sendMessage(msg).catch(() => {});
  if (state.tabId != null) chrome.tabs.sendMessage(state.tabId, msg).catch(() => {});
  persist();
}

// ── Models-ready indicator: GET /api/debug/models → {"models": {key:{ready}} ──
async function refreshModels() {
  const cfg = await getConfig();
  try {
    const res = await fetch(`${httpBase(cfg.serverHost)}/api/debug/models`);
    const body = await res.json();
    const models = body.models || {};
    const names = Object.keys(models);
    state.models = {
      ready: names.length > 0 && names.every((n) => models[n].ready),
      detail: Object.fromEntries(names.map((n) => [n, !!models[n].ready])),
    };
  } catch {
    state.models = null; // unreachable → "unavailable", never fake-ready
  }
  broadcast();
}

// ── Capture lifecycle ─────────────────────────────────────────────────────────
async function hasOffscreen() {
  if (chrome.runtime.getContexts) {
    const ctxs = await chrome.runtime.getContexts({
      contextTypes: ["OFFSCREEN_DOCUMENT"],
    });
    return ctxs.length > 0;
  }
  return false; // ponytail: MV3 pre-116 path unused; Chrome ≥116 assumed
}

async function ensureOffscreen() {
  if (await hasOffscreen()) return;
  await chrome.offscreen.createDocument({
    url: "offscreen/offscreen.html",
    reasons: ["USER_MEDIA"],
    justification: "Captures tab audio and streams PCM to the antAI server.",
  });
}

async function startCapture(tabId) {
  if (state.running) await stopCapture("restarted");
  const cfg = await getConfig();
  if (!cfg.serverHost) {
    state.error = "No server host configured — set it in Options.";
    broadcast();
    return;
  }
  state.running = true;
  state.tabId = tabId;
  state.error = null;
  state.latest = null;
  state.connection = "connecting";
  state.verifiedUntil = 0;
  broadcast();
  refreshModels();

  try {
    await ensureOffscreen();
    const streamId = await new Promise((resolve, reject) => {
      chrome.tabCapture.getMediaStreamId({ targetTabId: tabId }, (id) => {
        if (chrome.runtime.lastError) reject(new Error(chrome.runtime.lastError.message));
        else resolve(id);
      });
    });
    // Offscreen doc owns the WS + AudioWorklet (SWs have no AudioContext).
    await chrome.runtime.sendMessage({
      type: "antai-capture-start",
      streamId,
      tabId,
      cfg,
    });
    // Inject HUD into the protected tab (styles are inlined in its shadow root).
    await chrome.scripting.executeScript({
      target: { tabId },
      files: ["shared/risk.js", "content/hud.js"],
    });
  } catch (e) {
    state.running = false;
    state.connection = "error";
    state.error = e.message || String(e);
    broadcast();
  }
}

async function stopCapture(reason) {
  try {
    await chrome.runtime.sendMessage({ type: "antai-capture-stop", reason });
  } catch {}
  if (state.tabId != null) {
    chrome.tabs.sendMessage(state.tabId, { type: "antai-hud-remove" }).catch(() => {});
  }
  state.running = false;
  state.connection = "closed";
  state.error = reason === "stopped" ? null : reason;
  broadcast();
  if (chrome.offscreen.closeDocument && (await hasOffscreen())) {
    try {
      await chrome.offscreen.closeDocument();
    } catch {}
  }
}

// ── Notifications (port of windows_client/notifications.py) ───────────────────
function maybeNotify(nr, cfg) {
  const risk = Number(nr.risk);
  if (isNaN(risk)) return;
  const band = nr.band || "passive";
  // suppress: below verify threshold and passive
  if (risk < cfg.verifyAt && band === "passive") return;
  const now = Date.now();
  if (now < state.verifiedUntil) return; // user pressed "I verified" — snooze
  if (now - state.lastNotified < cfg.cooldownSec * 1000) return; // cooldown port
  state.lastNotified = now;
  const critical = band === "critical" || risk >= cfg.criticalAt;
  const title = critical
    ? "antAI — HIGH RISK DETECTED"
    : "antAI — Risk Elevated";
  const message =
    nr.recommendation ||
    (critical
      ? "Do not share credentials or authorize any request."
      : "Verify the caller before proceeding.");
  chrome.notifications.create({
    type: "basic",
    iconUrl: chrome.runtime.getURL("icons/icon128.png"),
    title,
    message: String(message).slice(0, 256),
    priority: critical ? 2 : 1,
  });
}

// ── Message router ────────────────────────────────────────────────────────────
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    switch (msg && msg.type) {
      case "antai-start":
        await startCapture(msg.tabId);
        sendResponse({ ok: !state.error });
        break;
      case "antai-stop":
        await stopCapture("stopped");
        sendResponse({ ok: true });
        break;
      case "antai-get-state":
        sendResponse({ ...state });
        break;
      case "antai-verify":
        state.verifiedUntil = Date.now() + 120000; // 2-minute snooze
        broadcast();
        sendResponse({ ok: true });
        break;
      case "antai-refresh-models":
        await refreshModels();
        sendResponse({ ok: true });
        break;
      case "antai-update": {
        // from offscreen: a normalized_result update/final from the server
        state.latest = msg.data;
        const cfg = await getConfig();
        maybeNotify(msg.data, cfg);
        broadcast();
        break;
      }
      case "antai-ws-status":
        state.connection = msg.status;
        state.error = msg.error || null;
        broadcast();
        break;
      case "antai-hud-stop":
        await stopCapture("stopped from HUD");
        break;
      case "antai-scan-text": {
        // Page-text scan → POST /api/notify/external (Bearer auth, real
        // agentic verdict — never fabricated client-side).
        const cfg = await getConfig();
        if (!cfg.token) {
          sendResponse({ ok: false, error: "Sign in first — Options → Account." });
          break;
        }
        try {
          const res = await fetch(`${httpBase(cfg.serverHost)}/api/notify/external`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${cfg.token}`,
            },
            body: JSON.stringify({ source: "other", sender: "page-scan", text: msg.text }),
          });
          if (res.status === 401) {
            sendResponse({ ok: false, error: "Session expired — sign in again in Options." });
            break;
          }
          sendResponse({ ok: res.ok, data: await res.json().catch(() => null) });
        } catch {
          sendResponse({ ok: false, error: "Could not reach the antAI server." });
        }
        break;
      }
      case "antai-signout":
        await chrome.storage.local.remove("antaiToken");
        broadcast();
        sendResponse({ ok: true });
        break;
    }
  })();
  return true; // async sendResponse
});

chrome.tabs.onRemoved.addListener((tabId) => {
  if (state.running && tabId === state.tabId) stopCapture("tab closed");
});

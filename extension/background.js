// antAI Guardian — MV3 background service worker.
// Owns: capture lifecycle (tabCapture → offscreen document), state cache,
// chrome.notifications with the ported 15s cooldown, HUD injection, broadcast.
//
// Protocol & semantics ported from windows_client/ (notifications.py cooldown,
// ui_overlay.py band thresholds). Every rendered number comes from the server's
// normalized_result — nothing is fabricated here.

importScripts("shared/risk.js", "shared/platform.js", "shared/incident.js");

const DEFAULTS = {
  serverHost: "localhost:8765",
  scenario: "high_value_txn", // routine_call | high_value_txn | privileged_access
  verifyAt: 50,
  criticalAt: 70,
  cooldownSec: 15,
  passThroughAudio: true,
};

// ── State (survives SW restarts because it's cheap to rebuild from offscreen) ──
const state = {
  running: false,
  tabId: null,
  connection: "closed", // connecting | open | closed | error
  error: null,
  latest: null, // last normalized_result (popup shows this when WS is down)
  latestText: null, // last page-text scan verdict {risk, band, recommendation, scamType, ts}
  textScan: { active: false, tabId: null }, // continuous page-text scanner target
  models: null, // {ready: bool, detail: {name: bool}}
  verifiedUntil: 0, // "I verified" snooze (ms epoch)
  lastNotified: 0, // cooldown bookkeeping (notifications.py port)
  audioLevel: 0, // latest audio rms level from offscreen
  platform: null, // current platform info {id, name, icon, isMeeting}
  sessionKey: null, // session identifier for active capture
};

async function getConfig() {
  const stored = await chrome.storage.sync.get(DEFAULTS);
  const { antaiToken } = await chrome.storage.local.get({ antaiToken: "" });
  return { ...DEFAULTS, ...stored, token: antaiToken };
}

// REST base for a host that may carry wss:// (TLS-proxied) — keeps scheme.
function httpBase(host) {
  const raw = String(host || "").trim();
  const isLocal = raw.includes("localhost") || raw.includes("127.0.0.1");
  const secure = /^wss:\/\//.test(raw) || /^https:\/\//.test(raw) || !isLocal;
  const h = raw.replace(/^https?:\/\//, "").replace(/^wss?:\/\//, "").replace(/\/+$/, "");
  return `${secure ? "https" : "http"}://${h}`;
}


// ── Incidents ring buffer (up to 50 items) ────────────────────────────────────
async function getIncidents() {
  try {
    const { incidents } = await chrome.storage.local.get({ incidents: [] });
    return Array.isArray(incidents) ? incidents : [];
  } catch {
    return [];
  }
}

async function saveIncident(inc) {
  if (!inc) return;
  try {
    const incidents = await getIncidents();
    const existingIdx = incidents.findIndex(
      (i) => i.sessionKey && inc.sessionKey && i.sessionKey === inc.sessionKey
    );
    if (existingIdx >= 0) {
      if (inc.risk >= (incidents[existingIdx].risk || 0)) {
        incidents[existingIdx] = { ...incidents[existingIdx], ...inc, id: incidents[existingIdx].id };
      }
    } else {
      incidents.unshift(inc);
    }
    const trimmed = incidents.slice(0, 50);
    await chrome.storage.local.set({ incidents: trimmed });
  } catch {}
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
        latestText: state.latestText,
        textScan: state.textScan,
        models: state.models,
        verifiedUntil: state.verifiedUntil,
        lastNotified: state.lastNotified,
        audioLevel: state.audioLevel,
        platform: state.platform,
        sessionKey: state.sessionKey,
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

  let platformInfo = null;
  let platformContext = null;
  try {
    const tab = await chrome.tabs.get(tabId);
    platformInfo = AntaiPlatform.detectPlatform(tab?.url, tab?.title);
    platformContext = AntaiPlatform.formatStartContext(platformInfo, tab);
  } catch {
    platformInfo = AntaiPlatform.detectPlatform(null, null);
    platformContext = AntaiPlatform.formatStartContext(platformInfo, null);
  }

  state.running = true;
  state.tabId = tabId;
  state.error = null;
  state.latest = null;
  state.audioLevel = 0;
  state.platform = platformInfo;
  state.sessionKey = `call_${tabId}_${Date.now()}`;
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
      platform: platformContext,
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
  state.audioLevel = 0;
  state.sessionKey = null;
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

        // Auto-save incident when risk >= verifyAt or band in ['verify', 'critical']
        const risk = Number(msg.data.risk);
        const band = String(msg.data.band || "").toLowerCase();
        if ((isFinite(risk) && risk >= cfg.verifyAt) || band === "verify" || band === "critical") {
          const platformName = (state.platform && state.platform.name) || "Web Page";
          const inc = AntaiIncident.createIncident({
            type: "audio",
            platform: platformName,
            risk: isFinite(risk) ? risk : 0,
            band,
            scamType: msg.data.scam_type || (band === "critical" ? "Deepfake / Voice Scam" : null),
            signals: msg.data,
            recommendation: msg.data.recommendation || "",
            sessionKey: state.sessionKey || `call_${state.tabId}`,
          });
          await saveIncident(inc);
        }

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
      case "antai-audio-meter":
        state.audioLevel = typeof msg.level === "number" ? msg.level : 0;
        broadcast();
        break;
      case "antai-get-incidents": {
        const incidents = await getIncidents();
        sendResponse({ ok: true, incidents });
        break;
      }
      case "antai-clear-incidents": {
        await chrome.storage.local.set({ incidents: [] });
        sendResponse({ ok: true });
        break;
      }
      case "antai-copy-fir": {
        let inc = msg.incident;
        if (!inc) {
          if (state.latest && (state.latest.risk || state.latest.band)) {
            inc = AntaiIncident.createIncident({
              type: "audio",
              platform: (state.platform && state.platform.name) || "Web Page",
              risk: state.latest.risk,
              band: state.latest.band,
              scamType: state.latest.scam_type || null,
              signals: state.latest,
              recommendation: state.latest.recommendation,
              sessionKey: state.sessionKey,
            });
          } else if (state.latestText) {
            inc = AntaiIncident.createIncident({
              type: "text",
              platform: "Web Page",
              risk: state.latestText.risk,
              band: state.latestText.band,
              scamType: state.latestText.scamType,
              signals: { prosody: { scam_prob: state.latestText.risk / 100 } },
              recommendation: state.latestText.recommendation,
            });
          }
        }
        const text = inc ? AntaiIncident.formatFirDraft(inc) : "";
        sendResponse({ ok: true, text, incident: inc });
        break;
      }
      case "antai-scan-text": {
        // Page-text scan → POST /api/notify/external (Bearer auth, real
        // agentic verdict — never fabricated client-side). Used by BOTH the
        // popup one-shot button and the continuous content scanner.
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
          const data = await res.json().catch(() => null);
          // A REAL, ingested verdict with a numeric risk surfaces through the
          // same notification (15s cooldown) + HUD path as the audio tap, so a
          // continuous scan raises an alert without the popup being open. A
          // keyword-gated benign message (ingested:false) is NOT an alert — that
          // is an honest "no scam signal", never a fabricated safe verdict.
          if (data && data.ingested === true && isFinite(Number(data.risk_score))) {
            const v = data.verdict || {};
            const nr = {
              risk: Number(data.risk_score),
              band: v.band || null,
              recommendation: v.action || v.why || v.verdict || null,
            };
            state.latestText = { ...nr, scamType: v.scam_type || null, ts: Date.now() };
            maybeNotify(nr, cfg);

            const risk = Number(data.risk_score);
            const band = String(v.band || "").toLowerCase();
            if ((isFinite(risk) && risk >= cfg.verifyAt) || band === "verify" || band === "critical") {
              let tabPlatform = "Web Page";
              if (sender?.tab) {
                tabPlatform = AntaiPlatform.detectPlatform(sender.tab.url, sender.tab.title).name;
              } else if (msg.tabId) {
                try {
                  const t = await chrome.tabs.get(msg.tabId);
                  tabPlatform = AntaiPlatform.detectPlatform(t?.url, t?.title).name;
                } catch {}
              }
              const inc = AntaiIncident.createIncident({
                type: "text",
                platform: tabPlatform,
                risk: isFinite(risk) ? risk : 0,
                band,
                scamType: v.scam_type || "Page Text Scam",
                signals: {
                  prosody: {
                    scam_prob: risk / 100,
                    urgency: v.urgency || null,
                  },
                },
                recommendation: nr.recommendation || "",
                sessionKey: `textscan_${Date.now()}`,
              });
              await saveIncident(inc);
            }

            broadcast();
          }
          sendResponse({ ok: res.ok, data });
        } catch {
          sendResponse({ ok: false, error: "Could not reach the antAI server." });
        }
        break;
      }
      case "antai-textscan-toggle": {
        // Opt-in continuous page-text scanner. Injected on a user gesture from
        // the popup (activeTab) — same mechanism as the HUD injection. Off →
        // tell the content script to disconnect its observer.
        const tabId = msg.tabId;
        const on = !(state.textScan.active && state.textScan.tabId === tabId);
        if (!on) {
          try { chrome.tabs.sendMessage(tabId, { type: "antai-textscan-stop" }); } catch {}
          state.textScan = { active: false, tabId: null };
          broadcast();
          sendResponse({ ok: true, active: false });
          break;
        }
        const cfg = await getConfig();
        if (!cfg.token) {
          sendResponse({ ok: false, error: "Sign in first — Options → Account." });
          break;
        }
        // If it was watching another tab, stop that one first.
        if (state.textScan.active && state.textScan.tabId != null && state.textScan.tabId !== tabId) {
          chrome.tabs.sendMessage(state.textScan.tabId, { type: "antai-textscan-stop" }).catch(() => {});
        }
        try {
          await chrome.scripting.executeScript({
            target: { tabId },
            files: ["content/textscan_core.js", "content/textscan.js"],
          });
          state.textScan = { active: true, tabId };
          broadcast();
          sendResponse({ ok: true, active: true });
        } catch (e) {
          sendResponse({ ok: false, error: e.message || String(e) });
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
  if (state.textScan.active && tabId === state.textScan.tabId) {
    state.textScan = { active: false, tabId: null };
    broadcast();
  }
});

// A navigation/reload drops the injected content script, so the scanner is no
// longer running even though we think it is — reflect that honestly.
chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status === "loading" && state.textScan.active && tabId === state.textScan.tabId) {
    state.textScan = { active: false, tabId: null };
    broadcast();
  }
});

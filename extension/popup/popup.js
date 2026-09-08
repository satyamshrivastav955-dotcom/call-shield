// antAI popup — start/stop protection for current tab, meeting detection banner,
// audio level visualizer, incident history & 1-click 1930 FIR draft copy, bilingual polish.

const ARC_LEN = Math.PI * 50;
const $ = (id) => document.getElementById(id);

let currentTabId = null;
let currentTabPlatform = null;
let running = false;

function updateAudioVisualizer(level, isLive) {
  const visualizer = $("audio-visualizer");
  if (!visualizer) return;
  if (!isLive) {
    visualizer.hidden = true;
    return;
  }
  visualizer.hidden = false;
  const fill = $("meter-bar-fill");
  if (fill) {
    const l = Number(level) || 0;
    const pct = Math.min(100, Math.max(12, Math.round(l * 600)));
    fill.style.width = `${pct}%`;
  }
}

function updateMeetingBanner(platform, isRunning) {
  const banner = $("meeting-banner");
  if (!banner) return;
  if (!platform || !platform.isMeeting) {
    banner.hidden = true;
    return;
  }
  banner.hidden = false;
  const iconEl = $("meeting-icon");
  const titleEl = $("meeting-title");
  const btnEl = $("meeting-protect-btn");

  iconEl.textContent = platform.icon || "📹";
  if (isRunning) {
    titleEl.textContent = `${platform.name} protected / कॉल सुरक्षित है ✓`;
    btnEl.hidden = true;
  } else {
    titleEl.textContent = `${platform.name} call detected / कॉल पहचानी गई`;
    btnEl.hidden = false;
    btnEl.textContent = `Protect this call / कॉल सुरक्षित करें`;
  }
}

async function loadPopupIncidents() {
  const listEl = $("popup-incidents-list");
  const countEl = $("popup-incidents-count");
  if (!listEl || !countEl) return;

  const resp = await new Promise((resolve) =>
    chrome.runtime.sendMessage({ type: "antai-get-incidents" }, resolve)
  ).catch(() => null);

  const incidents = (resp && resp.incidents) || [];
  countEl.textContent = String(incidents.length);

  if (!incidents.length) {
    listEl.innerHTML = `<p class="popup-empty-hint">No incidents recorded / कोई घटना दर्ज नहीं</p>`;
    return;
  }

  listEl.innerHTML = "";
  // Show up to 5 most recent incidents
  for (const inc of incidents.slice(0, 5)) {
    const item = document.createElement("div");
    const band = String(inc.band || "verify").toLowerCase();
    item.className = `popup-incident-item ${band === "critical" ? "critical" : "verify"}`;

    const timeStr = typeof AntaiIncident !== "undefined" ? AntaiIncident.formatIST(inc.timestamp) : new Date(inc.timestamp).toLocaleTimeString();
    const typeIcon = inc.type === "text" ? "💬" : "📹";
    const riskColor = band === "critical" ? "#dc2626" : "#ca8a04";

    item.innerHTML = `
      <div class="popup-incident-header">
        <span class="popup-incident-plat">${typeIcon} ${inc.platform || "Web Page"}</span>
        <span class="popup-incident-time">${timeStr}</span>
      </div>
      <div class="popup-incident-meta">
        <span style="color:${riskColor};font-weight:700">Risk: ${inc.risk || 0}/100</span>
        <span class="popup-incident-scam">${inc.scamType || "Suspicious Signal"}</span>
      </div>
      <button type="button" class="popup-fir-btn">Copy 1930 FIR Draft / 1930 एफआईआर कॉपी करें</button>
    `;

    const firBtn = item.querySelector(".popup-fir-btn");
    firBtn.addEventListener("click", async () => {
      if (typeof AntaiIncident !== "undefined") {
        await AntaiIncident.copyFirToClipboard(inc);
      } else {
        await navigator.clipboard.writeText(JSON.stringify(inc, null, 2));
      }
      firBtn.textContent = "Copied! / कॉपी हुआ ✓";
      setTimeout(() => {
        firBtn.textContent = "Copy 1930 FIR Draft / 1930 एफआईआर कॉपी करें";
      }, 1500);
    });

    listEl.appendChild(item);
  }
}

function render(state) {
  running = state.running && state.tabId === currentTabId;
  const nr = state.latest;
  const risk = nr ? Number(nr.risk) : NaN;
  const info = antaiBandInfo(isNaN(risk) ? null : risk, nr ? nr.band : null);

  $("num").textContent = nr ? antaiFmtRisk(risk) : "—";
  $("num").style.fill = info.color;
  $("arc").style.strokeDasharray = `${ARC_LEN * (isNaN(risk) ? 0 : Math.max(0, Math.min(100, risk)) / 100)} ${ARC_LEN}`;
  $("arc").style.stroke = info.color;

  const badge = $("badge");
  badge.textContent = info.icon + " " + info.label;
  badge.style.color = info.color;
  badge.style.borderColor = info.color;

  const rows = $("rows");
  rows.innerHTML = "";
  for (const s of antaiSignals(nr)) {
    const row = document.createElement("div");
    row.className = "row";
    const l = document.createElement("span");
    l.className = "row-label";
    l.textContent = s.label;
    const v = document.createElement("span");
    v.className = "row-value" + (s.value === "—" ? " na" : "");
    v.textContent = s.value;
    row.append(l, v);
    rows.appendChild(row);
  }

  $("rec-text").textContent =
    nr && nr.recommendation
      ? nr.recommendation
      : "No analysis yet / अभी तक कोई विश्लेषण नहीं।";

  // models indicator — "unavailable" when unreachable, never fake-ready
  const m = $("models");
  if (state.models === null) {
    m.textContent = "models: unreachable";
    m.className = "models na";
  } else if (state.models.ready) {
    m.textContent = "models: ready";
    m.className = "models ok";
  } else {
    const down = Object.entries(state.models.detail || {})
      .filter(([, v]) => !v)
      .map(([k]) => k);
    m.textContent = "models: unavailable" + (down.length ? ` (${down.join(", ")})` : "");
    m.className = "models warn";
  }

  // Meeting Detection Banner
  updateMeetingBanner(currentTabPlatform, running);

  // Audio Level Visualizer
  updateAudioVisualizer(state.audioLevel, running);

  // Status line + toggle
  const btn = $("toggle");
  const err = $("error");
  if (state.error) {
    err.textContent = "⚠ " + state.error + " — check the server host in Options.";
    err.hidden = false;
  } else {
    err.hidden = true;
  }

  const platName = (currentTabPlatform && currentTabPlatform.name) || "this tab";
  if (running) {
    const conn =
      {
        open: "live / सक्रिय",
        connecting: "connecting… / कनेक्ट हो रहा है",
        closed: "offline — last result / ऑफ़लाइन",
        error: "connection error / त्रुटि",
      }[state.connection] || "";
    $("status-line").textContent = `Protecting ${platName} · ${conn}`;
    btn.textContent = "Stop protection / सुरक्षा रोकें";
    btn.className = "danger";
  } else {
    $("status-line").textContent = state.latest
      ? "Not protecting — last result shown / अंतिम परिणाम दिखाया गया"
      : "Not protecting any tab / कोई टैब सुरक्षित नहीं";
    btn.textContent =
      currentTabPlatform && currentTabPlatform.isMeeting
        ? `Protect ${platName} / कॉल सुरक्षित करें`
        : "Protect this tab / टैब सुरक्षित करें";
    btn.className = "primary";
  }

  // Continuous page-text scanner toggle (per-tab, opt-in)
  const watch = $("watch");
  if (watch) {
    const watching = !!(state.textScan && state.textScan.active && state.textScan.tabId === currentTabId);
    watch.textContent = watching
      ? "Stop watching page text / टेक्स्ट निगरानी रोकें"
      : "Watch page text (live) / लाइव टेक्स्ट निगरानी";
    watch.className = watching ? "danger" : "secondary";
  }

  // Refresh incident history
  loadPopupIncidents();
}

function refresh() {
  chrome.runtime.sendMessage({ type: "antai-get-state" }, (state) => {
    if (state) render(state);
  });
}

document.addEventListener("DOMContentLoaded", async () => {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  currentTabId = tab ? tab.id : null;
  if (typeof AntaiPlatform !== "undefined") {
    currentTabPlatform = AntaiPlatform.detectPlatform(tab && tab.url, tab && tab.title);
  }

  $("toggle").addEventListener("click", () => {
    const type = running ? "antai-stop" : "antai-start";
    chrome.runtime.sendMessage({ type, tabId: currentTabId }, refresh);
  });

  const meetingBtn = $("meeting-protect-btn");
  if (meetingBtn) {
    meetingBtn.addEventListener("click", () => {
      chrome.runtime.sendMessage({ type: "antai-start", tabId: currentTabId }, refresh);
    });
  }

  $("options-link").addEventListener("click", (e) => {
    e.preventDefault();
    chrome.runtime.openOptionsPage();
  });

  // ── Continuous page-text scanner: inject/remove on this tab (opt-in) ──────
  $("watch").addEventListener("click", () => {
    const w = $("watch");
    w.disabled = true;
    chrome.runtime.sendMessage({ type: "antai-textscan-toggle", tabId: currentTabId }, (resp) => {
      w.disabled = false;
      if (resp && !resp.ok && resp.error) {
        const box = $("scan-result");
        box.textContent = "⚠ " + resp.error;
        box.className = "scan-result warn";
        box.hidden = false;
      }
      refresh();
    });
  });

  // ── Page-text scan → server verdict (POST /api/notify/external) ──────────
  $("scan").addEventListener("click", async () => {
    const box = $("scan-result");
    const btn = $("scan");
    btn.disabled = true;
    btn.textContent = "Scanning… / जांच जारी है…";
    box.hidden = true;
    try {
      const [{ result: text } = {}] = await chrome.scripting.executeScript({
        target: { tabId: currentTabId },
        func: () => {
          const sel = String(window.getSelection() || "");
          const t = sel.trim() ? sel : document.body.innerText;
          return t.replace(/\s+/g, " ").trim().slice(0, 2000);
        },
      });
      if (!text) {
        box.textContent = "No text found on this page / इस पेज पर कोई टेक्स्ट नहीं मिला।";
        box.hidden = false;
        return;
      }
      const resp = await new Promise((resolve) =>
        chrome.runtime.sendMessage({ type: "antai-scan-text", text, tabId: currentTabId }, resolve)
      );
      if (!resp || !resp.ok) {
        box.textContent = "⚠ " + ((resp && resp.error) || "Scan failed.");
        box.className = "scan-result warn";
      } else {
        renderScanResult(resp.data);
      }
      box.hidden = false;
    } catch (e) {
      box.textContent = "⚠ " + (e.message || "Scan failed.");
      box.className = "scan-result warn";
      box.hidden = false;
    } finally {
      btn.disabled = false;
      btn.textContent = "Scan text on this page / पेज टेक्स्ट स्कैन करें";
    }
  });

  function renderScanResult(data) {
    const box = $("scan-result");
    if (!data) {
      box.textContent = "No response from the server.";
      box.className = "scan-result warn";
      return;
    }
    if (data.ingested === false) {
      box.textContent = "✓ Not flagged — no scam-related language detected / सुरक्षित — कोई संदिग्ध भाषा नहीं।";
      box.className = "scan-result ok";
      box.style.color = "";
      box.style.borderColor = "";
      return;
    }
    const risk = Number(data.risk_score);
    const v = data.verdict || {};
    const band = v.band || (risk >= 70 ? "critical" : risk >= 50 ? "verify" : "passive");
    const info = antaiBandInfo(isNaN(risk) ? null : risk, band);
    const lines = [`${info.icon} Risk ${antaiFmtRisk(isNaN(risk) ? null : risk)}/100 — ${info.label}`];
    if (v.verdict) lines.push(v.verdict);
    if (v.why) lines.push(v.why);
    if (v.action) lines.push("→ " + v.action);
    if (!v.verdict && !v.why && !v.action) lines.push("Analyzed — no actionable risk found / कोई सीधा खतरा नहीं मिला।");
    box.textContent = lines.join("\n");
    box.className = "scan-result";
    box.style.color = info.color;
    box.style.borderColor = info.color;
  }

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.type === "antai-state") render(msg.state);
    if (msg?.type === "antai-audio-meter") updateAudioVisualizer(msg.level, running);
  });

  chrome.runtime.sendMessage({ type: "antai-refresh-models" });
  refresh();
  loadPopupIncidents();
});

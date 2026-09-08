// antAI popup — start/stop protection for the current tab, gauge + signal rows
// + recommendation (last-known state when the WS is down, with a reason line).

const ARC_LEN = Math.PI * 50;
const $ = (id) => document.getElementById(id);

let currentTabId = null;
let running = false;

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

  $("rec-text").textContent = nr && nr.recommendation ? nr.recommendation : "No analysis yet.";

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

  // status line + toggle
  const btn = $("toggle");
  const err = $("error");
  if (state.error) {
    err.textContent = "⚠ " + state.error + " — check the server host in Options.";
    err.hidden = false;
  } else {
    err.hidden = true;
  }
  if (running) {
    const conn =
      { open: "live", connecting: "connecting…", closed: "offline — showing last result", error: "connection error — showing last result" }[state.connection] || "";
    $("status-line").textContent = `Protecting this tab · ${conn}`;
    btn.textContent = "Stop protection";
    btn.className = "danger";
  } else {
    $("status-line").textContent = state.latest ? "Not protecting — last result shown" : "Not protecting any tab";
    btn.textContent = "Protect this tab";
    btn.className = "primary";
  }

  // continuous page-text scanner toggle (per-tab, opt-in)
  const watch = $("watch");
  if (watch) {
    const watching = !!(state.textScan && state.textScan.active && state.textScan.tabId === currentTabId);
    watch.textContent = watching ? "Stop watching page text" : "Watch page text (live)";
    watch.className = watching ? "danger" : "secondary";
  }
}

function refresh() {
  chrome.runtime.sendMessage({ type: "antai-get-state" }, (state) => {
    if (state) render(state);
  });
}

document.addEventListener("DOMContentLoaded", async () => {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  currentTabId = tab ? tab.id : null;

  $("toggle").addEventListener("click", () => {
    const type = running ? "antai-stop" : "antai-start";
    chrome.runtime.sendMessage({ type, tabId: currentTabId }, refresh);
  });
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
    btn.textContent = "Scanning…";
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
        box.textContent = "No text found on this page.";
        box.hidden = false;
        return;
      }
      const resp = await new Promise((resolve) =>
        chrome.runtime.sendMessage({ type: "antai-scan-text", text }, resolve)
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
      btn.textContent = "Scan text on this page";
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
      // Keyword gate dropped it — genuinely not scam-related language.
      box.textContent = "✓ Not flagged — no scam-related language detected.";
      box.className = "scan-result ok";
      box.style.color = "";
      box.style.borderColor = "";
      return;
    }
    // risk_score is 0–100; verdict is {band, verdict, why, action} or null
    // for benign messages (server gates — never fabricate one client-side).
    const risk = Number(data.risk_score);
    const v = data.verdict || {};
    const band = v.band || (risk >= 70 ? "critical" : risk >= 50 ? "verify" : "passive");
    const info = antaiBandInfo(isNaN(risk) ? null : risk, band);
    const lines = [`${info.icon} Risk ${antaiFmtRisk(isNaN(risk) ? null : risk)}/100 — ${info.label}`];
    if (v.verdict) lines.push(v.verdict);
    if (v.why) lines.push(v.why);
    if (v.action) lines.push("→ " + v.action);
    if (!v.verdict && !v.why && !v.action) lines.push("Analyzed — no actionable risk found.");
    box.textContent = lines.join("\n");
    box.className = "scan-result";
    box.style.color = info.color;
    box.style.borderColor = info.color;
  }

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.type === "antai-state") render(msg.state);
  });

  chrome.runtime.sendMessage({ type: "antai-refresh-models" });
  refresh();
});

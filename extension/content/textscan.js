// antAI Guardian — continuous page-text scanner (content script).
//
// Watches the tab for NEW readable text — chat messages, live captions, incoming
// mail — and streams each new snippet to the antAI server for a REAL agentic
// verdict through the background `antai-scan-text` route (POST /api/notify/external,
// bearer-auth). This script scores NOTHING and fabricates NOTHING: it only decides
// WHICH text to send (via AntaiTextScanCore) and dedupes / rate-limits it. Every
// risk number comes from the server; elevated verdicts surface through the same
// background notification + HUD path as the audio tap (shared 15s cooldown).
//
// PRIVACY: opt-in only. Injected on demand from the popup "Watch page text" toggle
// (a user gesture → activeTab) — never declared as an auto-run content script. It
// skips the antAI HUD, script/style nodes, and any field the user is editing, so
// it does not exfiltrate what you are typing. Requires content/textscan_core.js
// (AntaiTextScanCore) to be injected first.

(() => {
  if (window.__antaiTextScan) return; // re-injection guard
  const Core = window.AntaiTextScanCore;
  if (!Core) {
    console.warn("[antAI text-scan] core helper missing — scanner not started.");
    return;
  }
  window.__antaiTextScan = true;

  const DEBOUNCE_MS = 1200;
  const gate = Core.createGate();
  let timer = null;
  let pending = [];
  let stopped = false;

  // Skip our own HUD, script/style, and anything the user is editing/typing.
  function skip(node) {
    const el = node.nodeType === 1 ? node : node.parentElement;
    if (!el) return true;
    if (el.closest && el.closest("#antai-hud-host")) return true;
    const tag = el.tagName;
    if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT" || tag === "TEXTAREA") return true;
    if (el.isContentEditable) return true;
    if (el.closest && el.closest('input, textarea, [contenteditable=""], [contenteditable="true"]')) return true;
    return false;
  }

  function collect(node) {
    if (skip(node)) return;
    const text = node.nodeType === 3 ? node.nodeValue : node.innerText || node.textContent;
    const norm = Core.normalize(text || "");
    if (norm) pending.push(norm);
  }

  function flush() {
    timer = null;
    if (stopped) return;
    const now = Date.now();
    const merged = pending.join(" \n ");
    pending = [];
    // normalize() already stripped internal newlines from each fragment, so
    // splitting on the join delimiter recovers per-message granularity.
    for (const piece of merged.split(/\n+/)) {
      const res = gate.accept(piece, now);
      if (res.send) send(res.send);
    }
  }

  function schedule() {
    if (timer || stopped) return;
    timer = setTimeout(flush, DEBOUNCE_MS);
  }

  function send(text) {
    try {
      chrome.runtime.sendMessage({ type: "antai-scan-text", text }, (resp) => {
        if (chrome.runtime.lastError) return; // SW asleep / page navigated
        logVerdict(text, resp);
      });
    } catch (_) {
      // Extension context invalidated (reload/upgrade) — stop quietly.
      stop();
    }
  }

  // Console evidence for the verification gate — one line per scanned snippet,
  // showing the REAL server verdict or the honest gate/skip reason. This is what
  // a human reads in DevTools to certify the continuous path end-to-end.
  function logVerdict(text, resp) {
    const clip = text.length > 60 ? text.slice(0, 57) + "…" : text;
    if (!resp || !resp.ok) {
      console.info("[antAI text-scan] sent, no verdict:", (resp && resp.error) || "unreachable", "·", clip);
      return;
    }
    const d = resp.data || {};
    if (d.ingested === false) {
      console.info("[antAI text-scan] ✓ not scam-related (gated) ·", clip);
      return;
    }
    const v = d.verdict || {};
    console.info(
      `[antAI text-scan] risk=${d.risk_score} band=${v.band || "?"} type=${v.scam_type || "?"} · ${clip}`
    );
  }

  // First pass: leaf-ish text blocks only, so page nav/boilerplate is largely
  // skipped and the gate's rate limit protects the server on text-heavy pages.
  function initialSweep() {
    const root = document.body;
    if (!root) return;
    root.querySelectorAll("p, li, span, div, td, blockquote, article, section, h1, h2, h3").forEach((el) => {
      if (el.children.length === 0) collect(el);
    });
    schedule();
  }

  const observer = new MutationObserver((mutations) => {
    if (stopped) return;
    for (const m of mutations) {
      if (m.type === "characterData") collect(m.target);
      else for (const n of m.addedNodes) collect(n);
    }
    schedule();
  });

  function start() {
    initialSweep();
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      characterData: true,
    });
    console.info("[antAI text-scan] watching this page for new text (opt-in). Verdicts come from your antAI server.");
  }

  function stop() {
    if (stopped) return;
    stopped = true;
    try { observer.disconnect(); } catch (_) {}
    if (timer) { clearTimeout(timer); timer = null; }
    window.__antaiTextScan = false;
    console.info("[antAI text-scan] stopped.");
  }

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg && msg.type === "antai-textscan-stop") stop();
  });

  start();
})();

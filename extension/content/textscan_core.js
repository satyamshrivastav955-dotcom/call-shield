// antAI Guardian — pure text-scan helpers (DOM-free, chrome-free).
//
// Defined as a global (same pattern as shared/risk.js) so ONE implementation is
// shared by the content script (content/textscan.js, injected into the page)
// and the Node unit test (test/textscan_test.mjs, loaded via node:vm). Keeping
// the decision logic here — normalization, candidacy, dedupe, rate-limit — means
// "which text gets sent to the server" is deterministic and testable without a
// browser. It scores NOTHING: every risk number comes from the server.

(function (root) {
  const DEFAULTS = {
    minChars: 12, // shorter than this is UI chrome / lone words → skip
    maxChars: 2000, // server slices anyway; cap the payload
    maxSnippetsPerMin: 20, // a chatty/animated page can't hammer the server
    dedupeWindow: 300, // remember this many recent snippet hashes
  };

  // Collapse all whitespace and trim (mirrors the popup one-shot scan).
  function normalize(text) {
    return String(text == null ? "" : text).replace(/\s+/g, " ").trim();
  }

  // Cheap 32-bit FNV-1a hash for dedupe identity — not crypto, just equality.
  function hash(s) {
    let h = 0x811c9dc5;
    for (let i = 0; i < s.length; i++) {
      h ^= s.charCodeAt(i);
      h = (h + ((h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24))) >>> 0;
    }
    return h >>> 0;
  }

  // Worth sending? Length-bounded AND contains at least one letter/digit, so
  // pure punctuation / emoji-only / separator lines are skipped.
  function isCandidate(norm, opts) {
    const o = opts || DEFAULTS;
    if (norm.length < o.minChars) return false;
    if (!/[\p{L}\p{N}]/u.test(norm)) return false;
    return true;
  }

  // Clamp to maxChars, preferring a word boundary in the last 40%.
  function clamp(norm, maxChars) {
    const max = maxChars || DEFAULTS.maxChars;
    if (norm.length <= max) return norm;
    const cut = norm.slice(0, max);
    const sp = cut.lastIndexOf(" ");
    return (sp > max * 0.6 ? cut.slice(0, sp) : cut).trim();
  }

  // Stateful dedupe + rate-limit gate. Time is INJECTED (nowMs) so the 60s
  // rate window is deterministic in tests. accept() returns { send, reason }:
  // send is the cleaned snippet to transmit, or null when it should be dropped.
  function createGate(opts) {
    const o = Object.assign({}, DEFAULTS, opts || {});
    const seen = new Set();
    const order = [];
    let windowStart = null;
    let countInWindow = 0;
    return {
      accept(raw, nowMs) {
        const norm = clamp(normalize(raw), o.maxChars);
        if (!isCandidate(norm, o)) return { send: null, reason: "not-candidate" };
        const h = hash(norm);
        if (seen.has(h)) return { send: null, reason: "duplicate" };
        if (windowStart === null || nowMs - windowStart >= 60000) {
          windowStart = nowMs;
          countInWindow = 0;
        }
        if (countInWindow >= o.maxSnippetsPerMin) return { send: null, reason: "rate-limited" };
        // commit
        seen.add(h);
        order.push(h);
        if (order.length > o.dedupeWindow) seen.delete(order.shift());
        countInWindow++;
        return { send: norm, reason: "ok" };
      },
      _seenSize() { return seen.size; },
    };
  }

  root.AntaiTextScanCore = { DEFAULTS, normalize, hash, isCandidate, clamp, createGate };
})(typeof globalThis !== "undefined" ? globalThis : this);

// antAI text-scan core — pure-logic unit tests (no browser, no server).
//
// Loads the SHIPPED content/textscan_core.js in a node:vm sandbox (the same file
// the extension injects into pages) and asserts the send/skip decisions:
// normalization, candidacy, clamping, dedupe, and the 60s rate limit. This is the
// deterministic verification gate for "which text gets sent" — the live page →
// real server verdict path is exercised separately by test/browser_test.mjs.
//
// Run: node extension/test/textscan_test.mjs   (exit 0 = all pass)

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));
const corePath = join(here, "..", "content", "textscan_core.js");

// Evaluate the global-style script in a fresh context; it assigns to globalThis.
const ctx = vm.createContext({});
vm.runInContext(readFileSync(corePath, "utf8"), ctx, { filename: corePath });
const Core = ctx.AntaiTextScanCore;

let pass = 0, fail = 0;
const ok = (c, name, detail) => {
  if (c) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name + (detail ? " — " + detail : "")); }
};

console.log("core loaded from shipped file");
ok(!!Core, "AntaiTextScanCore global is defined by content/textscan_core.js");
ok(typeof Core.createGate === "function", "createGate exported");

console.log("normalize / clamp / hash");
ok(Core.normalize("  a\n\t  b   c ") === "a b c", "normalize collapses all whitespace");
ok(Core.normalize(null) === "", "normalize null → empty string");
const long = "word ".repeat(1000).trim(); // ~5000 chars
const clamped = Core.clamp(long, 2000);
ok(clamped.length <= 2000, "clamp respects maxChars", String(clamped.length));
ok(!clamped.endsWith("wor") && !/\s$/.test(clamped), "clamp trims to a word boundary");
ok(Core.hash("hello") === Core.hash("hello"), "hash is stable for equal strings");
ok(Core.hash("hello") !== Core.hash("hellp"), "hash differs for different strings");

console.log("isCandidate");
ok(Core.isCandidate("Please verify your bank OTP now") === true, "normal sentence is a candidate");
ok(Core.isCandidate("ok") === false, "too-short text rejected (min length)");
ok(Core.isCandidate("!!! ??? ---") === false, "punctuation-only rejected (no letters/digits)");
ok(Core.isCandidate("नमस्ते खाता बंद हो जाएगा") === true, "unicode (Hindi) sentence is a candidate");

console.log("gate: dedupe");
{
  const g = Core.createGate();
  const t = "URGENT: your account will be blocked, share OTP now";
  ok(g.accept(t, 0).send === t, "first occurrence sent");
  ok(g.accept(t, 10).send === null, "exact duplicate suppressed");
  ok(g.accept("   URGENT:  your account   will be blocked, share OTP now  ", 20).send === null,
    "whitespace-variant duplicate suppressed (normalized before hashing)");
  ok(g.accept("hi", 30).reason === "not-candidate", "short text reason=not-candidate");
}

console.log("gate: rate limit (maxSnippetsPerMin) + window reset");
{
  const g = Core.createGate({ maxSnippetsPerMin: 3, dedupeWindow: 1000 });
  let sent = 0;
  for (let i = 0; i < 10; i++) if (g.accept("scam message number " + i, 100).send) sent++;
  ok(sent === 3, "no more than maxSnippetsPerMin sent within one 60s window", "sent=" + sent);
  const later = g.accept("scam message number 99", 100 + 60000);
  ok(later.send !== null, "window resets after 60s — sending resumes", later.reason);
}

console.log("gate: dedupe window eviction");
{
  const g = Core.createGate({ maxSnippetsPerMin: 100000, dedupeWindow: 2 });
  ok(g.accept("alpha one two three", 0).send !== null, "A sent");
  ok(g.accept("bravo one two three", 1).send !== null, "B sent");
  ok(g.accept("charlie one two three", 2).send !== null, "C sent (evicts A)");
  ok(g.accept("alpha one two three", 3).send !== null, "A sendable again after eviction");
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

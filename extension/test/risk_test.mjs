// antAI Guardian — Risk calculation, freeze decision, and formatting unit tests.
// Tests ANTAI_COLORS, antaiBandColor, antaiBandInfo, antaiFmtPct, antaiFmtRisk, antaiSignals, antaiIsFreeze, antaiAudioLevelPct.
// Boundaries tested: null, NaN, undefined, 0, 49, 50, 69, 70, 100, custom verifyAt/criticalAt.
//
// Run: node extension/test/risk_test.mjs (exit 0 = all pass)

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));
const riskPath = join(here, "..", "shared", "risk.js");

const ctx = vm.createContext({
  Math,
  Number,
  String,
  isNaN,
});
vm.runInContext(readFileSync(riskPath, "utf8"), ctx, { filename: riskPath });

let pass = 0,
  fail = 0;
const ok = (c, name, detail) => {
  if (c) {
    pass++;
    console.log("  ✓ " + name);
  } else {
    fail++;
    console.log("  ✗ " + name + (detail ? " — " + detail : ""));
  }
};

console.log("=== 1. shared/risk.js exports ===");
ok(!!ctx.ANTAI_COLORS, "ANTAI_COLORS is exported");
ok(typeof ctx.antaiBandColor === "function", "antaiBandColor is exported");
ok(typeof ctx.antaiBandInfo === "function", "antaiBandInfo is exported");
ok(typeof ctx.antaiFmtPct === "function", "antaiFmtPct is exported");
ok(typeof ctx.antaiFmtRisk === "function", "antaiFmtRisk is exported");
ok(typeof ctx.antaiSignals === "function", "antaiSignals is exported");
ok(typeof ctx.antaiIsFreeze === "function", "antaiIsFreeze is exported");
ok(typeof ctx.antaiAudioLevelPct === "function", "antaiAudioLevelPct is exported");

console.log("\n=== 2. antaiBandColor boundaries ===");
ok(ctx.antaiBandColor(null) === ctx.ANTAI_COLORS.muted, "null risk returns muted");
ok(ctx.antaiBandColor(undefined) === ctx.ANTAI_COLORS.muted, "undefined risk returns muted");
ok(ctx.antaiBandColor(NaN) === ctx.ANTAI_COLORS.muted, "NaN risk returns muted");
ok(ctx.antaiBandColor("invalid") === ctx.ANTAI_COLORS.muted, "'invalid' string returns muted");
ok(ctx.antaiBandColor(0) === ctx.ANTAI_COLORS.low, "0 returns low color");
ok(ctx.antaiBandColor(49) === ctx.ANTAI_COLORS.low, "49 returns low color (<50)");
ok(ctx.antaiBandColor(50) === ctx.ANTAI_COLORS.med, "50 returns med color (verifyAt)");
ok(ctx.antaiBandColor(69) === ctx.ANTAI_COLORS.med, "69 returns med color (<70)");
ok(ctx.antaiBandColor(70) === ctx.ANTAI_COLORS.high, "70 returns high color (criticalAt)");
ok(ctx.antaiBandColor(100) === ctx.ANTAI_COLORS.high, "100 returns high color");
// Custom thresholds
ok(ctx.antaiBandColor(59, 60, 80) === ctx.ANTAI_COLORS.low, "59 returns low (custom verifyAt=60)");
ok(ctx.antaiBandColor(60, 60, 80) === ctx.ANTAI_COLORS.med, "60 returns med (custom verifyAt=60)");
ok(ctx.antaiBandColor(79, 60, 80) === ctx.ANTAI_COLORS.med, "79 returns med (custom criticalAt=80)");
ok(ctx.antaiBandColor(80, 60, 80) === ctx.ANTAI_COLORS.high, "80 returns high (custom criticalAt=80)");

console.log("\n=== 3. antaiBandInfo boundaries ===");
{
  const bNull = ctx.antaiBandInfo(null);
  ok(bNull.label === "MONITORING" && bNull.icon === "●" && bNull.color === ctx.ANTAI_COLORS.muted, "null risk yields MONITORING ●");
  const bUndef = ctx.antaiBandInfo(undefined);
  ok(bUndef.label === "MONITORING", "undefined risk yields MONITORING");
  const bNaN = ctx.antaiBandInfo(NaN);
  ok(bNaN.label === "MONITORING", "NaN risk yields MONITORING");

  const b0 = ctx.antaiBandInfo(0, "passive");
  ok(b0.label === "SAFE" && b0.icon === "✓" && b0.color === ctx.ANTAI_COLORS.low, "0 risk yields SAFE ✓");
  const b49 = ctx.antaiBandInfo(49, "passive");
  ok(b49.label === "SAFE", "49 risk yields SAFE");
  const b50 = ctx.antaiBandInfo(50, "verify");
  ok(b50.label === "ELEVATED" && b50.icon === "↑" && b50.color === ctx.ANTAI_COLORS.med, "50 risk yields ELEVATED ↑");
  const b69 = ctx.antaiBandInfo(69, "verify");
  ok(b69.label === "ELEVATED", "69 risk yields ELEVATED");
  const b70 = ctx.antaiBandInfo(70, "critical");
  ok(b70.label === "HIGH RISK" && b70.icon === "⚠" && b70.color === ctx.ANTAI_COLORS.high, "70 risk yields HIGH RISK ⚠");
  const b100 = ctx.antaiBandInfo(100, "critical");
  ok(b100.label === "HIGH RISK", "100 risk yields HIGH RISK");

  // Band takes precedence even if numeric score is low
  const bCrit = ctx.antaiBandInfo(20, "critical");
  ok(bCrit.label === "HIGH RISK" && bCrit.color === ctx.ANTAI_COLORS.high, "band=critical overrides low score");
  const bVer = ctx.antaiBandInfo(20, "verify");
  ok(bVer.label === "ELEVATED" && bVer.color === ctx.ANTAI_COLORS.med, "band=verify overrides low score");

  // Custom thresholds
  const bCustLow = ctx.antaiBandInfo(39, null, 40, 60);
  ok(bCustLow.label === "SAFE", "39 -> SAFE (custom verifyAt=40)");
  const bCustMed = ctx.antaiBandInfo(40, null, 40, 60);
  ok(bCustMed.label === "ELEVATED", "40 -> ELEVATED (custom verifyAt=40)");
  const bCustHigh = ctx.antaiBandInfo(60, null, 40, 60);
  ok(bCustHigh.label === "HIGH RISK", "60 -> HIGH RISK (custom criticalAt=60)");
}

console.log("\n=== 4. antaiFmtPct & antaiFmtRisk boundaries ===");
ok(ctx.antaiFmtPct(null) === "—", "pct null -> '—'");
ok(ctx.antaiFmtPct(undefined) === "—", "pct undefined -> '—'");
ok(ctx.antaiFmtPct(NaN) === "—", "pct NaN -> '—'");
ok(ctx.antaiFmtPct("invalid") === "—", "invalid pct renders —");
ok(ctx.antaiFmtPct(0) === "0%", "pct 0 -> 0%");
ok(ctx.antaiFmtPct(0.49) === "49%", "pct 0.49 -> 49%");
ok(ctx.antaiFmtPct(0.50) === "50%", "pct 0.50 -> 50%");
ok(ctx.antaiFmtPct(0.69) === "69%", "pct 0.69 -> 69%");
ok(ctx.antaiFmtPct(0.70) === "70%", "pct 0.70 -> 70%");
ok(ctx.antaiFmtPct(0.85) === "85%", "pct 0.85 -> 85%");
ok(ctx.antaiFmtPct(1) === "100%", "pct 1 -> 100%");

ok(ctx.antaiFmtRisk(null) === "—", "risk null -> '—'");
ok(ctx.antaiFmtRisk(undefined) === "—", "risk undefined -> '—'");
ok(ctx.antaiFmtRisk(NaN) === "—", "risk NaN -> '—'");
ok(ctx.antaiFmtRisk("invalid") === "—", "risk 'invalid' -> '—'");
ok(ctx.antaiFmtRisk(0) === "0", "risk 0 -> '0'");
ok(ctx.antaiFmtRisk(49) === "49", "risk 49 -> '49'");
ok(ctx.antaiFmtRisk(50) === "50", "risk 50 -> '50'");
ok(ctx.antaiFmtRisk(69.4) === "69", "risk 69.4 rounds to 69");
ok(ctx.antaiFmtRisk(69.6) === "70", "risk 69.6 rounds to 70");
ok(ctx.antaiFmtRisk(70) === "70", "risk 70 -> '70'");
ok(ctx.antaiFmtRisk(100) === "100", "risk 100 -> '100'");

console.log("\n=== 5. antaiSignals ===");
ok(Array.isArray(ctx.antaiSignals(null)) && ctx.antaiSignals(null).length === 0, "null returns empty array");
ok(Array.isArray(ctx.antaiSignals(undefined)) && ctx.antaiSignals(undefined).length === 0, "undefined returns empty array");
{
  const emptySig = ctx.antaiSignals({});
  ok(emptySig.length === 3, "empty nr returns 3 signal slots");
  ok(emptySig[0].value === "—", "empty voice value is '—'");
  ok(emptySig[1].value === "—", "empty prosody value is '—'");
  ok(emptySig[2].value === "—", "empty voiceprint value is '—'");

  const sigs = ctx.antaiSignals({
    acoustic: { voice_deepfake: 0.91 },
    prosody: { scam_prob: 0.88, urgency: 4 },
    voiceprint: { similarity: 0.12 },
  });
  ok(sigs.length === 3, "returns 3 signal rows");
  ok(sigs[0].key === "voice" && sigs[0].value === "91%", "formats voice deepfake pct");
  ok(sigs[1].key === "prosody" && sigs[1].value === "Scam 88%", "formats scam prob pct");
  ok(sigs[2].key === "voiceprint" && sigs[2].value === "12%", "formats voiceprint similarity pct");

  const urgencySig = ctx.antaiSignals({
    prosody: { urgency: 85 },
  });
  ok(urgencySig[1].value === "Urgency 85", "prosody urgency fallback formatted Urgency 85");
}

console.log("\n=== 6. antaiIsFreeze boundaries ===");
// Null / undefined / NaN
ok(ctx.antaiIsFreeze(null, null) === false, "null risk & null band -> false");
ok(ctx.antaiIsFreeze(undefined, undefined) === false, "undefined risk & undefined band -> false");
ok(ctx.antaiIsFreeze(NaN, null) === false, "NaN risk -> false");
ok(ctx.antaiIsFreeze("invalid", null) === false, "'invalid' string risk -> false");

// Numeric boundaries with default thresholds (verifyAt=50, criticalAt=70)
ok(ctx.antaiIsFreeze(0, null) === false, "0 risk -> false");
ok(ctx.antaiIsFreeze(49, null) === false, "49 risk -> false (<50)");
ok(ctx.antaiIsFreeze(50, null) === false, "50 risk -> false (verifyAt, not freeze)");
ok(ctx.antaiIsFreeze(69, null) === false, "69 risk -> false (<70 criticalAt)");
ok(ctx.antaiIsFreeze(70, null) === true, "70 risk -> true (boundary: criticalAt)");
ok(ctx.antaiIsFreeze(71, null) === true, "71 risk -> true (>70)");
ok(ctx.antaiIsFreeze(100, null) === true, "100 risk -> true");

// String numeric inputs
ok(ctx.antaiIsFreeze("70", null) === true, "'70' string risk -> true");
ok(ctx.antaiIsFreeze("69.9", null) === false, "'69.9' string risk -> false");

// Critical band overrides numeric score
ok(ctx.antaiIsFreeze(null, "critical") === true, "band='critical' with null risk -> true");
ok(ctx.antaiIsFreeze(0, "critical") === true, "band='critical' with 0 risk -> true");
ok(ctx.antaiIsFreeze(49, "critical") === true, "band='critical' with 49 risk -> true");
ok(ctx.antaiIsFreeze(69, "critical") === true, "band='critical' with 69 risk -> true");

// Non-critical bands
ok(ctx.antaiIsFreeze(49, "verify") === false, "band='verify' with 49 risk -> false");
ok(ctx.antaiIsFreeze(70, "verify") === true, "band='verify' with 70 risk -> true (score reaches criticalAt)");
ok(ctx.antaiIsFreeze(49, "passive") === false, "band='passive' with 49 risk -> false");

// Custom verifyAt / criticalAt
ok(ctx.antaiIsFreeze(59, null, 40, 60) === false, "59 risk -> false (custom criticalAt=60)");
ok(ctx.antaiIsFreeze(60, null, 40, 60) === true, "60 risk -> true (custom criticalAt=60)");
ok(ctx.antaiIsFreeze(85, null, 50, 90) === false, "85 risk -> false (custom criticalAt=90)");
ok(ctx.antaiIsFreeze(90, null, 50, 90) === true, "90 risk -> true (custom criticalAt=90)");

console.log("\n=== 7. antaiAudioLevelPct boundaries ===");
ok(ctx.antaiAudioLevelPct(null) === "—", "audio null -> '—'");
ok(ctx.antaiAudioLevelPct(undefined) === "—", "audio undefined -> '—'");
ok(ctx.antaiAudioLevelPct(NaN) === "—", "audio NaN -> '—'");
ok(ctx.antaiAudioLevelPct("abc") === "—", "audio 'abc' -> '—'");
ok(ctx.antaiAudioLevelPct(0) === "0%", "audio 0 -> '0%'");
ok(ctx.antaiAudioLevelPct(0.49) === "49%", "audio 0.49 -> '49%'");
ok(ctx.antaiAudioLevelPct(0.50) === "50%", "audio 0.50 -> '50%'");
ok(ctx.antaiAudioLevelPct(0.69) === "69%", "audio 0.69 -> '69%'");
ok(ctx.antaiAudioLevelPct(0.70) === "70%", "audio 0.70 -> '70%'");
ok(ctx.antaiAudioLevelPct(1) === "100%", "audio 1.0 -> '100%'");
ok(ctx.antaiAudioLevelPct(50) === "50%", "audio 50 -> '50%'");
ok(ctx.antaiAudioLevelPct(100) === "100%", "audio 100 -> '100%'");
ok(ctx.antaiAudioLevelPct(-5) === "0%", "audio -5 clamped -> '0%'");
ok(ctx.antaiAudioLevelPct(125) === "100%", "audio 125 clamped -> '100%'");

console.log(`\n================================`);
console.log(`Results: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

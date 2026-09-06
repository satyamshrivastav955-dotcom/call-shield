// antAI shared risk helpers — loaded by background (importScripts), popup,
// options (<script>) and HUD (scripting.executeScript files list).
//
// Ported verbatim from windows_client/ui_overlay.py (_band_color) — same
// thresholds, same colors. Ground rule §0.2: null values render as "—",
// never 0 or a fake green.

const ANTAI_COLORS = {
  accent: "#0e7c7b",
  low: "#16a34a",
  med: "#ca8a04",
  high: "#dc2626",
  muted: "#6b7280",
  ink: "#f3f4f6",
  surface: "#242830",
};

// _band_color(risk, verify_at=50, critical_at=70)
function antaiBandColor(risk, verifyAt = 50, criticalAt = 70) {
  if (risk === null || risk === undefined || isNaN(risk)) return ANTAI_COLORS.muted;
  if (risk >= criticalAt) return ANTAI_COLORS.high;
  if (risk >= verifyAt) return ANTAI_COLORS.med;
  return ANTAI_COLORS.low;
}

// Badge text + icon + color (never color alone — §0 / B5).
function antaiBandInfo(risk, band, verifyAt = 50, criticalAt = 70) {
  if (risk === null || risk === undefined || isNaN(risk)) {
    return { color: ANTAI_COLORS.muted, label: "MONITORING", icon: "●" };
  }
  if (band === "critical" || risk >= criticalAt) {
    return { color: ANTAI_COLORS.high, label: "HIGH RISK", icon: "⚠" };
  }
  if (band === "verify" || risk >= verifyAt) {
    return { color: ANTAI_COLORS.med, label: "ELEVATED", icon: "↑" };
  }
  return { color: ANTAI_COLORS.low, label: "SAFE", icon: "✓" };
}

// Null-safe formatters (windows_client ui_overlay.py _pct / _fmt_risk)
function antaiFmtPct(v) {
  if (v === null || v === undefined) return "—";
  const f = Number(v);
  return isNaN(f) ? "—" : Math.round(f * 100) + "%";
}

function antaiFmtRisk(v) {
  if (v === null || v === undefined) return "—";
  const f = Number(v);
  return isNaN(f) ? "—" : String(Math.round(f));
}

// Three signal rows from normalized_result. Nulls stay null → rendered "—".
function antaiSignals(nr) {
  if (!nr) return [];
  const acoustic = nr.acoustic || {};
  const prosody = nr.prosody || {};
  const voiceprint = nr.voiceprint || {};
  const sp = prosody.scam_prob;
  const ug = prosody.urgency;
  let prosodyValue = "—";
  if (sp !== null && sp !== undefined) prosodyValue = "Scam " + antaiFmtPct(sp);
  else if (ug !== null && ug !== undefined && !isNaN(Number(ug))) prosodyValue = "Urgency " + Number(ug).toFixed(0);
  return [
    {
      key: "voice",
      label: "Voice Authenticity / आवाज़ प्रामाणिकता",
      value: antaiFmtPct(acoustic.voice_deepfake),
      raw: acoustic.voice_deepfake,
    },
    {
      key: "prosody",
      label: "Message Patterns / संदेश पैटर्न",
      value: prosodyValue,
      raw: sp !== undefined ? sp : ug,
    },
    {
      key: "voiceprint",
      label: "Speaker Identity / वक्ता पहचान",
      value: antaiFmtPct(voiceprint.similarity),
      raw: voiceprint.similarity,
    },
  ];
}

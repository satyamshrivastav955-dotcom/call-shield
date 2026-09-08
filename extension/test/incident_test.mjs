// antAI Guardian — Incident schema & FIR draft generator unit tests.
//
// Asserts incident creation, FIR complaint string formatting,
// 1930 helpline, cybercrime.gov.in portal URLs, and Hindi translations.
//
// Run: node extension/test/incident_test.mjs (exit 0 = all pass)

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));
const incidentPath = join(here, "..", "shared", "incident.js");

// Evaluate in VM context
const ctx = vm.createContext({
  URL,
  Date,
  Math,
  String,
  Number,
  Array,
  Object,
  navigator: { clipboard: { writeText: async (t) => t } },
});
vm.runInContext(readFileSync(incidentPath, "utf8"), ctx, { filename: incidentPath });
const Incident = ctx.AntaiIncident;

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

console.log("incident module loaded");
ok(!!Incident, "AntaiIncident global is defined by shared/incident.js");
ok(typeof Incident.createIncident === "function", "createIncident exported");
ok(typeof Incident.formatFirDraft === "function", "formatFirDraft exported");
ok(typeof Incident.copyFirToClipboard === "function", "copyFirToClipboard exported");
ok(typeof Incident.formatIST === "function", "formatIST exported");

console.log("\nIncident schema creation");
{
  const inc = Incident.createIncident();
  ok(typeof inc.id === "string" && inc.id.startsWith("inc_"), "generates unique id with prefix");
  ok(typeof inc.timestamp === "number" && inc.timestamp > 0, "generates numeric timestamp");
  ok(inc.type === "audio", "default type is audio");
  ok(inc.platform === "Web Page", "default platform is Web Page");
  ok(inc.risk === 0, "default risk is 0");
  ok(inc.band === "verify", "default band is verify");
  ok(inc.scamType === null, "default scamType is null");
  ok(typeof inc.signals === "object", "signals is an object");
  ok(typeof inc.recommendation === "string", "recommendation is a string");
  ok(typeof inc.sessionKey === "string", "sessionKey is generated");

  const custom = Incident.createIncident({
    id: "custom_id_123",
    timestamp: 1710000000000,
    type: "text",
    platform: "Google Meet",
    risk: 88.6,
    band: "CRITICAL",
    scamType: "Digital Arrest",
    recommendation: "Hang up immediately.",
    sessionKey: "sess_xyz",
    signals: {
      voice_deepfake: 0.95,
      scam_prob: 0.9,
      urgency: 5,
      similarity: 0.12,
    },
  });

  ok(custom.id === "custom_id_123", "preserves custom id");
  ok(custom.timestamp === 1710000000000, "preserves custom timestamp");
  ok(custom.type === "text", "preserves custom type");
  ok(custom.platform === "Google Meet", "preserves custom platform");
  ok(custom.risk === 89, "rounds numeric risk");
  ok(custom.band === "critical", "normalizes band to lowercase");
  ok(custom.scamType === "Digital Arrest", "preserves scamType");
  ok(custom.recommendation === "Hang up immediately.", "preserves recommendation");
  ok(custom.sessionKey === "sess_xyz", "preserves sessionKey");
}

console.log("\nTimestamp IST formatting");
{
  const formatted = Incident.formatIST(1710000000000);
  ok(typeof formatted === "string" && formatted.includes("IST"), "formatted timestamp includes IST");
}

console.log("\nFIR Draft formatting & Required Contents");
{
  const inc = Incident.createIncident({
    id: "INC-TEST-999",
    timestamp: 1710000000000,
    type: "audio",
    platform: "Google Meet",
    risk: 85,
    band: "critical",
    scamType: "Police Impersonation / Digital Arrest",
    recommendation: "Do not transfer any money or share Aadhaar details.",
    sessionKey: "sess_meet_abc",
    signals: {
      acoustic: { voice_deepfake: 0.88 },
      prosody: { scam_prob: 0.92, urgency: 5 },
      voiceprint: { similarity: 0.15 },
    },
  });

  const fir = Incident.formatFirDraft(inc);

  // 1. Title verification
  ok(
    fir.includes("antAI CYBERCRIME COMPLAINT DRAFT / साइबर अपराध शिकायत प्रारूप"),
    "contains exact bilingual complaint title"
  );

  // 2. Incident Details
  ok(fir.includes("INC-TEST-999"), "contains Evidence Reference ID");
  ok(fir.includes("Google Meet"), "contains Platform name");
  ok(fir.includes("Audio Stream / ऑडियो कॉल"), "contains Detection Type");
  ok(fir.includes("sess_meet_abc"), "contains sessionKey");

  // 3. Risk Assessment
  ok(fir.includes("85 / 100"), "contains risk score / 100");
  ok(fir.includes("HIGH RISK / अति जोखिम (CRITICAL)"), "contains high risk band label in English & Hindi");
  ok(fir.includes("Police Impersonation / Digital Arrest"), "contains detected scam type");

  // 4. Detected Signals & Evidence (All 4 required signals)
  ok(fir.includes("Voice Authenticity / आवाज़ प्रामाणिकता"), "contains Voice Authenticity label");
  ok(fir.includes("88%"), "contains voice authenticity formatted percentage");

  ok(fir.includes("Message Scam Pattern / संदेश धोखाधड़ी पैटर्न"), "contains Message Scam Pattern label");
  ok(fir.includes("92%"), "contains scam pattern formatted percentage");

  ok(fir.includes("Urgency / तात्कालिकता"), "contains Urgency label");
  ok(fir.includes("5/5"), "contains urgency rating");

  ok(fir.includes("Voiceprint Similarity / वक्ता पहचान समानता"), "contains Voiceprint Similarity label");
  ok(fir.includes("15%"), "contains voiceprint similarity formatted percentage");

  // 5. Analysis Summary & Recommendation
  ok(fir.includes("Do not transfer any money or share Aadhaar details."), "contains recommendation");

  // 6. Official Reporting Channels (1930 & cybercrime.gov.in)
  ok(fir.includes("National Cybercrime Helpline: 1930"), "contains 1930 helpline");
  ok(fir.includes("https://cybercrime.gov.in"), "contains National Cyber Crime Reporting Portal URL");

  // 7. Hindi translations verification
  ok(fir.includes("साइबर अपराध शिकायत प्रारूप"), "contains Hindi title");
  ok(fir.includes("घटना का विवरण"), "contains Hindi incident details header");
  ok(fir.includes("जोखिम मूल्यांकन"), "contains Hindi risk assessment header");
  ok(fir.includes("पहचाने गए संकेत एवं साक्ष्य"), "contains Hindi evidence header");
  ok(fir.includes("विश्लेषण सारांश एवं सिफारिश"), "contains Hindi analysis header");
  ok(fir.includes("आधिकारिक रिपोर्टिंग चैनल"), "contains Hindi reporting channels header");
  ok(fir.includes("राष्ट्रीय साइबर अपराध हेल्पलाइन: 1930"), "contains Hindi 1930 helpline line");
  ok(fir.includes("राष्ट्रीय साइबर अपराध रिपोर्टिंग पोर्टल: https://cybercrime.gov.in"), "contains Hindi portal line");

  // 8. Disclaimer & Legal Evidence
  ok(fir.includes("DISCLAIMER & LEGAL EVIDENCE / कानूनी साक्ष्य एवं अस्वीकरण"), "contains legal disclaimer header");
  ok(fir.includes("antAI Guardian"), "contains antAI Guardian system name");
}

console.log("\nSignals extraction from Array format");
{
  const inc = Incident.createIncident({
    risk: 65,
    band: "verify",
    signals: [
      { key: "voice", label: "Voice Authenticity", value: "76%" },
      { key: "prosody", label: "Message Patterns", value: "Scam 82%" },
      { key: "voiceprint", label: "Speaker Identity", value: "24%" },
    ],
  });
  const fir = Incident.formatFirDraft(inc);
  ok(fir.includes("76%"), "extracts voice authenticity from signal array");
  ok(fir.includes("Scam 82%"), "extracts scam pattern from signal array");
  ok(fir.includes("24%"), "extracts voiceprint similarity from signal array");
  ok(fir.includes("ELEVATED / बढ़ा हुआ जोखिम (VERIFY)"), "band is ELEVATED for verify");
}

console.log("\nNull & edge cases safety");
{
  ok(Incident.formatFirDraft(null) === "", "null incident returns empty string");
  ok(Incident.formatFirDraft({}) !== "", "empty incident generates safe draft without throwing");
  const fallbackFir = Incident.formatFirDraft(Incident.createIncident({ risk: null }));
  ok(fallbackFir.includes("0 / 100"), "null risk safely defaults to 0");
}

console.log("\nClipboard copy helper");
{
  const inc = Incident.createIncident({ risk: 75, band: "critical" });
  const copied = await Incident.copyFirToClipboard(inc);
  ok(typeof copied === "string" && copied.includes("1930"), "copyFirToClipboard returns FIR text");
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

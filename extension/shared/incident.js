// antAI Guardian — Incident history schema & FIR draft generator
// Pure utility module attached to globalThis for background, popup, options, and tests.

(function (root) {
  function formatIST(timestamp) {
    const d = new Date(timestamp || Date.now());
    try {
      return (
        d.toLocaleString("en-IN", {
          timeZone: "Asia/Kolkata",
          day: "2-digit",
          month: "short",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          hour12: true,
        }) + " IST"
      );
    } catch {
      const utc = d.getTime() + d.getTimezoneOffset() * 60000;
      const ist = new Date(utc + 5.5 * 3600000);
      return ist.toISOString().replace("T", " ").slice(0, 19) + " IST";
    }
  }

  function createIncident(data = {}) {
    const now = Date.now();
    const id = data.id || `inc_${now}_${Math.random().toString(36).slice(2, 8)}`;
    const platform =
      typeof data.platform === "string"
        ? data.platform
        : (data.platform && data.platform.name) || "Web Page";

    return {
      id,
      timestamp: data.timestamp || now,
      type: data.type || "audio", // "audio" | "text"
      platform,
      risk: Number.isFinite(Number(data.risk)) ? Math.round(Number(data.risk)) : 0,
      band: (data.band || "verify").toLowerCase(),
      scamType: data.scamType || null,
      signals: data.signals || {},
      recommendation: data.recommendation || "",
      sessionKey: data.sessionKey || `sess_${now}`,
    };
  }

  function normalizePct(val) {
    if (val === null || val === undefined || val === "") return "—";
    if (typeof val === "string" && val.includes("%")) return val;
    const n = Number(val);
    if (isNaN(n)) return String(val);
    if (n <= 1 && n >= 0) return Math.round(n * 100) + "%";
    return Math.round(n) + "%";
  }

  function extractSignals(signals) {
    let voiceAuthenticity = "—";
    let scamPattern = "—";
    let urgency = "—";
    let voiceprintSimilarity = "—";

    if (!signals) {
      return { voiceAuthenticity, scamPattern, urgency, voiceprintSimilarity };
    }

    // Array form (e.g. from antaiSignals)
    if (Array.isArray(signals)) {
      for (const item of signals) {
        if (!item) continue;
        const k = (item.key || "").toLowerCase();
        if (k.includes("voice") && !k.includes("print")) {
          voiceAuthenticity = item.value || normalizePct(item.raw);
        } else if (k.includes("prosody") || k.includes("scam") || k.includes("message")) {
          scamPattern = item.value || normalizePct(item.raw);
        } else if (k.includes("urgency")) {
          urgency = item.value || String(item.raw);
        } else if (k.includes("voiceprint") || k.includes("speaker") || k.includes("similarity")) {
          voiceprintSimilarity = item.value || normalizePct(item.raw);
        }
      }
      return { voiceAuthenticity, scamPattern, urgency, voiceprintSimilarity };
    }

    // Object form
    const acoustic = signals.acoustic || {};
    const prosody = signals.prosody || {};
    const voiceprint = signals.voiceprint || {};

    const rawVoice =
      acoustic.voice_deepfake !== undefined
        ? acoustic.voice_deepfake
        : signals.voice_deepfake !== undefined
        ? signals.voice_deepfake
        : signals.voiceAuthenticity;
    if (rawVoice !== undefined) voiceAuthenticity = normalizePct(rawVoice);

    const rawScam =
      prosody.scam_prob !== undefined
        ? prosody.scam_prob
        : signals.scam_prob !== undefined
        ? signals.scam_prob
        : signals.scamPattern;
    if (rawScam !== undefined) scamPattern = normalizePct(rawScam);

    const rawUrgency =
      prosody.urgency !== undefined
        ? prosody.urgency
        : signals.urgency !== undefined
        ? signals.urgency
        : null;
    if (rawUrgency !== null && rawUrgency !== undefined) {
      urgency = typeof rawUrgency === "number" ? `${rawUrgency}/5` : String(rawUrgency);
    }

    const rawSim =
      voiceprint.similarity !== undefined
        ? voiceprint.similarity
        : signals.similarity !== undefined
        ? signals.similarity
        : signals.voiceprintSimilarity;
    if (rawSim !== undefined) voiceprintSimilarity = normalizePct(rawSim);

    return { voiceAuthenticity, scamPattern, urgency, voiceprintSimilarity };
  }

  function getBandLabel(band, risk) {
    const b = String(band || "").toLowerCase();
    if (b === "critical" || risk >= 70) {
      return "HIGH RISK / अति जोखिम (CRITICAL)";
    }
    if (b === "verify" || risk >= 50) {
      return "ELEVATED / बढ़ा हुआ जोखिम (VERIFY)";
    }
    return "SAFE / सुरक्षित (MONITORING)";
  }

  function formatFirDraft(incident) {
    if (!incident) return "";

    const istTime = formatIST(incident.timestamp);
    const platform = incident.platform || "Web Page";
    const risk = Number.isFinite(Number(incident.risk)) ? Math.round(Number(incident.risk)) : 0;
    const bandLabel = getBandLabel(incident.band, risk);
    const scamType = incident.scamType || "Suspected AI Impersonation / संदिग्ध एआई प्रतिरूपण";
    const sigs = extractSignals(incident.signals);
    const recommendation =
      incident.recommendation ||
      "Do not transfer funds, share OTPs, or authorize remote access. Verify identity through secondary channel.";

    const lines = [
      "================================================================================",
      "       antAI CYBERCRIME COMPLAINT DRAFT / साइबर अपराध शिकायत प्रारूप",
      "================================================================================",
      "",
      "[ INCIDENT DETAILS / घटना का विवरण ]",
      `• Evidence Reference ID / साक्ष्य संदर्भ संख्या : ${incident.id || "N/A"}`,
      `• Incident Timestamp / घटना दिनांक व समय        : ${istTime}`,
      `• Platform / Source / स्रोत                   : ${platform}`,
      `• Session Reference / सत्र संदर्भ              : ${incident.sessionKey || "N/A"}`,
      `• Incident Type / घटना प्रकार                 : ${incident.type === "text" ? "Page Text Scan / संदेश पाठ" : "Audio Stream / ऑडियो कॉल"}`,
      "",
      "[ RISK ASSESSMENT / जोखिम मूल्यांकन ]",
      `• Risk Score / जोखिम स्कोर                    : ${risk} / 100`,
      `• Threat Band / जोखिम श्रेणी                  : ${bandLabel}`,
      `• Detected Scam Type / पहचानी गई धोखाधड़ी     : ${scamType}`,
      "",
      "[ DETECTED SIGNALS & EVIDENCE / पहचाने गए संकेत एवं साक्ष्य ]",
      `• Voice Authenticity / आवाज़ प्रामाणिकता       : ${sigs.voiceAuthenticity}`,
      `• Message Scam Pattern / संदेश धोखाधड़ी पैटर्न : ${sigs.scamPattern}`,
      `• Urgency / तात्कालिकता                        : ${sigs.urgency}`,
      `• Voiceprint Similarity / वक्ता पहचान समानता   : ${sigs.voiceprintSimilarity}`,
      "",
      "[ ANALYSIS & RECOMMENDATION / विश्लेषण सारांश एवं सिफारिश ]",
      `• Recommendation / अनुशंसित कार्रवाई:`,
      `  ${recommendation}`,
      "",
      "[ OFFICIAL REPORTING CHANNELS / आधिकारिक रिपोर्टिंग चैनल ]",
      "• National Cybercrime Helpline: 1930",
      "  राष्ट्रीय साइबर अपराध हेल्पलाइन: 1930 (टोल-फ्री / 24x7 सेवा)",
      "• National Cyber Crime Reporting Portal: https://cybercrime.gov.in",
      "  राष्ट्रीय साइबर अपराध रिपोर्टिंग पोर्टल: https://cybercrime.gov.in",
      "",
      "[ DISCLAIMER & LEGAL EVIDENCE / कानूनी साक्ष्य एवं अस्वीकरण ]",
      "This complaint draft was automatically compiled by antAI Guardian (Real-time AI",
      "Deepfake & Voice Fraud Defense System). Acoustic features, spectral signatures,",
      "and model verdicts are logged with forensic cryptographic hashes and can be",
      "submitted to law enforcement cyber investigation cells.",
      "================================================================================",
    ];

    return lines.join("\n");
  }

  async function copyFirToClipboard(incident) {
    const text = formatFirDraft(incident);
    if (!text) return "";

    if (
      typeof navigator !== "undefined" &&
      navigator.clipboard &&
      typeof navigator.clipboard.writeText === "function"
    ) {
      await navigator.clipboard.writeText(text);
      return text;
    }

    if (typeof document !== "undefined" && typeof document.createElement === "function") {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.left = "-9999px";
      ta.style.top = "0";
      ta.setAttribute("readonly", "");
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      try {
        document.execCommand("copy");
      } finally {
        document.body.removeChild(ta);
      }
      return text;
    }

    return text;
  }

  const AntaiIncident = {
    createIncident,
    formatFirDraft,
    copyFirToClipboard,
    formatIST,
    extractSignals,
  };

  root.AntaiIncident = AntaiIncident;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = AntaiIncident;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

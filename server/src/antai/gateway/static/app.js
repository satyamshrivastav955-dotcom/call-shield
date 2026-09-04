/* antAI Security Console — SIH PS26104 demo dashboard.
 *
 * Engineering contract:
 *   - Every score comes from normalized_result() via /api/stream/ws or /api/stream/analyze.
 *   - A null/missing value renders as "—", NEVER as zero or a fabricated number.
 *   - The LLM recommendation comes from the server's deterministic policy; the
 *     dashboard never overrides or fabricates it.
 *   - The caller context card is simulated demo data and is clearly labeled as such.
 */
"use strict";

const $ = id => document.getElementById(id);
const state = { ws: null, history: [], scenario: "high_value_txn", scenarioData: {} };

// ── Scenario definitions (fetched from server; these are fallback defaults) ──
const SCENARIO_DEFAULTS = {
  routine_call:    { label: "Routine Call",          verify_at: 75, critical_at: 90 },
  high_value_txn:  { label: "High-Value Transaction", verify_at: 50, critical_at: 70 },
  privileged_access: { label: "Privileged Access",   verify_at: 40, critical_at: 60 },
};

// Demo context data — CLEARLY LABELED as simulated in the UI
const DEMO_CONTEXTS = {
  routine_call: {
    name: "Priya Mehta", phone: "+91 87654 32101", txn: "Account enquiry",
    amount: null, claimed: "Customer", initial: "P", hist: "None on record",
  },
  high_value_txn: {
    name: "Rahul Sharma", phone: "+91 98765 43210", txn: "Fund Transfer",
    amount: "₹8,50,000", claimed: "Branch Manager", initial: "R", hist: "None on record",
  },
  privileged_access: {
    name: "Ankit Verma", phone: "+91 99001 12233", txn: "System credential reset",
    amount: null, claimed: "IT Administrator", initial: "A", hist: "1 prior flag",
  },
};

// ── Helpers ──────────────────────────────────────────────────────────────────
function pct(v, decimals = 0) {
  if (v == null) return "—";
  return Math.round(v * 100).toFixed(decimals) + "%";
}

function riskColor(risk, scenario) {
  const sc = state.scenarioData[scenario] || SCENARIO_DEFAULTS[scenario] || SCENARIO_DEFAULTS.routine_call;
  if (risk >= sc.critical_at) return "var(--critical)";
  if (risk >= sc.verify_at)   return "var(--high)";
  if (risk >= 20)             return "var(--med)";
  return "var(--low)";
}

function riskClass(risk, scenario) {
  const sc = state.scenarioData[scenario] || SCENARIO_DEFAULTS[scenario] || SCENARIO_DEFAULTS.routine_call;
  if (risk >= sc.critical_at) return "critical";
  if (risk >= sc.verify_at)   return "high";
  if (risk >= 20)             return "verify";
  return "safe";
}

function verdictLabel(cls) {
  return { critical: "DO NOT AUTHORIZE", high: "HIGH RISK — VERIFY REQUIRED",
           verify: "ELEVATED — PROCEED WITH CAUTION", safe: "SAFE — Continue monitoring" }[cls] || "MONITORING";
}

function recClass(recommendation) {
  if (!recommendation) return "safe";
  const r = recommendation.toLowerCase();
  if (r.includes("do not") || r.includes("block") || r.includes("stop")) return "high";
  if (r.includes("verify") || r.includes("elevated")) return "verify";
  return "safe";
}

function escHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
}

// ── Arc math for risk gauge (half-circle, r=80, center 90,90) ──
// Arc length of the half-circle = π * 80 ≈ 251.3
const ARC_LEN = Math.PI * 80;

function setArc(risk) {
  const arc = $("riskArc");
  if (!arc) return;
  const frac = Math.min(100, Math.max(0, risk)) / 100;
  const offset = ARC_LEN * (1 - frac);
  arc.style.strokeDasharray = ARC_LEN.toFixed(1);
  arc.style.strokeDashoffset = offset.toFixed(1);
  arc.style.stroke = riskColor(risk, state.scenario);
}

// ── Threshold marker on gauge ──
function updateThreshMarker(scenario) {
  const sc = state.scenarioData[scenario] || SCENARIO_DEFAULTS[scenario] || SCENARIO_DEFAULTS.routine_call;
  const frac = sc.verify_at / 100;
  // half-circle from left (180°) to right (0°); frac=0 → left, frac=1 → right
  const angle = Math.PI * (1 - frac); // 0 at left end (180°), π at right end (0°)
  const cx = 90, cy = 90, r = 80;
  const mx = cx + r * Math.cos(Math.PI - angle);
  const my = cy - r * Math.sin(Math.PI - angle);
  const mx2 = cx + (r - 16) * Math.cos(Math.PI - angle);
  const my2 = cy - (r - 16) * Math.sin(Math.PI - angle);
  const m = $("threshMarker");
  if (m) { m.setAttribute("x1", mx.toFixed(1)); m.setAttribute("y1", my.toFixed(1)); m.setAttribute("x2", mx2.toFixed(1)); m.setAttribute("y2", my2.toFixed(1)); }
}

// ── Scenario tile update ──
function updateScenarioTile() {
  const name = state.scenario;
  const sc = state.scenarioData[name] || SCENARIO_DEFAULTS[name] || {};
  const label = sc.label || name;
  const verify_at = sc.verify_at ?? sc.risk_verify_at ?? 75;
  const critical_at = sc.critical_at ?? sc.risk_critical_at ?? 90;
  $("scenarioVal").textContent = label;
  $("thresholdSub").textContent = `Verify at ${verify_at} · Alert at ${critical_at}`;
  updateThreshMarker(name);
  updateDemoContext(name);
}

function updateDemoContext(scenario) {
  const ctx = DEMO_CONTEXTS[scenario] || DEMO_CONTEXTS.high_value_txn;
  $("callerAvatar").textContent = ctx.initial;
  $("callerName").textContent = ctx.name;
  $("callerSub").textContent = ctx.phone + " · Unknown number";
  $("txnType").textContent = ctx.txn;
  $("txnAmount").textContent = ctx.amount || "N/A";
  $("txnAmount").style.color = ctx.amount ? "var(--high)" : "var(--muted)";
  $("claimedId").textContent = ctx.claimed;
  $("historicalRisk").textContent = ctx.hist;
  $("historicalRisk").style.color = ctx.hist.includes("flag") ? "var(--high)" : "var(--low)";
}

// ── Main render ───────────────────────────────────────────────────────────────
function render(r) {
  if (!r) return;
  const risk = r.risk ?? 0;
  const cls = riskClass(risk, state.scenario);
  const color = riskColor(risk, state.scenario);

  // Top stat strip
  $("riskNum").innerHTML = (r.risk == null ? "—" : Math.round(risk)) + '<small style="font-size:14px;color:var(--muted);font-weight:600"> / 100</small>';
  $("riskNum").style.color = color;
  $("riskSub").textContent = { critical: "Critical — immediate action needed", high: "High risk detected",
    verify: "Elevated — verify before proceeding", safe: "Low risk" }[cls] || "Monitoring";
  if (r.risk_peak != null) {
    $("peakNum").textContent = Math.round(r.risk_peak);
    $("peakNum").style.color = riskColor(r.risk_peak, state.scenario);
  }
  $("segsNum").textContent = String(r.audio_segments ?? 0);

  // Gauge
  setArc(risk);
  $("gaugeNum").innerHTML = (r.risk == null ? "—" : Math.round(risk)) + '<small> / 100</small>';
  $("gaugeNum").style.color = color;
  $("riskBand").textContent = (r.band || "passive").toUpperCase();
  $("riskBand").style.color = color;

  // Meter
  const m = $("riskMeter");
  m.style.width = Math.min(100, risk) + "%";
  m.style.background = color;

  // Verdict banner
  const vb = $("verdictBanner");
  vb.className = "verdict-banner " + cls;
  vb.textContent = verdictLabel(cls);

  // ── Three signal families ──
  const ac = r.acoustic || {};
  const pr = r.prosody || {};
  const vp = r.voiceprint || {};

  // Acoustic: synthetic voice probability (0..1)
  const acV = ac.voice_deepfake;
  const acFlagged = acV != null && acV >= 0.7;
  $("sigAcoustic").className = "sig" + (acFlagged ? " flagged" : "");
  $("acVal").textContent = acV == null ? "—" : pct(acV);
  $("acVal").style.color = acV == null ? "var(--muted)" : (acFlagged ? "var(--high)" : "var(--low)");
  const acSubs = [];
  if (acV != null) acSubs.push(acV >= 0.85 ? "Likely synthetic" : acV >= 0.5 ? "Uncertain" : "Appears natural");
  if (ac.label) acSubs.push(ac.label);
  if (ac.sources) acSubs.push(ac.sources + " model" + (ac.sources > 1 ? "s" : ""));
  if (ac.backend) acSubs.push(ac.backend);
  $("acSub").textContent = acSubs.join(" · ") || "No result yet";
  setBar("acBar", acV, acFlagged ? "var(--high)" : "var(--low)");

  // Prosody: urgency (0-100 scale → show as-is) + scam
  const urgency = pr.urgency;
  const scamP = pr.scam_prob;
  const prFlagged = (urgency != null && urgency >= 60) || (scamP != null && scamP >= 0.5);
  $("sigProsody").className = "sig" + (prFlagged ? " flagged" : "");
  const prDisplay = urgency != null ? Math.round(urgency) : (scamP != null ? pct(scamP) : "—");
  $("prVal").textContent = String(prDisplay);
  $("prVal").style.color = prFlagged ? "var(--high)" : (urgency == null && scamP == null ? "var(--muted)" : "var(--low)");
  const prSubs = [];
  if (pr.kind) prSubs.push(pr.kind);
  if (pr.scam_type && pr.scam_type !== "unknown") prSubs.push(pr.scam_type);
  $("prSub").textContent = prSubs.join(" · ") || "No result yet";
  setBar("prBar", urgency != null ? urgency / 100 : scamP, prFlagged ? "var(--high)" : "var(--med)");

  // Voiceprint: similarity (0..1)
  const sim = vp.similarity;
  const vpMismatch = vp.identity_mismatch === true;
  const vpFlagged = vpMismatch || (sim != null && sim < 0.5);
  $("sigVoiceprint").className = "sig" + (vpFlagged ? " flagged" : "");
  $("vpVal").textContent = sim == null ? "—" : pct(sim);
  $("vpVal").style.color = sim == null ? "var(--muted)" : (vpFlagged ? "var(--high)" : "var(--low)");
  const vpSub = vpMismatch ? "Voice mismatch — identity unconfirmed" :
    (sim == null ? (r.audio_segments ? "No voiceprint enrolled" : "No result yet") :
     sim >= 0.7 ? "Voice matches enrolled profile" : sim >= 0.5 ? "Weak match" : "Voice mismatch");
  $("vpSub").textContent = vpSub;
  setBar("vpBar", sim, vpFlagged ? "var(--high)" : "var(--accent)");

  // ── Recommendation ──
  const rec = r.recommendation || "";
  const rClass = recClass(rec);
  const recBox = $("recBox");
  recBox.className = "rec-box " + rClass;
  $("recText").textContent = rec || "No recommendation yet.";
  // Action steps
  const steps = $("recSteps");
  steps.innerHTML = "";
  if (cls === "high" || cls === "critical") {
    [["Independent callback", rClass], ["MFA verification", rClass], ["Security escalation", rClass]]
      .forEach(([text, cl], i) => {
        const div = document.createElement("div");
        div.className = "rec-step";
        div.innerHTML = `<span class="rec-step-dot ${cl}">${i + 1}</span><span>${escHtml(text)}</span>`;
        steps.appendChild(div);
      });
  } else if (cls === "verify") {
    const div = document.createElement("div");
    div.className = "rec-step";
    div.innerHTML = `<span class="rec-step-dot verify">!</span><span>Verify caller identity before proceeding</span>`;
    steps.appendChild(div);
  }

  // ── Reasons ──
  const reasons = r.reasons || [];
  const ul = $("reasons");
  ul.innerHTML = "";
  if (reasons.length) {
    ul.classList.remove("empty");
    reasons.forEach(txt => {
      const li = document.createElement("li");
      const dot = document.createElement("span");
      dot.className = "r-dot";
      dot.style.background = cls === "safe" ? "var(--low)" : (cls === "verify" ? "var(--med)" : "var(--high)");
      li.appendChild(dot);
      li.appendChild(document.createTextNode(txt));
      ul.appendChild(li);
    });
  } else {
    ul.classList.add("empty");
    const li = document.createElement("li");
    li.textContent = "No concerning signals.";
    ul.appendChild(li);
  }

  // ── Transcript ──
  if (r.latest_text) {
    const t = $("transcript");
    if (t.querySelector("span[style*='color:var']")) t.innerHTML = "";
    const line = document.createElement("div");
    line.style.cssText = "padding:4px 0;border-bottom:1px solid var(--line);font-size:12px;";
    line.innerHTML = `<span class="ts">[${escHtml(r.t || "")}]</span>${escHtml(r.latest_text)}`;
    t.appendChild(line);
    t.scrollTop = t.scrollHeight;
  }

  // ── Engines ──
  const eng = r.engines_ready || {};
  const keys = Object.keys(eng);
  const box = $("engines");
  if (keys.length) {
    box.innerHTML = "";
    keys.forEach(k => {
      const chip = document.createElement("span");
      chip.className = "chip " + (eng[k] ? "on" : "off");
      chip.textContent = k.replace(/_/g, " ");
      box.appendChild(chip);
    });
  }

  // ── Misc stats ──
  $("asrFails").textContent = String(r.asr_failures ?? 0);
  $("lastT").textContent = r.t || "—";

  // ── Sparkline ──
  if (r.risk != null) {
    state.history.push(risk);
    if (state.history.length > 120) state.history.shift();
    drawSpark();
  }
}

function setBar(id, v, color) {
  const el = $(id);
  if (!el) return;
  el.style.width = (v == null ? 0 : Math.min(100, v * 100)) + "%";
  if (color) el.style.background = color;
}

function drawSpark() {
  const h = state.history;
  if (!h.length) return;
  const W = 600, H = 80;
  const step = h.length > 1 ? W / (h.length - 1) : W;
  const pts = h.map((v, i) => `${(i * step).toFixed(1)},${(H - (v / 100) * H).toFixed(1)}`);
  $("sparkLine").setAttribute("points", pts.join(" "));
  // fill polygon
  const first = pts[0], last = pts[pts.length - 1];
  const [lx] = last.split(",");
  const fillPts = pts.join(" ") + ` ${lx},${H} 0,${H}`;
  $("sparkFill").setAttribute("points", fillPts);
}

// ── Connection helpers ────────────────────────────────────────────────────────
function setConn(on, label) {
  $("dot").classList.toggle("on", !!on);
  $("connlbl").textContent = label;
}

// ── Scenario selector ─────────────────────────────────────────────────────────
$("scenario").addEventListener("change", e => {
  state.scenario = e.target.value;
  updateScenarioTile();
  // If a live session is active, send a new start frame with the scenario
  if (state.ws && state.ws.readyState === WebSocket.OPEN) {
    const ctx = DEMO_CONTEXTS[state.scenario] || {};
    state.ws.send(JSON.stringify({
      type: "start", sample_rate: 16000, format: "f32le", speaker_id: 1,
      scenario: state.scenario,
      context: { transaction_type: ctx.txn, claimed_identity: ctx.claimed,
                 transaction_amount: ctx.amount ? parseFloat(ctx.amount.replace(/[^0-9.]/g, "")) : null },
    }));
  }
});

// ── Analyze file (one-shot) ───────────────────────────────────────────────────
$("analyze").addEventListener("click", async () => {
  const f = $("file").files[0];
  if (!f) { alert("Choose an audio file first."); return; }
  $("recText").textContent = "Analyzing…";
  const fd = new FormData();
  fd.append("file", f);
  fd.append("scenario", state.scenario);
  try {
    const res = await fetch("/api/stream/analyze", { method: "POST", body: fd });
    const j = await res.json();
    if (j.error) { $("recText").textContent = "Error: " + j.error; return; }
    render(j);
  } catch (e) {
    $("recText").textContent = "Request failed: " + e;
  }
});

// ── Live WebSocket stream ────────────────────────────────────────────────────
$("attach").addEventListener("click", () => {
  if (state.ws) return;
  const proto = location.protocol === "https:" ? "wss" : "ws";
  const url = `${proto}://${location.host}/api/stream/ws`;
  const ws = new WebSocket(url);
  ws.binaryType = "arraybuffer";
  state.ws = ws;
  state.history = [];

  ws.onopen = () => {
    setConn(true, "Live · connected");
    $("stop").disabled = false;
    $("attach").disabled = true;
    const ctx = DEMO_CONTEXTS[state.scenario] || {};
    ws.send(JSON.stringify({
      type: "start", sample_rate: 16000, format: "f32le", speaker_id: 1,
      scenario: state.scenario,
      context: { transaction_type: ctx.txn, claimed_identity: ctx.claimed,
                 transaction_amount: ctx.amount ? parseFloat(ctx.amount.replace(/[^0-9.]/g, "")) : null },
    }));
  };
  ws.onmessage = ev => {
    let msg; try { msg = JSON.parse(ev.data); } catch { return; }
    if (msg.type === "update" || msg.type === "final") render(msg);
    if (msg.type === "started") $("recText").textContent = "Live session " + msg.session_key + " — streaming…";
  };
  ws.onclose = () => { setConn(false, "Not connected"); cleanupWs(); };
  ws.onerror = () => { setConn(false, "Connection error"); };
});

$("stop").addEventListener("click", () => {
  if (state.ws) { try { state.ws.send(JSON.stringify({ type: "stop" })); } catch {} state.ws.close(); }
});

function cleanupWs() { state.ws = null; $("stop").disabled = true; $("attach").disabled = false; }

// ── Fetch scenario data from server ──────────────────────────────────────────
async function fetchScenarios() {
  try {
    const res = await fetch("/api/scenarios");
    if (res.ok) {
      const data = await res.json();
      // Normalize: server may use risk_verify_at / risk_critical_at
      Object.entries(data).forEach(([k, v]) => {
        state.scenarioData[k] = {
          label: v.label || k,
          verify_at: v.verify_at ?? v.risk_verify_at ?? 75,
          critical_at: v.critical_at ?? v.risk_critical_at ?? 90,
        };
      });
      // Rebuild scenario selector options from real server data
      const sel = $("scenario");
      const current = sel.value;
      sel.innerHTML = "";
      Object.entries(state.scenarioData).forEach(([k, v]) => {
        const opt = document.createElement("option");
        opt.value = k; opt.textContent = v.label;
        if (k === current) opt.selected = true;
        sel.appendChild(opt);
      });
    }
  } catch { /* use defaults */ }
  updateScenarioTile();
}

// ── Init ──────────────────────────────────────────────────────────────────────
setConn(false, "Not connected");
setArc(0);
fetchScenarios();

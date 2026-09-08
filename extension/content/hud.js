// antAI Guardian HUD — floating risk pill on the protected tab.
// Collapsed: band color + icon + risk% + audio pulse dot + label (≥44px target).
// Expanded: gauge, audio stream indicator, 3 signal rows, recommendation, Verify / Stop actions.
// Active Defense Freeze Modal: high-risk scam / voice clone intercept modal with 60s hold.
// Draggable, remembers position, clamped to viewport, safe from meeting controls.
// All values come from the server's normalized_result; null signals render "—" (§0.2).
//
// Styles live inside the shadow root — page CSS cannot restyle it.

(() => {
  if (window.__antaiHud) return; // re-injection guard
  window.__antaiHud = true;

  const host = document.createElement("div");
  host.id = "antai-hud-host";
  const shadow = host.attachShadow({ mode: "open" });
  shadow.innerHTML = `
    <style>
      :host {
        all: initial; /* isolate from page styles */
        position: fixed;
        z-index: 2147483647;
        font-family: "Segoe UI", Roboto, system-ui, sans-serif;
        color: #f3f4f6;
        user-select: none;
        pointer-events: none;
      }
      .hud-wrap {
        pointer-events: auto;
      }
      .pill {
        display: flex;
        align-items: center;
        gap: 8px;
        min-height: 44px; /* ≥44px target (B5) */
        min-width: 44px;
        padding: 6px 16px;
        background: #1a1d23;
        border: 2px solid #6b7280;
        border-radius: 24px;
        box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);
        cursor: grab;
        font-size: 14px;
        font-weight: 600;
        color: #f3f4f6;
        box-sizing: border-box;
        pointer-events: auto;
      }
      .pill:active { cursor: grabbing; }
      .pill .risk {
        font-size: 20px;
        font-weight: 700;
        font-variant-numeric: tabular-nums;
      }
      .audio-pulse {
        width: 9px;
        height: 9px;
        border-radius: 50%;
        background: #6b7280;
        display: inline-block;
        flex-shrink: 0;
        transition: background 200ms ease, box-shadow 200ms ease;
      }
      .audio-pulse.active {
        background: #16a34a;
        box-shadow: 0 0 0 0 rgba(22, 163, 74, 0.7);
        animation: antai-pulse 1.8s infinite;
      }
      @keyframes antai-pulse {
        0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(22, 163, 74, 0.7); }
        70% { transform: scale(1.15); box-shadow: 0 0 0 6px rgba(22, 163, 74, 0); }
        100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(22, 163, 74, 0); }
      }
      .card {
        width: 280px;
        margin-top: 8px;
        background: #1a1d23;
        border: 1px solid #374151;
        border-radius: 14px;
        padding: 12px 14px;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
        box-sizing: border-box;
        pointer-events: auto;
      }
      .card header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 6px;
      }
      .logo { color: #0e7c7b; font-weight: 700; font-size: 15px; }
      .conn { font-size: 11px; color: #6b7280; }
      .stream-status {
        display: flex;
        align-items: center;
        gap: 6px;
        background: #242830;
        border-radius: 6px;
        padding: 5px 8px;
        margin-bottom: 8px;
        font-size: 11px;
        color: #9ca3af;
      }
      .stream-dot { font-size: 9px; color: #6b7280; }
      .stream-dot.active { color: #16a34a; }
      .gauge { display: flex; justify-content: center; }
      .track {
        fill: none; stroke: #374151; stroke-width: 12; stroke-linecap: round;
      }
      .arc {
        fill: none; stroke: #16a34a; stroke-width: 12; stroke-linecap: round;
        transition: stroke-dasharray 200ms ease-out, stroke 200ms ease-out;
      }
      .num {
        font-size: 26px; font-weight: 700; fill: #f3f4f6; text-anchor: middle;
        font-variant-numeric: tabular-nums;
      }
      .den { font-size: 9px; fill: #6b7280; text-anchor: middle; }
      .badge {
        text-align: center; font-size: 13px; font-weight: 700;
        border: 1px solid #16a34a; color: #16a34a; border-radius: 8px;
        padding: 6px; margin: 6px 0;
      }
      .row {
        display: flex; justify-content: space-between; align-items: center;
        background: #242830; border-radius: 8px; padding: 8px 10px;
        margin: 3px 0; font-size: 11px;
      }
      .row-label { color: #9ca3af; }
      .row-value { font-weight: 700; font-variant-numeric: tabular-nums; }
      .row-value.na { color: #6b7280; }
      .rec { background: #242830; border-radius: 8px; padding: 8px 10px; margin-top: 6px; }
      .rec-label { font-size: 8px; letter-spacing: 0.5px; color: #6b7280; }
      .rec-text {
        margin: 4px 0 0; font-size: 11px; line-height: 1.5; color: #f3f4f6;
        overflow-wrap: anywhere;
      }
      .actions { display: flex; gap: 8px; margin-top: 10px; }
      .btn {
        flex: 1;
        min-height: 44px; /* ≥44px target (B5) */
        border: none; border-radius: 8px; font-size: 12px; font-weight: 600;
        cursor: pointer; background: #2e333d; color: #f3f4f6;
        transition: background 150ms, transform 150ms;
        box-sizing: border-box;
      }
      .btn:hover { background: #374151; }
      .btn:active { transform: scale(0.97); }
      .btn.stop { background: #7f1d1d; }
      .btn.stop:hover { background: #991b1b; }

      /* Active Defense Freeze / Intercept Modal */
      .freeze-overlay {
        position: fixed;
        inset: 0;
        width: 100vw;
        height: 100vh;
        top: 0;
        left: 0;
        background: rgba(10, 12, 16, 0.88);
        backdrop-filter: blur(8px);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 2147483647;
        padding: 16px;
        box-sizing: border-box;
        pointer-events: auto;
      }
      .freeze-overlay[hidden] {
        display: none !important;
      }
      .freeze-card {
        max-width: 520px;
        width: 100%;
        background: #1a1d23;
        border: 2px solid #dc2626;
        border-radius: 16px;
        padding: 24px;
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.75), 0 0 24px rgba(220, 38, 38, 0.3);
        box-sizing: border-box;
      }
      .freeze-header {
        display: flex;
        align-items: flex-start;
        gap: 14px;
        margin-bottom: 14px;
      }
      .freeze-icon {
        font-size: 32px;
        line-height: 1;
      }
      .freeze-title-en {
        margin: 0;
        font-size: 16px;
        font-weight: 800;
        letter-spacing: 0.5px;
        color: #ef4444;
      }
      .freeze-title-hi {
        margin: 3px 0 0;
        font-size: 13px;
        font-weight: 600;
        color: #fca5a5;
      }
      .freeze-risk-badge {
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: #242830;
        border-radius: 8px;
        padding: 8px 12px;
        margin-bottom: 12px;
        font-size: 13px;
      }
      .freeze-badge-score {
        color: #9ca3af;
      }
      .freeze-score-val {
        font-size: 16px;
        color: #ef4444;
      }
      .freeze-badge-tag {
        font-weight: 700;
        color: #ef4444;
      }
      .freeze-warning-box {
        background: #2a1616;
        border-left: 4px solid #dc2626;
        border-radius: 6px;
        padding: 12px 14px;
        margin-bottom: 14px;
      }
      .freeze-warning-en {
        margin: 0;
        font-size: 13px;
        font-weight: 600;
        line-height: 1.5;
        color: #fecaca;
      }
      .freeze-warning-hi {
        margin: 6px 0 0;
        font-size: 12px;
        line-height: 1.4;
        color: #fca5a5;
      }
      .freeze-signals {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        gap: 8px;
        margin-bottom: 14px;
      }
      .freeze-sig-item {
        background: #242830;
        border-radius: 6px;
        padding: 6px 8px;
        font-size: 10px;
        color: #9ca3af;
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .freeze-sig-val {
        font-size: 12px;
        font-weight: 700;
        color: #f3f4f6;
      }
      .freeze-hold-box {
        background: #242830;
        border: 1px solid #374151;
        border-radius: 8px;
        padding: 10px 12px;
        margin-bottom: 16px;
      }
      .freeze-hold-header {
        display: flex;
        justify-content: space-between;
        font-size: 11px;
        font-weight: 600;
      }
      .freeze-hold-label {
        color: #fbbf24;
      }
      .freeze-hold-countdown {
        color: #f3f4f6;
        font-variant-numeric: tabular-nums;
      }
      .freeze-hold-track {
        height: 6px;
        background: #374151;
        border-radius: 3px;
        overflow: hidden;
        margin: 8px 0 4px;
      }
      .freeze-hold-fill {
        height: 100%;
        width: 100%;
        background: #eab308;
        transition: width 1s linear;
      }
      .freeze-hold-sub {
        margin: 4px 0 0;
        font-size: 10px;
        color: #9ca3af;
      }
      .freeze-actions {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .freeze-btn {
        min-height: 44px; /* ≥44px target (B5) */
        border: none;
        border-radius: 8px;
        font-size: 12px;
        font-weight: 600;
        cursor: pointer;
        padding: 10px 14px;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        transition: background 150ms, transform 150ms;
        box-sizing: border-box;
      }
      .freeze-btn:active { transform: scale(0.98); }
      .freeze-copy-fir {
        background: #0e7c7b;
        color: #ffffff;
        width: 100%;
      }
      .freeze-copy-fir:hover { background: #119897; }
      .freeze-copy-fir.copied {
        background: #16a34a !important;
      }
      .freeze-actions-row {
        display: flex;
        gap: 8px;
      }
      .freeze-ack {
        flex: 1;
        background: #2e333d;
        color: #f3f4f6;
      }
      .freeze-ack:hover { background: #374151; }
      .freeze-override {
        flex: 1;
        background: #7f1d1d;
        color: #fecaca;
      }
      .freeze-override:hover { background: #991b1b; }

      @media (prefers-reduced-motion: reduce) {
        * {
          transition: none !important;
          animation: none !important;
        }
      }
    </style>
    <div class="hud-wrap">
      <div class="pill" role="button" tabindex="0" aria-label="antAI risk status — click to expand">
        <span class="dot">●</span>
        <span class="risk">—</span>
        <span class="audio-pulse" title="Audio stream idle" aria-label="Audio stream activity"></span>
        <span class="band">MONITORING</span>
      </div>
      <div class="card" hidden>
        <header>
          <span class="logo">antAI</span>
          <span class="conn">● connecting</span>
        </header>
        <div class="stream-status">
          <span class="stream-dot">●</span>
          <span class="stream-text">Audio: 16 kHz stream active</span>
        </div>
        <div class="gauge">
          <svg viewBox="0 0 120 66" width="140" height="77" aria-hidden="true">
            <path class="track" d="M 10 60 A 50 50 0 0 1 110 60"/>
            <path class="arc" d="M 10 60 A 50 50 0 0 1 110 60"/>
            <text class="num" x="60" y="46">—</text>
            <text class="den" x="60" y="60">/ 100</text>
          </svg>
        </div>
        <div class="badge">MONITORING</div>
        <div class="rows"></div>
        <div class="rec">
          <span class="rec-label">RECOMMENDATION / सिफारिश</span>
          <p class="rec-text">Waiting for audio… / ऑडियो की प्रतीक्षा…</p>
        </div>
        <div class="actions">
          <button class="btn verify" type="button">I Verified / जाँच ली</button>
          <button class="btn stop" type="button">Stop / रोकें</button>
        </div>
      </div>
    </div>

    <div class="freeze-overlay" hidden role="alertdialog" aria-modal="true" aria-labelledby="freeze-title" aria-describedby="freeze-desc">
      <div class="freeze-card">
        <div class="freeze-header">
          <span class="freeze-icon" aria-hidden="true">🛑</span>
          <div class="freeze-titles">
            <h1 id="freeze-title" class="freeze-title-en">HIGH RISK SCAM / VOICE CLONE INTERCEPTED</h1>
            <h2 class="freeze-title-hi">उच्च जोखिम: संभावित डीपफेक या धोखाधड़ी रोकी गई</h2>
          </div>
        </div>

        <div class="freeze-risk-badge">
          <span class="freeze-badge-score">Risk: <strong class="freeze-score-val">—</strong> / 100</span>
          <span class="freeze-badge-tag">CRITICAL / गंभीर</span>
        </div>

        <div id="freeze-desc" class="freeze-warning-box">
          <p class="freeze-warning-en">
            Do NOT share OTP, passwords, or approve bank transfers. Verify the caller through an independent trusted channel.
          </p>
          <p class="freeze-warning-hi">
            ओटीपी, पासवर्ड या बैंक ट्रांसफर साझा न करें। एक स्वतंत्र विश्वसनीय माध्यम से कॉलर की पुष्टि करें।
          </p>
        </div>

        <div class="freeze-signals">
          <div class="freeze-sig-item">
            <span>Voice Deepfake</span>
            <strong class="freeze-sig-val freeze-sig-voice">—</strong>
          </div>
          <div class="freeze-sig-item">
            <span>Scam Patterns</span>
            <strong class="freeze-sig-val freeze-sig-prosody">—</strong>
          </div>
          <div class="freeze-sig-item">
            <span>Speaker Match</span>
            <strong class="freeze-sig-val freeze-sig-voiceprint">—</strong>
          </div>
        </div>

        <div class="freeze-hold-box">
          <div class="freeze-hold-header">
            <span class="freeze-hold-label">⏱ Conscious Hold / संयम अवधि:</span>
            <span class="freeze-hold-countdown">60s remaining</span>
          </div>
          <div class="freeze-hold-track">
            <div class="freeze-hold-fill"></div>
          </div>
          <p class="freeze-hold-sub">Take a moment to pause. Scammers exploit urgency and panic.</p>
        </div>

        <div class="freeze-actions">
          <button type="button" class="freeze-btn freeze-copy-fir">
            <span class="btn-icon">📋</span>
            <span class="btn-text">Copy 1930 FIR Draft / शिकायत प्रारूप</span>
          </button>
          <div class="freeze-actions-row">
            <button type="button" class="freeze-btn freeze-ack">
              <span class="btn-icon">✓</span>
              <span class="btn-text">Acknowledge / समझ गए</span>
            </button>
            <button type="button" class="freeze-btn freeze-override">
              <span class="btn-icon">⚠</span>
              <span class="btn-text">Override / जारी रखें</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  `;
  document.documentElement.appendChild(host);

  const $ = (sel) => shadow.querySelector(sel);
  const pill = $(".pill");
  const card = $(".card");
  const ARC_LEN = Math.PI * 50; // semicircle path length
  let expanded = false;
  let currentState = null;
  let sessionOverridden = false;
  let holdSeconds = 60;
  let holdInterval = null;

  function updateHoldUI() {
    const cd = $(".freeze-hold-countdown");
    const fill = $(".freeze-hold-fill");
    if (!cd || !fill) return;
    if (holdSeconds > 0) {
      cd.textContent = `${holdSeconds}s remaining / ${holdSeconds} सेकंड शेष`;
      fill.style.width = `${Math.round((holdSeconds / 60) * 100)}%`;
    } else {
      cd.textContent = "Hold complete / संयम पूर्ण";
      fill.style.width = "0%";
    }
  }

  function startHoldCountdown() {
    if (holdInterval) return;
    holdSeconds = 60;
    updateHoldUI();
    holdInterval = setInterval(() => {
      holdSeconds--;
      if (holdSeconds <= 0) {
        holdSeconds = 0;
        clearInterval(holdInterval);
        holdInterval = null;
      }
      updateHoldUI();
    }, 1000);
  }

  function stopHoldCountdown() {
    if (holdInterval) {
      clearInterval(holdInterval);
      holdInterval = null;
    }
  }

  function generateFirDraft(state) {
    const nr = (state && state.latest) || {};
    if (typeof AntaiIncident !== "undefined" && typeof AntaiIncident.formatFirDraft === "function") {
      const incident = AntaiIncident.createIncident({
        platform: window.location.hostname,
        risk: nr.risk,
        band: nr.band || "critical",
        scamType: nr.scam_type || "Voice Cloning / Social Engineering",
        signals: antaiSignals(nr),
        recommendation: nr.recommendation,
      });
      return AntaiIncident.formatFirDraft(incident);
    }

    const risk = nr.risk !== undefined ? antaiFmtRisk(nr.risk) : "—";
    const band = nr.band || "critical";
    const signals = antaiSignals(nr);
    const voice = signals.find((s) => s.key === "voice")?.value || "—";
    const prosody = signals.find((s) => s.key === "prosody")?.value || "—";
    const voiceprint = signals.find((s) => s.key === "voiceprint")?.value || "—";
    const dateStr = new Date().toLocaleString("en-IN", { timeZoneName: "short" });

    return `[NATIONAL CYBERCRIME REPORTING PORTAL (1930) — INCIDENT DRAFT]
Date & Time: ${dateStr}
Incident Category: AI Voice Cloning / Deepfake Cyber Fraud Call
Threat Level: ${band.toUpperCase()} (Risk Score: ${risk}/100)
Target URL / Origin: ${window.location.href}
Acoustic Deepfake Probability: ${voice}
Scam & Urgency Pattern: ${prosody}
Speaker Identity Match: ${voiceprint}
System Recommendation: ${nr.recommendation || "Verify caller through independent channel. Do NOT transfer funds."}

Summary of Incident:
A high-risk suspicious call was intercepted by antAI Guardian active defense on ${window.location.hostname}. The caller engaged in urgent deception patterns consistent with synthetic voice fraud.
Immediate defensive action was taken: OTP, passwords, and banking transfers were actively withheld.

Reported via antAI Guardian Active Defense Intercept.`;
  }

  function showFreezeModal(state) {
    const overlay = $(".freeze-overlay");
    if (!overlay) return;
    if (overlay.hidden) {
      overlay.hidden = false;
      startHoldCountdown();
    }

    const nr = state ? state.latest : null;
    const risk = nr ? Number(nr.risk) : NaN;
    const info = antaiBandInfo(isNaN(risk) ? null : risk, nr ? nr.band : null);
    const scoreVal = $(".freeze-score-val");
    if (scoreVal) {
      scoreVal.textContent = nr ? antaiFmtRisk(risk) : "—";
      scoreVal.style.color = info.color;
    }

    const signals = antaiSignals(nr);
    const vSig = signals.find((s) => s.key === "voice");
    const pSig = signals.find((s) => s.key === "prosody");
    const vpSig = signals.find((s) => s.key === "voiceprint");
    const voiceEl = $(".freeze-sig-voice");
    const prosodyEl = $(".freeze-sig-prosody");
    const vpEl = $(".freeze-sig-voiceprint");
    if (voiceEl && vSig) voiceEl.textContent = vSig.value;
    if (prosodyEl && pSig) prosodyEl.textContent = pSig.value;
    if (vpEl && vpSig) vpEl.textContent = vpSig.value;
  }

  function hideFreezeModal() {
    const overlay = $(".freeze-overlay");
    if (overlay && !overlay.hidden) {
      overlay.hidden = true;
      stopHoldCountdown();
    }
  }

  function render(state) {
    currentState = state;
    const nr = state ? state.latest : null;
    const risk = nr ? Number(nr.risk) : NaN;
    const info = antaiBandInfo(isNaN(risk) ? null : risk, nr ? nr.band : null);

    // pill
    $(".dot").textContent = info.icon;
    $(".dot").style.color = info.color;
    $(".risk").textContent = nr ? antaiFmtRisk(risk) : "—";
    $(".risk").style.color = info.color;
    $(".band").textContent = info.label;
    pill.style.borderColor = info.color;

    // audio pulse dot in pill
    const audioPulse = $(".audio-pulse");
    const isAudioActive = Boolean(state && state.running && (state.connection === "open" || state.connection === "connecting"));
    if (isAudioActive) {
      audioPulse.classList.add("active");
      audioPulse.title = "Audio: 16 kHz stream active";
    } else {
      audioPulse.classList.remove("active");
      audioPulse.title = "Audio: idle";
    }

    // audio stream indicator in card
    const streamText = $(".stream-text");
    const streamDot = $(".stream-dot");
    if (isAudioActive) {
      streamText.textContent = "Audio: 16 kHz stream active";
      streamDot.className = "stream-dot active";
    } else {
      streamText.textContent = "Audio: idle / disconnected";
      streamDot.className = "stream-dot";
    }

    // gauge
    const pct = isNaN(risk) ? 0 : Math.max(0, Math.min(100, risk)) / 100;
    $(".arc").style.strokeDasharray = `${ARC_LEN * pct} ${ARC_LEN}`;
    $(".arc").style.stroke = info.color;
    $(".num").textContent = nr ? antaiFmtRisk(risk) : "—";
    $(".num").style.fill = info.color;

    // badge
    const badge = $(".badge");
    badge.textContent = info.icon + " " + info.label;
    badge.style.color = info.color;
    badge.style.borderColor = info.color;

    // signal rows
    const rows = $(".rows");
    rows.innerHTML = "";
    for (const s of antaiSignals(nr)) {
      const row = document.createElement("div");
      row.className = "row";
      const label = document.createElement("span");
      label.className = "row-label";
      label.textContent = s.label;
      const value = document.createElement("span");
      value.className = "row-value" + (s.value === "—" ? " na" : "");
      value.textContent = s.value;
      row.append(label, value);
      rows.appendChild(row);
    }

    // recommendation (server text; null-safe)
    $(".rec-text").textContent =
      nr && nr.recommendation ? nr.recommendation : "Waiting for audio… / ऑडियो की प्रतीक्षा…";

    // connection dot
    const conn = $(".conn");
    const connMap = {
      open: ["● live", "#16a34a"],
      connecting: ["● connecting", "#ca8a04"],
      closed: ["● offline", "#6b7280"],
      error: ["● error", "#dc2626"],
    };
    const [ctext, ccolor] = connMap[state ? state.connection : "closed"] || connMap.closed;
    conn.textContent = ctext + (state && state.error ? ` — ${state.error}` : "");
    conn.style.color = ccolor;

    // Autonomous Active Defense Freeze check
    const band = nr ? nr.band : null;
    const verifyAt = (state && state.verifyAt) || 50;
    const criticalAt = (state && state.criticalAt) || 70;
    const isFreeze = nr && antaiIsFreeze(isNaN(risk) ? null : risk, band, verifyAt, criticalAt);
    const snoozed = state && state.verifiedUntil && Date.now() < state.verifiedUntil;

    if (isFreeze && !snoozed && !sessionOverridden) {
      showFreezeModal(state);
    } else {
      hideFreezeModal();
    }
  }

  // ── toggle / drag (click vs drag: 4px threshold) ──────────────────────────
  let pos = (() => {
    try {
      const p = JSON.parse(localStorage.getItem("antaiHudPos") || "null");
      if (p && typeof p.x === "number" && typeof p.y === "number") return p;
    } catch {}
    return null; // default: bottom-right
  })();

  function applyPos() {
    const target = expanded ? card : pill;
    const h = target.offsetHeight || 44;
    const w = target.offsetWidth || (expanded ? 280 : 180);
    let x = pos ? pos.x : window.innerWidth - w - 24;
    // Default y: 96px above bottom to stay clear of Meet/Zoom/Teams controls
    let y = pos ? pos.y : window.innerHeight - h - 96;
    // Clamped to viewport boundaries, safe from bottom meeting controls
    x = Math.max(8, Math.min(x, window.innerWidth - w - 8));
    y = Math.max(8, Math.min(y, window.innerHeight - h - 72));
    host.style.left = x + "px";
    host.style.top = y + "px";
  }
  applyPos();
  window.addEventListener("resize", applyPos);

  let drag = null;
  pill.addEventListener("pointerdown", (e) => {
    drag = { x: e.clientX, y: e.clientY, left: host.offsetLeft, top: host.offsetTop, moved: false };
    pill.setPointerCapture(e.pointerId);
  });
  pill.addEventListener("pointermove", (e) => {
    if (!drag) return;
    const dx = e.clientX - drag.x;
    const dy = e.clientY - drag.y;
    if (Math.abs(dx) + Math.abs(dy) > 4) drag.moved = true;
    if (drag.moved) {
      pos = { x: drag.left + dx, y: drag.top + dy };
      applyPos();
    }
  });
  pill.addEventListener("pointerup", () => {
    if (drag && drag.moved) {
      try {
        localStorage.setItem("antaiHudPos", JSON.stringify({ x: host.offsetLeft, y: host.offsetTop }));
      } catch {}
    } else {
      expanded = !expanded;
      card.hidden = !expanded;
      applyPos();
    }
    drag = null;
  });
  pill.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      expanded = !expanded;
      card.hidden = !expanded;
      applyPos();
    }
  });

  // Regular HUD actions
  $(".verify").addEventListener("click", () => {
    chrome.runtime.sendMessage({ type: "antai-verify" });
    expanded = false;
    card.hidden = true;
  });
  $(".stop").addEventListener("click", () => {
    chrome.runtime.sendMessage({ type: "antai-hud-stop" });
  });

  // Freeze Modal actions
  const copyFirBtn = $(".freeze-copy-fir");
  copyFirBtn.addEventListener("click", async () => {
    const draft = generateFirDraft(currentState);
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(draft);
      }
    } catch {}
    chrome.runtime.sendMessage({ type: "antai-copy-fir", draft }).catch(() => {});

    const origHtml = copyFirBtn.innerHTML;
    copyFirBtn.classList.add("copied");
    copyFirBtn.innerHTML = `<span class="btn-icon">✓</span><span class="btn-text">Copied ✓ / कॉपी हो गया</span>`;
    setTimeout(() => {
      copyFirBtn.classList.remove("copied");
      copyFirBtn.innerHTML = origHtml;
    }, 2000);
  });

  $(".freeze-ack").addEventListener("click", () => {
    chrome.runtime.sendMessage({ type: "antai-verify" });
    hideFreezeModal();
  });

  $(".freeze-override").addEventListener("click", () => {
    sessionOverridden = true;
    hideFreezeModal();
  });

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.type === "antai-state") render(msg.state);
    else if (msg?.type === "antai-hud-remove") {
      stopHoldCountdown();
      host.remove();
      window.__antaiHud = false;
    }
  });

  // ask for current state on injection
  chrome.runtime.sendMessage({ type: "antai-get-state" }, (state) => {
    if (state) render(state);
  });
})();

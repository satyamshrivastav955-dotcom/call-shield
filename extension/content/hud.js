// antAI Guardian HUD — floating risk pill on the protected tab.
// Collapsed: band color + icon + risk% + label (never color alone).
// Expanded: gauge, 3 signal rows, recommendation, Verify / Stop actions.
// Draggable, remembers position, clamped to viewport. All values come from
// the server's normalized_result; null signals render "—" (§0.2).
//
// Styles live inside the shadow root — page CSS and insertCSS can't reach
// shadow content, so the HUD is fully encapsulated and meeting pages can't
// restyle it.

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
      }
      .pill {
        display: flex;
        align-items: center;
        gap: 8px;
        min-height: 44px; /* ≥44px target (B5) */
        padding: 6px 16px;
        background: #1a1d23;
        border: 2px solid #6b7280;
        border-radius: 24px;
        box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);
        cursor: grab;
        font-size: 14px;
        font-weight: 600;
        color: #f3f4f6;
      }
      .pill:active { cursor: grabbing; }
      .pill .risk {
        font-size: 20px;
        font-weight: 700;
        font-variant-numeric: tabular-nums;
      }
      .card {
        width: 280px;
        margin-top: 8px;
        background: #1a1d23;
        border: 1px solid #374151;
        border-radius: 14px;
        padding: 12px 14px;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
      }
      .card header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 6px;
      }
      .logo { color: #0e7c7b; font-weight: 700; font-size: 15px; }
      .conn { font-size: 11px; color: #6b7280; }
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
        font-variant-numeric: tabular-nums; /* no layout shift as digits change */
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
      .row-value.na { color: #6b7280; } /* "unavailable", never 0 / fake green */
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
      }
      .btn:hover { background: #374151; }
      .btn:active { transform: scale(0.97); }
      .btn.stop { background: #7f1d1d; }
      .btn.stop:hover { background: #991b1b; }
      @media (prefers-reduced-motion: reduce) {
        * { transition: none !important; }
      }
    </style>
    <div class="pill" role="button" tabindex="0" aria-label="antAI risk status — click to expand">
      <span class="dot">●</span>
      <span class="risk">—</span>
      <span class="band">MONITORING</span>
    </div>
    <div class="card" hidden>
      <header><span class="logo">antAI</span><span class="conn">● connecting</span></header>
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
      <div class="rec"><span class="rec-label">RECOMMENDATION / सिफारिश</span><p class="rec-text">Waiting for audio…</p></div>
      <div class="actions">
        <button class="btn verify" type="button">I Verified / जाँच ली</button>
        <button class="btn stop" type="button">Stop / रोकें</button>
      </div>
    </div>
  `;
  document.documentElement.appendChild(host);

  const $ = (sel) => shadow.querySelector(sel);
  const pill = $(".pill");
  const card = $(".card");
  const ARC_LEN = Math.PI * 50; // semicircle path length
  let expanded = false;

  function render(state) {
    const nr = state.latest;
    const risk = nr ? Number(nr.risk) : NaN;
    const info = antaiBandInfo(isNaN(risk) ? null : risk, nr ? nr.band : null);

    // pill
    $(".dot").textContent = info.icon;
    $(".dot").style.color = info.color;
    $(".risk").textContent = nr ? antaiFmtRisk(risk) : "—";
    $(".risk").style.color = info.color;
    $(".band").textContent = info.label;
    pill.style.borderColor = info.color;

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
    const [ctext, ccolor] = connMap[state.connection] || connMap.closed;
    conn.textContent = ctext + (state.error ? ` — ${state.error}` : "");
    conn.style.color = ccolor;
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
    const h = host.offsetHeight || 60;
    const w = host.offsetWidth || 260;
    let x = pos ? pos.x : window.innerWidth - w - 24;
    let y = pos ? pos.y : window.innerHeight - h - 24;
    x = Math.max(8, Math.min(x, window.innerWidth - w - 8));
    y = Math.max(8, Math.min(y, window.innerHeight - h - 8));
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
    }
    drag = null;
  });
  pill.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      expanded = !expanded;
      card.hidden = !expanded;
    }
  });

  $(".verify").addEventListener("click", () => {
    chrome.runtime.sendMessage({ type: "antai-verify" });
    expanded = false;
    card.hidden = true;
  });
  $(".stop").addEventListener("click", () => {
    chrome.runtime.sendMessage({ type: "antai-hud-stop" });
  });

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.type === "antai-state") render(msg.state);
    else if (msg?.type === "antai-hud-remove") {
      host.remove();
      window.__antaiHud = false;
    }
  });

  // ask for current state on injection
  chrome.runtime.sendMessage({ type: "antai-get-state" }, (state) => {
    if (state) render(state);
  });
})();

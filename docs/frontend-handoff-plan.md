# antAI — Frontend Handoff Plan: Android UI Polish + Browser Extension

**Scope:** client work only. No detection-logic changes. Both workstreams build against the stable
API contracts in §4. Backend/detection crew works independently; scores flow through existing fields.

---

## 0. Ground Rules (both workstreams, non-negotiable)

1. **Do not redesign from scratch.** The design system (`app/.../ui/theme/`) is final and good:
   teal `AntaiTeal #0E7C7B` + `AntaiGreen #1EA362`, risk scale red/orange/green
   (`RiskCritical / RiskCaution / RiskSafe`), WhatsApp-style radii, existing type scale,
   dark tokens defined in `Color.kt`. The job is completion + consistency + polish.
2. **Every number comes from the pipeline.** Render only `ShieldPipeline.LiveResult` /
   `normalized_result` values. Never mock risk. If a model is missing → render an
   **"unavailable" state** (gray chip + label), never `0` or a fake green.
3. **One risk card, everywhere.** Live call, file analysis, notification overlay, incident
   history, extension HUD — all use one shared risk layout (gauge/badge + 3 signal rows +
   recommendation). One component per platform, not five divergent layouts.
4. **Hindi/Hinglish for all user-facing verdict text.** The Explainer already emits en/hi —
   mirror that in UI strings. Raw keys like `acoustic.voice_deepfake` are never shown to users.
5. **No new navigation library.** Keep state-switched tabs; add a proper back-stack only if
   predictive-back breakage is observed.
6. **Semantic tokens only — zero raw hex in screens.** `IncidentHistoryScreen.kt` (5 literals)
   and `SettingsScreen.kt` (6 literals) currently violate this; fixing them is task A6.

---

## 1. WORKSTREAM A — Android App UI/UX Final Polish

### A1. Calls idle home (MainScreen.kt) — make it a product, not a dev tool

**Problem:** `ServerConfig` IP card + `WhoToCall` dialer dominate the idle state.

**Do:**
- Demote `ServerConfig` into Settings (keep a small connection-status chip on Calls home:
  connected / local-only / offline, from `ConnectionState`).
- New idle layout: protection status hero ("antAI is guarding your calls" + mode chip from A4),
  last-incident card, then dialer as the primary CTA.
- Empty state when no incidents yet — message + guidance, not blank space.

**Files:** `ui/screens/MainScreen.kt`, `ui/components/ServerConfig.kt` (→ move usage to
`SettingsScreen.kt`), new `RiskStatusChip` composable (reuse across screens).

### A2. Recording-check flow (currently one buried button)

**Problem:** `ShieldViewModel.analyzeFile` + `AudioFileDecoder` work, but are reachable only via
a single button.

**Do:**
- First-class "Check a recording" card on Calls home: file picker → progress → unified risk card
  (§0 rule 3) → share/save result.
- Register the app as a **share target** for audio (`ACTION_SEND` audio/* intent filter +
  `MainActivity` handling) so users can check a forwarded voice note from WhatsApp in two taps.
- Progress state >300ms → skeleton/progress UI, not frozen button.

**Files:** `MainScreen.kt`, `AndroidManifest.xml`, `MainActivity.kt`, `ShieldViewModel`.

### A3. Unified risk card component

One composable, three uses: live call (`CallComponent`/`AiInsightWindow`), file result,
incident history rows.
- Inputs: risk float, band, recommendation (en/hi), per-signal values (deepfake, prosody, voiceprint).
- Unavailable sub-signals render as "—/unavailable" rows, per §0 rule 2.
- Color not the only indicator: band label text + icon alongside color (accessibility).

**Files:** new `ui/components/RiskResultCard.kt`; refactor `AiInsightWindow.kt` and
`IncidentHistoryScreen.kt` to consume it.

### A4. Server vs serverless mode chip

**Problem:** app silently degrades to serverless; user can't tell.

**Do:** persistent chip on Calls home + in-call header: "On-device" / "Server-backed" / "Offline",
driven by connection state + `GET /api/debug/models` result. Chip tap → explanation bottom sheet
(what's analyzed locally vs server, in Hinglish).

### A5. Voiceprint → family enroll flow

**Problem:** `VoiceprintScreen` is single-user, server-backed.

**Do:** multi-profile enroll: list of enrolled members (avatar + similarity status + last-verified),
"Add family member" flow (record 3 phrases → progress → confirmation), per-member detail.
Empty state with guidance. Keep the existing `VoiceprintRecorder`/server calls — UI only.

**Files:** `VoiceprintScreen.kt`, `VoiceprintViewModel.kt`.

### A6. Theme Incidents + Settings (dark-mode debt)

Replace all raw literals in `IncidentHistoryScreen.kt:90,104,117,203,273` and
`SettingsScreen.kt:57,67,197,219,250` with `MaterialTheme.colorScheme` tokens
(`AntaiInk`, `AntaiBackground`, `AntaiTeal`, dark variants already exist in `Color.kt`).
Verify both themes render — dividers, pressed states, and text contrast in dark mode
(≥4.5:1 primary, ≥3:1 secondary).

### A7. FIR / complaint actions in Incidents

Draft builder + share already exists in Shield. Surface it as an action on each incident card:
"Draft FIR / complaint" → prefilled text (en/hi) → system share sheet. One primary CTA per card.

### A8. Hindi/Hinglish strings

Extract all user-facing verdict/status text into `res/values/strings.xml` + `values-hi/`.
Prioritize: verdict chips, risk recommendations, error/empty states, mode chip labels.
Match Explainer terminology exactly.

### A9. Messages / Thread consistency

Message verdicts in `ThreadScreen.kt`/`ConversationListScreen.kt` must use the same risk badge
component as calls (A3). Same bands, same colors, same wording (en/hi).

---

## 2. WORKSTREAM B — Browser Extension (Meet/Zoom/Teams call protection)

### B1. Baseline to port (do not reinvent)

`windows_client/` already proves the protocol: mic → `ws://host:8765/api/stream/ws`
(`start{sample_rate,...}` → binary f32le → `update{normalized_result}` → `stop`→final),
tkinter gauge + desktop toasts. The extension is this ported to the browser, plus what desktop
lacks: scenario picker, per-tab routing, options UI.

Port **verbatim** from:
- `windows_client/ui_overlay.py:36` — `_band_color(risk, verify_at=50, critical_at=70)`
- `windows_client/notifications.py:35` — 15s notification cooldown logic

### B2. Architecture

```
Meet/Zoom/Teams tab → tabCapture/getUserMedia → AudioWorklet (16k mono, f32le)
  → background SW → /api/stream/ws
  → content-script HUD (floating risk pill, expandable)
  → popup (gauge, 3 signal rows, recommendation)
  → chrome.notifications (15s cooldown, verify/critical copy)
  → [later] page-text scan → POST /api/notify/external
```

Render from `normalized_result`: `risk`, `band`, `recommendation`,
`acoustic.voice_deepfake`, `prosody.{scam_prob, urgency}`, `voiceprint.similarity`.

**Visual language:** mirror the app palette — teal `#0E7C7B` chrome, red/orange/green risk
bands with the same thresholds (50/70). The HUD should look like the same product.

### B3. MVP scaffold (manifest v3)

```
extension/
  manifest.json          # MV3: action popup, background SW, content script, options
  background.js          # WS client + capture lifecycle + notifications + reconnect backoff
  content/hud.js|css     # floating risk pill (collapsed) / expanded card
  popup/                 # gauge, 3 signal rows, recommendation
  options/               # server URL, scenario, threshold overrides
  worklet/pcm.js         # 16k mono f32le AudioWorklet
```

### B4. MVP tasks (in order)

1. **Scaffold + connection**: manifest v3, options page (server URL, scenario picker —
   `routine / high_value / privileged`; desktop is CLI-only today), background SW that opens
   the WS and shows connection status. `GET /api/debug/models` → "models ready" indicator.
2. **Capture lifecycle**: per-tab audio capture toggle (tabCapture or getUserMedia),
   AudioWorklet → f32le frames → WS. Stop on tab close; reconnect with exponential backoff.
3. **HUD**: collapsed pill (band color + risk %) on meeting tabs; expand to full card — gauge,
   3 signal rows, recommendation, **Verify / End call** buttons mirroring the app's
   freeze/decide semantics. Per §0: unavailable signals show "unavailable", not 0.
4. **Notifications**: port cooldown logic; verify (≥50) and critical (≥70) variants with
   elevated copy; options for threshold overrides.
5. **Options polish**: scenario, verify/critical overrides, Deepgram-vs-local note
   (server-side; display only).

**Explicitly post-MVP (do not build now):** `wss://` + token auth, per-participant diarization
labels, page-text scan → `/api/notify/external`.

### B5. UX rules for the extension (from the ui-ux checklist)

- HUD must never block meeting controls; draggable, remembers position; safe from viewport edges.
- HUD pill: ≥44px touch/click target, color + icon + label (never color alone).
- Gauge + numbers update in place — no layout shift on band changes.
- Popup shows last-known state when WS is down, with a clear reconnect/reason line
  (error → cause + recovery path).
- Respect reduced-motion; animate risk transitions 150–300ms, transform/opacity only.

---

## 3. Shared API Contract (stable — build against this, nothing else)

| Endpoint | Purpose |
|---|---|
| `WS /api/stream/ws` | `start{sample_rate, format, speaker_id, scenario, context?}` → binary f32le frames → `update{...normalized_result}` → `stop` → `final` |
| `POST /api/stream/analyze` | one-shot file/screenshot-audio analysis |
| `POST /api/notify/external` | page-text scan submission (post-MVP for extension) |
| `GET /api/debug/models` | "models ready" / on-device status indicator |

`normalized_result` to render: `risk`, `band`, `recommendation`,
`acoustic.voice_deepfake`, `prosody.scam_prob`, `prosody.urgency`, `voiceprint.similarity`.
Threshold coloring: `_band_color` semantics (verify ≥50, critical ≥70). Full endpoint table
lives in the server `swarm_api.py`.

**Mocking policy:** safe to mock `normalized_result` JSON for layout work; never ship mocks.

---

## 4. Suggested Sequence

| Phase | Workstream A | Workstream B |
|---|---|---|
| 1 (parallel) | A6 theme debt (small, unblocks dark-mode QA everywhere) + A3 risk card | B4.1 scaffold + options |
| 2 | A1 idle home + A4 mode chip | B4.2 capture lifecycle |
| 3 | A2 recording-check + share target | B4.3 HUD |
| 4 | A5 family enroll + A7 FIR actions | B4.4 notifications |
| 5 | A8 hi strings + A9 messages consistency | B4.5 options polish + QA pass |

---

## 5. Pre-Delivery QA Gate (both workstreams)

- [ ] No raw hex literals in screens — semantic tokens only (grep `Color(0xFF` outside `theme/` = 0 hits)
- [ ] Unavailable model → "unavailable" state, never 0 / fake green
- [ ] One risk card used everywhere; same bands, colors, and wording across call/message/file/incident/extension
- [ ] Dark mode independently tested: text ≥4.5:1 primary, ≥3:1 secondary; dividers and pressed states visible
- [ ] Color never the sole risk indicator (icon + label always present)
- [ ] Touch/click targets ≥48dp (Android) / ≥44px (extension HUD)
- [ ] Async ops >300ms show progress; buttons disable + spinner during submit
- [ ] Error states state cause + recovery path; empty states give guidance + action
- [ ] All verdict strings present in `strings.xml` + `values-hi/`
- [ ] Reduced motion respected; animations 150–300ms, transform/opacity only
- [ ] No mocked risk data in shipped builds

## 6. Out of Scope (detection crew — do not touch)

Datasets → AASIST-L/ECAPA training → ONNX export; thresholds from measured curves.
**Integration point:** when model status flips to "all on-device ✓" and spoof % goes live,
no UI rework is required as long as the unavailable-state rule (§0.2) was honored.

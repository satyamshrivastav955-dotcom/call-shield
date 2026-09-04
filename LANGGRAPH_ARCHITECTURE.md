# antAI LangGraph Orchestration — Detailed Architecture

Read directly from `server/src/antai/orchestration/` (`graph.py`, `state.py`,
`nodes.py`) and its real-time driver (`dispatcher.py`).

---

## 1. Why LangGraph (design rationale)

- The detection pipeline is **not linear**: a text-only scam must never run the
  (expensive) voice/video deepfake models, and the two branches (media vs text)
  converge before fusion. LangGraph's `StateGraph` expresses this as a
  conditional DAG.
- Every node is a **pure-ish function `(state) -> updated_state`**, so the same
  node functions are reusable whether the graph is compiled
  (`graph.ainvoke`) or run sequentially as a fallback
  (`dispatcher._run_sequential`).
- The compiled graph (`build_graph()` → `StateGraph(AnalysisState).compile()`)
  is cached as a singleton via `get_graph()`; if compilation throws, `_graph`
  is `None` and the dispatcher degrades to the sequential path.

## 2. State contract — `AnalysisState` (`state.py`)

A `TypedDict (total=False)` — every key is optional, so nodes only set what
they produce:

- **Context:** `kind` (voice|video|message), `session_key`, `caller_id`,
  `callee_id`, `text`, `transcript[]`
- **Inputs (per-eval):** `pending_audio {speaker_id, audio, sample_rate}`,
  `pending_frames[]`, `claimed_identity_id`, `collective_phone_hash`
- **Voice signals:** `voice_deepfake`, `voice_per_model {asv5, w2v}`,
  `voice_sources`, `voice_agreement`, `voice_backend`, `voice_label`,
  `speaker_similarity`, `identity_mismatch`
- **Video signals:** `video_deepfake`, `video_votes {per-model}`,
  `video_agreement`, `lipsync_mismatch`
- **Text signals:** `scam_prob`, `scam_type`, `deviation`, `urgency`,
  `request_detected`, `request_type`, `request_confidence`, `text_skipped`
- **Fusion/decision:** `risk`, `band`, `risk_signals {hard[], soft[], capped}`,
  `decision`, `freeze`
- **Outputs:** `verdict`, `guidance`, `events`, `intercept_handled`

## 3. Node list (14 nodes, grouped by signal family)

| Node | File | What it does / models called |
|---|---|---|
| `router_node` | `nodes.py` | No-op: initializes `events`. Fan-out is done by the conditional edge. |
| `_media_detectors` (composite) | `graph.py` | Runs `voice_detector_node` + `video_detector_node`, **plus** `text_detector_node` if `state["text"]` is set (a call is also a text stream). |
| `voice_detector_node` | `nodes.py` | Voice-deepfake (`voice_deepfake` engine → Velma or local SSL) + speaker-verify (`speaker_verify` engine, only if `claimed_identity_id` set). |
| `video_detector_node` | `nodes.py` | Video-deepfake ensemble (`video_deepfake`), face mouth-openness (`face` → MediaPipe), lip-sync (`lipsync`). |
| `text_detector_node` | `nodes.py` | Scam-pattern classifier (`scam_pattern`) + behavioral deviation + profile update (`behavioral`). |
| `identity_claim_node` | `nodes.py` | Resolves `claimed_identity_id` → `identity_mismatch` (defaults False when no claim). |
| `collective_check_node` | `nodes.py` | SHA-256 phone-hash lookup in the collective flag DB → `collective_flagged`. |
| `urgency_node` | `nodes.py` | Urgency/pressure scorer (0–100). |
| `intent_node` | `nodes.py` | Request/intent classifier → `request_detected` / `request_type` / `request_confidence`. |
| `fusion_node` | `nodes.py` | The core: weighted risk fusion (see §5). |
| `decision_node` | `nodes.py` | Maps `risk`/`band` → `log` / `verify` / `freeze` / `escalate`. |
| `llm_reasoning_node` | `nodes.py` | Calls `generate_verdict(risk, signals, transcript, ctx)` → `verdict`. |

## 4. Graph topology (`graph.py`)

```
START → router
   router ──(kind=="message")──> text_detectors → collective
   router ──(else)─────────────> media_detectors → identity → collective
                                          collective → urgency → intent → fusion
                                               fusion → decision → llm → END
```

- `g.set_entry_point("router")`
- **Conditional edge** `_route_after_router`: `"text" if kind=="message" else "media"`.
- `media_detectors` and `text_detectors` **both converge at `collective`**
  (the text branch skips `identity` because a message isn't a live voice
  identity claim).
- All paths merge before `fusion`; `_route_after_fusion` is a trivial
  `→ "llm"`.

## 5. Fusion logic — the heart of the system (`fusion_node`)

The risk model separates evidence into **hard** (self-sufficient) and **soft**
(needs corroboration) signals, and every continuous signal passes through a
**dead-zone ramp** (`_above(value, floor, ceil, weight)`) that contributes 0
until the model is genuinely confident.

**Hard signals (each can raise the alarm on its own):**
- `identity_mismatch` → **+50**
- `collective_flagged` → **+45**
- `voice_deepfake` (floor 0.78 → ceil 0.95, max **+60**) — calibrated so a
  ~90% synthetic-voice confidence reaches ~42 (verify) and ≥0.95 reaches 60
  (critical with anything else).
- `video_deepfake` (floor 0.80 → ceil 0.97, max **+50**)

**Request-to-act (the scam core, weighted by severity):**
- `request_detected` → contribution = `_above(confidence, 0.60, 0.95, 50 × severity)`
- `_REQUEST_WEIGHT`: `money`/`otp`/`credential` = 1.0, `remote-access` = 0.9,
  `link` = 0.5 (a vague link ask is far weaker evidence than an OTP ask).
- A high-confidence money/OTP/credential ask (sev ≥ 0.9 AND conf ≥ 0.80) is
  promoted to **hard**.

**Soft signals (sized so any TWO clear the verify boundary, but no single one
ever can):**
- `scam_prob` (0.55→0.95, max +35) · `urgency` (60→95, max +22) ·
  `deviation` (0.55→1.0, max +12) · `lipsync_mismatch` (+12)

**Corroboration requirement (the false-positive guard):**
- If **no hard signal fired AND fewer than 2 soft signals agree**, risk is
  **capped just below the verify boundary** (`verify_at − 1`, i.e. 39). This
  is what stopped a lone out-of-distribution "hi" from alarming the user.
- `risk_signals {hard, soft, capped}` is stored for debug/log explainability.

**Banding:** `risk < 40 → passive`, `40–69 → verify`, `≥70 → critical`
(config `risk_bands`).

## 6. Decision node (`decision_node`)

- `risk ≥ 70` + `request_detected` → **`freeze`** (intercept the
  OTP/money/credential/link action)
- `risk ≥ 70` without a request → **`escalate`**
- `band == "verify"` → **`verify`** (trigger trusted-contact verification)
- else → **`log`** (passive)

## 7. LLM reasoning node (`llm_reasoning_node`)

- Collects the fused signals into a dict, builds context, and calls
  `reasoner.generate_verdict()` (off the event loop via `asyncio.to_thread`).
- `generate_verdict` has a **fast path**: `risk < 40` and no
  request/collective → a **template verdict** (milliseconds, no LLM).
  Otherwise it calls the LLM (Groq or local Qwen) and parses a JSON
  `{verdict, why, action, scam_type}`; on parse failure it falls back to the
  template.
- `_active_reasons(signals)` feeds the LLM **only genuinely-active signals**
  (thresholded), so the "why" never cites a miscalibrated 0.3 as "AI voice".

## 8. Resilience layer (why "all models at 0" can't happen anymore)

- **`@_safe_node`** wraps every node: if a detector throws on a bad
  frame/shape/OOM, the node logs and returns the **partially-populated
  state** — sibling nodes and fusion/verdict still run.
- **`_safe(label, coro)`** wraps each individual model call so one failing
  model doesn't block the other models *inside the same node*.
- **`text.triage.is_low_content()`** gates text *before* any model —
  greetings/one-word replies never reach the classifiers, so they can't
  manufacture a scam signal (the root cause of "every hi is flagged").

## 9. How the graph is driven in real time (`dispatcher.py`)

- **Calls:** `SessionRunner.on_audio_segment` / `on_video_frame` build a fresh
  `AnalysisState` (`pending_audio` or `pending_frames` + latest transcript)
  and call `_schedule_evaluate()`.
- **Coalesced background evaluation** (`_eval_drain`): evaluations run
  fire-and-forget and always process the **latest** state, so the heavy graph
  never blocks the media relay or the live transcript.
- **ASR is a separate worker** (`_asr_worker`): transcribe → push
  `transcript.update` → schedule evaluation, so transcription and detection
  are decoupled.
- **`_run_graph(state)`**: `get_graph().ainvoke(state)` if compiled, else
  `_run_sequential()` (same nodes in fixed order).
- **Live guidance** runs as a separate throttled, non-overlapping background
  task (LLM on its own), and **deepfake/freeze alerts** are pushed from
  `_evaluate_locked` after fusion.
- **Messages:** `dispatch_message()` runs the same graph on the text branch and
  enforces `freeze` interception.

## 10. Sequential fallback (used only if the graph fails to compile)

`dispatcher._run_sequential` calls the same node functions in order —
`router → (text | voice+video) → identity → collective → urgency → intent →
fusion → decision → llm` — and logs every model's `ready()` state first (so
"risk=0 because nothing loaded" is diagnosable, not silent).

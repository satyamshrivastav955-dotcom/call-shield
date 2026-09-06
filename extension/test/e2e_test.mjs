// antAI end-to-end test — exercises the extension's server contract against a
// live server: stream WS auth + protocol, OTP login, notify/external scan,
// debug/models shape. Run with: node test/e2e_test.mjs (server must be up).

const BASE = "http://localhost:8765";
let pass = 0, fail = 0;
function ok(cond, name, detail) {
  if (cond) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; console.log(`  ✗ ${name}${detail ? " — " + detail : ""}`); }
}

// 1 ── GET /api/debug/models : response shape {"models": {key: {ready}}}
console.log("GET /api/debug/models");
let modelsBody = null;
{
  const res = await fetch(`${BASE}/api/debug/models`);
  modelsBody = await res.json();
  ok(res.ok, "HTTP 200");
  ok(modelsBody && typeof modelsBody.models === "object", "top-level 'models' object (client parses this level)");
  const entries = Object.values(modelsBody.models || {});
  ok(entries.length > 0 && entries.every((m) => typeof m.ready === "boolean"), "each model has ready:boolean");
}

// 2 ── WS auth: invalid token is rejected (new server code)
console.log("WS /api/stream/ws — auth");
{
  const ws = new WebSocket(`ws://localhost:8765/api/stream/ws?token=garbage-token`);
  const frames = [];
  const closed = new Promise((r) => { ws.onclose = () => r(true); });
  ws.onmessage = (e) => frames.push(JSON.parse(e.data));
  await Promise.race([closed, new Promise((r) => setTimeout(r, 5000))]);
  ok(frames.some((f) => f.type === "error" && /invalid/.test(f.message)), "invalid token rejected with error frame");
  ok(ws.readyState === WebSocket.CLOSED, "connection closed after invalid token");
}

// 3 ── WS protocol: no token (flag off) → start → started → stop → final
console.log("WS /api/stream/ws — protocol (anonymous, require_stream_token=false)");
{
  const ws = new WebSocket("ws://localhost:8765/api/stream/ws");
  ws.binaryType = "arraybuffer";
  const frames = [];
  const done = new Promise((r) => {
    ws.onmessage = (e) => {
      if (typeof e.data === "string") {
        const f = JSON.parse(e.data);
        frames.push(f);
        if (f.type === "final") r("final");
        if (f.type === "error") r("error:" + f.message);
      }
    };
  });
  await new Promise((r) => { ws.onopen = r; });
  ws.send(JSON.stringify({ type: "start", sample_rate: 16000, format: "f32le", speaker_id: 1, scenario: "high_value_txn" }));
  // ~2s of 16k f32le silence-ish noise in 250ms chunks (what the worklet sends)
  const chunk = new Float32Array(4000);
  for (let i = 0; i < 4000; i++) chunk[i] = (Math.random() * 2 - 1) * 0.05;
  for (let i = 0; i < 8; i++) ws.send(chunk.buffer);
  await new Promise((r) => setTimeout(r, 1500));
  ws.send(JSON.stringify({ type: "stop" }));
  const outcome = await Promise.race([done, new Promise((r) => setTimeout(r, 8000, "timeout"))]);

  const started = frames.find((f) => f.type === "started");
  ok(!!started, "'started' frame received with session_key", JSON.stringify(frames.map((f) => f.type)));
  if (started) ok(!!started.session_key, "started carries session_key");
  const update = frames.find((f) => f.type === "update");
  if (update) {
    ok(typeof update.risk === "number" || update.risk === null, "update has risk (number|null)");
    ok(typeof update.band === "string", `update band="${update.band}"`);
    console.log(`    live update: risk=${update.risk} band=${update.band} rec="${(update.recommendation || "").slice(0, 60)}"`);
  } else {
    console.log("    (no update frame — heartbeat needs ≥1s; acceptable)");
  }
  if (outcome === "final") {
    const fin = frames.find((f) => f.type === "final");
    ok(true, "'final' frame after stop");
    console.log(`    final: risk=${fin.risk} band=${fin.band}`);
  } else {
    ok(false, "'final' frame after stop", "outcome=" + outcome);
  }
  ws.close();
}

// 4 ── OTP login (same flow as the extension options page)
console.log("POST /api/auth/otp + /api/auth/verify");
let token = null;
{
  const res = await fetch(`${BASE}/api/auth/otp`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ phone: "+911234567890" }),
  });
  const data = await res.json();
  ok(res.ok && data.sent, "otp requested");
  const otp = data.dev_otp;
  ok(!!otp, "dev_otp returned (auto_verify mode)", JSON.stringify(data));
  const vres = await fetch(`${BASE}/api/auth/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ phone: "+911234567890", otp, display_name: "e2e-test" }),
  });
  const vdata = await vres.json();
  ok(vres.ok && !!vdata.token, "verify returns token", JSON.stringify(vdata));
  token = vdata.token;
}

// 5 ── notify/external: 401 without token
console.log("POST /api/notify/external");
{
  const res = await fetch(`${BASE}/api/notify/external`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text: "hello there" }),
  });
  ok(res.status === 401, "401 without bearer token");
}

// 6 ── notify/external with token: benign text → keyword gate drops it
{
  const res = await fetch(`${BASE}/api/notify/external`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ source: "other", sender: "e2e", text: "hey, see you at the cafe tomorrow?" }),
  });
  const data = await res.json();
  ok(res.ok && data.ingested === false, "benign text gated (ingested:false, no alert)", JSON.stringify(data));
}

// 7 ── notify/external with token: scammy text → ingested
{
  const res = await fetch(`${BASE}/api/notify/external`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      source: "other",
      sender: "e2e",
      text: "URGENT: Your bank account will be blocked. Share your OTP immediately to verify your identity. Send the money now.",
    }),
  });
  const data = await res.json();
  ok(res.ok && data.ingested === true, "scam text ingested", JSON.stringify(data).slice(0, 200));
  if (data.ingested) {
    console.log(`    scan result: risk_score=${data.risk_score} verdict=${JSON.stringify(data.verdict)?.slice(0, 120)}`);
    ok(typeof data.risk_score === "number", "risk_score is a number");
  }
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

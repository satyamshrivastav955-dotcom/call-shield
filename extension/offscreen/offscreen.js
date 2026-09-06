// antAI offscreen document — owns the AudioContext + AudioWorklet + WebSocket.
// Service workers have no AudioContext, so tab audio capture happens here.
//
// WS protocol (server stream_api.py): {"type":"start", sample_rate, format,
// speaker_id, scenario} → binary f32le frames → {"type":"update"|"final",
// ...normalized_result}. Reconnects with exponential backoff until stopped.

let ws = null;
let ctx = null;
let node = null;
let stream = null;
let cfg = null;
let stopped = true;
let pendingFrames = [];
let backoffMs = 1000;

// Host may carry a scheme (wss:// for a TLS-proxied deployment); default ws://.
// A stored auth token (Options → Account) rides along as ?token= — validated
// by the server when present.
function wsUrl(host, token) {
  const raw = String(host || "").trim();
  const scheme = /^wss:\/\//.test(raw) ? "wss" : "ws";
  const h = raw.replace(/^wss?:\/\//, "").replace(/\/+$/, "");
  const q = token ? `?token=${encodeURIComponent(token)}` : "";
  return `${scheme}://${h}/api/stream/ws${q}`;
}

function report(status, error) {
  chrome.runtime.sendMessage({ type: "antai-ws-status", status, error: error || null }).catch(() => {});
}

function sendFrame(bytes) {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    if (pendingFrames.length < 64) pendingFrames.push(bytes); // bounded queue
    return;
  }
  ws.send(bytes);
}

function connect() {
  ws = new WebSocket(wsUrl(cfg.serverHost, cfg.token));
  ws.binaryType = "arraybuffer";
  report("connecting");

  ws.onopen = () => {
    backoffMs = 1000;
    ws.send(
      JSON.stringify({
        type: "start",
        sample_rate: 16000,
        format: "f32le",
        speaker_id: 1,
        scenario: cfg.scenario,
      })
    );
    for (const f of pendingFrames) ws.send(f);
    pendingFrames = [];
    report("open");
  };

  ws.onmessage = (ev) => {
    if (typeof ev.data !== "string") return;
    let msg;
    try {
      msg = JSON.parse(ev.data);
    } catch {
      return;
    }
    if (msg.type === "update" || msg.type === "final") {
      chrome.runtime.sendMessage({ type: "antai-update", data: msg }).catch(() => {});
    } else if (msg.type === "error") {
      report("error", msg.message);
    }
  };

  ws.onclose = () => {
    ws = null;
    if (!stopped) {
      report("connecting", "connection lost — reconnecting");
      setTimeout(connect, backoffMs);
      backoffMs = Math.min(backoffMs * 2, 15000);
    } else {
      report("closed");
    }
  };

  ws.onerror = () => report("error", "could not reach the antAI server");
}

async function startCapture(msg) {
  cfg = msg.cfg;
  stopped = false;
  pendingFrames = [];

  stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      mandatory: {
        chromeMediaSource: "tab",
        chromeMediaSourceId: msg.streamId,
      },
    },
    video: false,
  });

  ctx = new AudioContext({ sampleRate: 48000 });
  await ctx.audioWorklet.addModule(chrome.runtime.getURL("worklet/pcm.js"));
  const src = ctx.createMediaStreamSource(stream);
  node = new AudioWorkletNode(ctx, "antai-pcm");
  node.port.onmessage = (e) => sendFrame(e.data);
  src.connect(node);
  // Worklet has no output — do NOT connect to ctx.destination (tab keeps playing).

  connect();
}

async function stopCapture() {
  stopped = true;
  try {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: "stop" }));
  } catch {}
  setTimeout(() => ws && ws.close(), 200); // let the final frame arrive
  try {
    node?.disconnect();
    await ctx?.close();
  } catch {}
  stream?.getTracks().forEach((t) => t.stop());
  node = ctx = stream = ws = null;
  report("closed");
}

chrome.runtime.onMessage.addListener((msg) => {
  if (msg?.type === "antai-capture-start") {
    startCapture(msg).catch((e) => report("error", e.message));
  } else if (msg?.type === "antai-capture-stop") {
    stopCapture();
  }
});

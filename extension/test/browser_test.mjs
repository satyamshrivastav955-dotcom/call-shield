// antAI extension in-browser smoke test via Chrome DevTools Protocol.
// Prereqs: Chrome running with --remote-debugging-port=9222 and the extension
// loaded; antAI server on localhost:8765. Run: node extension/test/browser_test.mjs

const CDP = "http://localhost:9222";
const EXT_ID = process.env.ANTAI_EXT_ID || "cpjlcheifbkelhhdpcfjijhbdfoheilh";
let pass = 0, fail = 0;
const ok = (c, name, detail) => {
  if (c) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name + (detail ? " — " + detail : "")); }
};

async function newTab(url) {
  const res = await fetch(`${CDP}/json/new?${url}`, { method: "PUT" }); // raw slashes — %2F breaks Chrome's URL parse
  return res.json();
}

async function connect(wsUrl) {
  const ws = new WebSocket(wsUrl);
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let id = 0;
  const pending = new Map();
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  };
  return {
    send(method, params = {}) {
      return new Promise((resolve) => {
        const i = ++id;
        pending.set(i, resolve);
        ws.send(JSON.stringify({ id: i, method, params }));
      });
    },
    close: () => ws.close(),
  };
}

async function evalJS(cdp, expr, awaitPromise = false) {
  const r = await cdp.send("Runtime.evaluate", {
    expression: expr,
    returnByValue: true,
    awaitPromise,
  });
  // CDP nests twice: {id, result: {result: RemoteObject, exceptionDetails}}
  const ev = r.result || {};
  if (ev.exceptionDetails) throw new Error("page eval failed: " + JSON.stringify(ev.exceptionDetails.exception?.description || ev.exceptionDetails.text));
  return ev.result?.value;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ── 1. Options page: OTP login against the live server ──────────────────────
console.log("options page — OTP login");
const optTab = await newTab(`chrome-extension://${EXT_ID}/options/options.html`);
await sleep(1000);
const opt = await connect(optTab.webSocketDebuggerUrl);
await opt.send("Runtime.enable");

ok(await evalJS(opt, `!!document.getElementById("serverHost")`), "options page renders");
ok(await evalJS(opt, `document.getElementById("account-status").textContent.includes("not signed in")`), "initially not signed in");

await evalJS(opt, `document.getElementById("serverHost").value = "localhost:8765"`);
await evalJS(opt, `document.getElementById("phone").value = "+919999888877"`);
await evalJS(opt, `document.getElementById("send-otp").click()`);
await sleep(1500);
const loginMsg = await evalJS(opt, `document.getElementById("login-msg").textContent`);
ok(/OTP sent/.test(loginMsg), "send-otp shows confirmation", loginMsg);
const devOtp = (loginMsg.match(/code: (\d+)/) || [])[1];
ok(!!devOtp, "dev OTP surfaced", loginMsg);

await evalJS(opt, `document.getElementById("otp").value = "${devOtp}"`);
await evalJS(opt, `document.getElementById("verify-otp").click()`);
await sleep(1500);
ok(await evalJS(opt, `document.getElementById("account-status").textContent.includes("signed in")`), "signed in after verify");
ok(await evalJS(opt, `document.getElementById("logged-in").hidden === false`), "logged-in panel visible");

// ── 2. Popup page: models indicator from the live server (wakes the SW) ─────
console.log("popup page — live state + models indicator");
const popTab = await newTab(`chrome-extension://${EXT_ID}/popup/popup.html`);
await sleep(500);
const pop = await connect(popTab.webSocketDebuggerUrl);
await pop.send("Runtime.enable");
await sleep(2500); // allow refresh-models round-trip + broadcast

const modelsText = await evalJS(pop, `document.getElementById("models").textContent`);
ok(/^models:/.test(modelsText), "models indicator populated by SW", modelsText);
ok(!modelsText.includes("checking"), "models check completed", modelsText);
console.log("    " + modelsText);
ok(await evalJS(pop, `document.getElementById("toggle").textContent === "Protect this tab"`), "idle toggle state");

// ── 3. Scan: benign + scam text through the real server path ────────────────
console.log("popup page — page-text scan");
// The #scan button uses chrome.scripting on the ACTIVE tab — a web page in real
// use; opened as a tab here it targets the popup itself, which scripting can't
// inject. Exercise the scan via the same SW message the button sends.
const scanBenign = await evalJS(pop, `new Promise((resolve) => {
  chrome.runtime.sendMessage({ type: "antai-scan-text", text: "hey, see you at the cafe tomorrow?" }, resolve);
})`, true);
ok(scanBenign && scanBenign.ok && scanBenign.data && scanBenign.data.ingested === false, "benign text gated (ingested:false)", JSON.stringify(scanBenign).slice(0, 160));

const scanScam = await evalJS(pop, `new Promise((resolve) => {
  chrome.runtime.sendMessage({ type: "antai-scan-text", text: "URGENT: Your bank account will be blocked. Share your OTP immediately. Send money now." }, resolve);
})`, true);
ok(scanScam && scanScam.ok && scanScam.data && scanScam.data.ingested === true, "scam text ingested", JSON.stringify(scanScam).slice(0, 160));
if (scanScam && scanScam.data) console.log("    scan: risk_score=" + scanScam.data.risk_score + " verdict=" + JSON.stringify(scanScam.data.verdict)?.slice(0, 100));

// ── 3b. Continuous scanner wiring: a scam scan surfaces via state.latestText,
// and the textscan toggle handler is registered. (Real injection targets a
// normal web tab via the popup's activeTab gesture in live use; here we only
// assert the SW contract — a defined {ok} response — since an invented tabId
// can't be injected.) ────────────────────────────────────────────────────────
console.log("continuous text-scan wiring");
const stAfter = await evalJS(pop, `new Promise((resolve) => {
  chrome.runtime.sendMessage({ type: "antai-get-state" }, resolve);
})`, true);
ok(
  stAfter && stAfter.latestText && typeof stAfter.latestText.risk === "number",
  "scam scan populated state.latestText (continuous-verdict surface)",
  JSON.stringify(stAfter && stAfter.latestText)
);
const toggleResp = await evalJS(pop, `new Promise((resolve) => {
  chrome.runtime.sendMessage({ type: "antai-textscan-toggle", tabId: 999999 }, resolve);
})`, true);
ok(toggleResp && typeof toggleResp.ok === "boolean", "antai-textscan-toggle handler responds with {ok}", JSON.stringify(toggleResp));

// ── 4. Service worker is alive and error-free ────────────────────────────────
console.log("service worker");
const targets = await (await fetch(`${CDP}/json/list`)).json();
const sw = targets.find((t) => t.type === "service_worker" && t.url.includes(EXT_ID));
ok(!!sw, "antAI service worker running", targets.filter(t=>t.type==="service_worker").map(t=>t.url).join(", "));

opt.close(); pop.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

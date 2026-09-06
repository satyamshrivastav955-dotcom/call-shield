// antAI options — persisted via chrome.storage.sync, read by background + offscreen.
// Account (OTP login) talks to the antAI server directly; the token is stored in
// chrome.storage.local (device-local secret, never synced).

const DEFAULTS = {
  serverHost: "localhost:8765",
  scenario: "high_value_txn",
  verifyAt: 50,
  criticalAt: 70,
  cooldownSec: 15,
};

const $ = (id) => document.getElementById(id);

function apiBase(host) {
  const raw = String(host || "").trim();
  const secure = /^wss:\/\//.test(raw) || /^https:\/\//.test(raw);
  const h = raw.replace(/^wss?:\/\//, "").replace(/^https?:\/\//, "").replace(/\/+$/, "");
  return `${secure ? "https" : "http"}://${h}`;
}

function loginMsg(text, isError) {
  const el = $("login-msg");
  el.textContent = text;
  el.style.color = isError ? "#f87171" : "#9ca3af";
}

async function refreshAccount() {
  const { antaiToken } = await chrome.storage.local.get({ antaiToken: "" });
  $("login-form").hidden = !!antaiToken;
  $("logged-in").hidden = !antaiToken;
  $("account-status").textContent = antaiToken ? "· signed in" : "· not signed in";
  $("account-status").style.color = antaiToken ? "#16a34a" : "#6b7280";
  if (antaiToken) {
    const { antaiUser } = await chrome.storage.local.get({ antaiUser: "" });
    $("logged-in-as").textContent = antaiUser ? `Signed in as ${antaiUser}` : "Signed in";
  }
}

document.addEventListener("DOMContentLoaded", async () => {
  const cfg = { ...DEFAULTS, ...(await chrome.storage.sync.get(DEFAULTS)) };
  $("serverHost").value = cfg.serverHost;
  $("scenario").value = cfg.scenario;
  $("verifyAt").value = cfg.verifyAt;
  $("criticalAt").value = cfg.criticalAt;
  $("cooldownSec").value = cfg.cooldownSec;
  refreshAccount();

  // ── Account: OTP login (same endpoints as the Android app) ──────────────
  let otpSentFor = null;
  $("send-otp").addEventListener("click", async () => {
    const phone = $("phone").value.trim();
    if (!/^\+?[0-9]{8,15}$/.test(phone)) {
      loginMsg("Enter a valid phone number (8–15 digits, optional +).", true);
      return;
    }
    try {
      const res = await fetch(`${apiBase($("serverHost").value)}/api/auth/otp`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || data.message || "request failed");
      otpSentFor = phone;
      $("otp").disabled = false;
      $("verify-otp").disabled = false;
      loginMsg(
        data.dev_otp
          ? `OTP sent. Dev mode code: ${data.dev_otp}`
          : "OTP sent to your phone."
      );
    } catch (e) {
      loginMsg("Could not reach the server — check the host above. " + e.message, true);
    }
  });

  $("verify-otp").addEventListener("click", async () => {
    if (!otpSentFor) return;
    const otp = $("otp").value.trim();
    try {
      const res = await fetch(`${apiBase($("serverHost").value)}/api/auth/verify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone: otpSentFor, otp }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || "invalid or expired OTP");
      await chrome.storage.local.set({
        antaiToken: data.token,
        antaiUser: data.display_name || otpSentFor,
      });
      $("otp").value = "";
      $("otp").disabled = true;
      $("verify-otp").disabled = true;
      refreshAccount();
    } catch (e) {
      loginMsg(e.message, true);
    }
  });

  $("signout").addEventListener("click", async () => {
    await chrome.storage.local.remove(["antaiToken", "antaiUser"]);
    refreshAccount();
  });

  // ── Settings ─────────────────────────────────────────────────────────────
  $("save").addEventListener("click", async () => {
    const host = $("serverHost").value.trim().replace(/^wss?:\/\//, "").replace(/\/+$/, "");
    const verifyAt = Math.max(1, Math.min(99, Number($("verifyAt").value) || 50));
    const criticalAt = Math.max(verifyAt + 1, Math.min(100, Number($("criticalAt").value) || 70));
    await chrome.storage.sync.set({
      serverHost: host,
      scenario: $("scenario").value,
      verifyAt,
      criticalAt,
      cooldownSec: Math.max(5, Math.min(300, Number($("cooldownSec").value) || 15)),
    });
    $("serverHost").value = host;
    $("verifyAt").value = verifyAt;
    $("criticalAt").value = criticalAt;
    const s = $("saved");
    s.hidden = false;
    setTimeout(() => (s.hidden = true), 1500);
  });
});

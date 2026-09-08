// antAI platform detection & context — unit tests (no browser, no server).
//
// Tests detectPlatform across Meet, Zoom, Teams, Webex, WhatsApp, and generic pages,
// plus formatStartContext and null/malformed input safety.
//
// Run: node extension/test/platform_test.mjs   (exit 0 = all pass)

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));
const platformPath = join(here, "..", "shared", "platform.js");

// Evaluate in VM context (as loaded in extension or service worker)
const ctx = vm.createContext({ URL, console });
vm.runInContext(readFileSync(platformPath, "utf8"), ctx, { filename: platformPath });
const Platform = ctx.AntaiPlatform;

let pass = 0, fail = 0;
const ok = (c, name, detail) => {
  if (c) {
    pass++;
    console.log("  ✓ " + name);
  } else {
    fail++;
    console.log("  ✗ " + name + (detail ? " — " + detail : ""));
  }
};

console.log("platform module loaded");
ok(!!Platform, "AntaiPlatform global is defined by shared/platform.js");
ok(typeof Platform.detectPlatform === "function", "detectPlatform function exported");
ok(typeof Platform.formatStartContext === "function", "formatStartContext function exported");

console.log("\nGoogle Meet detection");
{
  const p1 = Platform.detectPlatform("https://meet.google.com/abc-defg-hij");
  ok(p1.id === "google_meet" && p1.name === "Google Meet" && p1.icon === "📹" && p1.isMeeting === true,
    "detects https://meet.google.com standard meeting URL");

  const p2 = Platform.detectPlatform("http://meet.google.com/xyz");
  ok(p2.id === "google_meet" && p2.isMeeting === true, "detects http:// URL");

  const p3 = Platform.detectPlatform("meet.google.com/landing");
  ok(p3.id === "google_meet", "detects schemeless URL string");

  const p4 = Platform.detectPlatform("https://sub.meet.google.com/test");
  ok(p4.id === "google_meet", "detects subdomain of meet.google.com");

  const p5 = Platform.detectPlatform(null, "Google Meet: Daily Standup");
  ok(p5.id === "google_meet", "falls back to title when URL is null");
}

console.log("\nZoom detection");
{
  const p1 = Platform.detectPlatform("https://zoom.us/j/1234567890");
  ok(p1.id === "zoom" && p1.name === "Zoom Meeting" && p1.icon === "💻" && p1.isMeeting === true,
    "detects https://zoom.us standard URL");

  const p2 = Platform.detectPlatform("https://us04web.zoom.us/j/987654321");
  ok(p2.id === "zoom" && p2.isMeeting === true, "detects *.zoom.us subdomains");

  const p3 = Platform.detectPlatform("zoom.us/wc/join/123456");
  ok(p3.id === "zoom", "detects schemeless zoom URL");

  const p4 = Platform.detectPlatform("", "Zoom Meeting - Project Review");
  ok(p4.id === "zoom", "falls back to title when URL is empty");
}

console.log("\nMicrosoft Teams detection");
{
  const p1 = Platform.detectPlatform("https://teams.microsoft.com/l/meetup-join/123");
  ok(p1.id === "teams" && p1.name === "Microsoft Teams" && p1.icon === "👥" && p1.isMeeting === true,
    "detects teams.microsoft.com");

  const p2 = Platform.detectPlatform("https://teams.live.com/meet/987654321");
  ok(p2.id === "teams" && p2.name === "Microsoft Teams" && p2.icon === "👥" && p2.isMeeting === true,
    "detects teams.live.com");

  const p3 = Platform.detectPlatform("https://sub.teams.microsoft.com/v2");
  ok(p3.id === "teams", "detects sub.teams.microsoft.com");

  const p4 = Platform.detectPlatform(undefined, "Microsoft Teams Call");
  ok(p4.id === "teams", "falls back to title when URL is undefined");
}

console.log("\nCisco Webex detection");
{
  const p1 = Platform.detectPlatform("https://mycompany.webex.com/meet/user");
  ok(p1.id === "webex" && p1.name === "Cisco Webex" && p1.icon === "🌐" && p1.isMeeting === true,
    "detects *.webex.com subdomain");

  const p2 = Platform.detectPlatform("https://webex.com/join");
  ok(p2.id === "webex" && p2.isMeeting === true, "detects root webex.com");

  const p3 = Platform.detectPlatform(null, "Webex Meeting in progress");
  ok(p3.id === "webex", "falls back to title when URL is null");
}

console.log("\nWhatsApp Web detection");
{
  const p1 = Platform.detectPlatform("https://web.whatsapp.com");
  ok(p1.id === "whatsapp" && p1.name === "WhatsApp Web" && p1.icon === "💬" && p1.isMeeting === false && p1.isChat === true,
    "detects web.whatsapp.com with isMeeting=false, isChat=true");

  const p2 = Platform.detectPlatform("web.whatsapp.com");
  ok(p2.id === "whatsapp" && p2.isChat === true, "detects schemeless web.whatsapp.com");

  const p3 = Platform.detectPlatform("", "WhatsApp Web");
  ok(p3.id === "whatsapp" && p3.isChat === true, "falls back to title when URL is empty");
}

console.log("\nGeneric / Other URLs");
{
  const p1 = Platform.detectPlatform("https://www.google.com");
  ok(p1.id === "generic" && p1.name === "Web Page" && p1.icon === "🌐" && p1.isMeeting === false,
    "identifies standard webpage as generic");

  const p2 = Platform.detectPlatform("https://github.com/google/antAI");
  ok(p2.id === "generic", "identifies GitHub as generic");

  const p3 = Platform.detectPlatform("https://whatsapp.com");
  ok(p3.id === "generic", "regular whatsapp.com marketing site is generic (not web.whatsapp.com)");

  const p4 = Platform.detectPlatform("https://fakezoom.us.attacker.com");
  ok(p4.id === "generic", "phishing domain fakezoom.us.attacker.com is generic (domain ends with attacker.com)");

  const p5 = Platform.detectPlatform("https://notwebex.com");
  ok(p5.id === "generic", "notwebex.com is generic");
}

console.log("\nNull / Undefined / Error safety");
{
  const p1 = Platform.detectPlatform(null, null);
  ok(p1.id === "generic" && p1.isMeeting === false, "null URL & null title returns generic");

  const p2 = Platform.detectPlatform(undefined, undefined);
  ok(p2.id === "generic", "undefined arguments return generic");

  const p3 = Platform.detectPlatform("", "");
  ok(p3.id === "generic", "empty strings return generic");

  const p4 = Platform.detectPlatform(12345, {});
  ok(p4.id === "generic", "non-string arguments return generic without throwing");

  const p5 = Platform.detectPlatform("::: invalid url :::");
  ok(p5.id === "generic", "malformed URL returns generic without throwing");
}

console.log("\nformatStartContext");
{
  const pInfo = Platform.detectPlatform("https://meet.google.com/abc-defg-hij");
  const tab = { url: "https://meet.google.com/abc-defg-hij", title: "Standup" };
  const ctx1 = Platform.formatStartContext(pInfo, tab);
  ok(ctx1.platform === "google_meet", "ctx1 platform is google_meet");
  ok(ctx1.platform_name === "Google Meet", "ctx1 platform_name is Google Meet");
  ok(ctx1.url === "https://meet.google.com/abc-defg-hij", "ctx1 url populated");
  ok(ctx1.title === "Standup", "ctx1 title populated");

  // Inferred platform when platformInfo is null
  const ctx2 = Platform.formatStartContext(null, { url: "https://zoom.us/j/111", title: "Review" });
  ok(ctx2.platform === "zoom" && ctx2.platform_name === "Zoom Meeting", "infers platform from tab when platformInfo is null");
  ok(ctx2.url === "https://zoom.us/j/111" && ctx2.title === "Review", "preserves url and title");

  // Null safety with null tab and null platformInfo
  const ctx3 = Platform.formatStartContext(null, null);
  ok(ctx3.platform === "generic" && ctx3.platform_name === "Web Page", "null platformInfo & null tab fallback to generic");
  ok(ctx3.url === "" && ctx3.title === "", "null tab results in empty url and title");

  // Empty tab object
  const ctx4 = Platform.formatStartContext(null, {});
  ok(ctx4.platform === "generic" && ctx4.url === "" && ctx4.title === "", "empty tab object safely handled");
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

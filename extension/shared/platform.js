// antAI Guardian — Platform detection & context helpers
// Pure global utility module attached to globalThis.

(function (root) {
  function extractHost(url) {
    if (typeof url !== "string") return "";
    const trimmed = url.trim();
    if (!trimmed) return "";
    try {
      const URLClass =
        typeof URL !== "undefined"
          ? URL
          : typeof globalThis !== "undefined"
          ? globalThis.URL
          : root && root.URL;
      if (URLClass) {
        const hasScheme = /^[a-zA-Z][a-zA-Z\d+\-.]*:\/\//.test(trimmed);
        const parsed = new URLClass(hasScheme ? trimmed : `https://${trimmed}`);
        if (parsed.hostname) return parsed.hostname.toLowerCase();
      }
    } catch {}
    // Fallback domain extraction if URL class is unavailable or parsing fails
    const match = trimmed.match(/^(?:[a-zA-Z][a-zA-Z\d+\-.]*:\/\/)?([^/?#:]+)/);
    return match ? match[1].toLowerCase() : "";
  }

  function matchesDomain(host, targetDomain) {
    if (!host || !targetDomain) return false;
    return host === targetDomain || host.endsWith("." + targetDomain);
  }

  function detectPlatform(url, title) {
    const host = extractHost(url);
    const t = typeof title === "string" ? title.toLowerCase() : "";

    // 1. Google Meet: meet.google.com
    if (matchesDomain(host, "meet.google.com") || (!host && t.includes("google meet"))) {
      return {
        id: "google_meet",
        name: "Google Meet",
        icon: "📹",
        isMeeting: true,
      };
    }

    // 2. Zoom: zoom.us or *.zoom.us
    if (matchesDomain(host, "zoom.us") || (!host && t.includes("zoom meeting"))) {
      return {
        id: "zoom",
        name: "Zoom Meeting",
        icon: "💻",
        isMeeting: true,
      };
    }

    // 3. Microsoft Teams: teams.microsoft.com or teams.live.com
    if (
      matchesDomain(host, "teams.microsoft.com") ||
      matchesDomain(host, "teams.live.com") ||
      (!host && t.includes("microsoft teams"))
    ) {
      return {
        id: "teams",
        name: "Microsoft Teams",
        icon: "👥",
        isMeeting: true,
      };
    }

    // 4. Cisco Webex: *.webex.com
    if (matchesDomain(host, "webex.com") || (!host && t.includes("webex"))) {
      return {
        id: "webex",
        name: "Cisco Webex",
        icon: "🌐",
        isMeeting: true,
      };
    }

    // 5. WhatsApp Web: web.whatsapp.com
    if (matchesDomain(host, "web.whatsapp.com") || (!host && t.includes("whatsapp web"))) {
      return {
        id: "whatsapp",
        name: "WhatsApp Web",
        icon: "💬",
        isMeeting: false,
        isChat: true,
      };
    }

    // Default / Other:
    return {
      id: "generic",
      name: "Web Page",
      icon: "🌐",
      isMeeting: false,
    };
  }

  function formatStartContext(platformInfo, tab) {
    const p = platformInfo || detectPlatform(tab && tab.url, tab && tab.title);
    return {
      platform: (p && p.id) || "generic",
      platform_name: (p && p.name) || "Web Page",
      url: (tab && tab.url) || "",
      title: (tab && tab.title) || "",
    };
  }

  const AntaiPlatform = {
    detectPlatform,
    formatStartContext,
  };

  root.AntaiPlatform = AntaiPlatform;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = AntaiPlatform;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

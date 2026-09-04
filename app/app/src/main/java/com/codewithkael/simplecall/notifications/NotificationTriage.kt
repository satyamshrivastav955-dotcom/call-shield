package com.codewithkael.simplecall.notifications

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification

/**
 * Decides whether a captured notification is worth sending to the antAI server.
 *
 * Why this exists
 * ---------------
 * The listener used to forward *every* notification with non-blank text, so a
 * Spotify "Now playing" line and every message in every WhatsApp group went
 * through the full LangGraph pipeline and an LLM call. That is both expensive
 * and noisy: the classifiers have no reject class, so out-of-distribution text
 * still produced a label, and the user saw a scam alert on their music.
 *
 * The gate here is deliberately cheap and deterministic — no model, no network.
 * It answers one question: does this notification contain the kind of language a
 * scam or fraud attempt uses? Only then do we spend a server round trip.
 *
 * This is a *relevance* filter, not a verdict. Passing the gate means "worth
 * analysing"; the server still decides whether anything is actually wrong.
 */
object NotificationTriage {

    sealed interface Decision {
        /** Not worth analysing. [reason] is for logcat only. */
        data class Drop(val reason: String) : Decision

        /** Send to the server. [matched] lists the terms that made it relevant. */
        data class Analyze(val matched: List<String>) : Decision
    }

    fun decide(sbn: StatusBarNotification, title: String, text: String): Decision {
        val pkg = sbn.packageName ?: return Decision.Drop("no package")
        val n = sbn.notification ?: return Decision.Drop("no notification")

        // ---- structural drops: things that are not messages at all ----------
        if (pkg in IGNORED_PACKAGES) return Decision.Drop("package denylisted")
        if (IGNORED_PREFIXES.any { pkg.startsWith(it) })
            return Decision.Drop("system/OEM package")

        // A media-session notification is a transport control (Spotify, YouTube
        // Music, any player). Checking the extra rather than the package name
        // catches every player, including ones we have never heard of.
        if (n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true)
            return Decision.Drop("media session")

        n.category?.let { if (it in IGNORED_CATEGORIES) return Decision.Drop("category=$it") }

        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0)
            return Decision.Drop("ongoing")
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
            return Decision.Drop("group summary")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        ) return Decision.Drop("foreground service")

        if (text.isBlank()) return Decision.Drop("blank text")

        // ---- content drops --------------------------------------------------
        val body = buildString {
            if (title.isNotBlank()) append(title).append(' ')
            append(text)
        }
        if (isLowContent(text)) return Decision.Drop("low-content / greeting")

        // ---- relevance ------------------------------------------------------
        val strong = STRONG_TERMS.filter { body.contains(it, ignoreCase = true) }
        val weak = WEAK_TERMS.filter { body.contains(it, ignoreCase = true) }.toMutableList()
        if (MONEY.containsMatchIn(body)) weak += "<money-amount>"
        if (URL.containsMatchIn(body)) weak += "<url>"
        if (CODE.containsMatchIn(body) &&
            CODE_CONTEXT.any { body.contains(it, ignoreCase = true) }
        ) return Decision.Analyze(listOf("<verification-code>") + strong + weak)

        // Group chats are the biggest false-positive source: ordinary banter
        // mentions money, links and urgency all the time. Require an
        // unambiguous term there, not just two everyday ones.
        val isGroup = isGroupConversation(n)
        return when {
            strong.isNotEmpty() -> Decision.Analyze(strong + weak)
            isGroup -> Decision.Drop("group chat without a strong scam term")
            weak.size >= 2 -> Decision.Analyze(weak)
            else -> Decision.Drop("no scam-related terms")
        }
    }

    private fun isGroupConversation(n: Notification): Boolean {
        val extras = n.extras ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        ) return true
        return extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()?.isNotBlank() == true
    }

    /** True for greetings and one-word replies, which carry nothing to score. */
    fun isLowContent(text: String): Boolean {
        val norm = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (norm.isEmpty()) return true
        if (ALWAYS_ANALYSE.any { text.contains(it, ignoreCase = true) }) return false
        if (norm in GREETINGS) return true
        val words = norm.split(' ').filter { it.isNotEmpty() }
        if (words.none { it !in GREETINGS }) return true
        return words.size < 3 && norm.none { it.isDigit() }
    }

    // ------------------------------------------------------------ lexicons
    // Kept intentionally in sync with server/src/antai/inference/text/triage.py.

    /** SMS is captured by SmsReceiver, so skip SMS apps to avoid double-scoring. */
    private val IGNORED_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.android.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "android",
        "com.android.systemui",
        "com.android.providers.downloads",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.apps.nbu.paisa.user", // GPay: its own txn receipts
        "com.spotify.music",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "in.startv.hotstar",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "com.google.android.deskclock",
        "com.google.android.calendar",
        "com.android.settings",
        "com.miui.player",
        "com.samsung.android.app.music.chn"
    )

    private val IGNORED_PREFIXES = listOf(
        "com.android.",
        "com.google.android.gms",
        "com.miui.",
        "com.samsung.android.launcher",
        "com.sec.android",
        "com.oneplus.",
        "com.oppo.",
        "com.vivo.",
        "com.coloros.",
        "com.transsion."
    )

    private val IGNORED_CATEGORIES = setOf(
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_STATUS,
        Notification.CATEGORY_RECOMMENDATION
    )

    private val GREETINGS = setOf(
        "hi", "hii", "hiii", "hiiii", "hello", "helo", "hey", "heyy", "yo", "sup",
        "ok", "okk", "okay", "oky", "k", "kk", "hmm", "hm", "hmmm", "oh", "ah",
        "thanks", "thank you", "thanku", "thankyou", "ty", "tysm", "welcome",
        "good morning", "good afternoon", "good evening", "good night", "gm", "gn",
        "bye", "byee", "goodbye", "see you", "cya", "ttyl", "gtg",
        "sure", "yes", "yeah", "yep", "yup", "no", "nope", "nah", "maybe",
        "lol", "lmao", "haha", "hahaha", "hehe", "wow", "nice", "cool", "great",
        "congrats", "cheers", "sorry", "np", "no problem", "same", "done", "got it",
        "namaste", "namaskar", "hanji", "haanji", "ji", "arre", "arey",
        "theek hai", "thik hai", "theek", "accha", "acha", "achha", "haan", "han",
        "nahi", "nai", "kya", "kya hua", "kaise ho", "kaisi ho", "kaise hai",
        "sab thik", "sab theek", "koi baat nahi", "chalo", "bas",
        "नमस्ते", "नमस्कार", "हाँ", "हां", "नहीं", "ठीक है", "अच्छा", "धन्यवाद"
    )

    /** Tokens that justify analysing even a two-word message ("send otp"). */
    private val ALWAYS_ANALYSE = listOf(
        "otp", "cvv", "upi", "ifsc", "kyc", "aadhaar", "aadhar", "pan card",
        "password", "netbanking", "anydesk", "teamviewer", "quicksupport",
        "http://", "https://", "www.", ".apk", "bitcoin", "usdt",
        "ओटीपी", "पासवर्ड"
    )

    private val STRONG_TERMS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "verify code", "security code", "cvv", "atm pin", "upi pin", "mpin",
        "netbanking", "net banking", "ifsc", "kyc", "re-kyc", "aadhaar", "aadhar",
        "pan card", "debit card", "credit card", "card number", "card details",
        "account suspended", "account blocked", "account will be blocked",
        "account has been blocked", "account frozen", "account deactivated",
        "anydesk", "teamviewer", "quicksupport", "remote access", "screen share",
        "screen sharing", "install this app", "download this apk",
        "lottery", "lucky draw", "you have won", "you've won", "prize money",
        "claim your prize", "cash prize", "jackpot",
        "digital arrest", "arrest warrant", "cyber crime", "cybercrime branch",
        "parcel seized", "customs clearance", "money laundering",
        "income tax notice", "legal action will be taken", "court notice",
        "work from home job", "part time job", "earn daily", "earn per day",
        "double your money", "guaranteed returns", "guaranteed profit",
        "gift card", "google play card", "itunes card", "steam card",
        "send money urgently", "need money urgently", "wire transfer",
        "bitcoin", "usdt", "crypto investment", "trading signal",
        "ओटीपी", "खाता बंद", "खाता ब्लॉक", "लॉटरी", "गिरफ्तार", "पैसे भेजो"
    )

    private val WEAK_TERMS = listOf(
        "urgent", "urgently", "immediately", "right now", "asap", "hurry",
        "expire", "expires", "expiring", "expired", "last chance", "final notice",
        "last warning", "within 24 hours", "act now", "limited time",
        "bank", "banking", "account", "transaction", "transfer", "payment",
        "paid", "refund", "deposit", "withdraw", "balance", "amount", "fee",
        "charges", "penalty", "fine", "loan", "emi", "insurance", "policy",
        "upi", "paytm", "phonepe", "gpay", "google pay", "wallet", "rupees",
        "password", "login", "sign in", "credentials", "username", "user id",
        "click here", "click the link", "click below", "tap here", "open link",
        "verify", "verification", "confirm", "update your", "resubmit",
        "congratulations", "congrats", "selected", "winner", "bonus",
        "reward", "cashback", "voucher",
        "customer care", "customer support", "helpdesk", "help desk",
        "support team", "bank official", "officer", "government",
        "do not tell", "don't tell", "keep it secret", "confidential",
        "emergency", "accident", "hospital", "police", "court",
        "bit.ly", "tinyurl",
        "तुरंत", "जल्दी", "पैसा", "पैसे", "रुपये", "खाता", "बैंक", "लिंक",
        "इनाम", "मुफ्त", "जीत", "पुलिस", "अस्पताल"
    )

    private val CODE_CONTEXT = listOf("code", "otp", "pin", "verify", "verification", "ओटीपी")
    private val CODE = Regex("\\b\\d{4,8}\\b")
    private val MONEY = Regex("(?:₹|rs\\.?|inr|\\$|usd)\\s*[\\d,]+|\\b[\\d,]{3,}\\s*(?:rupees|rs\\.?|inr)\\b",
        RegexOption.IGNORE_CASE)
    private val URL = Regex("https?://|www\\.|\\b[\\w-]+\\.(?:com|net|in|org|co|xyz|link|info|top)\\b",
        RegexOption.IGNORE_CASE)
}

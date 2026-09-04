"""Cheap, deterministic text triage that runs BEFORE any model.

Why this exists
---------------
The scam/intent/urgency classifiers are fine-tuned on a small templated corpus
of full sentences. Anything outside that distribution — a bare "hi", a Spotify
"Now playing" line, group-chat banter — is scored by a softmax that has no
reject option, so the models confidently emit *something*. That is how a
one-word greeting ended up raising a scam alert.

The fix is a gate, not a bigger model: text with no scam-relevant content is
never handed to the classifiers at all, so it cannot produce a signal, so it
cannot produce an alert. This module is pure Python (no deps, microseconds per
call) and is the server-side twin of the Android-side NotificationTriage.

Nothing here decides that something *is* a scam. It only decides whether a
string is worth spending a model on.
"""
from __future__ import annotations

import re
import unicodedata

# --------------------------------------------------------------- normalisation
_PUNCT = re.compile(r"[^\w\sऀ-ॿ]+", re.UNICODE)
_WS = re.compile(r"\s+")


def normalize(text: str) -> str:
    """Lowercase, strip emoji/punctuation, collapse whitespace."""
    if not text:
        return ""
    # drop emoji / symbols / other non-letter marks
    cleaned = "".join(
        ch for ch in unicodedata.normalize("NFKC", text)
        if not unicodedata.category(ch).startswith("So")
    )
    cleaned = _PUNCT.sub(" ", cleaned.lower())
    return _WS.sub(" ", cleaned).strip()


# ------------------------------------------------------------------ greetings
# Short social pleasantries in English, Hindi and Hinglish. A message that
# reduces to exactly one of these carries no analysable content.
_GREETINGS = {
    "hi", "hii", "hiii", "hiiii", "hello", "helo", "hey", "heyy", "yo", "sup",
    "ok", "okk", "okay", "oky", "k", "kk", "hmm", "hm", "hmmm", "oh", "ah",
    "thanks", "thank you", "thanku", "thankyou", "ty", "tysm", "welcome",
    "good morning", "good afternoon", "good evening", "good night", "gm", "gn",
    "bye", "byee", "goodbye", "see you", "cya", "ttyl", "gtg",
    "sure", "yes", "yeah", "yep", "yup", "no", "nope", "nah", "maybe",
    "lol", "lmao", "haha", "hahaha", "hehe", "wow", "nice", "cool", "great",
    "congrats", "cheers", "sorry", "np", "no problem", "same", "done", "got it",
    "namaste", "namaskar", "hanji", "haanji", "ji", "ji hello", "arre",
    "theek hai", "thik hai", "theek", "accha", "acha", "achha", "haan", "han",
    "nahi", "nai", "kya", "kya hua", "kaise ho", "kaisi ho", "kaise hai",
    "sab thik", "sab theek", "koi baat nahi", "chalo", "bas", "arey",
    "नमस्ते", "नमस्कार", "हाँ", "हां", "नहीं", "ठीक है", "अच्छा", "धन्यवाद",
    "शुभ प्रभात", "शुभ रात्रि", "क्या हुआ", "कैसे हो",
}

# Tokens that make even a very short message worth analysing ("send otp",
# "pay now", "click link"). Deliberately narrow — these are the words that
# only appear in a request, not in chatter.
_ALWAYS_ANALYSE = (
    "otp", "cvv", "pin", "upi", "ifsc", "kyc", "aadhaar", "aadhar", "pan card",
    "password", "passcode", "netbanking", "net banking", "atm",
    "anydesk", "teamviewer", "quicksupport",
    "http://", "https://", "www.", ".apk",
    "bitcoin", "usdt", "crypto",
    "ओटीपी", "पासवर्ड", "बैंक खाता",
)

# Scam / fraud lexicon. STRONG terms are rarely innocent in a message you did
# not expect; WEAK terms are common in ordinary speech and only count when two
# or more of them co-occur.
_STRONG = (
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
    "narcotics", "parcel seized", "customs clearance", "money laundering",
    "income tax notice", "legal action will be taken", "court notice",
    "work from home job", "part time job offer", "earn daily", "earn per day",
    "double your money", "guaranteed returns", "guaranteed profit",
    "gift card", "google play card", "itunes card", "steam card",
    "send money urgently", "need money urgently", "wire transfer",
    "bitcoin", "usdt", "crypto investment", "trading signal",
    "ओटीपी", "खाता बंद", "खाता ब्लॉक", "लॉटरी", "इनाम जीता", "गिरफ्तार",
    "पैसे भेजो", "पैसा भेजो", "तुरंत पैसे",
)

_WEAK = (
    "urgent", "urgently", "immediately", "right now", "asap", "hurry",
    "expire", "expires", "expiring", "expired", "last chance", "final notice",
    "last warning", "within 24 hours", "act now", "limited time",
    "bank", "banking", "account", "transaction", "transfer", "payment", "pay",
    "paid", "refund", "deposit", "withdraw", "balance", "amount", "fee",
    "charges", "penalty", "fine", "loan", "emi", "insurance", "policy",
    "upi", "paytm", "phonepe", "gpay", "google pay", "wallet", "rupees",
    "rs.", "inr", "₹", "$", "usd", "dollars",
    "password", "login", "sign in", "credentials", "username", "user id",
    "click here", "click the link", "click below", "tap here", "open link",
    "verify", "verification", "confirm", "update your", "re-submit", "resubmit",
    "congratulations", "congrats", "selected", "winner", "free", "bonus",
    "offer", "reward", "cashback", "discount", "voucher",
    "customer care", "customer support", "helpdesk", "help desk",
    "support team", "bank official", "manager", "officer", "government",
    "do not tell", "don't tell", "keep it secret", "confidential",
    "emergency", "accident", "hospital", "police", "court", "case",
    "http://", "https://", "www.", ".apk", "bit.ly", "tinyurl",
    "तुरंत", "जल्दी", "पैसा", "पैसे", "रुपये", "खाता", "बैंक", "लिंक",
    "इनाम", "मुफ्त", "जीत", "सत्यापन", "पुलिस", "अस्पताल",
)

_DIGIT_CODE = re.compile(r"\b\d{4,8}\b")
_MONEY = re.compile(r"(?:₹|rs\.?|inr|\$|usd)\s*[\d,]+|\b[\d,]{3,}\s*(?:rupees|rs\.?|inr)\b")
_URL = re.compile(r"https?://|www\.|\b[\w-]+\.(?:com|net|in|org|co|xyz|link|info|top)\b")


# --------------------------------------------------------------------- checks
def is_low_content(text: str) -> bool:
    """True when the message is a greeting / one-word reply with nothing to score.

    A message is low-content when, after normalisation, it is either exactly a
    known pleasantry or shorter than two words — *unless* it contains a token
    that only ever shows up in a request (see ``_ALWAYS_ANALYSE``), so "send
    otp" is still analysed despite being two words.
    """
    norm = normalize(text)
    if not norm:
        return True
    low = text.lower()
    if any(tok in low for tok in _ALWAYS_ANALYSE):
        return False
    if norm in _GREETINGS:
        return True
    words = norm.split()
    # strip pleasantry words, then see whether anything substantive is left
    residue = [w for w in words if w not in _GREETINGS]
    if not residue:
        return True
    if len(words) < 3 and not any(ch.isdigit() for ch in norm):
        return True
    return False


def scam_relevance(text: str) -> dict:
    """Score how scam-relevant a string looks, using keywords only.

    Returns ``{relevant, strong, weak, matched, reason}``. ``relevant`` is True
    when at least one STRONG term, or at least two WEAK terms, or a
    money-amount/URL plus one WEAK term appear. Two independent weak hits are
    required so that a lone "payment" or a lone link — both ordinary in group
    chats — does not trip the gate.
    """
    if not text:
        return {"relevant": False, "strong": [], "weak": [], "matched": [],
                "reason": "empty"}
    low = text.lower()
    strong = [t for t in _STRONG if t in low]
    weak = [t for t in _WEAK if t in low]

    has_money = bool(_MONEY.search(low))
    has_url = bool(_URL.search(low))
    has_code = bool(_DIGIT_CODE.search(low))
    if has_money:
        weak.append("<money-amount>")
    if has_url and not any(u in weak for u in ("http://", "https://", "www.")):
        weak.append("<url>")
    if has_code and any(t in low for t in ("code", "otp", "pin", "verify", "verification")):
        strong.append("<numeric-code-with-verification-word>")

    matched = strong + weak
    if strong:
        reason = "strong-term"
        relevant = True
    elif len(weak) >= 2:
        reason = "two-weak-terms"
        relevant = True
    else:
        reason = "no-scam-signal"
        relevant = False
    return {"relevant": relevant, "strong": strong, "weak": weak,
            "matched": matched, "reason": reason}


def should_analyse(text: str) -> dict:
    """Combined gate: skip low-content text, then require scam relevance.

    Used by the notification path (``/api/notify/external``) so only plausibly
    fraudulent notifications cost an LLM call. Direct messages are NOT gated
    this way — they always run the classifiers — because the user explicitly
    chose to read them, and a scam text that avoids our lexicon should still be
    scored by the models.
    """
    if is_low_content(text):
        return {"analyse": False, "reason": "low-content", "matched": []}
    rel = scam_relevance(text)
    return {"analyse": rel["relevant"], "reason": rel["reason"],
            "matched": rel["matched"]}

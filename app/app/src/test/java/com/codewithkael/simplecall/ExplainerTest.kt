package com.codewithkael.simplecall

import com.codewithkael.simplecall.ai.Explainer
import org.junit.Assert.*
import org.junit.Test

/**
 * #10: the on-device explanation must read as plain language, never raw engine
 * tokens. These pin the humanization map (and its graceful fallback) so a future
 * signal rename can't silently leak "voice_deepfake" / "request:money" to users.
 *
 * The raw hard/soft token lists are intentionally NOT changed by humanization —
 * only the display string is — so fusion logic and the verdict log keep keying
 * off the stable tokens. (That contract is covered by FusionEngineTest.)
 */
class ExplainerTest {

    @Test fun humanizesKnownVoiceAndIdentityTokens() {
        assertEquals("synthetic (cloned) voice", Explainer.humanizeSignal("voice_deepfake"))
        assertEquals("possibly synthetic voice", Explainer.humanizeSignal("spoof_suspect"))
        assertEquals("voice doesn't match the saved contact", Explainer.humanizeSignal("identity_mismatch"))
        assertEquals("caller's voice unconfirmed", Explainer.humanizeSignal("identity_unconfirmed"))
    }

    @Test fun humanizesRequestTokensByType() {
        assertEquals("a request for money", Explainer.humanizeSignal("request:money"))
        assertEquals("a request for an OTP", Explainer.humanizeSignal("request:otp"))
        assertEquals("a request for passwords", Explainer.humanizeSignal("request:credential"))
        assertEquals("pressure to open a link", Explainer.humanizeSignal("request:link"))
        // Unknown request subtype still reads as a request, not raw token.
        assertEquals("a suspicious request", Explainer.humanizeSignal("request:gift_card"))
    }

    @Test fun unknownTokenDegradesToDeUnderscoredText() {
        // A brand-new token must never leak snake_case to the user.
        assertEquals("some new signal", Explainer.humanizeSignal("some_new_signal"))
    }

    @Test fun explanationContainsNoRawTokens() {
        // End-to-end: the assembled explanation string is plain language.
        val out = Explainer.explain(
            risk = 82f,
            hard = listOf("voice_deepfake", "identity_mismatch"),
            soft = listOf("urgency"),
        )
        assertFalse("no raw token should survive", out.contains("voice_deepfake"))
        assertFalse(out.contains("identity_mismatch"))
        assertTrue(out.contains("synthetic (cloned) voice"))
        assertTrue(out.contains("High risk"))
        // High-risk action guidance is present.
        assertTrue(out.contains("Hang up"))
    }

    @Test fun capsAtThreeSignals() {
        val out = Explainer.humanizeSignals(
            listOf("voice_deepfake", "identity_mismatch", "urgency", "scam_pattern"),
        )
        // 4 in, 3 shown -> two comma separators.
        assertEquals(2, out.count { it == ',' })
        assertFalse(out.contains("known scam script"))
    }

    @Test fun hindiLocaleIsTranslated() {
        val out = Explainer.humanizeSignal("voice_deepfake", lang = "hi")
        assertEquals("नकली (क्लोन) आवाज़", out)
    }
}

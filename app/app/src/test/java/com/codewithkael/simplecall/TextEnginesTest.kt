package com.codewithkael.simplecall

import com.codewithkael.simplecall.ai.TextEngines
import org.junit.Assert.*
import org.junit.Test

class TextEnginesTest {

    @Test fun benignText_lowScam() {
        val r = TextEngines.scamHeuristic("hello, how are you doing today?")
        assertTrue(r.prob < 0.2f)
    }

    @Test fun otpScam_flagged() {
        val r = TextEngines.scamHeuristic(
            "Your bank account blocked, share your OTP immediately on this call",
        )
        assertTrue(r.prob >= 0.5f)
        assertNotNull(r.type)
    }

    @Test fun hindiScam_flagged() {
        val r = TextEngines.scamHeuristic("aapka khata band ho jayega, turant OTP batao")
        assertTrue(r.prob >= 0.5f)
    }

    @Test fun loneKeyword_modest() {
        val r = TextEngines.scamHeuristic("my bank is nearby")
        assertTrue(r.prob <= 0.4f)
    }

    @Test fun intentMoney_detected() {
        val r = TextEngines.intentHeuristic("please UPI paisa bhejo right now")
        assertTrue(r.detected)
        assertEquals("money", r.type)
    }

    @Test fun urgencyCalm_low() {
        assertTrue(TextEngines.urgencyHeuristic("hello, take your time") < 40f)
    }

    @Test fun urgencyThreat_high() {
        assertTrue(
            TextEngines.urgencyHeuristic("URGENT! account blocked, police case ho jayega!!") > 60f,
        )
    }

    // ---------------- Phase 1.2b: whole-word matcher + expanded dictionaries ----------------

    @Test fun wholeWord_rejectsSubstringFalsePositives() {
        // "suitcase"/"staircase" contain "case" and "premier" contains "emi";
        // the OLD blanket substring matcher scored these ~0.55 (case + emi = 2
        // hits). Whole-word matching means none of those keywords fire here.
        val r = TextEngines.scamHeuristic(
            "I left it in the suitcase on the staircase, watching the premier league",
        )
        assertTrue("substring false-positive leaked: prob=${r.prob}", r.prob < 0.2f)
    }

    @Test fun digitalArrest_flagged_withType() {
        val r = TextEngines.scamHeuristic(
            "This is CBI, you are under digital arrest, do not disconnect, it is money laundering",
        )
        assertTrue(r.prob >= 0.75f)
        assertEquals("digital_arrest", r.type)
    }

    @Test fun hinglishDigitalArrest_flagged() {
        val r = TextEngines.scamHeuristic(
            "video call par raho, camera on rakho, warna giraftar hoga",
        )
        assertTrue(r.prob >= 0.5f)
    }

    @Test fun remoteAccessIntent_detected() {
        val r = TextEngines.intentHeuristic("download the app anydesk for screen share")
        assertTrue(r.detected)
        assertEquals("remote-access", r.type)
    }

    @Test fun benignChat_stillLowScam_afterExpansion() {
        // Guards the expanded dictionary against drift: ordinary chatter must
        // stay well below the alert floor.
        for (s in listOf(
            "hey are we still on for lunch tomorrow?",
            "the match was great, our team finally won",
            "can you send me the photos from the trip",
        )) {
            assertTrue("benign leaked: '$s' -> ${TextEngines.scamHeuristic(s).prob}",
                TextEngines.scamHeuristic(s).prob < 0.4f)
        }
    }
}

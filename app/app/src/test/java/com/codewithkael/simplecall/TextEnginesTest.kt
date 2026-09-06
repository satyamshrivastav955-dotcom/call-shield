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
}

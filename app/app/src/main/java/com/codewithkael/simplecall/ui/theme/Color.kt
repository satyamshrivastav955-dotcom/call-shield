package com.codewithkael.simplecall.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * antAI brand palette — a calm "guardian" identity for a scam/deepfake shield.
 *
 * Direction (Truecaller × WhatsApp): trustworthy and calm, never alarmist.
 * Anchored on a deep teal primary (trust + shield), a WhatsApp-style send-green
 * for actions, and a clear risk semantic scale used by the verdict chips that
 * appear consistently across calls and messages.
 *
 * These are pure design tokens (no logic). Risk-band colors intentionally match
 * the values already used by the in-call AiInsightWindow so the whole app speaks
 * one visual language.
 */

// ---- CallShield Brand / Primary (Deep Forest Green & Teal) ----
val AntaiTeal = Color(0xFF0F392E)          // CallShield deep forest green primary
val AntaiTealDark = Color(0xFF09231C)      // pressed / dark-scheme primary
val AntaiTealLight = Color(0xFF4E8A68)     // sage green on dark surfaces
val AntaiTealContainer = Color(0xFFD5E8DD) // soft sage container
val AntaiOnTealContainer = Color(0xFF041A13)

// ---- Secondary Action & Accents (CallShield Leaf Sage Green) ----
val AntaiGreen = Color(0xFF4F8B68)         // CallShield leaf green
val AntaiGreenContainer = Color(0xFFD7ECE0)
val AntaiOnGreenContainer = Color(0xFF0C2B1D)

// ---- Neutrals (Warm Cream & Clean White) ----
val AntaiBackground = Color(0xFFF8F6F0)    // CallShield warm cream canvas
val AntaiSurface = Color(0xFFFFFFFF)       // cards, sheets, bars
val AntaiSurfaceVariant = Color(0xFFEEEDE8) // subtle fills (search, bubble in)
val AntaiInk = Color(0xFF0E1A16)           // primary text
val AntaiMuted = Color(0xFF5D6B65)         // secondary text
val AntaiHairline = Color(0xFFDFE3DD)      // dividers / outlines

// ---- Risk semantic scale (Truecaller-style badges) ----
// passive/safe -> verify/caution -> critical/high-risk
val RiskSafe = Color(0xFF2E7D32)
val RiskSafeBg = Color(0xFFE7F4E8)
val RiskCaution = Color(0xFFF57C00)
val RiskCautionBg = Color(0xFFFFF3E0)
val RiskCritical = Color(0xFFC62828)
val RiskCriticalBg = Color(0xFFFDECEA)

// ---- Chat bubbles ----
val BubbleOutgoing = Color(0xFFD8EFE2)     // messages I sent (CallShield soft tint)
val BubbleIncoming = Color(0xFFFFFFFF)     // messages received

// ---- Dark scheme (used by system dark mode) ----
val AntaiInkDark = Color(0xFF081510)
val AntaiSurfaceDark = Color(0xFF0F211A)
val AntaiSurfaceVariantDark = Color(0xFF182D24)
val AntaiOnDark = Color(0xFFE4EDE7)
val AntaiMutedDark = Color(0xFF90A39A)
val AntaiHairlineDark = Color(0xFF233A30)

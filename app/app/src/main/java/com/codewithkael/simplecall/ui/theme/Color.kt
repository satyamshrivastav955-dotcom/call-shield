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

// ---- Brand / primary (deep teal) ----
val AntaiTeal = Color(0xFF0E7C7B)          // primary brand
val AntaiTealDark = Color(0xFF0A5F5E)      // pressed / dark-scheme primary
val AntaiTealLight = Color(0xFF3AA6A4)     // primary on dark surfaces
val AntaiTealContainer = Color(0xFFD1ECEB) // tinted container (chips, selected)
val AntaiOnTealContainer = Color(0xFF00201F)

// ---- Secondary action (WhatsApp-style send green) ----
val AntaiGreen = Color(0xFF1EA362)
val AntaiGreenContainer = Color(0xFFD6F3E3)
val AntaiOnGreenContainer = Color(0xFF00210F)

// ---- Neutrals (cool, calm) ----
val AntaiBackground = Color(0xFFF6F7F9)    // app background
val AntaiSurface = Color(0xFFFFFFFF)       // cards, sheets, bars
val AntaiSurfaceVariant = Color(0xFFEFF1F4) // subtle fills (search, bubbles-in)
val AntaiInk = Color(0xFF111827)           // primary text (slate near-black)
val AntaiMuted = Color(0xFF6B7280)         // secondary text
val AntaiHairline = Color(0xFFE3E6EA)      // dividers / outlines

// ---- Risk semantic scale (Truecaller-style badges) ----
// passive/safe -> verify/caution -> critical/high-risk
val RiskSafe = Color(0xFF2E7D32)
val RiskSafeBg = Color(0xFFE7F4E8)
val RiskCaution = Color(0xFFF57C00)
val RiskCautionBg = Color(0xFFFFF3E0)
val RiskCritical = Color(0xFFC62828)
val RiskCriticalBg = Color(0xFFFDECEA)

// ---- Chat bubbles ----
val BubbleOutgoing = Color(0xFFDCF6EC)     // messages I sent (soft green tint)
val BubbleIncoming = Color(0xFFFFFFFF)     // messages received

// ---- Dark scheme (used by system dark mode; call screen uses its own overlays) ----
val AntaiInkDark = Color(0xFF0E1512)
val AntaiSurfaceDark = Color(0xFF161D1B)
val AntaiSurfaceVariantDark = Color(0xFF202826)
val AntaiOnDark = Color(0xFFE6EAE8)
val AntaiMutedDark = Color(0xFF9AA5A2)
val AntaiHairlineDark = Color(0xFF2C3532)

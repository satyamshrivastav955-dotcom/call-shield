package com.codewithkael.simplecall.shield

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real system overlay (#5): a floating risk card drawn over other apps via
 * TYPE_APPLICATION_OVERLAY, so a clone/scam alert is visible even when antAI is
 * in the background. Requires the SYSTEM_ALERT_WINDOW runtime grant
 * (Settings.canDrawOverlays) — the ShieldService only calls show() and any
 * failure is swallowed, so a missing grant degrades to notification-only.
 *
 * Phase 2.1 styling: high-contrast, band-coded card — CRIMSON for CRITICAL,
 * AMBER for VERIFY — with a translucent (~95% alpha) near-black backdrop faintly
 * cast in the band colour so the whole card reads as the alert, not just its
 * border. The card is a floating PILL (wrap-content, capped at 85% width), and
 * [topInsetPx] positions it below the status bar AND any display cutout /
 * punch-hole camera. Both the fill and stroke are re-applied on every [show], so
 * a VERIFY card that escalates to CRITICAL recolours in place.
 *
 * THRESHOLD GATE (who decides when this pops): this class only draws what it is
 * told; the "pop only when risk >= VERIFY/CRITICAL or synthetic voice/fraud
 * confirmed" rule lives in ShieldService.onResult, which calls show() only for
 * elevated bands. That band already integrates confirmed synthetic-voice + fraud
 * signals via FusionEngine; a lone, uncorroborated spoof is intentionally
 * dead-zone-capped to passive (Phase 1.2a), so it correctly does NOT pop here —
 * adding a separate lone-spoof trigger would re-introduce the false positives
 * that gate was built to remove.
 *
 * All WindowManager mutations are marshalled to the main thread because the mic
 * pipeline runs on Dispatchers.Default.
 */
@Singleton
class ShieldOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var titleView: TextView? = null
    private var bodyView: TextView? = null
    // Phase 2.3 — one-tap hang-up control (the "manual HANG-UP FAB" for calls the
    // Shield is watching from the background). Its click is wired by ShieldService
    // to CellularCallController; for a call it can't end programmatically (e.g. a
    // third-party VoIP call), ShieldService falls back to an honest manual hint.
    private var hangupView: TextView? = null
    private var hangupBg: android.graphics.drawable.GradientDrawable? = null
    private var onHangUp: (() -> Unit)? = null
    // Held so the card can be RE-COLOURED on every show() (band escalation).
    // Previously the stroke/fill were set only at first creation, so a verify
    // card that escalated to critical kept a stale amber border with red text.
    private var rootBg: android.graphics.drawable.GradientDrawable? = null

    private companion object {
        // Phase 2.1 palette — high-contrast, band-coded (ARGB ints).
        val CRIMSON = 0xFFFF3B5C.toInt()   // CRITICAL accent (crimson)
        val AMBER = 0xFFFFB300.toInt()      // VERIFY accent (amber)
        // Translucent (~95% alpha) near-black backdrops with a faint band cast,
        // so the whole card reads as the alert colour, not just its border.
        val BG_CRITICAL = 0xF21A0B0F.toInt()
        val BG_VERIFY = 0xF21A150A.toInt()
        val BODY = 0xFFE6E6E6.toInt()
    }

    fun canDraw(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(risk: Int, band: String, explanation: String, onHangUp: (() -> Unit)? = null) {
        if (!canDraw()) return
        this.onHangUp = onHangUp
        main.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val critical = band == "critical"
                val accentColor = if (critical) CRIMSON else AMBER
                val bgColor = if (critical) BG_CRITICAL else BG_VERIFY
                // Reuse one drawable so band escalation recolours the SAME card.
                val bgDrawable = (rootBg ?: android.graphics.drawable.GradientDrawable()).apply {
                    setColor(bgColor)
                    cornerRadius = 36f
                    setStroke(5, accentColor)
                }
                rootBg = bgDrawable

                if (view == null) {
                    val root = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(32, 28, 24, 28)
                        background = bgDrawable
                        elevation = 20f
                    }

                    val textLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    titleView = TextView(context).apply {
                        textSize = 15f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        setPadding(0, 0, 0, 8)
                    }

                    bodyView = TextView(context).apply {
                        setTextColor(BODY)
                        textSize = 13f
                        setLineSpacing(4f, 1.1f)
                    }

                    // Phase 2.3 — one-tap hang-up button. Hidden unless an onHangUp
                    // handler is supplied for this show(); its label/action never
                    // implies a disconnect the app can't actually perform (the
                    // handler decides, and falls back to a manual hint honestly).
                    hangupView = TextView(context).apply {
                        text = "⛔  End this call"
                        textSize = 14f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(28, 20, 28, 20)
                        visibility = View.GONE
                        setOnClickListener { this@ShieldOverlay.onHangUp?.invoke() }
                    }

                    textLayout.addView(titleView)
                    textLayout.addView(bodyView)
                    textLayout.addView(hangupView)
                    root.addView(textLayout)

                    val closeBtn = TextView(context).apply {
                        text = "✕"
                        setTextColor(Color.parseColor("#B0BEC5"))
                        textSize = 18f
                        setPadding(20, 0, 10, 0)
                        setOnClickListener { hide() }
                    }
                    root.addView(closeBtn)

                    view = root

                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                    // Floating PILL, not a full-width bar (Bug 4): wrap-content
                    // width capped at 85% of screen width, so it reads as a
                    // floating alert card instead of overlapping bands of UI.
                    val maxW = (context.resources.displayMetrics.widthPixels * 0.85f).toInt()
                    val lp = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        // Offset below the status bar / display cutout instead of a
                        // hardcoded 60px, which hid the pill under the notch or a
                        // tall status bar on many phones (P1 inset collision).
                        y = topInsetPx(wm)
                        width = maxW
                        // Lay out sensibly around cutouts (API 28+); with the y
                        // offset the card always clears the top safe area.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            layoutInDisplayCutoutMode =
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }
                    wm.addView(root, lp)
                }

                titleView?.apply {
                    text = "🛡️ antAI Live Shield · ${band.uppercase()} ($risk/100)"
                    setTextColor(accentColor)
                }
                bodyView?.text = explanation
                // Phase 2.3 — show the hang-up button only when a handler is wired
                // for this alert; colour it to the band accent, full width.
                hangupView?.apply {
                    if (onHangUp != null) {
                        val btnBg = (hangupBg ?: android.graphics.drawable.GradientDrawable()).apply {
                            setColor(accentColor)
                            cornerRadius = 24f
                        }
                        hangupBg = btnBg
                        background = btnBg
                        // Readable ink on either accent: dark on amber, white on crimson.
                        setTextColor(if (critical) Color.WHITE else 0xFF1A0B0F.toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(12) }
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }
            } catch (_: Exception) {
                view = null
            }
        }
    }

    /** dp -> px using the current display density. */
    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    /**
     * Safe top offset for the floating pill: the max of the status-bar height and
     * the display-cutout safe inset, plus a 12dp breathing margin (Phase 2.1).
     * Replaces the old hardcoded y=60 that tucked the card under the notch on many
     * devices. On API 30+ it reads live window insets — crucially
     * displayCutout.safeInsetTop, which covers punch-hole front cameras, not just
     * a notch — so the card always clears the camera. Older devices fall back to
     * the platform status_bar_height dimen (already enlarged on notched 28-29
     * phones).
     */
    private fun topInsetPx(wm: WindowManager): Int {
        val margin = dp(12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val insets = wm.currentWindowMetrics.windowInsets
                val cutoutTop = insets.displayCutout?.safeInsetTop ?: 0
                val statusTop = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                maxOf(cutoutTop, statusTop, statusBarHeightRes()) + margin
            } catch (_: Exception) {
                statusBarHeightRes() + margin
            }
        }
        return statusBarHeightRes() + margin
    }

    /** Status-bar height from the platform dimen resource; ~24dp fallback. */
    private fun statusBarHeightRes(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    /**
     * Phase 2.3 — replace the body with an HONEST manual-hangup instruction and
     * hide the one-tap button. Used when the app genuinely cannot end the call
     * programmatically (a third-party VoIP call like WhatsApp, or antAI is not the
     * default phone app). It never claims a disconnect happened — it tells the user
     * to hang up themselves.
     */
    fun showManualHangupHint(hint: String) {
        main.post {
            try {
                bodyView?.text = hint
                hangupView?.visibility = View.GONE
            } catch (_: Exception) {
            }
        }
    }

    fun hide() {
        main.post {
            try {
                val v = view ?: return@post
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(v)
            } catch (_: Exception) {
            } finally {
                view = null
                titleView = null
                bodyView = null
                hangupView = null
                hangupBg = null
                onHangUp = null
                rootBg = null
            }
        }
    }
}

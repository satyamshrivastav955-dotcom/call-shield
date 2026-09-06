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

    fun canDraw(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(risk: Int, band: String, explanation: String) {
        if (!canDraw()) return
        main.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val critical = band == "critical"
                val accent = if (critical) Color.parseColor("#C62828") else Color.parseColor("#E65100")
                if (view == null) {
                    val container = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(36, 28, 36, 28)
                        setBackgroundColor(Color.parseColor("#F2000000"))
                    }
                    titleView = TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 18f
                    }
                    bodyView = TextView(context).apply {
                        setTextColor(Color.parseColor("#EEEEEE"))
                        textSize = 14f
                    }
                    container.addView(titleView)
                    container.addView(bodyView)
                    view = container

                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                    val lp = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        gravity = Gravity.TOP
                        y = 80
                    }
                    wm.addView(container, lp)
                }
                titleView?.apply {
                    text = "🛡️ antAI: ${band.uppercase()} · risk $risk/100"
                    setTextColor(accent)
                }
                bodyView?.text = explanation
            } catch (_: Exception) {
                view = null
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
            }
        }
    }
}

package com.antai.app.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.TextView
import com.antai.app.AppContainer
import com.antai.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Freeze/Intercept dialogs (MD 4.6): the server pushed a hold directive; the
 * user must explicitly confirm ("I understand, proceed anyway") or override.
 * Nothing is auto-blocked.
 */
object FreezeDialogs {

    fun show(context: Context, freeze: JSONObject, scope: CoroutineScope) {
        val action = freeze.optString("action", "confirm")
        val title = freeze.optString("title", "Action held")
        val message = freeze.optString("message", "antAI is holding this action.")
        val freezeId = freeze.optLong("freeze_id", -1)

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)

        when (action) {
            "delay" -> {
                // OTP visibility held: reveal only after explicit confirm
                val holdMs = freeze.optLong("hold_seconds", 60) * 1000L
                builder.setNeutralButton("Show anyway (${holdMs / 1000}s)") { _, _ ->
                    decide(freezeId, "confirm")
                }
            }

            "block" -> {
                builder.setPositiveButton(
                    freeze.optString("confirm_label", "I still want to proceed")) { _, _ ->
                    decide(freezeId, "confirm")
                }
                builder.setNegativeButton(
                    freeze.optString("override_label", "Cancel")) { _, _ ->
                    decide(freezeId, "override")
                }
            }

            else -> { // confirm
                builder.setPositiveButton(
                    freeze.optString("confirm_label", "Proceed anyway")) { _, _ ->
                    decide(freezeId, "confirm")
                }
                builder.setNegativeButton(
                    freeze.optString("override_label", "Cancel")) { _, _ ->
                    decide(freezeId, "override")
                }
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFC62828.toInt())
        }
        dialog.show()
    }

    private fun decide(freezeId: Long, decision: String) {
        if (freezeId < 0) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AppContainer.api.decideFreeze(freezeId, decision) }
        }
    }
}

/** Verify-with-trusted-contact prompt (MD 4.7) shown on the claimed contact's device. */
object VerifyDialogs {

    fun show(context: Context, msg: JSONObject, scope: CoroutineScope) {
        val sessionKey = msg.optString("session_key")
        val question = msg.optString("question", "Are you really on that call right now?")
        AlertDialog.Builder(context)
            .setTitle("Identity verification")
            .setMessage(question)
            .setCancelable(false)
            .setPositiveButton(msg.optJSONArray("options")?.optString(0) ?: "Yes, it's me") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    runCatching { AppContainer.api.respondVerify(sessionKey, true) }
                }
            }
            .setNegativeButton(msg.optJSONArray("options")?.optString(1) ?: "No, that's not me") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    runCatching { AppContainer.api.respondVerify(sessionKey, false) }
                }
            }
            .show()
    }
}

/** Plain-language verdict card content shown in-call and in-chat (MD 6). */
object VerdictCard {

    fun bind(container: TextView, verdict: JSONObject, liveGuidance: String? = null) {
        val v = verdict.optString("verdict", "")
        val why = verdict.optString("why", "")
        val action = verdict.optString("action", "")
        val band = verdict.optString("band", "passive")
        val risk = verdict.optDouble("risk_score", 0.0)

        val sb = StringBuilder()
        if (liveGuidance.isNullOrBlank().not()) sb.append("⚠️ $liveGuidance\n\n")
        if (v.isNotBlank()) sb.append("$v\n")
        if (why.isNotBlank()) sb.append("\nWhy: $why\n")
        if (action.isNotBlank()) sb.append("\nWhat to do now: $action\n")
        if (sb.isEmpty()) sb.append("No concerns detected. Continuing to monitor…")

        container.text = sb.toString().trim()
        val bg = when (band) {
            "critical" -> 0xFFDC2626.toInt()
            "verify" -> 0xFFD97706.toInt()
            else -> 0xFF059669.toInt()
        }
        container.setBackgroundColor(bg)
    }
}
package com.codewithkael.simplecall.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.3 — one-tap hang-up of the current CELLULAR call.
 *
 * HONEST SCOPE / hard limits (this is the crux of the feature, not a footnote):
 *  - Android gives an ordinary app NO way to end an arbitrary phone call. The one
 *    exception is [TelecomManager.endCall] (added API 28, deprecated API 29 but
 *    still functional), and it only works when the app is the user's DEFAULT phone
 *    app or holds the call-screening role AND has the ANSWER_PHONE_CALLS grant.
 *  - There is NO API to end a third-party VoIP call (WhatsApp, Signal, Telegram,
 *    Google Meet, …). For those this controller returns false and the caller must
 *    fall back to a manual "hang up in your app" instruction.
 *
 * So every method here is capability-gated and returns an HONEST result:
 * [endCellularCall] returns true ONLY if the platform actually reported it ended a
 * call. It never fakes a disconnect, never claims success it can't verify, and
 * no-ops (returns false) whenever the app lacks the role/permission or the OS is
 * too old. [reason] gives a user-facing explanation of any no-op.
 */
@Singleton
class CellularCallController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Why (or whether) a programmatic cellular hang-up is possible right now. */
    enum class Capability { SUPPORTED, NO_PERMISSION, NOT_DEFAULT_DIALER, UNSUPPORTED_OS }

    fun capability(): Capability {
        // endCall() is API 28+. Below that there is no supported path at all.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Capability.UNSUPPORTED_OS
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ANSWER_PHONE_CALLS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Capability.NO_PERMISSION
        if (!isDefaultDialerOrScreener()) return Capability.NOT_DEFAULT_DIALER
        return Capability.SUPPORTED
    }

    fun canEndCellularCall(): Boolean = capability() == Capability.SUPPORTED

    /**
     * Attempt to end the foreground cellular call. Returns true ONLY if the
     * platform reported a call was actually ended — otherwise false (honest no-op).
     * Callers MUST treat false as "I could not end it" and fall back to a manual
     * hang-up instruction; they must never present false as success.
     */
    @SuppressLint("MissingPermission") // gated by capability()'s runtime permission check
    fun endCellularCall(): Boolean {
        // Re-assert the OS guard in-method so the API-28 call is unambiguously
        // version-guarded (lint NewApi) even though capability() also checks it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val cap = capability()
        if (cap != Capability.SUPPORTED) {
            Log.i(TAG, "endCellularCall no-op: capability=$cap (not faking a disconnect)")
            return false
        }
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return false
            @Suppress("DEPRECATION") // endCall() deprecated API 29 but still the only path for a default dialer
            val ended = tm.endCall()
            Log.i(TAG, "TelecomManager.endCall() -> $ended")
            ended
        } catch (e: SecurityException) {
            Log.w(TAG, "endCall SecurityException (role/permission lost): ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "endCall failed: ${e.message}")
            false
        }
    }

    /** True if antAI is the default phone app or holds the call-screening role. */
    private fun isDefaultDialerOrScreener(): Boolean = try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val isDefaultDialer = tm?.defaultDialerPackage == context.packageName
        val isScreener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }
        isDefaultDialer || isScreener
    } catch (_: Exception) {
        false
    }

    /** User-facing explanation for the current capability (shown on a no-op). */
    fun reason(): String = when (capability()) {
        Capability.SUPPORTED -> "antAI can end this cellular call."
        Capability.NO_PERMISSION -> "Grant the phone-calls permission so antAI can hang up for you."
        Capability.NOT_DEFAULT_DIALER -> "Set antAI as your default phone app to enable one-tap hang-up."
        Capability.UNSUPPORTED_OS -> "This Android version doesn't allow apps to end calls."
    }

    private companion object {
        const val TAG = "CellularCallCtl"
    }
}

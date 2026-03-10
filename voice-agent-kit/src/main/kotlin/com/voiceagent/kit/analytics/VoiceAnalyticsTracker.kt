package com.voiceagent.kit.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.voiceagent.kit.security.LogSanitizer
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Wraps Firebase Analytics for the VoiceAgentKit SDK.
 *
 * - Respects [enableAnalytics] flag set in [VoiceAgentConfig]
 * - Sanitizes all params via [LogSanitizer] before firing
 * - Never throws — all calls wrapped in try/catch
 */
class VoiceAnalyticsTracker(
    private val context: Context,
    private val enableAnalytics: Boolean
) {

    private val TAG = "VoiceAnalyticsTracker"
    private val analytics: FirebaseAnalytics? by lazy {
        if (enableAnalytics) {
            try {
                FirebaseAnalytics.getInstance(context)
            } catch (e: Exception) {
                VoiceAgentLogger.w(TAG, "Firebase Analytics not available", e)
                null
            }
        } else null
    }

    fun track(event: VoiceEvent) {
        if (!enableAnalytics) return
        try {
            val (name, params) = mapEvent(event)
            val sanitized = LogSanitizer.sanitizeParams(params)
            val bundle = Bundle().apply {
                sanitized.forEach { (k, v) -> putString(k, v) }
            }
            analytics?.logEvent(name, bundle)
            VoiceAgentLogger.d(TAG, "Tracked: $name → $sanitized")
        } catch (e: Exception) {
            VoiceAgentLogger.e(TAG, "Analytics tracking failed for $event", e)
        }
    }

    private fun mapEvent(event: VoiceEvent): Pair<String, Map<String, Any?>> {
        return when (event) {
            is VoiceEvent.SessionStart -> Pair(
                "voice_session_start",
                mapOf(
                    "screen_id" to event.screenId,
                    "user_id"   to event.userId,
                    "mode"      to event.mode
                )
            )
            is VoiceEvent.FieldFilled -> Pair(
                "voice_field_filled",
                mapOf(
                    "screen_id"     to event.screenId,
                    "field_id"      to event.fieldId,
                    "attempt_count" to event.attemptCount.toString()
                )
            )
            is VoiceEvent.FormCompleted -> Pair(
                "voice_form_completed",
                mapOf(
                    "screen_id"    to event.screenId,
                    "total_fields" to event.totalFields.toString(),
                    "duration_ms"  to event.durationMs.toString()
                )
            )
            is VoiceEvent.Error -> Pair(
                "voice_error",
                mapOf(
                    "screen_id"    to event.screenId,
                    "error_type"   to event.errorType,
                    "error_message" to event.errorMessage
                )
            )
            is VoiceEvent.SessionCancelled -> Pair(
                "voice_session_cancelled",
                mapOf(
                    "screen_id"        to event.screenId,
                    "fields_completed" to event.fieldsCompleted.toString()
                )
            )
        }
    }
}

package com.voiceagent.kit.context

import com.voiceagent.kit.livekit.LiveKitEngine
import com.voiceagent.kit.schema.FieldType
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.schema.VoiceField
import com.voiceagent.kit.utils.VoiceAgentLogger
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner

/**
 * Builds and sends the "screen opened" context payload to the LiveKit DataChannel.
 *
 * Replaces the ~40-line `sendInitialDataToLiveKit()` method that currently exists
 * in every DDFin fragment using voice. The payload format replicates the existing
 * backend contract exactly:
 *
 * ```json
 * {
 *   "type"          : "add_new_site",
 *   "screen_name"   : "add_new_site",
 *   "field"         : "screen_name",
 *   "value"         : "add_new_site",
 *   "event"         : "app_opened",
 *   "timestamp"     : 1714000000000,
 *   "irr_source_list": ["Borewell/Tube well", "Canal/River", ...],
 *   "draft_site_name": "Existing Farm"   // if already filled
 * }
 * ```
 */
class ScreenContextBuilder(private val engine: LiveKitEngine) {

    private val TAG = "ScreenContextBuilder"

    /** Minimum interval between context sends to prevent duplicate sends. */
    private val THROTTLE_MS = 500L

    @Volatile
    private var lastContextSentTime: Long = 0L

    /**
     * Build and send the screen context payload for [schema].
     * Throttled to at most once per [THROTTLE_MS].
     *
     * @param schema       The schema of the currently active screen.
     * @param currentValues Map of fieldId → current view value (may be empty).
     */
    fun sendContext(schema: FormSchema, currentValues: Map<String, String> = emptyMap()) {
        val now = System.currentTimeMillis()
        if (now - lastContextSentTime < THROTTLE_MS) {
            VoiceAgentLogger.d(TAG, "Context send throttled — too soon since last send")
            return
        }

        if (!engine.isConnected()) {
            VoiceAgentLogger.d(TAG, "Not connected — cannot send context for ${schema.screenId}")
            return
        }

        lastContextSentTime = now
        val payload = buildPayload(schema, currentValues)
        VoiceAgentLogger.i(TAG, "Sending screen context for '${schema.screenId}'")
        engine.sendDataMessage(payload)
    }

    private fun buildPayload(schema: FormSchema, currentValues: Map<String, String>): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>()

        // Core screen identification fields
        payload["type"]        = schema.screenId
        payload["screen_name"] = schema.screenId
        payload["field"]       = "screen_name"
        payload["value"]       = schema.screenId
        payload["event"]       = "app_opened"
        payload["timestamp"]   = System.currentTimeMillis()

        // For every SPINNER or RADIO field, include "{field_id}_list"
        schema.fields.forEach { field ->
            if ((field.fieldType == FieldType.SPINNER || field.fieldType == FieldType.RADIO)
                && !field.options.isNullOrEmpty()
            ) {
                payload["${field.id}_list"] = field.options
            }
        }

        // Include current values for TEXT/NUMBER fields that are already filled
        currentValues.forEach { (fieldId, value) ->
            if (value.isNotBlank()) {
                val field = schema.fieldById(fieldId)
                if (field != null &&
                    (field.fieldType == FieldType.TEXT || field.fieldType == FieldType.NUMBER)
                ) {
                    payload["${schema.outgoingKeyPrefix}${fieldId}"] = value
                }
            }
        }

        return payload
    }

    fun resetThrottle() {
        lastContextSentTime = 0L
    }
}

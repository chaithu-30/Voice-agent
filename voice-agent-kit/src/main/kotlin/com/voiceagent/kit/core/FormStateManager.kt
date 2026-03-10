package com.voiceagent.kit.core

import com.voiceagent.kit.schema.FieldType
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.schema.VoiceField
import com.voiceagent.kit.utils.VoiceAgentLogger
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner

/**
 * Tracks the current state of all fields on a screen (in-memory).
 * Used to:
 * - Detect form completion (all required fields filled)
 * - Snapshot field values when pausing (for session persistence)
 * - Restore field count state when resuming
 */
class FormStateManager(private val schema: FormSchema) {

    private val TAG = "FormStateManager"

    /** Per-field fill-attempt counter. Increments each time a field is filled by voice. */
    private val fieldAttemptCounts = mutableMapOf<String, Int>()

    /** Tracks whether each required field has a valid value. */
    private val requiredFieldFilled = mutableMapOf<String, Boolean>()

    init {
        schema.fields.filter { it.isRequired }.forEach { field ->
            requiredFieldFilled[field.id] = false
            fieldAttemptCounts[field.id] = 0
        }
    }

    /**
     * Call when a field is filled by voice. Updates attempt count and required-field state.
     * @return how many times this field has been filled (for analytics).
     */
    fun onFieldFilled(fieldId: String): Int {
        val count = (fieldAttemptCounts[fieldId] ?: 0) + 1
        fieldAttemptCounts[fieldId] = count
        if (schema.fieldById(fieldId)?.isRequired == true) {
            requiredFieldFilled[fieldId] = true
        }
        VoiceAgentLogger.d(TAG, "Field '$fieldId' filled (attempt #$count)")
        return count
    }

    /** Returns true if all required fields have been filled at least once. */
    fun isFormComplete(): Boolean {
        return requiredFieldFilled.values.all { it }
    }

    /** Returns the number of required fields that have been filled. */
    fun filledRequiredFieldCount(): Int {
        return requiredFieldFilled.values.count { it }
    }

    /** Returns total required field count. */
    fun totalRequiredFieldCount(): Int = requiredFieldFilled.size

    /** Reads current view state of all schema fields as a flat Map<fieldId, value>. */
    fun snapshotCurrentValues(): Map<String, String> {
        val snapshot = mutableMapOf<String, String>()
        schema.fields.forEach { field ->
            val value = readViewValue(field)
            if (!value.isNullOrBlank()) {
                snapshot[field.id] = value
            }
        }
        return snapshot
    }

    private fun readViewValue(field: VoiceField): String? {
        return try {
            when (field.fieldType) {
                FieldType.TEXT, FieldType.NUMBER ->
                    (field.viewRef as? EditText)?.text?.toString()
                FieldType.RADIO -> {
                    val rg = field.viewRef as? RadioGroup ?: return null
                    val cid = rg.checkedRadioButtonId
                    if (cid == -1) null
                    else rg.findViewById<RadioButton>(cid)?.text?.toString()
                }
                FieldType.SPINNER ->
                    (field.viewRef as? Spinner)?.selectedItem?.toString()
                FieldType.CHECKBOX ->
                    ((field.viewRef as? CheckBox)?.isChecked ?: false).toString()
            }
        } catch (e: Exception) {
            VoiceAgentLogger.w(TAG, "Could not read view value for '${field.id}'", e)
            null
        }
    }

    fun reset() {
        fieldAttemptCounts.clear()
        requiredFieldFilled.keys.forEach { requiredFieldFilled[it] = false }
    }
}

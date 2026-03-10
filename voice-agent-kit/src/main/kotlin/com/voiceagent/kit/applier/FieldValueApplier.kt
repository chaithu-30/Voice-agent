package com.voiceagent.kit.applier

import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.voiceagent.kit.mapping.ValueNormalizer
import com.voiceagent.kit.schema.FieldType
import com.voiceagent.kit.schema.VoiceField
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Applies resolved values to their corresponding Android Views.
 *
 * All operations are dispatched on the main thread via [android.view.View.post].
 * Fragment validity ([Fragment.isAdded] && ![Fragment.isDetached]) is checked
 * before every UI operation.
 *
 * TextWatcher loop prevention: Before setting text, the SDK-owned watcher is
 * temporarily removed and re-added after setting text, preventing the watcher
 * from firing an outgoing update for what was actually an incoming change.
 */
class FieldValueApplier(private val fragment: Fragment) {

    private val TAG = "FieldValueApplier"

    /** Map of fieldId → SDK-owned TextWatcher. Used for loop prevention. */
    private val textWatchers = mutableMapOf<String, TextWatcher>()

    /** Set of paused field IDs — their watchers are detached, do not send updates. */
    private val pausedFields = mutableSetOf<String>()
    private val pauseLock = Any()

    // ──────────────────────────────────────────────────────────────
    // TextWatcher Registry (for loop prevention)
    // ──────────────────────────────────────────────────────────────

    /** Register the SDK-owned TextWatcher for a field so it can be removed/re-added. */
    fun registerTextWatcher(fieldId: String, watcher: TextWatcher) {
        textWatchers[fieldId] = watcher
    }

    fun unregisterTextWatcher(fieldId: String) {
        textWatchers.remove(fieldId)
    }

    fun isPaused(fieldId: String): Boolean {
        return synchronized(pauseLock) { pausedFields.contains(fieldId) }
    }

    fun pauseListenerFor(fieldId: String) {
        synchronized(pauseLock) { pausedFields.add(fieldId) }
    }

    fun resumeListenerFor(fieldId: String) {
        synchronized(pauseLock) { pausedFields.remove(fieldId) }
    }

    // ──────────────────────────────────────────────────────────────
    // Apply
    // ──────────────────────────────────────────────────────────────

    /**
     * Apply [value] to the view described by [field].
     * Schedules the update on the main thread via view.post{}.
     */
    fun apply(field: VoiceField, value: String) {
        val view = field.viewRef
        view.post {
            if (!fragment.isAdded || fragment.isDetached) {
                VoiceAgentLogger.w(TAG, "Fragment not attached, skipping apply for ${field.id}")
                return@post
            }

            try {
                when (field.fieldType) {
                    FieldType.TEXT, FieldType.NUMBER -> applyText(field, value)
                    FieldType.RADIO                  -> applyRadio(field, value)
                    FieldType.SPINNER                -> applySpinner(field, value)
                    FieldType.CHECKBOX               -> applyCheckbox(field, value)
                }
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Error applying value to field '${field.id}'", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Type-specific appliers
    // ──────────────────────────────────────────────────────────────

    private fun applyText(field: VoiceField, value: String) {
        val editText = field.viewRef as? EditText ?: run {
            VoiceAgentLogger.w(TAG, "Field '${field.id}' is TEXT but viewRef is not an EditText")
            return
        }

        val current = editText.text?.toString() ?: ""
        if (!ValueNormalizer.hasChanged(current, value)) {
            VoiceAgentLogger.v(TAG, "Field '${field.id}' unchanged, skipping text set")
            return
        }

        // Loop prevention: remove SDK watcher before setting text
        val watcher = textWatchers[field.id]
        pauseListenerFor(field.id)
        watcher?.let { editText.removeTextChangedListener(it) }

        editText.setText(value)
        editText.setSelection(editText.text?.length ?: 0)

        // Re-add watcher
        watcher?.let { editText.addTextChangedListener(it) }
        resumeListenerFor(field.id)

        VoiceAgentLogger.d(TAG, "Applied text to '${field.id}': '$value'")
    }

    private fun applyRadio(field: VoiceField, value: String) {
        val radioGroup = field.viewRef as? RadioGroup ?: run {
            VoiceAgentLogger.w(TAG, "Field '${field.id}' is RADIO but viewRef is not a RadioGroup")
            return
        }

        for (i in 0 until radioGroup.childCount) {
            val child = radioGroup.getChildAt(i)
            if (child is RadioButton) {
                val buttonText = child.text?.toString() ?: ""
                val buttonTag  = child.tag?.toString() ?: ""
                if (buttonText.equals(value, ignoreCase = true) || buttonTag.equals(value, ignoreCase = true)) {
                    if (!child.isChecked) {
                        radioGroup.check(child.id)
                        VoiceAgentLogger.d(TAG, "Applied radio to '${field.id}': '$value'")
                    }
                    return
                }
            }
        }
        VoiceAgentLogger.w(TAG, "No RadioButton found for value '$value' in field '${field.id}'")
    }

    private fun applySpinner(field: VoiceField, value: String) {
        val spinner = field.viewRef as? Spinner ?: run {
            VoiceAgentLogger.w(TAG, "Field '${field.id}' is SPINNER but viewRef is not a Spinner")
            return
        }

        val adapter = spinner.adapter ?: run {
            VoiceAgentLogger.w(TAG, "Spinner '${field.id}' has no adapter")
            return
        }

        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)?.toString() ?: ""
            if (item.equals(value, ignoreCase = true)) {
                if (spinner.selectedItemPosition != i) {
                    spinner.setSelection(i)
                    VoiceAgentLogger.d(TAG, "Applied spinner to '${field.id}': '$value' at index $i")
                }
                return
            }
        }
        VoiceAgentLogger.w(TAG, "No spinner item found for value '$value' in field '${field.id}'")
    }

    private fun applyCheckbox(field: VoiceField, value: String) {
        val checkBox = field.viewRef as? CheckBox ?: run {
            VoiceAgentLogger.w(TAG, "Field '${field.id}' is CHECKBOX but viewRef is not a CheckBox")
            return
        }

        val newState = ValueNormalizer.toBoolean(value)
        if (checkBox.isChecked != newState) {
            checkBox.isChecked = newState
            VoiceAgentLogger.d(TAG, "Applied checkbox to '${field.id}': $newState")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    fun teardown() {
        textWatchers.clear()
        synchronized(pauseLock) { pausedFields.clear() }
    }
}

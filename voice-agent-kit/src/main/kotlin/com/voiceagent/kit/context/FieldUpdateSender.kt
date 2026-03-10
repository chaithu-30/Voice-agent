package com.voiceagent.kit.context

import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import com.voiceagent.kit.applier.FieldValueApplier
import com.voiceagent.kit.livekit.LiveKitEngine
import com.voiceagent.kit.mapping.ValueNormalizer
import com.voiceagent.kit.schema.FieldType
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.schema.SchemaMode
import com.voiceagent.kit.schema.VoiceField
import com.voiceagent.kit.utils.DebounceHandler
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Attaches SDK-managed listeners to every registered View and sends outgoing field
 * update messages via [LiveKitEngine.sendDataMessage()].
 *
 * * EditText / TextInputEditText: 600ms debounce via [DebounceHandler]
 * * RadioGroup: immediate send on check changed
 * * Spinner: immediate send on item selected
 * * CheckBox: immediate send on check changed
 *
 * Dedup: only sends if the value actually changed since the last send.
 * Loop prevention: uses [FieldValueApplier.isPaused] to skip sends triggered by
 * incoming value application.
 */
class FieldUpdateSender(
    private val schema: FormSchema,
    private val engine: LiveKitEngine,
    private val applier: FieldValueApplier
) {

    private val TAG = "FieldUpdateSender"

    private val debounce = DebounceHandler()
    private val lastSentValues = mutableMapOf<String, String>()  // fieldId → last sent value

    /** Registered TextWatcher instances keyed by fieldId for lifecycle cleanup. */
    private val textWatchers = mutableMapOf<String, TextWatcher>()

    /** Registered RadioGroup listeners keyed by fieldId. */
    private val radioListeners = mutableMapOf<String, RadioGroup.OnCheckedChangeListener>()

    /** Registered AdapterView listeners keyed by fieldId. */
    private val spinnerListeners = mutableMapOf<String, AdapterView.OnItemSelectedListener>()

    /** Registered CheckBox listeners keyed by fieldId. */
    private val checkboxListeners = mutableMapOf<String, CompoundButton.OnCheckedChangeListener>()

    // ──────────────────────────────────────────────────────────────
    // Attach listeners to all schema views
    // ──────────────────────────────────────────────────────────────

    fun attachAll() {
        schema.fields.forEach { field ->
            when (field.fieldType) {
                FieldType.TEXT, FieldType.NUMBER -> attachTextWatcher(field)
                FieldType.RADIO                  -> attachRadioListener(field)
                FieldType.SPINNER                -> attachSpinnerListener(field)
                FieldType.CHECKBOX               -> attachCheckboxListener(field)
            }
        }
        VoiceAgentLogger.d(TAG, "Attached listeners to ${schema.fields.size} fields for '${schema.screenId}'")
    }

    // ──────────────────────────────────────────────────────────────
    // Detach all listeners (called on onPause / onDestroyView)
    // ──────────────────────────────────────────────────────────────

    fun detachAll() {
        debounce.cancelAll()

        textWatchers.forEach { (fieldId, watcher) ->
            val field = schema.fieldById(fieldId) ?: return@forEach
            (field.viewRef as? EditText)?.removeTextChangedListener(watcher)
            applier.unregisterTextWatcher(fieldId)
        }
        textWatchers.clear()

        radioListeners.forEach { (fieldId, listener) ->
            val field = schema.fieldById(fieldId) ?: return@forEach
            (field.viewRef as? RadioGroup)?.setOnCheckedChangeListener(null)
        }
        radioListeners.clear()

        spinnerListeners.forEach { (fieldId, _) ->
            val field = schema.fieldById(fieldId) ?: return@forEach
            (field.viewRef as? Spinner)?.onItemSelectedListener = null
        }
        spinnerListeners.clear()

        checkboxListeners.forEach { (fieldId, _) ->
            val field = schema.fieldById(fieldId) ?: return@forEach
            (field.viewRef as? CheckBox)?.setOnCheckedChangeListener(null)
        }
        checkboxListeners.clear()

        VoiceAgentLogger.d(TAG, "All field listeners detached for '${schema.screenId}'")
    }

    // ──────────────────────────────────────────────────────────────
    // Current values snapshot (used by ScreenContextBuilder)
    // ──────────────────────────────────────────────────────────────

    fun getCurrentValues(): Map<String, String> {
        val values = mutableMapOf<String, String>()
        schema.fields.forEach { field ->
            val value = readCurrentValue(field)
            if (!value.isNullOrBlank()) {
                values[field.id] = value
            }
        }
        return values
    }

    private fun readCurrentValue(field: VoiceField): String? {
        return try {
            when (field.fieldType) {
                FieldType.TEXT, FieldType.NUMBER -> (field.viewRef as? EditText)?.text?.toString()
                FieldType.RADIO -> {
                    val rg = field.viewRef as? RadioGroup ?: return null
                    val checkedId = rg.checkedRadioButtonId
                    if (checkedId == -1) null
                    else rg.findViewById<android.widget.RadioButton>(checkedId)?.text?.toString()
                }
                FieldType.SPINNER -> (field.viewRef as? Spinner)?.selectedItem?.toString()
                FieldType.CHECKBOX -> ((field.viewRef as? CheckBox)?.isChecked ?: false).toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Private: Attach individual listeners
    // ──────────────────────────────────────────────────────────────

    private fun attachTextWatcher(field: VoiceField) {
        val editText = field.viewRef as? EditText ?: return

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (applier.isPaused(field.id)) return
                val value = s?.toString() ?: return
                debounce.schedule(field.id) {
                    maybeSendFieldUpdate(field, value)
                }
            }
        }
        editText.addTextChangedListener(watcher)
        textWatchers[field.id] = watcher
        applier.registerTextWatcher(field.id, watcher)
    }

    private fun attachRadioListener(field: VoiceField) {
        val radioGroup = field.viewRef as? RadioGroup ?: return

        val listener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
            if (checkedId == -1) return@OnCheckedChangeListener
            val button = group.findViewById<android.widget.RadioButton>(checkedId)
            val value = button?.text?.toString() ?: return@OnCheckedChangeListener
            maybeSendFieldUpdate(field, value)
        }
        radioGroup.setOnCheckedChangeListener(listener)
        radioListeners[field.id] = listener
    }

    private fun attachSpinnerListener(field: VoiceField) {
        val spinner = field.viewRef as? Spinner ?: return

        val listener = object : AdapterView.OnItemSelectedListener {
            private var initialized = false
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                // Skip the initial automatic selection on attach
                if (!initialized) { initialized = true; return }
                val value = parent?.getItemAtPosition(position)?.toString() ?: return
                maybeSendFieldUpdate(field, value)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.onItemSelectedListener = listener
        spinnerListeners[field.id] = listener
    }

    private fun attachCheckboxListener(field: VoiceField) {
        val checkBox = field.viewRef as? CheckBox ?: return

        val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            maybeSendFieldUpdate(field, isChecked.toString())
        }
        checkBox.setOnCheckedChangeListener(listener)
        checkboxListeners[field.id] = listener
    }

    // ──────────────────────────────────────────────────────────────
    // Send logic
    // ──────────────────────────────────────────────────────────────

    private fun maybeSendFieldUpdate(field: VoiceField, rawValue: String) {
        if (schema.mode == SchemaMode.VIEW) {
            VoiceAgentLogger.d(TAG, "Schema is VIEW mode — suppressing outgoing update for '${field.id}'")
            return
        }

        val normalized = ValueNormalizer.normalize(rawValue) ?: return

        if (!ValueNormalizer.hasChanged(lastSentValues[field.id], normalized)) {
            VoiceAgentLogger.v(TAG, "No change for '${field.id}' — skipping send")
            return
        }

        if (!engine.isConnected()) {
            VoiceAgentLogger.d(TAG, "Not connected — dropping field update for '${field.id}'")
            return
        }

        lastSentValues[field.id] = normalized
        val jsonKey = "${schema.outgoingKeyPrefix}${field.id}"
        val message = mapOf<String, Any?>(jsonKey to normalized)
        VoiceAgentLogger.d(TAG, "Sending field update: '$jsonKey' = '$normalized'")
        engine.sendDataMessage(message)
    }

    fun teardown() {
        detachAll()
        lastSentValues.clear()
    }
}

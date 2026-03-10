package com.voiceagent.kit.schema

import android.view.View

/**
 * Describes a single UI field that the VoiceAgentKit SDK manages.
 *
 * @param id              Unique identifier for this field. Must match (or be aliased to)
 *                        the JSON keys that the backend sends and expects.
 * @param viewRef         Reference to the actual Android [View] in the fragment layout.
 *                        SDK will attach listeners and apply values to this view.
 * @param fieldType       The type of the view, determines which listeners are attached
 *                        and how incoming values are applied.
 * @param options         Valid string options for [FieldType.RADIO] and [FieldType.SPINNER]
 *                        fields. Used when building the screen context payload.
 * @param jsonKeyAliases  Optional extra JSON keys to check during incoming message
 *                        resolution, beyond the automatic draft_/editing_/viewing_ prefixes.
 * @param isRequired      Indicates whether this field is mandatory. Used in context payloads
 *                        and form-completion detection.
 */
data class VoiceField(
    val id: String,
    val viewRef: View,
    val fieldType: FieldType,
    val options: List<String>? = null,
    val jsonKeyAliases: List<String>? = null,
    val isRequired: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "VoiceField id must not be blank" }
        if (fieldType == FieldType.RADIO || fieldType == FieldType.SPINNER) {
            require(!options.isNullOrEmpty()) {
                "VoiceField '$id' of type $fieldType must provide a non-empty options list"
            }
        }
    }
}

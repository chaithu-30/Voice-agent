package com.voiceagent.kit.schema

/**
 * Describes the complete form schema for a single screen.
 *
 * @param screenId    Unique screen identifier. Sent as screen context payload when a
 *                    voice session starts or the fragment resumes. Maps to the "screen_name"
 *                    and "type" keys in the outgoing JSON.
 * @param fields      Ordered list of all [VoiceField]s on this screen.
 * @param mode        Whether this screen is in ADD, EDIT, or VIEW mode. Controls the JSON
 *                    key prefix used for outgoing field updates.
 */
data class FormSchema(
    val screenId: String,
    val fields: List<VoiceField>,
    val mode: SchemaMode = SchemaMode.ADD
) {
    init {
        require(screenId.isNotBlank()) { "FormSchema screenId must not be blank" }
        require(fields.isNotEmpty()) { "FormSchema must have at least one field" }
        val ids = fields.map { it.id }
        require(ids.size == ids.toSet().size) {
            "FormSchema '${screenId}' contains duplicate field ids: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}"
        }
    }

    /** Returns the field with the given id, or null. */
    fun fieldById(id: String): VoiceField? = fields.firstOrNull { it.id == id }

    /** Returns the JSON key prefix for outgoing updates based on [mode]. */
    val outgoingKeyPrefix: String
        get() = when (mode) {
            SchemaMode.ADD  -> "draft_"
            SchemaMode.EDIT -> "editing_"
            SchemaMode.VIEW -> ""
        }
}

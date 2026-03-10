package com.voiceagent.kit.mapping

/**
 * Normalizes raw values arriving from the LiveKit DataChannel JSON.
 * Filters out semantically empty strings so that the SDK never applies
 * blank/null/"null"/"undefined" to a live View.
 */
object ValueNormalizer {

    /** Strings that are semantically empty and must be treated as null. */
    private val EMPTY_TOKENS = setOf("null", "undefined", "none", "")

    /**
     * Normalize a raw JSON value string.
     * @return Trimmed value, or null if the value is semantically empty.
     */
    fun normalize(raw: Any?): String? {
        if (raw == null) return null
        val str = raw.toString().trim()
        return if (str.lowercase() in EMPTY_TOKENS) null else str
    }

    /**
     * Returns true if [newValue] is semantically different from [currentValue]
     * — meaning we should actually update the View and send the outgoing message.
     */
    fun hasChanged(currentValue: String?, newValue: String?): Boolean {
        val normCurrent = normalize(currentValue)
        val normNew = normalize(newValue)
        return normCurrent != normNew
    }

    /**
     * Normalizes a boolean-like string to an actual Boolean.
     * Accepts: "true", "1", "yes", "on" (case-insensitive).
     */
    fun toBoolean(value: String?): Boolean {
        val normalized = normalize(value) ?: return false
        return normalized.lowercase() in setOf("true", "1", "yes", "on")
    }
}

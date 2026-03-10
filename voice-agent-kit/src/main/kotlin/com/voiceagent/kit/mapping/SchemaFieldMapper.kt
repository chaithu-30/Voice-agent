package com.voiceagent.kit.mapping

import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Resolves incoming JSON key-value pairs to their corresponding [VoiceField] IDs
 * using the following priority resolution order per field:
 *
 *  1. `json[field.id]`
 *  2. `json["draft_{field.id}"]`
 *  3. `json["editing_{field.id}"]`
 *  4. `json["viewing_{field.id}"]`
 *  5. `json[alias]` for each alias in `field.jsonKeyAliases`
 *
 * First non-null, non-empty value wins.
 */
object SchemaFieldMapper {

    private val TAG = "SchemaFieldMapper"

    data class ResolvedField(
        val fieldId: String,
        val value: String,
        val resolvedKey: String   // for debugging
    )

    /**
     * Resolve all fields in [schema] against the incoming [json] payload.
     * Returns a list of [ResolvedField] for every field that has a value in [json].
     * Fields with no match are omitted.
     */
    fun resolve(schema: FormSchema, json: Map<String, Any?>): List<ResolvedField> {
        val resolved = mutableListOf<ResolvedField>()
        for (field in schema.fields) {
            val result = resolveField(field.id, field.jsonKeyAliases, json)
            if (result != null) {
                resolved.add(ResolvedField(field.id, result.first, result.second))
                VoiceAgentLogger.d(
                    TAG,
                    "Field '${field.id}' resolved via key '${result.second}' → '${result.first}'"
                )
            }
        }
        return resolved
    }

    /**
     * Try to find a value for a single field.
     *
     * @return Pair(normalizedValue, resolvedJsonKey) or null if no valid value found.
     */
    private fun resolveField(
        fieldId: String,
        aliases: List<String>?,
        json: Map<String, Any?>
    ): Pair<String, String>? {

        val candidateKeys = mutableListOf<String>()
        // Priority 1: exact id
        candidateKeys.add(fieldId)
        // Priority 2-4: standard prefixes
        candidateKeys.add("draft_$fieldId")
        candidateKeys.add("editing_$fieldId")
        candidateKeys.add("viewing_$fieldId")
        // Priority 5: custom aliases
        aliases?.forEach { candidateKeys.add(it) }

        for (key in candidateKeys) {
            val raw = json[key]
            val normalized = ValueNormalizer.normalize(raw)
            if (normalized != null) {
                return Pair(normalized, key)
            }
        }
        return null
    }
}

package com.voiceagent.kit.analytics

/**
 * Strongly-typed event definitions for the VoiceAgentKit Firebase Analytics events.
 */
sealed class VoiceEvent {

    /** Fired when a voice session starts (LiveKit connected + screen context sent). */
    data class SessionStart(
        val screenId: String,
        val userId: String,
        val mode: String  // "add" | "edit" | "view"
    ) : VoiceEvent()

    /** Fired each time a field is filled by the voice agent. */
    data class FieldFilled(
        val screenId: String,
        val fieldId: String,
        val attemptCount: Int
    ) : VoiceEvent()

    /** Fired when all required fields in the schema are filled. */
    data class FormCompleted(
        val screenId: String,
        val totalFields: Int,
        val durationMs: Long
    ) : VoiceEvent()

    /** Fired when a recoverable or non-recoverable error occurs in the SDK. */
    data class Error(
        val screenId: String,
        val errorType: String,
        val errorMessage: String
    ) : VoiceEvent()

    /** Fired when the user navigates away before completing the form. */
    data class SessionCancelled(
        val screenId: String,
        val fieldsCompleted: Int
    ) : VoiceEvent()
}

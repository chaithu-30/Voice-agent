package com.voiceagent.kit.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single voice session persisted to the local Room database.
 * Sessions survive fragment destruction and can be restored on re-entry.
 */
@Entity(tableName = "voice_sessions")
data class VoiceSessionEntity(
    @PrimaryKey
    val sessionId: String,

    /** The screenId from [FormSchema] — used to match sessions on restore. */
    val screenId: String,

    /** The userId passed to [VoiceAgentKit.connect()]. */
    val userId: String,

    /**
     * JSON serialization of Map<fieldId, currentValue>.
     * Stored as a flat JSON string for simplicity; deserialized on restore.
     */
    val fieldStatesJson: String,

    /**
     * Session lifecycle state:
     *  - "active"    — session in progress
     *  - "completed" — all required fields filled
     *  - "cancelled" — user navigated away without completing
     */
    val status: String,

    val createdAt: Long,
    val updatedAt: Long
)

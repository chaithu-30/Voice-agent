package com.voiceagent.kit.schema

/**
 * Describes the interaction mode for a form screen.
 * Used by [FieldUpdateSender] to determine the JSON key prefix for outgoing updates.
 */
enum class SchemaMode {
    /** Creating a new record — outgoing keys prefixed with "draft_" */
    ADD,

    /** Editing an existing record — outgoing keys prefixed with "editing_" */
    EDIT,

    /** View-only mode — outgoing updates are suppressed */
    VIEW
}

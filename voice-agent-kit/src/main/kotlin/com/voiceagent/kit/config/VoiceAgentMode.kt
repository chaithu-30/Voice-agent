package com.voiceagent.kit.config

/**
 * Global mode for the SDK — matches the intent of the host app interaction.
 * Currently used as metadata; may influence routing in future versions.
 */
enum class VoiceAgentMode {
    /** Standard operational mode */
    STANDARD,

    /** Debug/test mode — enables extra logging and mock responses */
    DEBUG
}

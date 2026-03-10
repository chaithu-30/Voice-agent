package com.voiceagent.kit.config

/**
 * Log verbosity levels for the VoiceAgentKit SDK.
 */
enum class VoiceLogLevel {
    /** No logs emitted */
    NONE,

    /** Critical errors only */
    ERROR,

    /** Warnings and errors */
    WARN,

    /** Informational messages, warnings, and errors */
    INFO,

    /** Full verbose debug output — do NOT use in production */
    DEBUG
}

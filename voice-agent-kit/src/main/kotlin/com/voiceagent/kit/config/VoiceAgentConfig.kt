package com.voiceagent.kit.config

/**
 * Main configuration class for the VoiceAgentKit SDK.
 *
 * @param backendUrl       Base URL of the backend that issues LiveKit tokens.
 * @param liveKitUrl       WebSocket URL of the LiveKit server (wss://…). If null,
 *                         the URL is taken from the token endpoint response.
 * @param enableAnalytics  Whether Firebase Analytics events should be fired.
 * @param logLevel         Verbosity of internal SDK logs.
 * @param languageProvider Lambda that returns the currently selected BCP-47 language
 *                         tag (e.g. "en-IN", "hi-IN"). Injected into every outgoing
 *                         DataChannel message as "selected_language".
 * @param mode             Global SDK operational mode (STANDARD or DEBUG).
 */
data class VoiceAgentConfig(
    val backendUrl: String,
    val liveKitUrl: String? = null,
    val enableAnalytics: Boolean = true,
    val logLevel: VoiceLogLevel = VoiceLogLevel.INFO,
    val languageProvider: (() -> String)? = null,
    val mode: VoiceAgentMode = VoiceAgentMode.STANDARD
) {
    init {
        require(backendUrl.isNotBlank()) { "backendUrl must not be blank" }
    }
}

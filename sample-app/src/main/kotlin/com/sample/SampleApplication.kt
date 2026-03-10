package com.sample.voiceagent

import android.app.Application
import com.voiceagent.kit.VoiceAgentKit
import com.voiceagent.kit.config.VoiceAgentConfig
import com.voiceagent.kit.config.VoiceLogLevel

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        VoiceAgentKit.initialize(
            context = this,
            config = VoiceAgentConfig(
                backendUrl       = "https://bmgf-backend.doordrishti.ai/",
                liveKitUrl       = "wss://your-livekit-server.com",
                enableAnalytics  = true,
                logLevel         = VoiceLogLevel.DEBUG,
                // Inject the selected language from your session/prefs manager:
                languageProvider = { getSelectedLanguage() }
            )
        )
    }

    /** Placeholder — replace with your actual session/language manager. */
    private fun getSelectedLanguage(): String {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getString("selected_language", "en-IN") ?: "en-IN"
    }
}

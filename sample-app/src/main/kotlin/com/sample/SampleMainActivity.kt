package com.sample.voiceagent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voiceagent.kit.VoiceAgentKit

/**
 * Sample MainActivity demonstrating how VoiceAgentKit is connected/disconnected
 * at the Activity (app-session) level — not at the Fragment level.
 *
 * In your DDFin app, add these two calls to your existing MainActivity:
 * - [VoiceAgentKit.connect] after the user authenticates
 * - [VoiceAgentKit.disconnect] on sign-out
 *
 * Everything else (per-screen integration) happens inside each Fragment.
 */
class SampleMainActivity : AppCompatActivity() {

    // Replace with your actual authenticated user ID source
    private val currentUserId: String get() = "user_12345"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        // Connect when the user is signed in and the activity starts
        if (VoiceAgentKit.isInitialized) {
            VoiceAgentKit.connect(userId = currentUserId)
        }
    }

    override fun onStop() {
        super.onStop()
        // Disconnect when the activity goes fully background
        if (VoiceAgentKit.isInitialized) {
            VoiceAgentKit.disconnect()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // If you have existing getLiveKitManager() / getHybridModeManager() methods,
    // they can remain as-is — the SDK does NOT require you to remove them
    // from fragments that haven't migrated yet. Migration is incremental.
    // ──────────────────────────────────────────────────────────────
}

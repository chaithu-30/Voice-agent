package com.voiceagent.kit.core

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.voiceagent.kit.analytics.VoiceAnalyticsTracker
import com.voiceagent.kit.livekit.LiveKitEngine
import com.voiceagent.kit.persistence.VoiceSessionDao
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Internal factory that creates [VoiceAgentAttachment] instances.
 * Handles ViewModel creation and lifecycle observer registration.
 */
class VoiceAgentManager(
    private val engine: LiveKitEngine,
    private val dao: VoiceSessionDao,
    private val tracker: VoiceAnalyticsTracker,
    private val userId: String
) {

    private val TAG = "VoiceAgentManager"

    /**
     * Creates and registers a [VoiceAgentAttachment] on the given [fragment]'s lifecycle.
     *
     * @param fragment          The host fragment.
     * @param schema            The [FormSchema] for the screen.
     * @param onFieldFilled     Callback: a field was filled by voice.
     * @param onFormCompleted   Callback: all required fields are filled.
     * @param onNavigate        Callback: a navigation instruction was received.
     * @param onError           Callback: an error occurred in the SDK.
     * @return The created [VoiceAgentAttachment].
     */
    fun attach(
        fragment: Fragment,
        schema: FormSchema,
        onFieldFilled: (fieldId: String, value: String) -> Unit,
        onFormCompleted: () -> Unit,
        onNavigate: (screenName: String) -> Unit,
        onError: (error: String) -> Unit
    ): VoiceAgentAttachment {
        VoiceAgentLogger.i(TAG, "Attaching VoiceAgent to fragment for screen '${schema.screenId}'")

        // Create or get the ViewModel for session persistence
        val viewModel = ViewModelProvider(
            fragment,
            VoiceSessionViewModelFactory(dao, schema.screenId, userId)
        )[VoiceSessionViewModel::class.java]

        // Purge stale sessions on every new attach
        viewModel.purgeExpiredSessions()

        val attachment = VoiceAgentAttachment(
            fragment = fragment,
            schema = schema,
            engine = engine,
            sessionViewModel = viewModel,
            tracker = tracker,
            userId = userId,
            onFieldFilled = onFieldFilled,
            onFormCompleted = onFormCompleted,
            onNavigate = onNavigate,
            onError = onError
        )

        // Register on the fragment lifecycle — attachment self-manages from here
        fragment.lifecycle.addObserver(attachment)
        VoiceAgentLogger.d(TAG, "VoiceAgentAttachment registered on fragment lifecycle")

        return attachment
    }
}

/**
 * [ViewModelProvider.Factory] for [VoiceSessionViewModel].
 */
private class VoiceSessionViewModelFactory(
    private val dao: VoiceSessionDao,
    private val screenId: String,
    private val userId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceSessionViewModel::class.java)) {
            return VoiceSessionViewModel(dao, screenId, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

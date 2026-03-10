package com.voiceagent.kit

import androidx.fragment.app.Fragment
import com.voiceagent.kit.core.VoiceAgentAttachment
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Public API for per-fragment voice agent integration.
 *
 * Usage — single call from any fragment:
 * ```kotlin
 * VoiceAgent.attach(
 *     fragment        = this,
 *     schema          = schema,
 *     onFieldFilled   = { fieldId, value -> validateFormAndUpdateButton() },
 *     onFormCompleted = { handleAddSiteDetails() },
 *     onNavigate      = { screenName ->
 *         when (screenName) {
 *             "digitization_method" -> handleAddSiteDetails()
 *             "dashboard_home"      -> findNavController().navigate(R.id.nav_home)
 *         }
 *     },
 *     onError = { error -> Log.e(TAG, error) }
 * )
 * ```
 *
 * The returned [VoiceAgentAttachment] is registered on the fragment's lifecycle
 * automatically — **no further management is required in the fragment**.
 */
object VoiceAgent {

    private val TAG = "VoiceAgent"

    /**
     * Attach the VoiceAgentKit SDK to a fragment.
     *
     * @param fragment          The fragment that owns the form screen.
     * @param schema            The [FormSchema] defining which views the SDK manages.
     * @param onFieldFilled     Invoked on the main thread when a field is filled by voice.
     *                          Use this to run validation or update UI state.
     * @param onFormCompleted   Invoked when all [VoiceField.isRequired] fields are filled.
     *                          Use this to trigger form submission or navigation.
     * @param onNavigate        Invoked when the backend sends a navigation intent.
     *                          Use a `when` block to handle known screen names.
     * @param onError           Invoked when a recoverable error occurs in the SDK.
     * @return The [VoiceAgentAttachment] — registered automatically on fragment lifecycle.
     *         You may hold a reference to it but you do **not** need to manage its lifecycle.
     */
    fun attach(
        fragment: Fragment,
        schema: FormSchema,
        onFieldFilled: (fieldId: String, value: String) -> Unit = { _, _ -> },
        onFormCompleted: () -> Unit = {},
        onNavigate: (screenName: String) -> Unit = {},
        onError: (error: String) -> Unit = {}
    ): VoiceAgentAttachment {
        val kit = VoiceAgentKit
        check(kit.isInitialized) {
            "VoiceAgentKit must be initialized before calling VoiceAgent.attach(). " +
            "Call VoiceAgentKit.initialize() in your Application.onCreate()."
        }

        VoiceAgentLogger.i(TAG, "Attaching to fragment '${fragment::class.simpleName}' " +
                "for schema '${schema.screenId}'")

        return kit.manager.attach(
            fragment      = fragment,
            schema        = schema,
            onFieldFilled = onFieldFilled,
            onFormCompleted = onFormCompleted,
            onNavigate    = onNavigate,
            onError       = onError
        )
    }
}

package com.voiceagent.kit.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.voiceagent.kit.analytics.VoiceAnalyticsTracker
import com.voiceagent.kit.analytics.VoiceEvent
import com.voiceagent.kit.applier.FieldValueApplier
import com.voiceagent.kit.context.FieldUpdateSender
import com.voiceagent.kit.context.ScreenContextBuilder
import com.voiceagent.kit.livekit.LiveKitEngine
import com.voiceagent.kit.livekit.LiveKitEventHandler
import com.voiceagent.kit.mapping.SchemaFieldMapper
import com.voiceagent.kit.mapping.ValueNormalizer
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.schema.SchemaMode
import com.voiceagent.kit.utils.VoiceAgentLogger
import java.lang.ref.WeakReference

/**
 * The central lifecycle-aware SDK object for a single fragment screen.
 *
 * Created by [VoiceAgent.attach()] and registered on [Fragment.lifecycle].
 * Wires together all SDK components:
 *  - [FormStateManager]       — tracks fill state and form completion
 *  - [FieldValueApplier]      — applies incoming values to Views safely
 *  - [FieldUpdateSender]      — attaches outgoing listeners to Views
 *  - [ScreenContextBuilder]   — sends screen open context payload
 *  - [LiveKitEventHandler]    — registers form data + connection state listeners
 *  - [VoiceSessionViewModel]  — persists session state
 *  - [VoiceAnalyticsTracker]  — fires Firebase Analytics events
 *
 * Key design rules enforced here:
 *  1. All listeners stored as named class properties (not anonymous lambdas) for removability
 *  2. pendingFormData cache held internally — never in the fragment
 *  3. TextWatcher loop prevention delegated to [FieldValueApplier]
 *  4. All View operations on main thread via view.post{}
 *  5. Fragment validity checked (isAdded && !isDetached) before every UI op
 */
class VoiceAgentAttachment(
    fragment: Fragment,
    private val schema: FormSchema,
    private val engine: LiveKitEngine,
    private val sessionViewModel: VoiceSessionViewModel,
    private val tracker: VoiceAnalyticsTracker,
    private val userId: String,
    private val onFieldFilled: (fieldId: String, value: String) -> Unit,
    private val onFormCompleted: () -> Unit,
    private val onNavigate: (screenName: String) -> Unit,
    private val onError: (error: String) -> Unit
) : DefaultLifecycleObserver {

    private val TAG = "VoiceAgentAttachment"
    private val gson = Gson()

    // WeakReference to avoid leaking the fragment
    private val fragmentRef = WeakReference(fragment)

    /** Pending form data received while the fragment was not yet resumed. */
    private var pendingFormData: Map<String, Any?>? = null

    /** Per-field attempt count for analytics. */
    private val fieldAttemptCounts = mutableMapOf<String, Int>()

    /** Timestamp when this attachment's session started (for duration tracking). */
    private val sessionStartTime = System.currentTimeMillis()

    // ──────────────────────────────────────────────────────────────
    // Sub-components created once per attachment
    // ──────────────────────────────────────────────────────────────

    private val formState = FormStateManager(schema)
    private val applier = FieldValueApplier(fragment)
    private val contextBuilder = ScreenContextBuilder(engine)
    private val fieldUpdateSender = FieldUpdateSender(schema, engine, applier)

    // ──────────────────────────────────────────────────────────────
    // Listener instances stored as named properties for removal by reference
    // ──────────────────────────────────────────────────────────────

    private val formDataListener = LiveKitEventHandler.FormDataListener { data ->
        handleIncomingFormData(data)
    }

    private val connectionStateListener = LiveKitEventHandler.ConnectionStateListener { isConnected ->
        handleConnectionStateChanged(isConnected)
    }

    // ──────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────

    override fun onResume(owner: LifecycleOwner) {
        VoiceAgentLogger.d(TAG, "onResume for '${schema.screenId}'")

        // Re-attach outgoing listeners
        fieldUpdateSender.attachAll()

        // Register incoming listeners
        engine.eventHandler.addFormDataListener(formDataListener)
        engine.eventHandler.addConnectionStateListener(connectionStateListener)

        // If LiveKit is already connected, fire context immediately
        if (engine.isConnected()) {
            val currentValues = fieldUpdateSender.getCurrentValues()
            contextBuilder.sendContext(schema, currentValues)
        }

        // Apply any form data that arrived while paused
        pendingFormData?.let { pending ->
            VoiceAgentLogger.d(TAG, "Applying pending form data (${pending.size} keys)")
            applyFormData(pending)
            pendingFormData = null
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        VoiceAgentLogger.d(TAG, "onPause for '${schema.screenId}'")

        // Remove incoming listeners
        engine.eventHandler.removeFormDataListener(formDataListener)
        engine.eventHandler.removeConnectionStateListener(connectionStateListener)

        // Detach outgoing listeners + cancel debounce
        fieldUpdateSender.detachAll()

        // Persist field states
        val snapshot = formState.snapshotCurrentValues()
        val json = gson.toJson(snapshot)
        sessionViewModel.saveFieldStates(json)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        VoiceAgentLogger.d(TAG, "onDestroy for '${schema.screenId}'")
        teardown()
    }

    // ──────────────────────────────────────────────────────────────
    // Connection state handler
    // ──────────────────────────────────────────────────────────────

    private fun handleConnectionStateChanged(isConnected: Boolean) {
        VoiceAgentLogger.d(TAG, "Connection state changed: $isConnected")
        if (isConnected) {
            val frag = fragmentRef.get() ?: return
            if (!frag.isResumed) return  // Guard: only send context if resumed

            val currentValues = fieldUpdateSender.getCurrentValues()
            contextBuilder.sendContext(schema, currentValues)

            sessionViewModel.startSession()
            tracker.track(
                VoiceEvent.SessionStart(
                    screenId = schema.screenId,
                    userId = userId,
                    mode = when (schema.mode) {
                        SchemaMode.ADD  -> "add"
                        SchemaMode.EDIT -> "edit"
                        SchemaMode.VIEW -> "view"
                    }
                )
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Incoming form data handler
    // ──────────────────────────────────────────────────────────────

    private fun handleIncomingFormData(data: Map<String, Any?>) {
        val frag = fragmentRef.get()

        // RULE 7: Extract screen_name BEFORE field mapping — it is a navigation instruction
        val screenName = ValueNormalizer.normalize(data["screen_name"])
        if (screenName != null && screenName != schema.screenId) {
            VoiceAgentLogger.d(TAG, "Navigation intent received: '$screenName'")
            frag?.view?.post {
                if (frag.isAdded && !frag.isDetached) {
                    try { onNavigate(screenName) }
                    catch (e: Exception) { VoiceAgentLogger.e(TAG, "onNavigate threw", e) }
                }
            }
            // Navigation-only payload — do not attempt field mapping
            val hasOnlyNavKeys = data.keys.all { it == "screen_name" || it == "type" }
            if (hasOnlyNavKeys) return
        }

        // If fragment is not resumed, cache the data for application on next resume
        if (frag == null || !frag.isResumed) {
            VoiceAgentLogger.d(TAG, "Fragment not resumed — caching form data")
            pendingFormData = data
            return
        }

        applyFormData(data)
    }

    private fun applyFormData(data: Map<String, Any?>) {
        val frag = fragmentRef.get() ?: return

        // FEATURE 4: Resolve fields using SchemaFieldMapper
        val resolvedFields = SchemaFieldMapper.resolve(schema, data)

        if (resolvedFields.isEmpty()) {
            VoiceAgentLogger.d(TAG, "No fields resolved from incoming data for '${schema.screenId}'")
            return
        }

        resolvedFields.forEach { resolved ->
            val field = schema.fieldById(resolved.fieldId) ?: return@forEach

            // FEATURE 5: Apply to View
            applier.apply(field, resolved.value)

            // Update form state
            val attemptCount = formState.onFieldFilled(resolved.fieldId)

            // Analytics
            tracker.track(
                VoiceEvent.FieldFilled(
                    screenId = schema.screenId,
                    fieldId = resolved.fieldId,
                    attemptCount = attemptCount
                )
            )

            // Host-app callback on main thread
            frag.view?.post {
                if (frag.isAdded && !frag.isDetached) {
                    try {
                        onFieldFilled(resolved.fieldId, resolved.value)
                    } catch (e: Exception) {
                        VoiceAgentLogger.e(TAG, "onFieldFilled callback threw", e)
                    }
                }
            }
        }

        // Check form completion
        if (formState.isFormComplete()) {
            val duration = System.currentTimeMillis() - sessionStartTime
            tracker.track(
                VoiceEvent.FormCompleted(
                    screenId = schema.screenId,
                    totalFields = schema.fields.size,
                    durationMs = duration
                )
            )
            sessionViewModel.completeSession()
            frag.view?.post {
                if (frag.isAdded && !frag.isDetached) {
                    try { onFormCompleted() }
                    catch (e: Exception) { VoiceAgentLogger.e(TAG, "onFormCompleted callback threw", e) }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Teardown
    // ──────────────────────────────────────────────────────────────

    private fun teardown() {
        engine.eventHandler.removeFormDataListener(formDataListener)
        engine.eventHandler.removeConnectionStateListener(connectionStateListener)
        fieldUpdateSender.teardown()
        applier.teardown()
        pendingFormData = null
        fieldAttemptCounts.clear()

        // Cancel incomplete session
        if (sessionViewModel.activeSessionId != null && !formState.isFormComplete()) {
            tracker.track(
                VoiceEvent.SessionCancelled(
                    screenId = schema.screenId,
                    fieldsCompleted = formState.filledRequiredFieldCount()
                )
            )
            sessionViewModel.cancelSession()
        }
        VoiceAgentLogger.d(TAG, "VoiceAgentAttachment fully torn down for '${schema.screenId}'")
    }
}

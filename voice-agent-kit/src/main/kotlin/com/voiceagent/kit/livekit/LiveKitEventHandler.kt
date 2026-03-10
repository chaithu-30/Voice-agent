package com.voiceagent.kit.livekit

import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * Internal event handler that processes raw [RoomEvent.DataReceived] payloads from
 * the LiveKit DataChannel, parses them into Map<String, Any?>, and dispatches to
 * all registered [FormDataListener]s.
 *
 * This is a separate class to keep [LiveKitEngine] focused on connection management.
 */
class LiveKitEventHandler {

    private val TAG = "LiveKitEventHandler"

    /** Listener called when structured form data arrives from the DataChannel. */
    fun interface FormDataListener {
        fun onFormData(data: Map<String, Any?>)
    }

    /** Listener called when the LiveKit connection state changes. */
    fun interface ConnectionStateListener {
        fun onConnectionStateChanged(isConnected: Boolean)
    }

    private val formDataListeners = mutableSetOf<FormDataListener>()
    private val connectionListeners = mutableSetOf<ConnectionStateListener>()
    private val formDataLock = Any()
    private val connectionLock = Any()

    // ──────────────────────────────────────────────────────────────
    // Registration
    // ──────────────────────────────────────────────────────────────

    fun addFormDataListener(listener: FormDataListener) {
        synchronized(formDataLock) { formDataListeners.add(listener) }
        VoiceAgentLogger.d(TAG, "FormDataListener added. Total: ${formDataListeners.size}")
    }

    fun removeFormDataListener(listener: FormDataListener) {
        synchronized(formDataLock) { formDataListeners.remove(listener) }
        VoiceAgentLogger.d(TAG, "FormDataListener removed. Total: ${formDataListeners.size}")
    }

    fun addConnectionStateListener(listener: ConnectionStateListener) {
        synchronized(connectionLock) { connectionListeners.add(listener) }
        VoiceAgentLogger.d(TAG, "ConnectionStateListener added. Total: ${connectionListeners.size}")
    }

    fun removeConnectionStateListener(listener: ConnectionStateListener) {
        synchronized(connectionLock) { connectionListeners.remove(listener) }
        VoiceAgentLogger.d(TAG, "ConnectionStateListener removed. Total: ${connectionListeners.size}")
    }

    // ──────────────────────────────────────────────────────────────
    // Dispatch — thread-safe snapshot iteration
    // ──────────────────────────────────────────────────────────────

    internal fun dispatchFormData(data: Map<String, Any?>) {
        val snapshot = synchronized(formDataLock) { formDataListeners.toList() }
        VoiceAgentLogger.d(TAG, "Dispatching form data to ${snapshot.size} listeners. Keys: ${data.keys}")
        snapshot.forEach { listener ->
            try {
                listener.onFormData(data)
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "FormDataListener threw exception", e)
            }
        }
    }

    internal fun dispatchConnectionState(isConnected: Boolean) {
        val snapshot = synchronized(connectionLock) { connectionListeners.toList() }
        VoiceAgentLogger.d(TAG, "Dispatching connection state: $isConnected to ${snapshot.size} listeners")
        snapshot.forEach { listener ->
            try {
                listener.onConnectionStateChanged(isConnected)
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "ConnectionStateListener threw exception", e)
            }
        }
    }

    fun clearAll() {
        synchronized(formDataLock) { formDataListeners.clear() }
        synchronized(connectionLock) { connectionListeners.clear() }
        VoiceAgentLogger.d(TAG, "All listeners cleared")
    }
}

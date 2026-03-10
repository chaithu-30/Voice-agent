package com.voiceagent.kit.utils

import android.os.Handler
import android.os.Looper

/**
 * A per-field debounce handler that delays sending outgoing field updates by
 * [DEFAULT_DEBOUNCE_MS] milliseconds. Any intermediate changes within the window
 * collapse into a single send, preventing server spam while the user types.
 */
class DebounceHandler(private val delayMs: Long = DEFAULT_DEBOUNCE_MS) {

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 600L
    }

    private val handler = Handler(Looper.getMainLooper())

    // fieldId → pending runnable
    private val pendingRunnables = mutableMapOf<String, Runnable>()
    private val lock = Any()

    /**
     * Schedule [action] to run after [delayMs], keyed by [fieldId].
     * Cancels any previously pending runnable for the same [fieldId].
     */
    fun schedule(fieldId: String, action: () -> Unit) {
        synchronized(lock) {
            pendingRunnables[fieldId]?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                synchronized(lock) { pendingRunnables.remove(fieldId) }
                action()
            }
            pendingRunnables[fieldId] = runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    /** Cancel any pending debounce for [fieldId]. */
    fun cancel(fieldId: String) {
        synchronized(lock) {
            pendingRunnables[fieldId]?.let { handler.removeCallbacks(it) }
            pendingRunnables.remove(fieldId)
        }
    }

    /** Cancel ALL pending debounces. Call from onPause / onDestroyView. */
    fun cancelAll() {
        synchronized(lock) {
            pendingRunnables.values.forEach { handler.removeCallbacks(it) }
            pendingRunnables.clear()
        }
    }
}

package com.voiceagent.kit.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * A convenience base that registers itself on the provided Fragment's lifecycle
 * and provides clean hooks for [onResume], [onPause], and [onDestroyView].
 *
 * Subclasses should override the methods they need and ensure [teardown] is called
 * (automatically) from [onDestroy] to prevent memory leaks.
 */
abstract class LifecycleAwareAttachment(
    protected val fragment: Fragment
) : DefaultLifecycleObserver {

    init {
        // Register on the fragment view lifecycle so that onDestroyView fires
        // when the fragment's view is destroyed (not just the fragment itself).
        fragment.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        onAttachmentResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        onAttachmentPause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        teardown()
        fragment.lifecycle.removeObserver(this)
    }

    /** Called when the fragment resumes. Override to re-register listeners. */
    protected open fun onAttachmentResume() {}

    /** Called when the fragment pauses. Override to deregister listeners / cancel debounce. */
    protected open fun onAttachmentPause() {}

    /** Called on fragment destroy. Override to fully clean up all resources. */
    protected open fun teardown() {}
}

package com.voiceagent.kit.utils

import android.util.Log
import com.voiceagent.kit.config.VoiceLogLevel

/**
 * Internal logger for the VoiceAgentKit SDK.
 * Respects the [VoiceLogLevel] set in [VoiceAgentConfig].
 * All log tags are automatically prefixed with "VAK/" for easy logcat filtering.
 */
object VoiceAgentLogger {

    private const val TAG_PREFIX = "VAK"
    internal var currentLevel: VoiceLogLevel = VoiceLogLevel.INFO

    fun v(tag: String, message: String) {
        if (currentLevel == VoiceLogLevel.DEBUG) {
            Log.v("$TAG_PREFIX/$tag", message)
        }
    }

    fun d(tag: String, message: String) {
        if (currentLevel.ordinal >= VoiceLogLevel.DEBUG.ordinal) {
            Log.d("$TAG_PREFIX/$tag", message)
        }
    }

    fun i(tag: String, message: String) {
        if (currentLevel.ordinal >= VoiceLogLevel.INFO.ordinal) {
            Log.i("$TAG_PREFIX/$tag", message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (currentLevel.ordinal >= VoiceLogLevel.WARN.ordinal) {
            if (throwable != null) {
                Log.w("$TAG_PREFIX/$tag", message, throwable)
            } else {
                Log.w("$TAG_PREFIX/$tag", message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (currentLevel.ordinal >= VoiceLogLevel.ERROR.ordinal) {
            if (throwable != null) {
                Log.e("$TAG_PREFIX/$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX/$tag", message)
            }
        }
    }
}

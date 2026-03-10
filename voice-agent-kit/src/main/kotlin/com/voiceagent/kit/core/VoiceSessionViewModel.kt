package com.voiceagent.kit.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceagent.kit.persistence.VoiceSessionDao
import com.voiceagent.kit.persistence.VoiceSessionEntity
import com.voiceagent.kit.utils.VoiceAgentLogger
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * [ViewModel] that bridges the SDK's session persistence layer with the fragment lifecycle.
 * Survives configuration changes. Created by [VoiceAgentManager] per fragment.
 */
class VoiceSessionViewModel(
    private val dao: VoiceSessionDao,
    private val screenId: String,
    private val userId: String
) : ViewModel() {

    private val TAG = "VoiceSessionViewModel"
    private val SESSION_TTL_MS = 24 * 60 * 60 * 1000L  // 24 hours

    var activeSessionId: String? = null
        private set

    /**
     * Called when a new voice session starts (e.g. LiveKit connects).
     * Creates a new [VoiceSessionEntity] in the database.
     */
    fun startSession() {
        if (activeSessionId != null) return  // already active
        val id = UUID.randomUUID().toString()
        activeSessionId = id
        val now = System.currentTimeMillis()
        val entity = VoiceSessionEntity(
            sessionId = id,
            screenId = screenId,
            userId = userId,
            fieldStatesJson = "{}",
            status = "active",
            createdAt = now,
            updatedAt = now
        )
        viewModelScope.launch {
            try {
                dao.insertOrReplace(entity)
                VoiceAgentLogger.d(TAG, "Session started: $id for screen '$screenId'")
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Failed to start session", e)
            }
        }
    }

    /**
     * Persist a field state snapshot. Called on [VoiceAgentAttachment.onPause].
     */
    fun saveFieldStates(jsonSnapshot: String) {
        val id = activeSessionId ?: return
        viewModelScope.launch {
            try {
                dao.updateFieldStates(id, jsonSnapshot)
                VoiceAgentLogger.d(TAG, "Field states saved for session $id")
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Failed to save field states", e)
            }
        }
    }

    /**
     * Mark the session completed. Called when [FormStateManager.isFormComplete()] returns true.
     */
    fun completeSession() {
        val id = activeSessionId ?: return
        viewModelScope.launch {
            try {
                dao.markCompleted(id)
                VoiceAgentLogger.d(TAG, "Session completed: $id")
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Failed to complete session", e)
            }
        }
        activeSessionId = null
    }

    /**
     * Mark the session cancelled. Called when the fragment is destroyed without completion.
     */
    fun cancelSession() {
        val id = activeSessionId ?: return
        viewModelScope.launch {
            try {
                dao.markCancelled(id)
                VoiceAgentLogger.d(TAG, "Session cancelled: $id")
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Failed to cancel session", e)
            }
        }
        activeSessionId = null
    }

    /**
     * Check if a restorable active session exists for the current screen/user.
     * @return The most recent restorable [VoiceSessionEntity], or null.
     */
    suspend fun findRestorableSession(): VoiceSessionEntity? {
        return try {
            dao.findActiveSession(screenId, userId)
        } catch (e: Exception) {
            VoiceAgentLogger.e(TAG, "Failed to find restorable session", e)
            null
        }
    }

    /**
     * Clean up expired sessions (>24h old). Should be called once per app session.
     */
    fun purgeExpiredSessions() {
        val cutoff = System.currentTimeMillis() - SESSION_TTL_MS
        viewModelScope.launch {
            try {
                dao.deleteExpired(cutoff)
                VoiceAgentLogger.d(TAG, "Expired sessions purged (cutoff: $cutoff)")
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Failed to purge expired sessions", e)
            }
        }
    }
}

package com.voiceagent.kit.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for [VoiceSessionEntity].
 */
@Dao
interface VoiceSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(session: VoiceSessionEntity)

    @Update
    suspend fun update(session: VoiceSessionEntity)

    @Query("SELECT * FROM voice_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun findById(sessionId: String): VoiceSessionEntity?

    /** Find the most recent active session for a given screen and user. */
    @Query(
        """
        SELECT * FROM voice_sessions
        WHERE screenId = :screenId AND userId = :userId AND status = 'active'
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun findActiveSession(screenId: String, userId: String): VoiceSessionEntity?

    /** Mark a session as completed. */
    @Query("UPDATE voice_sessions SET status = 'completed', updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun markCompleted(sessionId: String, now: Long = System.currentTimeMillis())

    /** Mark a session as cancelled. */
    @Query("UPDATE voice_sessions SET status = 'cancelled', updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun markCancelled(sessionId: String, now: Long = System.currentTimeMillis())

    /** Update the field state snapshot and updatedAt timestamp. */
    @Query("UPDATE voice_sessions SET fieldStatesJson = :json, updatedAt = :now WHERE sessionId = :sessionId")
    suspend fun updateFieldStates(sessionId: String, json: String, now: Long = System.currentTimeMillis())

    /** Delete sessions older than [cutoffMs] (default: 24 hours). */
    @Query("DELETE FROM voice_sessions WHERE createdAt < :cutoffMs")
    suspend fun deleteExpired(cutoffMs: Long)

    /** Delete all sessions for a given user. */
    @Query("DELETE FROM voice_sessions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

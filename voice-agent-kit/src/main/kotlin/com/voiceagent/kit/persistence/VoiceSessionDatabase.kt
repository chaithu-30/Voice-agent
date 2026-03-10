package com.voiceagent.kit.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for VoiceAgentKit session persistence.
 * This is a singleton managed by [VoiceAgentKit].
 */
@Database(
    entities = [VoiceSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoiceSessionDatabase : RoomDatabase() {

    abstract fun voiceSessionDao(): VoiceSessionDao

    companion object {
        private const val DB_NAME = "voice_agent_sessions.db"

        @Volatile
        private var INSTANCE: VoiceSessionDatabase? = null

        internal fun getInstance(context: Context): VoiceSessionDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoiceSessionDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Only for testing — allows injecting an in-memory database. */
        internal fun setTestInstance(db: VoiceSessionDatabase) {
            INSTANCE = db
        }
    }
}

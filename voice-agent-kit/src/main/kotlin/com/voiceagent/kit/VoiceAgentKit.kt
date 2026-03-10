package com.voiceagent.kit

import android.content.Context
import com.voiceagent.kit.analytics.VoiceAnalyticsTracker
import com.voiceagent.kit.config.VoiceAgentConfig
import com.voiceagent.kit.core.VoiceAgentManager
import com.voiceagent.kit.livekit.LiveKitEngine
import com.voiceagent.kit.persistence.VoiceSessionDatabase
import com.voiceagent.kit.utils.VoiceAgentLogger

/**
 * SDK entry point. Must be initialized exactly once in `Application.onCreate()`:
 *
 * ```kotlin
 * VoiceAgentKit.initialize(
 *     context = this,
 *     config = VoiceAgentConfig(
 *         backendUrl        = "https://bmgf-backend.doordrishti.ai/",
 *         liveKitUrl        = "wss://your-livekit-server.com",
 *         enableAnalytics   = true,
 *         logLevel          = VoiceLogLevel.DEBUG,
 *         languageProvider  = { sessionManager.getSelectedLanguageForLiveKit() }
 *     )
 * )
 * ```
 *
 * Then from MainActivity:
 * ```kotlin
 * VoiceAgentKit.connect(userId = currentUserId)
 * // on sign-out:
 * VoiceAgentKit.disconnect()
 * ```
 */
object VoiceAgentKit {

    private val TAG = "VoiceAgentKit"

    /** True once [initialize] has been called successfully. */
    @Volatile
    var isInitialized: Boolean = false
        private set

    // ──────────────────────────────────────────────────────────────
    // Singleton components
    // ──────────────────────────────────────────────────────────────

    private lateinit var _config: VoiceAgentConfig
    private lateinit var _engine: LiveKitEngine
    private lateinit var _tracker: VoiceAnalyticsTracker
    private lateinit var _database: VoiceSessionDatabase
    private lateinit var _manager: VoiceAgentManager

    val config: VoiceAgentConfig get() = _config
    val engine: LiveKitEngine     get() = _engine
    val tracker: VoiceAnalyticsTracker get() = _tracker

    /** Internal access for [VoiceAgent]. */
    internal val manager: VoiceAgentManager get() = _manager

    // ──────────────────────────────────────────────────────────────
    // Initialize
    // ──────────────────────────────────────────────────────────────

    /**
     * Initialize the SDK. Safe to call multiple times — subsequent calls are no-ops.
     *
     * @param context Application [Context].
     * @param config  [VoiceAgentConfig] with backend URL, log level, analytics flag, etc.
     */
    @Synchronized
    fun initialize(context: Context, config: VoiceAgentConfig) {
        if (isInitialized) {
            VoiceAgentLogger.w(TAG, "VoiceAgentKit already initialized — skipping")
            return
        }

        _config = config

        // Configure logger
        VoiceAgentLogger.currentLevel = config.logLevel

        VoiceAgentLogger.i(TAG, "Initializing VoiceAgentKit (backendUrl=${config.backendUrl})")

        // Initialize Room database
        _database = VoiceSessionDatabase.getInstance(context)

        // Initialize LiveKit engine
        _engine = LiveKitEngine(context.applicationContext, config)

        // Initialize Analytics tracker
        _tracker = VoiceAnalyticsTracker(context.applicationContext, config.enableAnalytics)

        // Internal manager — userId populated on connect()
        // Manager is re-created on each connect() call to update userId
        // For now, create with placeholder; real userId set on connect()
        _manager = VoiceAgentManager(
            engine  = _engine,
            dao     = _database.voiceSessionDao(),
            tracker = _tracker,
            userId  = ""
        )

        isInitialized = true
        VoiceAgentLogger.i(TAG, "VoiceAgentKit initialized successfully")
    }

    // ──────────────────────────────────────────────────────────────
    // Connect / Disconnect (called from MainActivity)
    // ──────────────────────────────────────────────────────────────

    /**
     * Connect to the LiveKit room for [userId].
     * Must be called after [initialize].
     * Safe to call on any thread.
     *
     * @param userId Authenticated user's ID. Used to form the room name and identity.
     */
    fun connect(userId: String) {
        checkInitialized()
        VoiceAgentLogger.i(TAG, "Connecting for userId: $userId")

        // Recreate manager with real userId
        _manager = VoiceAgentManager(
            engine  = _engine,
            dao     = _database.voiceSessionDao(),
            tracker = _tracker,
            userId  = userId
        )

        _engine.connect(userId)
    }

    /**
     * Disconnect from the LiveKit room.
     * Should be called on sign-out or when the app goes fully background.
     */
    fun disconnect() {
        checkInitialized()
        VoiceAgentLogger.i(TAG, "Disconnecting LiveKit")
        _engine.disconnect()
    }

    /**
     * Fully release all SDK resources. Typically called on Application termination.
     * After this call, [initialize] must be called again before using the SDK.
     */
    fun release() {
        if (!isInitialized) return
        _engine.release()
        _database.close()
        isInitialized = false
        VoiceAgentLogger.i(TAG, "VoiceAgentKit released")
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private fun checkInitialized() {
        check(isInitialized) {
            "VoiceAgentKit is not initialized. Call VoiceAgentKit.initialize() in Application.onCreate()."
        }
    }

    /** @return True if the LiveKit engine is currently connected. */
    val isConnected: Boolean
        get() = isInitialized && _engine.isConnected()
}

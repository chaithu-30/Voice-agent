package com.voiceagent.kit.livekit

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.voiceagent.kit.config.VoiceAgentConfig
import com.voiceagent.kit.utils.VoiceAgentLogger
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * SDK-owned singleton that manages the LiveKit WebRTC connection lifecycle.
 * Created once inside [VoiceAgentKit.initialize()] and reused across all fragment
 * attachments.  Fragment-level [VoiceAgentAttachment]s only register/deregister
 * listeners here — they never open or close connections.
 */
class LiveKitEngine(
    private val context: Context,
    private val config: VoiceAgentConfig
) {

    private val TAG = "LiveKitEngine"
    private val gson = Gson()

    // ──────────────────────────────────────────────────────────────
    // Coroutine scope — supervisor so one failing child doesn't cancel siblings
    // ──────────────────────────────────────────────────────────────
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventCollectionJob: Job? = null

    // ──────────────────────────────────────────────────────────────
    // LiveKit Room
    // ──────────────────────────────────────────────────────────────
    private var room: Room? = null
    private var localAudioTrack: LocalAudioTrack? = null

    // ──────────────────────────────────────────────────────────────
    // Connection state
    // ──────────────────────────────────────────────────────────────
    @Volatile
    private var _isConnected: Boolean = false

    fun isConnected(): Boolean = _isConnected

    // ──────────────────────────────────────────────────────────────
    // Event Handler (fan-out dispatcher)
    // ──────────────────────────────────────────────────────────────
    val eventHandler = LiveKitEventHandler()

    // ──────────────────────────────────────────────────────────────
    // Retrofit / Token Service
    // ──────────────────────────────────────────────────────────────
    private val tokenService: LiveKitTokenService by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(config.backendUrl.trimEnd('/') + "/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LiveKitTokenService::class.java)
    }

    // ──────────────────────────────────────────────────────────────
    // Connect
    // ──────────────────────────────────────────────────────────────

    /**
     * Fetches a LiveKit token and connects to the room.
     * Should be called from MainActivity (app-level), not from fragments.
     *
     * @param userId The authenticated user's ID. Forms part of the identity and room names.
     */
    fun connect(userId: String) {
        engineScope.launch {
            try {
                VoiceAgentLogger.i(TAG, "Connecting for userId: $userId")

                val identity = "user_$userId"
                val timestamp = System.currentTimeMillis()
                val roomName = "user_${userId}_room_$timestamp"

                val response = tokenService.getToken(identity = identity, room = roomName)
                if (!response.isSuccessful || response.body() == null) {
                    val errorMsg = "Token fetch failed: HTTP ${response.code()}"
                    VoiceAgentLogger.e(TAG, errorMsg)
                    eventHandler.dispatchConnectionState(false)
                    return@launch
                }

                val tokenResponse = response.body()!!
                val serverUrl = config.liveKitUrl ?: tokenResponse.url

                VoiceAgentLogger.i(TAG, "Token received. Connecting to $serverUrl")

                val liveKitRoom = withContext(Dispatchers.Main) {
                    LiveKit.create(
                        appContext = context,
                        overrides = LiveKitOverrides()
                    )
                }

                room = liveKitRoom

                withContext(Dispatchers.Main) {
                    liveKitRoom.connect(
                        url = serverUrl,
                        token = tokenResponse.token,
                        options = RoomOptions()
                    )

                    // Publish microphone
                    val audioTrack = liveKitRoom.localParticipant.setMicrophoneEnabled(true)
                    localAudioTrack = liveKitRoom.localParticipant.getTrackPublication(Track.Source.MICROPHONE)
                        ?.track as? LocalAudioTrack
                }

                _isConnected = true
                eventHandler.dispatchConnectionState(true)
                VoiceAgentLogger.i(TAG, "LiveKit room connected successfully")

                // Start collecting room events
                startEventCollection(liveKitRoom)

            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Connection failed", e)
                _isConnected = false
                eventHandler.dispatchConnectionState(false)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Disconnect
    // ──────────────────────────────────────────────────────────────

    fun disconnect() {
        engineScope.launch {
            try {
                VoiceAgentLogger.i(TAG, "Disconnecting")
                eventCollectionJob?.cancel()
                eventCollectionJob = null

                withContext(Dispatchers.Main) {
                    localAudioTrack?.stop()
                    room?.disconnect()
                    room?.release()
                }
                room = null
                localAudioTrack = null
                _isConnected = false
                eventHandler.dispatchConnectionState(false)
                VoiceAgentLogger.i(TAG, "Disconnected")
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "Error during disconnect", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Room event collection
    // ──────────────────────────────────────────────────────────────

    private fun startEventCollection(liveKitRoom: Room) {
        eventCollectionJob?.cancel()
        eventCollectionJob = engineScope.launch {
            liveKitRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.DataReceived -> handleDataReceived(event)
                    is RoomEvent.Disconnected -> {
                        VoiceAgentLogger.w(TAG, "Room disconnected event received")
                        _isConnected = false
                        eventHandler.dispatchConnectionState(false)
                    }
                    is RoomEvent.Reconnecting -> {
                        VoiceAgentLogger.i(TAG, "Room reconnecting…")
                        _isConnected = false
                        eventHandler.dispatchConnectionState(false)
                    }
                    is RoomEvent.Reconnected -> {
                        VoiceAgentLogger.i(TAG, "Room reconnected")
                        _isConnected = true
                        eventHandler.dispatchConnectionState(true)
                    }
                    else -> { /* Ignore unhandled events */ }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Data Received parsing
    // ──────────────────────────────────────────────────────────────

    private fun handleDataReceived(event: RoomEvent.DataReceived) {
        try {
            val rawString = event.data.toString(Charsets.UTF_8)
            VoiceAgentLogger.d(TAG, "DataReceived: $rawString")

            val jsonElement = JsonParser.parseString(rawString)
            if (!jsonElement.isJsonObject) {
                VoiceAgentLogger.w(TAG, "DataReceived payload is not a JSON object, ignoring")
                return
            }

            val map = mutableMapOf<String, Any?>()
            jsonElement.asJsonObject.entrySet().forEach { (key, value) ->
                map[key] = when {
                    value.isJsonPrimitive -> {
                        val prim = value.asJsonPrimitive
                        when {
                            prim.isBoolean -> prim.asBoolean
                            prim.isNumber  -> prim.asNumber
                            else           -> prim.asString
                        }
                    }
                    value.isJsonNull -> null
                    value.isJsonArray -> gson.fromJson(value, List::class.java)
                    else -> value.toString()
                }
            }
            eventHandler.dispatchFormData(map)

        } catch (e: Exception) {
            VoiceAgentLogger.e(TAG, "Failed to parse DataReceived payload", e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Send outgoing data message
    // ──────────────────────────────────────────────────────────────

    /**
     * Sends a map of key-value pairs as a JSON DataChannel message.
     * Automatically injects "selected_language" from [VoiceAgentConfig.languageProvider].
     */
    fun sendDataMessage(data: Map<String, Any?>) {
        if (!_isConnected) {
            VoiceAgentLogger.w(TAG, "sendDataMessage called but not connected — dropping message")
            return
        }

        engineScope.launch {
            try {
                val mutable = data.toMutableMap()

                // Inject selected_language
                val language = try {
                    config.languageProvider?.invoke() ?: "en-IN"
                } catch (e: Exception) {
                    VoiceAgentLogger.w(TAG, "languageProvider threw exception", e)
                    "en-IN"
                }
                mutable["selected_language"] = language

                val json = gson.toJson(mutable)
                VoiceAgentLogger.d(TAG, "Sending DataMessage: $json")

                withContext(Dispatchers.Main) {
                    room?.localParticipant?.publishData(
                        data = json.toByteArray(Charsets.UTF_8),
                        reliability = io.livekit.android.room.participant.Participant.Reliability.RELIABLE
                    )
                }
            } catch (e: Exception) {
                VoiceAgentLogger.e(TAG, "sendDataMessage failed", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    fun release() {
        disconnect()
        engineScope.cancel()
        eventHandler.clearAll()
        VoiceAgentLogger.i(TAG, "LiveKitEngine released")
    }
}

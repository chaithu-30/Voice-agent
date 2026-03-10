package com.voiceagent.kit.livekit

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Response model from the LiveKit token endpoint.
 * @param token JWT token string for LiveKit Room authentication.
 * @param url   WebSocket URL of the LiveKit server. May override [VoiceAgentConfig.liveKitUrl].
 */
data class LiveKitTokenResponse(
    val token: String,
    val url: String
)

/**
 * Retrofit service interface for fetching a LiveKit participant token.
 * Matches the existing DDFin backend endpoint: GET /livekit/token
 */
interface LiveKitTokenService {

    @GET("livekit/token")
    suspend fun getToken(
        @Query("identity") identity: String,
        @Query("room") room: String
    ): Response<LiveKitTokenResponse>
}

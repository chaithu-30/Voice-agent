# Keep all SDK public classes & interfaces
-keep class com.voiceagent.kit.** { *; }
-keep interface com.voiceagent.kit.** { *; }
-keepclassmembers class com.voiceagent.kit.** { *; }

# Keep LiveKit data classes
-keep class io.livekit.android.** { *; }
-keepclassmembers class io.livekit.android.** { *; }

# Keep Room entities and DAOs
-keep class com.voiceagent.kit.persistence.** { *; }
-keepclassmembers class com.voiceagent.kit.persistence.** { *; }

# Keep Retrofit service interfaces
-keep interface com.voiceagent.kit.livekit.LiveKitTokenService { *; }
-keep class com.voiceagent.kit.livekit.LiveKitTokenResponse { *; }
-keepclassmembers class com.voiceagent.kit.livekit.LiveKitTokenResponse { *; }

# Keep Firebase event names and params
-keep class com.voiceagent.kit.analytics.** { *; }

# Gson serialization support
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

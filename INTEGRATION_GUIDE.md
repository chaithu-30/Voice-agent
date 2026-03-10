# Integration Guide — VoiceAgentKit

## Step 1: Gradle Setup

### `settings.gradle.kts` (root)
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // For local module (monorepo):
        // No extra config needed — just include(":voice-agent-kit") in settings

        // For GitHub Package (published AAR):
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/voice-agent-kit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_USERNAME")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
// For local module:
include(":voice-agent-kit")
```

### `app/build.gradle.kts`
```kotlin
dependencies {
    // Option A — local Gradle module:
    implementation(project(":voice-agent-kit"))

    // Option B — GitHub Packages AAR:
    // implementation("com.voiceagent:voice-agent-kit:1.0.0")

    // REQUIRED — the SDK declares these compileOnly; you must provide them:
    implementation("io.livekit:livekit-android:2.5.0")
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

---

## Step 2: Application Init

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VoiceAgentKit.initialize(
            context = this,
            config = VoiceAgentConfig(
                backendUrl       = BuildConfig.BACKEND_URL,
                liveKitUrl       = BuildConfig.LIVEKIT_URL,  // optional if backend returns it
                enableAnalytics  = !BuildConfig.DEBUG,
                logLevel         = if (BuildConfig.DEBUG) VoiceLogLevel.DEBUG else VoiceLogLevel.ERROR,
                languageProvider = { sessionManager.getSelectedLanguageForLiveKit() }
            )
        )
    }
}
```

---

## Step 3: MainActivity Setup

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onStart() {
        super.onStart()
        VoiceAgentKit.connect(userId = authManager.getCurrentUserId())
    }
    override fun onStop() {
        super.onStop()
        VoiceAgentKit.disconnect()
    }
}
```
> Your existing `getLiveKitManager()` / `getHybridModeManager()` methods remain unchanged.
> Migrate fragments one at a time — both integrations coexist safely.

---

## Step 4: Fragment Migration

### Before (old DDFin pattern):
```kotlin
// ~500 lines including:
private var liveKitManager: LiveKitManager? = null
private var hybridModeManager: HybridModeManager? = null
private var pendingFormData: LiveKitManager.FormData? = null
private var lastSiteName: String = ""
// ... 10-15 more vars ...
private val formDataListener = { formData -> /* 40 lines */ }
fun setupLiveKitIntegration() { /* 30 lines */ }
fun teardownLiveKitIntegration() { /* 20 lines */ }
fun sendInitialDataToLiveKit() { /* 40 lines */ }
fun applyLiveKitSiteName(value: String?) { /* 10 lines */ }
// ... 8-15 more apply functions ...
override fun onResume() { setupLiveKitIntegration(); applyPendingFormData() }
override fun onPause() { removeFormDataListener(); cancelConnectionCheck() }
```

### After (SDK pattern):
```kotlin
private val schema by lazy {
    FormSchema(
        screenId = "add_new_site",
        mode     = SchemaMode.ADD,
        fields   = listOf(
            VoiceField("site_name",  binding.tieSiteName,            FieldType.TEXT,    isRequired = true),
            VoiceField("ownership",  binding.rgOwnOrLeased,          FieldType.RADIO,   options = listOf("own", "leased")),
            VoiceField("irr_source", binding.spinnerIrrigationSource, FieldType.SPINNER, options = listOf("Borewell/Tube well", "Canal/River", "Well", "Tank/Pond"))
        )
    )
}

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // ... your existing non-voice logic unchanged ...
    
    VoiceAgent.attach(
        fragment        = this,
        schema          = schema,
        onFieldFilled   = { _, _ -> validateFormAndUpdateButton() },
        onFormCompleted = { handleAddSiteDetails() },
        onNavigate      = { screen -> when (screen) { "digitization_method" -> handleAddSiteDetails() } },
        onError         = { error -> Log.e(TAG, error) }
    )
}
// No onResume/onPause voice logic needed.
```

---

## Step 5: Schema Definition Guide

### TEXT / NUMBER Fields
```kotlin
VoiceField(
    id        = "site_name",
    viewRef   = binding.tieSiteName,   // EditText or TextInputEditText
    fieldType = FieldType.TEXT,
    isRequired = true
)
```

### RADIO Fields
```kotlin
VoiceField(
    id        = "ownership",
    viewRef   = binding.rgOwnOrLeased,  // RadioGroup
    fieldType = FieldType.RADIO,
    options   = listOf("own", "leased") // Must match RadioButton text or tag (case-insensitive)
)
```

### SPINNER Fields
```kotlin
VoiceField(
    id        = "irr_source",
    viewRef   = binding.spinnerIrrigationSource,  // Spinner
    fieldType = FieldType.SPINNER,
    options   = listOf("Borewell/Tube well", "Canal/River", "Well", "Tank/Pond")
)
```

### CHECKBOX Fields
```kotlin
VoiceField(
    id        = "is_organic",
    viewRef   = binding.cbIsOrganic,  // CheckBox
    fieldType = FieldType.CHECKBOX
)
```

### Custom JSON Key Aliases
```kotlin
VoiceField(
    id             = "irr_source",
    viewRef        = binding.spinnerIrrigationSource,
    fieldType      = FieldType.SPINNER,
    options        = listOf("Borewell/Tube well", "Canal/River"),
    jsonKeyAliases = listOf("irrigation_source", "draft_irrigation_source")
)
```

---

## Step 6: Navigation Callbacks

```kotlin
onNavigate = { screenName ->
    when (screenName) {
        "digitization_method" -> handleAddSiteDetails()
        "dashboard_home"      -> findNavController().navigate(R.id.nav_home)
        "site_list"           -> findNavController().navigateUp()
        else                  -> Log.w(TAG, "Unknown screen: $screenName")
    }
}
```

> `screen_name` from the backend data is extracted **before** field mapping.
> It is never applied to any registered View.

---

## Step 7: Session Persistence

When your fragment is backgrounded mid-session, the SDK automatically:
1. Saves current field values to the local Room database
2. On the next resume (same screen + same user), the session can be restored

Sessions expire after **24 hours**. Sessions are automatically purged on the next `VoiceAgent.attach()` call.

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `VoiceAgentKit is not initialized` | `initialize()` not called | Call `VoiceAgentKit.initialize()` in `Application.onCreate()` |
| Field never gets filled | JSON key mismatch | Add the backend key to `jsonKeyAliases` |
| `onFormCompleted` never fires | No fields marked `isRequired = true` | Set `isRequired = true` on mandatory fields |
| TextWatcher loops | Internal | Already handled by SDK — no action needed |
| Spinner fires immediately on attach | Normal Android behavior | SDK suppresses the first automatic `onItemSelected` call |
| LiveKit not connecting | Missing `RECORD_AUDIO` permission | Request `RECORD_AUDIO` before `VoiceAgentKit.connect()` |

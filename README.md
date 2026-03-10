# voice-agent-kit

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blueviolet.svg)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-28-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub Package](https://img.shields.io/badge/GitHub%20Packages-published-brightgreen)](https://github.com)

A **production-grade Android SDK** that eliminates all LiveKit voice-agent boilerplate from your fragments. Replace ~500 lines of per-fragment boilerplate with a clean **10–15 line integration** that is schema-driven, lifecycle-safe, and fully compatible with your existing DDFin tech stack.

---

## Quick Start

### Step 1 — Add the dependency

**`settings.gradle.kts`:**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/voice-agent-kit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_USERNAME")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

**`app/build.gradle.kts`:**
```kotlin
dependencies {
    implementation("com.voiceagent:voice-agent-kit:1.0.0")
    // You must also provide LiveKit, Firebase, and Retrofit (SDK declares them compileOnly)
    implementation("io.livekit:livekit-android:2.x.x")
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
```

### Step 2 — Initialize in `Application.onCreate()`

```kotlin
VoiceAgentKit.initialize(
    context = this,
    config = VoiceAgentConfig(
        backendUrl       = "https://bmgf-backend.doordrishti.ai/",
        liveKitUrl       = "wss://your-livekit-server.com",
        enableAnalytics  = true,
        logLevel         = VoiceLogLevel.DEBUG,
        languageProvider = { sessionManager.getSelectedLanguageForLiveKit() }
    )
)
```

### Step 3 — Connect from `MainActivity`

```kotlin
override fun onStart() {
    super.onStart()
    VoiceAgentKit.connect(userId = currentUserId)
}
override fun onStop() {
    super.onStop()
    VoiceAgentKit.disconnect()
}
```

### Step 4 — Attach to any Fragment

```kotlin
private val schema by lazy {
    FormSchema(
        screenId = "add_new_site",
        mode     = SchemaMode.ADD,
        fields   = listOf(
            VoiceField("site_name",  binding.tieSiteName,             FieldType.TEXT,    isRequired = true),
            VoiceField("ownership",  binding.rgOwnOrLeased,           FieldType.RADIO,   options = listOf("own", "leased")),
            VoiceField("irr_source", binding.spinnerIrrigationSource,  FieldType.SPINNER, options = listOf("Borewell/Tube well", "Canal/River", "Well", "Tank/Pond"))
        )
    )
}

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // ... your existing logic ...

    VoiceAgent.attach(
        fragment        = this,
        schema          = schema,
        onFieldFilled   = { fieldId, value -> validateFormAndUpdateButton() },
        onFormCompleted = { handleAddSiteDetails() },
        onNavigate      = { screen -> when (screen) { "dashboard_home" -> findNavController().navigate(R.id.nav_home) } },
        onError         = { error -> Log.e(TAG, error) }
    )
}
```

That's it. **No onResume/onPause/onDestroyView voice code needed.**

---

## Schema Field Types

| `FieldType` | View Type | Listener Applied | Send Trigger |
|-------------|-----------|-----------------|--------------|
| `TEXT` | `EditText` / `TextInputEditText` | `TextWatcher` | 600ms debounce |
| `NUMBER` | `EditText` / `TextInputEditText` | `TextWatcher` | 600ms debounce |
| `RADIO` | `RadioGroup` | `OnCheckedChangeListener` | Immediate |
| `SPINNER` | `AppCompatSpinner` | `OnItemSelectedListener` | Immediate |
| `CHECKBOX` | `CheckBox` | `OnCheckedChangeListener` | Immediate |

## VoiceField Options

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | `String` | ✅ | Unique field ID. SDK auto-generates JSON keys using mode prefix. |
| `viewRef` | `View` | ✅ | Reference to the Android View in your layout. |
| `fieldType` | `FieldType` | ✅ | Determines which listener is attached and how values are applied. |
| `options` | `List<String>?` | For RADIO/SPINNER | Valid options. Sent in screen context payload. |
| `jsonKeyAliases` | `List<String>?` | No | Extra JSON keys to check during resolution. |
| `isRequired` | `Boolean` | No | Marks field as required for form-completion detection. |

## JSON Key Resolution Order

For a field with `id = "site_name"`, the SDK tries these keys in order:

1. `site_name`
2. `draft_site_name`
3. `editing_site_name`
4. `viewing_site_name`
5. *(any alias in `jsonKeyAliases`)*

First non-null, non-empty, non-`"null"`, non-`"undefined"` value wins.

## Callbacks

| Callback | When it fires | Use it for |
|----------|-------------|------------|
| `onFieldFilled(fieldId, value)` | A field was filled by voice | Validate form, update button state |
| `onFormCompleted()` | All `isRequired` fields filled | Submit form, navigate |
| `onNavigate(screenName)` | Backend sends navigation intent | `when(screenName) { ... navigateUp() }` |
| `onError(error)` | SDK recoverable error | Log / show snackbar |

---

## GitHub Packages Setup

1. Create a GitHub Personal Access Token with `read:packages` scope
2. Add to `~/.gradle/gradle.properties`:
   ```
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=YOUR_GITHUB_TOKEN
   ```
3. Add the repository to `settings.gradle.kts` (shown above)

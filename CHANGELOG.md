# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org).

## [1.0.0] — 2026-03-10

### Added
- **VoiceAgentKit** — SDK entry point, initialized once in `Application.onCreate()`
- **VoiceAgent.attach()** — Single public API for fragment-level voice integration
- **FormSchema + VoiceField** — Schema-driven field declaration (no hardcoded JSON keys)
- **SchemaFieldMapper** — 5-priority JSON key resolution (`id`, `draft_`, `editing_`, `viewing_`, aliases)
- **ValueNormalizer** — Strips `null` / `"null"` / `"undefined"` / blank values
- **FieldValueApplier** — Applies incoming values to TEXT, NUMBER, RADIO, SPINNER, CHECKBOX views with TextWatcher loop prevention
- **FieldUpdateSender** — Debounced (600ms) outgoing updates for EditText; immediate for RadioGroup, Spinner, CheckBox
- **ScreenContextBuilder** — Builds and sends the screen-open context payload (throttled 500ms)
- **LiveKitEngine** — Full LiveKit connection lifecycle (token fetch, room connect, mic publish, DataChannel events)
- **LiveKitEventHandler** — Thread-safe fan-out dispatcher for form data and connection state
- **VoiceSessionViewModel** — Room-backed session persistence; 24h expiry
- **VoiceAnalyticsTracker** — Firebase Analytics wrapper with PII sanitization
- **LogSanitizer** — Strips phone numbers, emails, Aadhaar, PAN from logs
- **DebounceHandler** — Per-field debounce with `cancelAll()` for onPause/onDestroyView
- **VoiceAgentAttachment** — `DefaultLifecycleObserver` wiring all components to fragment lifecycle
- **consumer-rules.pro** — ProGuard/R8 rules for all SDK public classes
- **Sample app** — Full DDFin `SiteDetailsFragment` migration demonstration

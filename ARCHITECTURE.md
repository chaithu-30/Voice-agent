# Architecture — VoiceAgentKit

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     HOST APPLICATION                            │
│                                                                 │
│  Application.onCreate()                                         │
│  └── VoiceAgentKit.initialize(config)                           │
│       ├── LiveKitEngine (singleton)                             │
│       ├── VoiceSessionDatabase (Room, singleton)                │
│       └── VoiceAnalyticsTracker (singleton)                     │
│                                                                 │
│  MainActivity                                                   │
│  ├── VoiceAgentKit.connect(userId)  →  LiveKitEngine.connect()  │
│  └── VoiceAgentKit.disconnect()     →  LiveKitEngine.disconnect │
│                                                                 │
│  Fragment                                                       │
│  └── VoiceAgent.attach(fragment, schema, callbacks)             │
│       └── VoiceAgentManager.attach()                            │
│            ├── VoiceSessionViewModel (ViewModelProvider)        │
│            └── VoiceAgentAttachment (LifecycleObserver)         │
│                 ├── FormStateManager                            │
│                 ├── FieldValueApplier                           │
│                 ├── FieldUpdateSender                           │
│                 └── ScreenContextBuilder                        │
└─────────────────────────────────────────────────────────────────┘
                            │ WebRTC
                            ▼
                   LiveKit Server
                   (wss://...)
                            │ DataChannel JSON
                            ▼
              LiveKitEngine.handleDataReceived()
                            │
              LiveKitEventHandler.dispatchFormData()
                            │
              VoiceAgentAttachment.formDataListener
                            │
                ┌───────────┼───────────┐
                ▼           ▼           ▼
    SchemaFieldMapper  ValueNorm.  screen_name
         (resolve)     (sanitize)  → onNavigate()
                │
                ▼
       FieldValueApplier.apply()
         view.post { setText / check / setSelection }
                │
                ▼
       onFieldFilled(fieldId, value)
```

## Incoming Data Flow

```
LiveKit DataChannel JSON arrives
         │
         ▼
handleDataReceived() — parse bytes → Map<String, Any?>
         │
         ▼
STEP 1: Extract "screen_name"
         ├─ Non-null & != current screen? → onNavigate(screenName)
         └─ Continue with remaining keys only
         │
         ▼
STEP 2: SchemaFieldMapper.resolve(schema, json)
         For each VoiceField:
           Try: field.id → draft_{id} → editing_{id} → viewing_{id} → aliases
           First non-empty value wins
         │
         ▼
STEP 3: FieldValueApplier.apply(field, value)
         view.post {
           Fragment validity check (isAdded && !isDetached)
           Dispatch by FieldType:
             TEXT/NUMBER  → pauseWatcher → setText → resumeWatcher
             RADIO        → find RadioButton by text/tag → check
             SPINNER      → find item index → setSelection
             CHECKBOX     → toBoolean(value) → isChecked = ...
         }
         │
         ▼
STEP 4: FormStateManager.onFieldFilled(fieldId)
         Track attempt counts, mark required fields filled
         │
         ▼
STEP 5: Analytics + Callbacks
         tracker.track(VoiceEvent.FieldFilled(...))
         onFieldFilled(fieldId, value)   ← host app callback
         IF isFormComplete → onFormCompleted() + SessionCompleted
```

## Outgoing Data Flow

```
User interacts with View
         │
         ▼
SDK Listener fires:
  EditText   → TextWatcher.afterTextChanged → DebounceHandler (600ms)
  RadioGroup → OnCheckedChangeListener → immediate
  Spinner    → OnItemSelectedListener → immediate (skip first)
  CheckBox   → OnCheckedChangeListener → immediate
         │
         ▼
FieldUpdateSender.maybeSendFieldUpdate(field, rawValue)
         │
         ├─ ValueNormalizer.normalize() — strip null/"null"/"undefined"
         ├─ ValueNormalizer.hasChanged() — dedup vs lastSentValues[fieldId]
         ├─ schema.mode == VIEW? → suppress
         └─ engine.isConnected()? → drop if not
         │
         ▼
JSON key = "{schema.outgoingKeyPrefix}{field.id}"
  ADD  mode → "draft_site_name"
  EDIT mode → "editing_site_name"
         │
         ▼
LiveKitEngine.sendDataMessage({ key: value })
  Injects "selected_language" from config.languageProvider
  Serializes to JSON → DataChannel publish
```

## Lifecycle Diagram

```
Fragment onCreate
       │
       ▼
VoiceAgent.attach()
  └── VoiceAgentAttachment registered on fragment.lifecycle
                │
       ┌────────┼────────┐
       │        │        │
    onResume  onPause  onDestroy
       │        │        │
  attachAll  detachAll  teardown()
  +register  +cancel    ├─ removeFormDataListener
  listeners  debounce   ├─ removeConnectionStateListener
  +send      +save      ├─ detachAll (all view listeners)
  context    session    ├─ applier.teardown()
  +apply     state      ├─ pendingFormData = null
  pending               └─ cancelSession if incomplete
```

## JSON Key Resolution Flowchart

```
VoiceField(id = "site_name")
         │
         ▼
Try json["site_name"]
  └─ non-null & non-empty → ✅ RESOLVED
         │ (null/empty)
         ▼
Try json["draft_site_name"]
  └─ non-null & non-empty → ✅ RESOLVED
         │ (null/empty)
         ▼
Try json["editing_site_name"]
  └─ non-null & non-empty → ✅ RESOLVED
         │ (null/empty)
         ▼
Try json["viewing_site_name"]
  └─ non-null & non-empty → ✅ RESOLVED
         │ (null/empty)
         ▼
Try json[alias] for each alias in jsonKeyAliases
  └─ non-null & non-empty → ✅ RESOLVED
         │ (all null/empty)
         ▼
❌ NOT RESOLVED — field skipped
```

## SDK vs DDFin Responsibility Matrix

| Concern | Pre-SDK (DDFin) | Post-SDK |
|---------|----------------|---------|
| LiveKit connection | ✅ MainActivity | ✅ SDK (LiveKitEngine) |
| Token fetch | ✅ LiveKitManager | ✅ SDK (LiveKitEngine) |
| Room events | ✅ Per-fragment | ✅ SDK (LiveKitEventHandler) |
| JSON parsing | ✅ Per-fragment | ✅ SDK (SchemaFieldMapper) |
| View updates | ✅ Per-fragment (15+ methods) | ✅ SDK (FieldValueApplier) |
| Outgoing updates | ✅ Per-fragment (hybridManager) | ✅ SDK (FieldUpdateSender) |
| Screen context send | ✅ Per-fragment (40 lines) | ✅ SDK (ScreenContextBuilder) |
| Lifecycle cleanup | ✅ Per-fragment manually | ✅ SDK (LifecycleObserver) |
| Session persistence | ❌ None | ✅ SDK (Room) |
| Analytics | ❌ Manual | ✅ SDK (Firebase) |
| PII sanitization | ❌ None | ✅ SDK (LogSanitizer) |

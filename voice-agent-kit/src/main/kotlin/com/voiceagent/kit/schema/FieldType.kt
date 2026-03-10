package com.voiceagent.kit.schema

/**
 * The type of an Android View field registered with the SDK.
 */
enum class FieldType {
    /** EditText / TextInputEditText */
    TEXT,

    /** EditText / TextInputEditText constrained to numeric input */
    NUMBER,

    /** RadioGroup containing RadioButton children */
    RADIO,

    /** AppCompatSpinner / Spinner */
    SPINNER,

    /** CheckBox */
    CHECKBOX
}

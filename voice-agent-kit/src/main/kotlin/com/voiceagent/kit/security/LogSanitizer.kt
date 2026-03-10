package com.voiceagent.kit.security

/**
 * Sanitizes log messages and analytics params to strip potential PII before writing
 * to Logcat or Firebase Analytics.
 *
 * The sanitizer replaces known PII patterns (phone numbers, email addresses, Aadhaar-like
 * numbers) with redacted placeholders. It does NOT attempt to redact all possible PII —
 * host apps should ensure they do not pass raw user strings to SDK callbacks.
 */
object LogSanitizer {

    private val PHONE_REGEX = Regex("""(\+91[\-\s]?)?[6-9]\d{9}""")
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
    private val AADHAAR_REGEX = Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b""")
    private val PAN_REGEX = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")

    /**
     * Sanitize a string value for safe logging.
     * @return sanitized string with PII replaced by redaction tokens.
     */
    fun sanitize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value
            .replace(PHONE_REGEX, "[PHONE]")
            .replace(EMAIL_REGEX, "[EMAIL]")
            .replace(AADHAAR_REGEX, "[AADHAAR]")
            .replace(PAN_REGEX, "[PAN]")
    }

    /**
     * Sanitize a map of analytics parameters.
     * All values are converted to strings and sanitized.
     */
    fun sanitizeParams(params: Map<String, Any?>): Map<String, String> {
        return params.mapValues { (_, v) -> sanitize(v?.toString()) }
    }
}

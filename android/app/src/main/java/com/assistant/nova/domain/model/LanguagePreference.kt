package com.assistant.nova.domain.model

enum class LanguagePreference(val code: String, val displayName: String) {
    AUTO("auto", "Auto Detect (Tamil / English)"),
    TAMIL("ta", "தமிழ் (Tamil)"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String): LanguagePreference {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: AUTO
        }
    }
}

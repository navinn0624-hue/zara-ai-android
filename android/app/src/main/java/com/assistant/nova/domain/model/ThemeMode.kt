package com.assistant.nova.domain.model

enum class ThemeMode(val code: String, val displayName: String) {
    SYSTEM("system", "System Default"),
    DARK("dark", "Dark Theme (OLED)"),
    LIGHT("light", "Light Theme");

    companion object {
        fun fromCode(code: String): ThemeMode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}

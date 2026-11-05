package com.assistant.nova.domain.usecase

class DetectLanguageUseCase {

    private val tanglishKeywords = setOf(
        "vanakkam", "eppadi", "irukku", "irukkinga", "pannu", "pannunga",
        "vai", "vainga", "sollu", "sollunga", "neram", "kaalai", "maalai",
        "iravu", "nandri", "romba", "nalla", "podu", "kodu", "yenna", "enna",
        "yaar", "yenge", "enge", "eppo", "yeppo", "pathu", "paaru", "theriyuma"
    )

    /**
     * Detects if the given text is in Tamil script, Tanglish (Tamil in Latin script), or English.
     * Returns "ta" (Tamil script), "ta-Latn" (Tanglish), or "en" (English).
     */
    fun detectLanguage(text: String): String {
        if (text.isBlank()) return "en"

        // 1. Check for Tamil Unicode characters (\u0B80 - \u0BFF)
        var tamilCharCount = 0
        var totalLetters = 0
        for (char in text) {
            if (char.isLetter()) {
                totalLetters++
                if (char in '\u0B80'..'\u0BFF') {
                    tamilCharCount++
                }
            }
        }

        if (totalLetters > 0 && (tamilCharCount.toFloat() / totalLetters) >= 0.25f) {
            return "ta"
        }

        // 2. Check for Tanglish words in Latin script
        val words = text.lowercase().split(Regex("[\\s,?.!]+"))
        val tanglishMatches = words.count { it in tanglishKeywords }

        if (tanglishMatches > 0) {
            return "ta-Latn"
        }

        return "en"
    }

    /**
     * Determines whether TextToSpeech should use the Tamil engine
     * based on detected script or preference.
     */
    fun shouldSpeakTamil(detectedLang: String): Boolean {
        return detectedLang == "ta"
    }
}

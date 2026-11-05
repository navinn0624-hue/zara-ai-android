package com.assistant.nova

import com.assistant.nova.domain.usecase.DetectLanguageUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectLanguageUseCaseTest {

    private lateinit var useCase: DetectLanguageUseCase

    @Before
    fun setUp() {
        useCase = DetectLanguageUseCase()
    }

    @Test
    fun detectLanguage_withTamilScript_returnsTa() {
        val input = "வணக்கம் நோவா! எப்படி இருக்கீங்க?"
        val result = useCase.detectLanguage(input)
        assertEquals("ta", result)
        assertTrue(useCase.shouldSpeakTamil(result))
    }

    @Test
    fun detectLanguage_withTanglish_returnsTaLatn() {
        val input = "Alarm vai 7 am ku"
        val result = useCase.detectLanguage(input)
        assertEquals("ta-Latn", result)
        assertFalse(useCase.shouldSpeakTamil(result))
    }

    @Test
    fun detectLanguage_withEnglish_returnsEn() {
        val input = "What is the capital of Tamil Nadu?"
        val result = useCase.detectLanguage(input)
        assertEquals("en", result)
        assertFalse(useCase.shouldSpeakTamil(result))
    }

    @Test
    fun detectLanguage_withBlankString_returnsEn() {
        val input = "   "
        val result = useCase.detectLanguage(input)
        assertEquals("en", result)
    }
}

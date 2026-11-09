package com.example

import com.example.services.HardwareToolsManager
import com.example.services.PdfRagManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class Etapa2ComponentsTest {

    @Test
    fun testWhistleMathematicalSineWaveGeneration() {
        val sampleRate = HardwareToolsManager.SAMPLE_RATE
        val freq = HardwareToolsManager.WHISTLE_FREQUENCY_HZ.toDouble()
        assertEquals(3000.0, freq, 0.001)

        // Verify sample values
        for (i in 0 until 100) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freq)
            val sampleValue = (sin(angle) * Short.MAX_VALUE * 0.95).toInt().toShort()
            assertTrue(sampleValue in -Short.MAX_VALUE..Short.MAX_VALUE)
        }
    }

    @Test
    fun testMorseTimingStandards() {
        assertEquals(200L, HardwareToolsManager.MORSE_DOT_MS)
        assertEquals(600L, HardwareToolsManager.MORSE_DASH_MS)
        assertEquals(200L, HardwareToolsManager.MORSE_ELEMENT_GAP_MS)
        assertEquals(600L, HardwareToolsManager.MORSE_LETTER_GAP_MS)
        assertEquals(1400L, HardwareToolsManager.MORSE_CYCLE_GAP_MS)
    }

    @Test
    fun testStrictRagSystemPromptTemplate() {
        val template = PdfRagManager.STRICT_RAG_SYSTEM_PROMPT_TEMPLATE
        assertTrue(template.contains("assistente de emergência civil offline"))
        assertTrue(template.contains("Responda APENAS com base nas cartilhas oficiais abaixo"))
        assertTrue(template.contains("em tópicos"))
    }
}

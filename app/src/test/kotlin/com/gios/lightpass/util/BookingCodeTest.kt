package com.gios.lightpass.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingCodeTest {

    @Test
    fun `a short alphanumeric reference is drawn as bars`() {
        assertEquals(Symbology.CODE_128, BookingCode.symbologyFor("AMC7T4K9"))
        assertEquals(Symbology.CODE_128, BookingCode.symbologyFor("8829-4471"))
        assertEquals(Symbology.CODE_128, BookingCode.symbologyFor("12345678901234567890"))
    }

    @Test
    fun `punctuation or a URL forces QR`() {
        assertEquals(Symbology.QR, BookingCode.symbologyFor("ABC 123 XYZ"))
        assertEquals(Symbology.QR, BookingCode.symbologyFor("REF:8829/4471"))
        assertEquals(Symbology.QR, BookingCode.symbologyFor("https://tix.example/x/7t4k9"))
    }

    @Test
    fun `one character past the 1D limit goes to QR`() {
        assertEquals(Symbology.CODE_128, BookingCode.symbologyFor("A".repeat(20)))
        assertEquals(Symbology.QR, BookingCode.symbologyFor("A".repeat(21)))
    }

    @Test
    fun `an airline-length string goes to PDF417`() {
        val boardingPass = "M1DOE/JOHN            EABC123 LHRJFKBA 0117 234C012F0001 100"
        assertEquals(Symbology.PDF417, BookingCode.symbologyFor(boardingPass))
    }

    @Test
    fun `the reference is passed through byte for byte`() {
        // Stripping the spaces out would be tidier and would encode a string the cinema never
        // issued, so only the ends are trimmed.
        assertEquals("ABC 123 XYZ", BookingCode.normalize("  ABC 123 XYZ  "))
    }

    @Test
    fun `placeholders and stray fragments are not codes`() {
        assertNull(BookingCode.normalize(null))
        assertNull(BookingCode.normalize(""))
        assertNull(BookingCode.normalize("null"))
        assertNull(BookingCode.normalize("N/A"))
        assertNull(BookingCode.normalize("-"))
        assertNull(BookingCode.normalize("F7"))
    }
}

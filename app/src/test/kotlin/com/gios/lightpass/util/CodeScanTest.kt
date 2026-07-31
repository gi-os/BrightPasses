package com.gios.lightpass.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plan and the judgement in [CodeScan]. The pixels are [CodeReader]'s and aren't here. */
class CodeScanTest {

    private fun found(text: String, format: String) = CodeScan.Found(text, format)

    @Test
    fun `a two dimensional code beats a linear one`() {
        val best = CodeScan.best(
            listOf(found("1234567890128", "CODE_128"), found("HTTPS://T.CO/AB", "QR_CODE"))
        )
        assertEquals("HTTPS://T.CO/AB", best?.text)
    }

    @Test
    fun `the longer payload wins inside a tier`() {
        val best = CodeScan.best(
            listOf(found("SHORT12", "QR_CODE"), found("A-MUCH-LONGER-RECORD-1234", "QR_CODE"))
        )
        assertEquals("A-MUCH-LONGER-RECORD-1234", best?.text)
    }

    @Test
    fun `a short linear read is not believed`() {
        // Five characters of Code 39 out of a photograph of perforated paper is noise, not a code.
        assertFalse(CodeScan.isCredible(found("A1B2C", "CODE_39")))
        assertNull(CodeScan.best(listOf(found("A1B2C", "CODE_39"))))
    }

    @Test
    fun `a short two dimensional read is believed`() {
        // A QR carries its own error correction, so length is no part of trusting it.
        assertTrue(CodeScan.isCredible(found("A1B", "QR_CODE")))
    }

    @Test
    fun `nothing found means nothing shown`() {
        assertNull(CodeScan.best(emptyList()))
        assertNull(CodeScan.best(listOf(found("   ", "QR_CODE"))))
    }

    @Test
    fun `the same code seen twice is one code`() {
        val best = CodeScan.best(List(4) { found("TICKET-99", "QR_CODE") })
        assertEquals("TICKET-99", best?.text)
    }

    @Test
    fun `only a two dimensional read ends the search`() {
        assertTrue(CodeScan.isConclusive(found("TICKET-99", "QR_CODE")))
        // A long Code 128 is credible enough to keep and not certain enough to stop on: the
        // 2D code that would beat it may still be further down the list.
        assertTrue(CodeScan.isCredible(found("1234567890128", "CODE_128")))
        assertFalse(CodeScan.isConclusive(found("1234567890128", "CODE_128")))
    }

    @Test
    fun `the crop is tried before the whole photograph`() {
        val plan = CodeScan.attempts(hasCrop = true)
        assertEquals(CodeScan.Source.CROPPED, plan.first().source)
        val firstOriginal = plan.indexOfFirst { it.source == CodeScan.Source.ORIGINAL }
        val lastCrop = plan.indexOfLast { it.source == CodeScan.Source.CROPPED }
        assertTrue("every crop attempt comes first", lastCrop < firstOriginal)
    }

    @Test
    fun `with no crop nothing asks for one`() {
        assertTrue(CodeScan.attempts(hasCrop = false).none { it.source == CodeScan.Source.CROPPED })
    }

    @Test
    fun `the plan covers both turns, both binarizers and inversion`() {
        val plan = CodeScan.attempts(hasCrop = false)
        assertTrue(plan.any { it.quarterTurns == 0 })
        assertTrue("a barcode printed down the side needs a turn", plan.any { it.quarterTurns == 1 })
        assertTrue(plan.any { it.binarizer == CodeScan.Binarizer.HYBRID })
        assertTrue(plan.any { it.binarizer == CodeScan.Binarizer.GLOBAL_HISTOGRAM })
        assertTrue("a ticket on a screen in dark mode is inverted", plan.any { it.inverted })
        // The cheapest, likeliest rendering is first, because a 2D hit ends the search.
        val first = plan.first()
        assertEquals(0, first.quarterTurns)
        assertEquals(CodeScan.Binarizer.HYBRID, first.binarizer)
        assertFalse(first.inverted)
    }

    @Test
    fun `no attempt is repeated`() {
        val plan = CodeScan.attempts(hasCrop = true)
        assertEquals(plan.size, plan.distinct().size)
    }
}

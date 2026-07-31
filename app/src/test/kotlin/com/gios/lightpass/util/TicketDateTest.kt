package com.gios.lightpass.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Today is always passed in. The whole point of the rule is what it does relative to now, so a
 * test that read the clock would pass in July and start failing in December.
 */
class TicketDateTest {

    private val july = LocalDate.of(2026, 7, 31)

    @Test
    fun `no year, earlier this year, means next year`() {
        assertEquals("2027-03-14", TicketDate.resolveFromModel("03-14", july))
    }

    @Test
    fun `no year, later this year, means this year`() {
        assertEquals("2026-12-18", TicketDate.resolveFromModel("12-18", july))
    }

    @Test
    fun `no year, today, means today`() {
        assertEquals("2026-07-31", TicketDate.resolveFromModel("07-31", july))
    }

    @Test
    fun `no year, inside the grace window, stays in the past`() {
        // Yesterday: the 10pm showing photographed on the way home after midnight.
        assertEquals("2026-07-30", TicketDate.resolveFromModel("07-30", july))
        // Two days back is the edge of the window and still counts.
        assertEquals("2026-07-29", TicketDate.resolveFromModel("07-29", july))
    }

    @Test
    fun `no year, just outside the grace window, jumps to next year`() {
        assertEquals("2027-07-28", TicketDate.resolveFromModel("07-28", july))
    }

    @Test
    fun `no year, leap day, finds the next February that has one`() {
        // 2026 and 2027 have no 29th of February at all, so the next occurrence is 2028's.
        assertEquals("2028-02-29", TicketDate.resolveFromModel("02-29", july))
    }

    @Test
    fun `December read in January means this coming December`() {
        val january = LocalDate.of(2027, 1, 3)
        // A consequence of the rule rather than an accident of it: a stub from a fortnight ago
        // and a booking eleven months out are the same two digits on the paper, and the
        // instruction is to read a year-less date as upcoming.
        assertEquals("2027-12-18", TicketDate.resolveFromModel("12-18", january))
    }

    @Test
    fun `a year printed on the ticket is not overridden`() {
        assertEquals("2026-03-14", TicketDate.resolveFromModel("2026-03-14", july))
        assertEquals("2025-11-02", TicketDate.resolveFromModel("2025-11-02", july))
        assertEquals("2027-01-09", TicketDate.resolveFromModel("2027-01-09", july))
    }

    @Test
    fun `a year the model invented is overridden`() {
        // The reported bug: December 18th came back as 2024.
        assertEquals("2026-12-18", TicketDate.resolveFromModel("2024-12-18", july))
        assertEquals("2027-03-14", TicketDate.resolveFromModel("2019-03-14", july))
    }

    @Test
    fun `a year a person typed is kept however old`() {
        assertEquals("2019-03-14", TicketDate.resolveTyped("2019-03-14", july))
        // Typing a year-less date still gets one filled in.
        assertEquals("2026-12-18", TicketDate.resolveTyped("12-18", july))
    }

    @Test
    fun `ISO's own year-less spelling is understood`() {
        assertEquals("2026-12-18", TicketDate.resolveFromModel("--12-18", july))
    }

    @Test
    fun `single-digit months and days are padded`() {
        assertEquals("2026-09-05", TicketDate.resolveFromModel("9-5", july))
    }

    @Test
    fun `nothing at all stays nothing`() {
        assertNull(TicketDate.resolveFromModel(null, july))
        assertNull(TicketDate.resolveFromModel("   ", july))
    }

    @Test
    fun `a shape we don't recognise is left where it can be corrected`() {
        assertEquals("Dec 18", TicketDate.resolveFromModel("Dec 18", july))
        assertEquals("18/12/2026", TicketDate.resolveFromModel("18/12/2026", july))
    }

    @Test
    fun `an impossible day is not turned into a real one`() {
        assertEquals("2026-02-30", TicketDate.resolveFromModel("2026-02-30", july))
    }
}

package com.gios.lightpass.util

import java.time.LocalDate

/**
 * Which year a ticket belongs to when the paper doesn't say.
 *
 * Cinema stubs print "DEC 18" and leave the year off, and the prompt used to hand the model a
 * hardcoded "use current year 2026" and hope. It came back with 2024, which is not a year that
 * appears anywhere on a ticket bought last week — it is the model filling in a blank. So the
 * model is now told to omit the year whenever the paper omits it, and the year is decided here:
 * a date with no year means its next occurrence.
 *
 * Nothing from `android.*` is imported on purpose. This is the one piece of the parse that is
 * pure arithmetic over a calendar, and a plain JVM test can only reach it if it stays that way.
 */
object TicketDate {

    /**
     * How far into the past a year-less date may still mean this year.
     *
     * Read strictly, "next occurrence" would send yesterday's date eleven months forward — and a
     * 10pm showing photographed on the walk home is yesterday's date. Two days covers that, plus
     * the stub still in a coat pocket the following evening.
     *
     * Longer was tempting and is the wrong trade. Past a couple of days "last week" and "next
     * year" look identical on the paper, and the instruction is to assume upcoming. The window
     * only ever reaches backwards, so no genuinely future date is touched by it at all.
     */
    private const val GRACE_DAYS = 2L

    /**
     * Years either side of today in which a year the model reports is believed on sight.
     *
     * Anything outside is treated as though the model had reported no year at all, which is the
     * whole point: last year's date on a ticket photographed today is plausible enough to keep,
     * since people do file old stubs, but 2024 in 2026 is a guess wearing a year's clothing.
     */
    private const val YEAR_SLACK = 1

    /**
     * How far forward to look for a date that doesn't happen every year.
     *
     * February 29th is the only case, and it needs a search rather than "this year or next":
     * with no year printed, the next February 29th can be three years out.
     */
    private const val MAX_YEARS_AHEAD = 8

    private val FULL = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")

    /** "12-18", and ISO 8601's own year-less spelling "--12-18", which the model also emits. */
    private val MONTH_DAY = Regex("""-{0,2}(\d{1,2})-(\d{1,2})""")

    /**
     * A date as the model read it off a stub. Fills in a missing year, and overrides one the
     * model appears to have invented — see [YEAR_SLACK].
     */
    fun resolveFromModel(raw: String?, today: LocalDate = LocalDate.now()): String? =
        resolve(raw, today, trustWrittenYear = false)

    /**
     * A date somebody typed into the edit field. A year they wrote is theirs to keep, however
     * long past: filing an old stub is something this app is for, and correcting the parser is
     * the reason the field is editable at all.
     */
    fun resolveTyped(raw: String?, today: LocalDate = LocalDate.now()): String? =
        resolve(raw, today, trustWrittenYear = true)

    private fun resolve(raw: String?, today: LocalDate, trustWrittenYear: Boolean): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null

        FULL.matchEntire(text)?.let { m ->
            val year = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            val written = dateOrNull(year, month, day)
            if (written != null && (trustWrittenYear || year in believable(today))) {
                return written.toString()
            }
            return nextOccurrence(month, day, today)?.toString() ?: text
        }

        val partial = MONTH_DAY.matchEntire(text)
        // Any other shape is left exactly as it came. Something we don't recognise is more use
        // sitting in the field where it can be seen and corrected than quietly rewritten into a
        // date nobody chose.
            ?: return text

        return nextOccurrence(partial.groupValues[1].toInt(), partial.groupValues[2].toInt(), today)
            ?.toString() ?: text
    }

    /**
     * The soonest real date with this month and day that isn't older than the grace window.
     *
     * Years are walked rather than computed because of February 29th: asking for it in a
     * non-leap year is not a date at all, so the answer is the next February that has one.
     */
    private fun nextOccurrence(month: Int, day: Int, today: LocalDate): LocalDate? {
        val earliest = today.minusDays(GRACE_DAYS)
        for (year in today.year..(today.year + MAX_YEARS_AHEAD)) {
            val candidate = dateOrNull(year, month, day) ?: continue
            if (!candidate.isBefore(earliest)) return candidate
        }
        return null
    }

    private fun believable(today: LocalDate): IntRange =
        (today.year - YEAR_SLACK)..(today.year + YEAR_SLACK)

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

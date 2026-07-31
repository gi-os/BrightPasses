package com.gios.lightpass.util

/** How a booking reference gets drawn. See [BookingCode.symbologyFor]. */
enum class Symbology { QR, CODE_128, PDF417 }

/**
 * The booking reference, and what shape of code it wants to be.
 *
 * Kept free of `android.*` alongside [TicketDate] so the choice can be tested; the drawing
 * lives in [BarcodeRender], which needs a `Bitmap` and so can't be.
 */
object BookingCode {

    /**
     * Where a string stops being a reference and starts being a record.
     *
     * An airline-style pass is one long fixed-layout string — an IATA boarding pass is sixty-odd
     * characters — and PDF417 is what those are printed as, which is also the only symbology here
     * that carries that much without becoming unreadably fine. Nothing a cinema calls a booking
     * reference reaches this length, so in practice this branch is for the tickets that aren't
     * for films at all.
     */
    private const val PDF417_FROM = 40

    /**
     * Longest reference still worth drawing as bars rather than a square.
     *
     * Code 128 spends about eleven modules a character, so twenty characters is already 230
     * modules wide; across a 1080px panel that is four pixels a bar, which scans, and much past
     * it does not. A cinema handheld reads 1D fastest, so short codes stay bars and everything
     * longer goes to QR — which is what a cinema prints for a long reference anyway.
     */
    private const val CODE_128_UPTO = 20

    /** Below this it isn't a booking reference, it's a row number the model mislabelled. */
    private const val MIN_LENGTH = 4

    private val NOT_A_CODE = setOf("null", "none", "n/a", "na", "-", "--", "unknown")

    /** Characters a reference can hold and still be worth drawing as bars. */
    private val ONE_D_SAFE = Regex("""[A-Za-z0-9-]+""")

    /**
     * The reference as it should be encoded, or null if there isn't one.
     *
     * Only the ends are trimmed. It is tempting to strip the spaces out of "ABC 123 XYZ", and
     * wrong: the string has to come back out of the scanner byte for byte or it means nothing, so
     * the only safe edit is the whitespace the model added around it.
     */
    fun normalize(raw: String?): String? {
        val code = raw?.trim() ?: return null
        if (code.length < MIN_LENGTH) return null
        if (code.lowercase() in NOT_A_CODE) return null
        return code
    }

    fun symbologyFor(code: String): Symbology = when {
        code.length > PDF417_FROM -> Symbology.PDF417
        code.length > CODE_128_UPTO -> Symbology.QR
        !ONE_D_SAFE.matches(code) -> Symbology.QR
        else -> Symbology.CODE_128
    }
}

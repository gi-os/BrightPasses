package com.gios.lightpass.util

/**
 * Reading the ticket's own code off the photograph, rather than drawing a new one.
 *
 * The first version of this feature re-encoded the booking reference a model had read off the
 * paper, which produces a code that scans only if the cinema's system happens to look that string
 * up. Most don't — they encode an internal id — so the barcode on screen was decorative. But the
 * real code was in the photo the whole time, unread. Decode it and the thing on screen is byte for
 * byte what the cinema issued, which is the only version of this that works at a door.
 *
 * This half is the plan and the judgement, kept free of `android.*` so both can be tested;
 * [CodeReader] does the pixels.
 */
object CodeScan {

    /** Symbologies with real error correction, so a decode is a fact rather than a guess. */
    val TWO_D = setOf("QR_CODE", "PDF_417", "AZTEC", "DATA_MATRIX")

    /**
     * Shortest 1D payload worth believing.
     *
     * A photograph of a ticket is full of parallel edges — perforations, table rules, folded
     * paper — and the 1D readers will occasionally find a short Code 39 in them. Two-dimensional
     * symbologies can't do that: their checksums make a false positive effectively impossible, so
     * they're trusted at any length while bars have to be long enough to be deliberate.
     */
    private const val MIN_1D_LENGTH = 6

    /** One decode to try: which image, how far scaled down, rotated, and how binarized. */
    data class Attempt(
        val source: Source,
        val maxEdge: Int,
        val quarterTurns: Int,
        val binarizer: Binarizer,
        val inverted: Boolean,
    )

    /** The crop is the ticket alone, so it's tried first — less paper, fewer false edges. */
    enum class Source { CROPPED, ORIGINAL }

    enum class Binarizer { HYBRID, GLOBAL_HISTOGRAM }

    data class Found(val text: String, val format: String)

    /**
     * Every decode worth attempting, in the order worth attempting it.
     *
     * One pass almost never finds a code in a photograph, which is why the first implementation of
     * this looked like it didn't work at all. Each axis here earns its place:
     *
     *  - **Scale.** A 12MP photo of a code an inch across gives the binarizer a module a few pixels
     *    wide surrounded by grain. Downscaling averages the grain away, and past a point takes the
     *    code with it, so three sizes rather than a guess at one.
     *  - **Quarter turn.** ZXing's 1D readers scan horizontal rows, and `RGBLuminanceSource`
     *    doesn't implement rotation, so a barcode printed down the side of a ticket is invisible
     *    until the bitmap itself is turned.
     *  - **Binarizer.** Hybrid handles uneven lighting, which is most phone photos; global
     *    histogram handles a flat high-contrast screenshot that hybrid's local blocks over-think.
     *    They fail on different pictures.
     *  - **Inversion.** A ticket shown on someone's phone in dark mode is a white code on black,
     *    and ZXing will not find that at all.
     *
     * Ordered cheapest and likeliest first, because [Found] from a 2D symbology ends the search.
     */
    fun attempts(hasCrop: Boolean): List<Attempt> {
        val sources = if (hasCrop) listOf(Source.CROPPED, Source.ORIGINAL) else listOf(Source.ORIGINAL)
        val out = mutableListOf<Attempt>()
        for (source in sources) {
            for (maxEdge in intArrayOf(1600, 1000, 2400, 640)) {
                for (turns in intArrayOf(0, 1)) {
                    out += Attempt(source, maxEdge, turns, Binarizer.HYBRID, inverted = false)
                    out += Attempt(source, maxEdge, turns, Binarizer.GLOBAL_HISTOGRAM, inverted = false)
                    // Inversion last per size: it's the rarest case and doubles the work.
                    out += Attempt(source, maxEdge, turns, Binarizer.HYBRID, inverted = true)
                }
            }
        }
        return out
    }

    /** Whether a decode is solid enough to put in front of a scanner. See [MIN_1D_LENGTH]. */
    fun isCredible(found: Found): Boolean {
        val text = found.text.trim()
        if (text.isEmpty()) return false
        if (found.format in TWO_D) return true
        return text.length >= MIN_1D_LENGTH
    }

    /** True once there's a result good enough to stop scanning for more. */
    fun isConclusive(found: Found): Boolean = isCredible(found) && found.format in TWO_D

    /**
     * The one to keep out of everything found.
     *
     * Tickets often carry two codes — a QR for the app and a linear one for the usher's handheld —
     * and they are not interchangeable. The 2D one is preferred because it's the one that survives
     * being photographed and redrawn, and because a longer payload is the record rather than a row
     * number. Credibility is checked here rather than at the call site so a caller can hand over
     * everything it saw.
     */
    fun best(found: List<Found>): Found? = found
        .filter(::isCredible)
        .distinctBy { it.text }
        .maxWithOrNull(
            compareBy<Found> { if (it.format in TWO_D) 1 else 0 }.thenBy { it.text.length }
        )
}

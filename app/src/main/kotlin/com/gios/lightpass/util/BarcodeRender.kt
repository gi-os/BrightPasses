package com.gios.lightpass.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Draws a booking reference as a scannable code.
 *
 * ZXing's encoder is already here: `zxing-android-embedded` is what scans the API-key QR in
 * Settings, and it exposes `com.google.zxing:core` as an api dependency, so the writers come
 * along with it and nothing new is added to the build for this.
 */
object BarcodeRender {

    /**
     * Quiet zone, in modules.
     *
     * The specs ask for four modules of white around a QR and ten bar-widths either side of a
     * Code 128, and a scanner that can't find that margin doesn't struggle, it simply declines —
     * which at the door looks like a broken barcode rather than a missing border.
     */
    private const val QUIET_MODULES_2D = 4
    private const val QUIET_MODULES_1D = 10

    /**
     * A code at exactly the pixels it will occupy on screen.
     *
     * Both halves of that matter on this panel. ZXing scales a module up by a whole number and
     * pads the remainder, so asking for the displayed size gives bars of even width; ask for some
     * other size and let the view resample it and the greyscale screen turns the edges to mush
     * that nothing will lock onto. `ARGB_8888` rather than `RGB_565` for the same reason — pure
     * black on pure white with no dithering anywhere near it.
     *
     * Returns null rather than throwing: an unencodable reference should cost a missing barcode,
     * not the ticket page.
     */
    fun bitmap(content: String, symbology: Symbology, widthPx: Int, heightPx: Int): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val format = when (symbology) {
            Symbology.QR -> BarcodeFormat.QR_CODE
            Symbology.CODE_128 -> BarcodeFormat.CODE_128
            Symbology.PDF417 -> BarcodeFormat.PDF_417
        }
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN,
                if (symbology == Symbology.CODE_128) QUIET_MODULES_1D else QUIET_MODULES_2D)
            put(EncodeHintType.CHARACTER_SET, "ISO-8859-1")
            // Only QR takes a level here; PDF417 wants an Int for the same key and Code 128 has
            // no such setting, so setting it for either is a thrown exception, not a hint.
            if (symbology == Symbology.QR) put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        }

        val matrix = runCatching {
            MultiFormatWriter().encode(content, format, widthPx, heightPx, hints)
        }.getOrNull() ?: return null

        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            .apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }
}

package com.gios.lightpass.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.File

/**
 * Finds the code that is already printed on the ticket.
 *
 * Built the way [LightQR](https://github.com/gi-os/LightQR) does it — `com.google.zxing:core`,
 * `MultiFormatReader`, `TRY_HARDER`, and nothing else. That app's own comment gives the reason and
 * it applies here twice over: it's pure Java, and LightOS ships without Play Services, so ML Kit's
 * barcode scanner would compile, install, and then quietly never work on this phone.
 *
 * The one thing that differs from LightQR: it reads a live `ImageProxy`, so it takes the Y plane
 * straight off the camera. Here the source is a still that was already saved, which means
 * `RGBLuminanceSource` over `getPixels`, and it means the picture is a photograph of a code rather
 * than a viewfinder aimed at one — much harder, hence the list of attempts in [CodeScan].
 */
object CodeReader {

    /**
     * How long a whole scan may take before it gives up on the rest of its attempts.
     *
     * The full plan is dozens of decodes and a big photograph makes each of them cost real time, so
     * an exhaustive search would sit on a background thread for half a minute per ticket. Anything
     * a scanner will read is found in the first handful of attempts; the budget is what stops the
     * hopeless ones — a ticket with no code at all — from costing the same as the rest.
     */
    private const val BUDGET_MS = 6_000L

    private val FORMATS = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.PDF_417,
        BarcodeFormat.AZTEC,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.ITF,
    )

    /** What was on the ticket: the payload, and the symbology it was printed as. */
    data class Scanned(val text: String, val format: String)

    /**
     * Decode the ticket's code, or null.
     *
     * Reads each source at most once per size and turn — decoding is cheap next to `decodeFile`, so
     * the bitmap is prepared, then handed to every binarizer and inversion that wants it.
     */
    fun scan(originalPath: String, croppedPath: String?): Scanned? {
        val deadline = SystemClock.uptimeMillis() + BUDGET_MS
        val found = mutableListOf<CodeScan.Found>()
        var prepared: Prepared? = null

        for (attempt in CodeScan.attempts(hasCrop = croppedPath != null)) {
            if (SystemClock.uptimeMillis() > deadline) break
            val path = when (attempt.source) {
                CodeScan.Source.CROPPED -> croppedPath ?: continue
                CodeScan.Source.ORIGINAL -> originalPath
            }
            // The plan groups its attempts, so consecutive ones usually share a bitmap.
            if (prepared?.matches(path, attempt.maxEdge, attempt.quarterTurns) != true) {
                prepared?.release()
                prepared = Prepared.of(path, attempt.maxEdge, attempt.quarterTurns) ?: continue
            }
            val hit = decode(prepared, attempt) ?: continue
            found += hit
            // A 2D decode is checksummed, so there is nothing to gain by looking further.
            if (CodeScan.isConclusive(hit)) break
        }
        prepared?.release()
        return CodeScan.best(found)?.let { Scanned(it.text.trim(), it.format) }
    }

    private fun decode(prepared: Prepared, attempt: CodeScan.Attempt): CodeScan.Found? {
        var source: LuminanceSource = prepared.source
        if (attempt.inverted) source = source.invert()
        val binary = when (attempt.binarizer) {
            CodeScan.Binarizer.HYBRID -> BinaryBitmap(HybridBinarizer(source))
            CodeScan.Binarizer.GLOBAL_HISTOGRAM -> BinaryBitmap(GlobalHistogramBinarizer(source))
        }
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.POSSIBLE_FORMATS to FORMATS,
                )
            )
        }
        return try {
            val result = reader.decode(binary)
            result?.text?.takeIf { it.isNotBlank() }
                ?.let { CodeScan.Found(it, result.barcodeFormat.name) }
        } catch (_: Exception) {
            // No code in this rendering of the image. Expected for most attempts, and the reason
            // there is a list of them.
            null
        } finally {
            reader.reset()
        }
    }

    /** One decodable rendering of a file, kept while consecutive attempts want the same one. */
    private class Prepared(
        private val path: String,
        private val maxEdge: Int,
        private val quarterTurns: Int,
        private val bitmap: Bitmap,
        val source: RGBLuminanceSource,
    ) {
        fun matches(path: String, maxEdge: Int, quarterTurns: Int) =
            this.path == path && this.maxEdge == maxEdge && this.quarterTurns == quarterTurns

        fun release() = runCatching { bitmap.recycle() }

        companion object {
            fun of(path: String, maxEdge: Int, quarterTurns: Int): Prepared? = runCatching {
                val file = File(path)
                if (!file.exists()) return null
                // Two passes: measure, then decode at the nearest power-of-two subsample. Loading a
                // 12MP still at full size four times over is how this would run the app out of
                // memory instead of finding a barcode.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                if (longest <= 0) return null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = generateSequence(1) { it * 2 }
                        .takeWhile { longest / it > maxEdge }
                        .lastOrNull()?.let { it * 2 } ?: 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val loaded = BitmapFactory.decodeFile(path, opts) ?: return null
                val turned = if (quarterTurns == 0) loaded else {
                    val m = Matrix().apply { postRotate(90f * quarterTurns) }
                    Bitmap.createBitmap(loaded, 0, 0, loaded.width, loaded.height, m, true)
                        .also { if (it !== loaded) loaded.recycle() }
                }
                val pixels = IntArray(turned.width * turned.height)
                turned.getPixels(pixels, 0, turned.width, 0, 0, turned.width, turned.height)
                Prepared(
                    path, maxEdge, quarterTurns, turned,
                    RGBLuminanceSource(turned.width, turned.height, pixels),
                )
            }.getOrNull()
        }
    }
}

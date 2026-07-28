package com.gios.lightpass.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

object ImageUtils {

    /** Decode bytes and rotate upright per EXIF, so stored pixels match what the model sees. */
    fun normalizeUpright(bytes: ByteArray): Bitmap {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int = 92): File {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return file
    }

    /** Crop to a normalized [0..1] box (x,y,w,h) with small padding; null/invalid box -> original. */
    fun cropToBox(src: Bitmap, x: Double?, y: Double?, w: Double?, h: Double?, pad: Double = 0.02): Bitmap {
        if (x == null || y == null || w == null || h == null || w <= 0 || h <= 0) return src
        val W = src.width; val H = src.height
        val left = ((x - pad).coerceIn(0.0, 1.0) * W).toInt()
        val top = ((y - pad).coerceIn(0.0, 1.0) * H).toInt()
        val right = ((x + w + pad).coerceIn(0.0, 1.0) * W).toInt()
        val bottom = ((y + h + pad).coerceIn(0.0, 1.0) * H).toInt()
        val cw = (right - left).coerceAtLeast(1)
        val ch = (bottom - top).coerceAtLeast(1)
        if (cw >= W && ch >= H) return src
        return runCatching { Bitmap.createBitmap(src, left, top, cw, ch) }.getOrDefault(src)
    }
}

object AutoCrop {
    /**
     * Trim near-uniform margins (table/hand of a solid tone) around the ticket.
     * Conservative: samples on a downscaled copy, caps trim at 42% per side, and
     * returns the original if there's nothing clearly uniform to remove.
     */
    fun trimBorders(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val maxDim = 480
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        val sw = (src.width * scale).toInt().coerceAtLeast(1)
        val sh = (src.height * scale).toInt().coerceAtLeast(1)
        val small = android.graphics.Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)

        fun lum(c: Int): Int {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
        // background ~ median-ish of the 4 corners
        val corners = intArrayOf(px[0], px[sw - 1], px[(sh - 1) * sw], px[sh * sw - 1]).map { lum(it) }
        val bg = corners.sorted()[1]
        val tol = 26   // luminance tolerance for "same as background"
        fun rowUniform(y: Int): Boolean {
            var i = y * sw
            for (x in 0 until sw) { if (kotlin.math.abs(lum(px[i]) - bg) > tol) return false; i++ }
            return true
        }
        fun colUniform(x: Int): Boolean {
            var i = x
            for (y in 0 until sh) { if (kotlin.math.abs(lum(px[i]) - bg) > tol) return false; i += sw }
            return true
        }
        var top = 0; while (top < sh * 42 / 100 && rowUniform(top)) top++
        var bottom = sh - 1; while (bottom > sh - sh * 42 / 100 && rowUniform(bottom)) bottom--
        var left = 0; while (left < sw * 42 / 100 && colUniform(left)) left++
        var right = sw - 1; while (right > sw - sw * 42 / 100 && colUniform(right)) right--
        small.recycle()

        if (top == 0 && left == 0 && bottom == sh - 1 && right == sw - 1) return src
        // map back to full-res with a little padding
        val padX = (sw * 0.01f); val padY = (sh * 0.01f)
        val fl = (((left - padX) / sw).coerceIn(0f, 1f) * src.width).toInt()
        val ft = (((top - padY) / sh).coerceIn(0f, 1f) * src.height).toInt()
        val fr = (((right + padX) / sw).coerceIn(0f, 1f) * src.width).toInt()
        val fb = (((bottom + padY) / sh).coerceIn(0f, 1f) * src.height).toInt()
        val cw = (fr - fl).coerceAtLeast(1); val ch = (fb - ft).coerceAtLeast(1)
        if (cw >= src.width && ch >= src.height) return src
        if (cw < src.width / 4 || ch < src.height / 4) return src  // guard against over-trim
        return runCatching { android.graphics.Bitmap.createBitmap(src, fl, ft, cw, ch) }.getOrDefault(src)
    }
}

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

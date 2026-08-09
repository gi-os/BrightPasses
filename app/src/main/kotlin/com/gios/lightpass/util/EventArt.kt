package com.gios.lightpass.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Generated poster art for passes that have no poster.
 *
 * A movie has TMDb. A game has two crests: parse the matchup out of the title, resolve each
 * side against ESPN's search API (the same keyless API BrightSports lives on), and composite
 * the two logos across a diagonal — home upper-left, away lower-right, cut over each other
 * the way a broadcast versus card does it. A concert just gets a drawn music note for now.
 *
 * Everything here is best-effort: any failure means no art, and no art means the pass shows
 * its own photo, which is what it did before this existed.
 */
object EventArt {
    private const val SIZE = 500
    private const val SEARCH_URL = "https://site.web.api.espn.com/apis/search/v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * "Knicks vs Celtics" / "NYCFC at Inter Miami" / "Yankees @ Red Sox" -> the two sides.
     * The separator must be a whole word so "Vsevolod" or "Attack" can't split a title.
     */
    fun splitMatchup(title: String?): Pair<String, String>? {
        val t = title?.trim() ?: return null
        val m = Regex("""\s+(?:vs\.?|v\.?|at|@)\s+""", RegexOption.IGNORE_CASE).find(t) ?: return null
        val a = t.take(m.range.first).trim().trimEnd(':', '-', '–', '—').trim()
        val b = t.substring(m.range.last + 1).trim()
        if (a.isBlank() || b.isBlank()) return null
        return a to b
    }

    /**
     * The crest for a team name, resolved through ESPN's search. `defaultDark` is preferred
     * for the same reason BrightSports prefers `rel: dark` — the default variants are drawn
     * for white pages and can vanish on this app's black.
     */
    fun teamLogoUrl(team: String): String? {
        val q = URLEncoder.encode(team, "UTF-8")
        val req = Request.Builder().url("$SEARCH_URL?query=$q&limit=5").build()
        client.newCall(req).execute().use { resp ->
            val root = JSONObject(resp.body?.string().orEmpty())
            val results = root.optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                if (r.optString("type") != "team") continue
                val contents = r.optJSONArray("contents") ?: continue
                for (j in 0 until contents.length()) {
                    val img = contents.getJSONObject(j).optJSONObject("image") ?: continue
                    val url = img.optString("defaultDark").ifBlank { img.optString("default") }
                    if (url.isNotBlank()) return url
                }
            }
        }
        return null
    }

    /** Fetch and decode a crest, downsampled — ESPN serves 500px, half a megapixel a side. */
    fun downloadLogo(url: String): Bitmap? {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE / 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }

    /**
     * The versus card: black square, home crest clipped to the upper-left of the diagonal,
     * away crest to the lower-right, a thin white line where they meet.
     */
    fun versusCard(home: Bitmap, away: Bitmap): Bitmap {
        val s = SIZE.toFloat()
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)

        val upperLeft = Path().apply { moveTo(0f, 0f); lineTo(s, 0f); lineTo(0f, s); close() }
        val lowerRight = Path().apply { moveTo(s, 0f); lineTo(s, s); lineTo(0f, s); close() }

        c.save(); c.clipPath(upperLeft); drawCrest(c, home, s * 0.30f, s * 0.30f, s * 0.46f); c.restore()
        c.save(); c.clipPath(lowerRight); drawCrest(c, away, s * 0.70f, s * 0.70f, s * 0.46f); c.restore()

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = s * 0.012f
        }
        c.drawLine(0f, s, s, 0f, line)
        return out
    }

    /** A crest drawn centered at (cx, cy), scaled to fit a [size] box, aspect kept. */
    private fun drawCrest(c: Canvas, crest: Bitmap, cx: Float, cy: Float, size: Float) {
        val scale = minOf(size / crest.width, size / crest.height)
        val w = crest.width * scale
        val h = crest.height * scale
        val dst = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(crest, null, dst, paint)
    }

    /**
     * The concert placeholder: a beamed pair of eighth notes, white on black. Drawn, not an
     * asset, for the same reason LightNoise ships no audio files — nothing to bundle.
     */
    fun musicCard(): Bitmap {
        val s = SIZE.toFloat()
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

        val stemW = s * 0.030f
        // Note heads: two tilted ovals, the right one sitting slightly higher.
        val head1 = RectF(s * 0.28f, s * 0.62f, s * 0.44f, s * 0.73f)
        val head2 = RectF(s * 0.56f, s * 0.57f, s * 0.72f, s * 0.68f)
        c.save(); c.rotate(-18f, head1.centerX(), head1.centerY()); c.drawOval(head1, ink); c.restore()
        c.save(); c.rotate(-18f, head2.centerX(), head2.centerY()); c.drawOval(head2, ink); c.restore()
        // Stems up from each head's right edge.
        c.drawRect(head1.right - stemW, s * 0.32f, head1.right, head1.centerY(), ink)
        c.drawRect(head2.right - stemW, s * 0.27f, head2.right, head2.centerY(), ink)
        // The beam joining the stem tops, sloped like the heads.
        val beam = Path().apply {
            moveTo(head1.right - stemW, s * 0.32f)
            lineTo(head2.right, s * 0.27f)
            lineTo(head2.right, s * 0.335f)
            lineTo(head1.right - stemW, s * 0.385f)
            close()
        }
        c.drawPath(beam, ink)
        return out
    }

    fun savePng(bmp: Bitmap, file: File): File {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}

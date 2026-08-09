package com.gios.lightpass.ai

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class ParsedPass(
    val movieTitle: String,
    val theater: String?,
    val date: String?,
    val time: String?,
    val seat: String?,
    val price: String?,
    val code: String?,
    val confidence: Double,
    // normalized [0..1] bounding box of the ticket within the image
    val boxX: Double?,
    val boxY: Double?,
    val boxW: Double?,
    val boxH: Double?,
    val notATicket: Boolean = false,
    /** "MOVIE", "SPORTS" or "CONCERT" — the model's read of what the ticket is for. */
    val kind: String = com.gios.lightpass.data.EventType.MOVIE,
)

object PassParser {
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-4-5-20251001"
    private const val MAX_RETRIES = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Today's date goes in because the model has no clock and the stub usually has no year.
     *
     * This used to read "infer year if absent (use current year 2026)" — a literal in the source,
     * which goes stale and invites exactly the guess it was meant to prevent. It answered 2024.
     * Now the model is told to leave the year off whenever the paper leaves it off, and
     * [com.gios.lightpass.util.TicketDate] decides it afterwards from a real calendar.
     */
    private fun prompt(today: LocalDate) = """
        Analyze this image of an event ticket (movie, sports game, or concert) and extract
        the following. Return ONLY valid JSON, no markdown, no backticks, no explanation.
        {
          "kind": "movie" | "sports" | "concert",
          "movieTitle": "exact event title on ticket (film title, matchup like 'Knicks vs Celtics', or artist/tour name)",
          "theater": "theater/cinema, stadium/arena, or venue name",
          "date": "YYYY-MM-DD if a year is printed on the ticket, otherwise MM-DD",
          "time": "h:mm AM/PM",
          "seat": "seat/row info or null",
          "price": "price with $ or null",
          "code": "booking/confirmation reference, exactly as printed, or null",
          "confidence": 0.95,
          "box": [x0, y0, x1, y1]
        }
        "box" is the TIGHT rectangle around ONLY the printed ticket/receipt paper,
        as INTEGERS on a 0-1000 grid: [x0,y0] = top-left corner, [x1,y1] = bottom-right,
        where 0,0 is the image's top-left and 1000,1000 is bottom-right.
        Exclude any hand, table, background, or empty margins outside the ticket.
        Trace the actual paper edges. Only use [0,0,1000,1000] if the paper truly
        bleeds to every edge of the photo.
        Today is $today. A ticket with no year printed on it is for an UPCOMING showing,
        so do not guess the year: return MM-DD and let the app work it out. Return a
        four-digit year only when you can actually read one on the paper.
        "code" is the booking or confirmation reference: the human-readable string printed
        under or beside the barcode, copied character for character. Never invent one and
        never substitute a seat number or an order total — return null if none is legible.
        Rules: time is 12-hour with AM/PM.
        Read AM/PM carefully from the ticket. Showings and events are almost always
        matinee or evening (11:00 AM - 11:59 PM); a 1-6 AM start time is almost
        certainly a misread PM. confidence 0-1 over all fields.
        "kind" is your best read of what the ticket admits you to: "movie" for a film
        screening, "sports" for a game or match, "concert" for live music or a show.
        If the image is NOT a ticket at all, return {"error":"not_a_ticket","confidence":0}.
    """.trimIndent()

    fun parse(imageFile: File, apiKey: String): ParsedPass {
        var last: Exception? = null
        repeat(MAX_RETRIES) {
            try { return requestOnce(imageFile, apiKey) } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("parse failed")
    }

    private fun requestOnce(imageFile: File, apiKey: String): ParsedPass {
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 500)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64"); put("media_type", "image/jpeg"); put("data", b64)
                        })
                    })
                    .put(JSONObject().apply { put("type", "text"); put("text", prompt(LocalDate.now())) }))
            }))
        }.toString()

        val req = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val text = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
            val j = JSONObject(text.substringAfter('{').substringBeforeLast('}').let { "{$it}" })
            if (j.optString("error") == "not_a_ticket") {
                return ParsedPass("", null, null, null, null, null, null, 0.0, null, null, null, null, notATicket = true)
            }
            val boxArr = j.optJSONArray("box")
            var nx: Double? = null; var ny: Double? = null; var nw: Double? = null; var nh: Double? = null
            if (boxArr != null && boxArr.length() == 4) {
                val x0 = boxArr.optDouble(0); val y0 = boxArr.optDouble(1)
                val x1 = boxArr.optDouble(2); val y1 = boxArr.optDouble(3)
                if (!x0.isNaN() && !y0.isNaN() && !x1.isNaN() && !y1.isNaN() && x1 > x0 && y1 > y0) {
                    nx = x0 / 1000.0; ny = y0 / 1000.0
                    nw = (x1 - x0) / 1000.0; nh = (y1 - y0) / 1000.0
                }
            }
            return ParsedPass(
                movieTitle = j.optString("movieTitle").ifBlank { "Untitled" },
                theater = j.optString("theater").ifBlank { null },
                date = j.optString("date").ifBlank { null },
                time = j.optString("time").ifBlank { null },
                seat = j.optString("seat").ifBlank { null },
                price = j.optString("price").ifBlank { null },
                code = j.optString("code").ifBlank { null },
                confidence = j.optDouble("confidence", 0.0),
                boxX = nx, boxY = ny, boxW = nw, boxH = nh,
                kind = when (j.optString("kind").lowercase()) {
                    "sports" -> com.gios.lightpass.data.EventType.SPORTS
                    "concert" -> com.gios.lightpass.data.EventType.CONCERT
                    else -> com.gios.lightpass.data.EventType.MOVIE
                },
            )
        }
    }
}

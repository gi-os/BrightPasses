package com.gios.lightpass.ai

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
)

object PassParser {
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-4-5-20251001"
    private const val MAX_RETRIES = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prompt = """
        Analyze this image of a movie ticket and extract the following.
        Return ONLY valid JSON, no markdown, no backticks, no explanation.
        {
          "movieTitle": "exact movie title on ticket",
          "theater": "theater/cinema name",
          "date": "YYYY-MM-DD, infer year if absent (use current year 2026)",
          "time": "h:mm AM/PM",
          "seat": "seat/row info or null",
          "price": "price with $ or null",
          "code": "alphanumeric code under the barcode or null",
          "confidence": 0.95,
          "box": {"x":0.0,"y":0.0,"w":1.0,"h":1.0}
        }
        "box" is the ticket's tight bounding box as fractions of the image
        (x,y = top-left corner; w,h = width,height; all 0-1). If the ticket fills
        the frame use {"x":0,"y":0,"w":1,"h":1}.
        Rules: date is ISO; time is 12-hour AM/PM; confidence 0-1 over all fields.
        If NOT a movie ticket, return {"error":"not_a_ticket","confidence":0}.
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
                    .put(JSONObject().apply { put("type", "text"); put("text", prompt) }))
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
            val box = j.optJSONObject("box")
            return ParsedPass(
                movieTitle = j.optString("movieTitle").ifBlank { "Untitled" },
                theater = j.optString("theater").ifBlank { null },
                date = j.optString("date").ifBlank { null },
                time = j.optString("time").ifBlank { null },
                seat = j.optString("seat").ifBlank { null },
                price = j.optString("price").ifBlank { null },
                code = j.optString("code").ifBlank { null },
                confidence = j.optDouble("confidence", 0.0),
                boxX = box?.optDouble("x"), boxY = box?.optDouble("y"),
                boxW = box?.optDouble("w"), boxH = box?.optDouble("h"),
            )
        }
    }
}

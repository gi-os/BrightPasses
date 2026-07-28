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
        Analyze this image of a movie ticket and extract the following information.
        Return ONLY valid JSON, no markdown, no backticks, no explanation.
        {
          "movieTitle": "exact movie title shown on ticket",
          "theater": "theater/cinema name",
          "date": "YYYY-MM-DD format, infer year if not shown (use current year 2026)",
          "time": "h:mm AM/PM format",
          "seat": "seat/row info if visible, or null",
          "price": "price with $ if visible, or null",
          "code": "the alphanumeric code printed under the barcode if visible, or null",
          "confidence": 0.95
        }
        Rules:
        - If a field is illegible or missing, best-guess and lower confidence.
        - date is always ISO YYYY-MM-DD; time is 12-hour with AM/PM.
        - confidence 0-1 over ALL fields.
        - If this is NOT a movie ticket, return {"error":"not_a_ticket","confidence":0}.
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
            put("max_tokens", 400)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", b64)
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
            val jsonText = text.substringAfter('{').substringBeforeLast('}').let { "{$it}" }
            val j = JSONObject(jsonText)
            if (j.optString("error") == "not_a_ticket") {
                return ParsedPass("", null, null, null, null, null, null, 0.0, notATicket = true)
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
            )
        }
    }
}

package com.lightpass.reader

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.Base64

/** Mirrors the NDPass ParsedTicketData shape. */
data class ParsedPass(
    val movieTitle: String,
    val theater: String?,
    val date: String?,      // YYYY-MM-DD
    val time: String?,      // h:mm AM/PM
    val seat: String?,
    val price: String?,
    val code: String?,      // barcode/QR text if visible
    val confidence: Double,
    val notATicket: Boolean = false,
)

class PassParser {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 25_000
        }
    }

    // NDPass prompt, adapted (adds barcode code capture for on-screen scanning).
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
        - If a field is illegible or missing, use your best guess and lower the confidence score.
        - For date, always output ISO format YYYY-MM-DD.
        - For time, always output 12-hour with AM/PM.
        - confidence is 0-1 representing how sure you are about ALL extracted fields.
        - If this is NOT a movie ticket, return {"error": "not_a_ticket", "confidence": 0}.
    """.trimIndent()

    suspend fun parse(imageFile: File, apiKey: String, model: String = DEFAULT_MODEL): ParsedPass {
        var lastError: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return requestOnce(imageFile, apiKey, model)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("parse failed")
    }

    private suspend fun requestOnce(imageFile: File, apiKey: String, model: String): ParsedPass {
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("model", model)
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

        val raw = client.post(API_URL) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

        val text = JSONObject(raw)
            .getJSONArray("content").getJSONObject(0).getString("text")
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

    fun close() = client.close()

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MAX_RETRIES = 3
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    }
}

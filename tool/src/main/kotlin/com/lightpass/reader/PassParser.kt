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

data class ParsedPass(
    val type: String,
    val title: String,
    val date: String?,
    val code: String?,
    val issuer: String?,
)

/**
 * Sends the original image to Claude Vision and returns structured fields.
 * The API key is the user's own Anthropic key, stored locally (never in this repo).
 * Fails soft: caller falls back to a filename title if this throws.
 */
class PassParser {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 25_000
        }
    }

    private val prompt = """
        Extract this pass/ticket as JSON only, no prose, no markdown:
        {"type":"movie|flight|event|transit|loyalty|other","title":"","date":"ISO 8601 or empty","code":"barcode/QR text or empty","issuer":""}
    """.trimIndent()

    suspend fun parse(imageFile: File, apiKey: String, model: String = DEFAULT_MODEL): ParsedPass {
        val b64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 300)
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
                    .put(JSONObject().apply {
                        put("type", "text"); put("text", prompt)
                    }))
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
        return ParsedPass(
            type = j.optString("type", "other").ifBlank { "other" },
            title = j.optString("title").ifBlank { "Untitled pass" },
            date = j.optString("date").ifBlank { null },
            code = j.optString("code").ifBlank { null },
            issuer = j.optString("issuer").ifBlank { null },
        )
    }

    fun close() = client.close()

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001" // cheap, good enough for OCR-ish parse
    }
}

package com.gios.lightpass.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MovieMeta(
    val posterUrl: String?,
    val overview: String?,
    val runtimeMin: Int?,
    val year: String?,
)

/** Optional TMDb lookup (v3 api_key). All failures return null fields. */
object TmdbClient {
    private const val IMG = "https://image.tmdb.org/t/p/w500"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    fun lookup(title: String, apiKey: String, year: String? = null): MovieMeta? {
        if (apiKey.isBlank() || title.isBlank()) return null
        return runCatching {
            val q = URLEncoder.encode(title, "UTF-8")
            val yr = year?.let { "&year=$it" } ?: ""
            val searchUrl = "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$q$yr"
            val results = JSONObject(get(searchUrl)).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            val id = first.optInt("id", -1)
            val poster = first.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
            val overview = first.optString("overview").ifBlank { null }
            val relYear = first.optString("release_date").take(4).ifBlank { null }

            var runtime: Int? = null
            if (id > 0) {
                runCatching {
                    val det = JSONObject(get("https://api.themoviedb.org/3/movie/$id?api_key=$apiKey"))
                    runtime = det.optInt("runtime").takeIf { it > 0 }
                }
            }
            MovieMeta(
                posterUrl = poster?.let { "$IMG$it" },
                overview = overview,
                runtimeMin = runtime,
                year = relYear,
            )
        }.getOrNull()
    }

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            return r.body?.string().orEmpty()
        }
    }
}

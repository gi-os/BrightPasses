package com.gios.lightpass.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MovieCandidate(
    val id: Int,
    val title: String,
    val year: String?,
    val posterUrl: String?,
    val overview: String?,
)

/** Optional TMDb lookup (v3 api_key). All failures return empty/null. */
object TmdbClient {
    private const val IMG = "https://image.tmdb.org/t/p/w500"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /** All results for the title, so the user can pick the right one. */
    fun search(title: String, apiKey: String, year: String? = null): List<MovieCandidate> {
        if (apiKey.isBlank() || title.isBlank()) return emptyList()
        return runCatching {
            val q = URLEncoder.encode(title, "UTF-8")
            val yr = year?.let { "&year=$it" } ?: ""
            val json = JSONObject(get("https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=$q$yr"))
            val arr = json.optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val poster = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                MovieCandidate(
                    id = o.optInt("id", -1),
                    title = o.optString("title").ifBlank { o.optString("original_title") },
                    year = o.optString("release_date").take(4).ifBlank { null },
                    posterUrl = poster?.let { "$IMG$it" },
                    overview = o.optString("overview").ifBlank { null },
                )
            }.filter { it.id > 0 }.take(20)
        }.getOrDefault(emptyList())
    }

    fun runtime(id: Int, apiKey: String): Int? = runCatching {
        JSONObject(get("https://api.themoviedb.org/3/movie/$id?api_key=$apiKey"))
            .optInt("runtime").takeIf { it > 0 }
    }.getOrNull()

    private fun get(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            return r.body?.string().orEmpty()
        }
    }
}

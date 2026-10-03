package org.randomdhamma.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.randomdhamma.data.Pick
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.Talk
import java.io.IOException
import java.util.concurrent.TimeUnit

class DhammaTalksClient(
    private val baseUrl: String = DhammaParsers.BASE_URL,
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun request(path: String) = Request.Builder()
        .url(DhammaParsers.resolve(path, baseUrl))
        .header("User-Agent", USER_AGENT)
        .build()

    /** The site's /suttas/random answers with a 302 to a random sutta; read the Location header. */
    suspend fun randomSutta(): Pick = withContext(Dispatchers.IO) {
        val url = client.newCall(request("/suttas/random")).execute().use { resp ->
            val location = resp.header("Location")
            if (!resp.isRedirect || location.isNullOrBlank()) {
                throw IOException("Expected redirect from /suttas/random, got HTTP ${resp.code}")
            }
            DhammaParsers.resolve(location, baseUrl)
        }
        val title = runCatching { fetchText(url) }.getOrNull()
            ?.let(DhammaParsers::parseSuttaTitle)
            ?: DhammaParsers.suttaTitleFromUrl(url)
        Pick(ContentType.SUTTA, url, title)
    }

    suspend fun fetchYears(): List<Int> = withContext(Dispatchers.IO) {
        DhammaParsers.parseYears(fetchText(MORNING_INDEX))
    }

    /** Talks for [year]. The current year has no page of its own; it lives on the index (year pages redirect there). */
    suspend fun fetchTalks(year: Int, latestYear: Int): List<Talk> = withContext(Dispatchers.IO) {
        val path = if (year >= latestYear) MORNING_INDEX else "$MORNING_INDEX$year/"
        DhammaParsers.parseTalks(fetchText(path), year)
    }

    private fun fetchText(pathOrUrl: String): String {
        var target = DhammaParsers.resolve(pathOrUrl, baseUrl)
        repeat(MAX_REDIRECTS) {
            val req = Request.Builder().url(target).header("User-Agent", USER_AGENT).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isRedirect) {
                    val loc = resp.header("Location") ?: throw IOException("Redirect without Location")
                    target = DhammaParsers.resolve(loc, target)
                } else if (resp.isSuccessful) {
                    return resp.body.string()
                } else {
                    throw IOException("HTTP ${resp.code} for $target")
                }
            }
        }
        throw IOException("Too many redirects for $pathOrUrl")
    }

    companion object {
        const val MORNING_INDEX = "/audio/morning/"
        private const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "RandomDhamma/1.0 (personal Android app; Pixel)"
    }
}

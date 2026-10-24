package com.assistant.app.network

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** One web result: where it is, what it is called and a short excerpt. */
data class SearchHit(val title: String, val url: String, val snippet: String)

/**
 * Free web search through DuckDuckGo, no account or key. There is no official search API, so this reads
 * DuckDuckGo's plain HTML result pages (the same ones its no-JavaScript site uses), falls back to the lite
 * page, and last to the Instant Answer API, which only knows well-covered topics.
 */
object DuckDuckGoClient {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** [time] narrows to recent results: "day", "week", "month" or "year". */
    suspend fun search(query: String, time: String?, max: Int = 6): Result<List<SearchHit>> = withContext(Dispatchers.IO) {
        val region = "wt-wt"
        val dateFilter = when (time) { "day" -> "d"; "week" -> "w"; "month" -> "m"; "year" -> "y"; else -> "" }
        var blocked = false
        for (page in listOf(Page.HTML, Page.LITE)) {
            val body = runCatching { post(page.url, query, region, dateFilter) }.getOrNull() ?: continue
            if (isChallenge(body)) { blocked = true; continue }
            val hits = (if (page == Page.HTML) parseHtml(body) else parseLite(body)).take(max)
            if (hits.isNotEmpty()) return@withContext Result.success(hits)
        }
        // DuckDuckGo challenges some IPs (mobile networks included) for hours; Bing's plain page has no such
        // check, so use it before settling for the thin Instant Answer API.
        val bing = runCatching { parseBing(getBing(query, time)) }.getOrNull().orEmpty().take(max)
        if (bing.isNotEmpty()) return@withContext Result.success(bing)
        val answer = runCatching { instantAnswer(query) }.getOrNull().orEmpty()
        if (answer.isNotEmpty()) return@withContext Result.success(answer.take(max))
        Result.failure(
            IllegalStateException(
                if (blocked) "DuckDuckGo is asking for a human check right now, so search is paused. Try again in a minute."
                else "DuckDuckGo returned no results."
            )
        )
    }

    private enum class Page(val url: String) {
        HTML("https://html.duckduckgo.com/html/"),
        LITE("https://lite.duckduckgo.com/lite/")
    }

    private fun post(url: String, query: String, region: String, dateFilter: String): String {
        val form = FormBody.Builder().add("q", query).add("kl", region).apply { if (dateFilter.isNotEmpty()) add("df", dateFilter) }.build()
        val request = Request.Builder().url(url).post(form)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://duckduckgo.com/")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 202) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun getBing(query: String, time: String?): String {
        val freshness = when (time) { "day" -> "ex1:\"ez1\""; "week" -> "ex1:\"ez2\""; "month" -> "ex1:\"ez3\""; else -> "" }
        val url = "https://www.bing.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("setlang", "en").addQueryParameter("cc", "us")
            .apply { if (freshness.isNotEmpty()) addQueryParameter("filters", freshness) }.build()
        val request = Request.Builder().url(url).header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    internal fun parseBing(body: String): List<SearchHit> {
        val blocks = Regex("""<li class="b_algo"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL).findAll(body)
        return blocks.mapNotNull { block ->
            val html = block.groupValues[1]
            val head = Regex("""<h2[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: Regex("""<a[^>]*href="([^"]+)"[^>]*>\s*<h2[^>]*>(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: return@mapNotNull null
            val url = bingTarget(head.groupValues[1].replace("&amp;", "&")) ?: return@mapNotNull null
            val snippet = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1).orEmpty()
            SearchHit(clean(head.groupValues[2]), url, clean(snippet))
        }.toList()
    }

    /** Bing sometimes wraps the result as /ck/a?...&u=a1<base64 address>; a plain address passes through. */
    internal fun bingTarget(href: String): String? {
        val wrapped = Regex("""[?&]u=a1([A-Za-z0-9_-]+)""").find(href)?.groupValues?.get(1)
        val url = if (wrapped != null) {
            runCatching { String(android.util.Base64.decode(wrapped, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)) }
                .getOrNull() ?: return null
        } else href
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    internal fun isChallenge(body: String): Boolean =
        body.contains("anomaly-modal", ignoreCase = true) || body.contains("bots use DuckDuckGo too", ignoreCase = true)

    internal fun parseHtml(body: String): List<SearchHit> {
        val links = Regex("""<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippets = Regex("""<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body).map { clean(it.groupValues[1]) }.toList()
        return links.findAll(body).mapIndexedNotNull { index, match ->
            val url = realUrl(match.groupValues[1]) ?: return@mapIndexedNotNull null
            SearchHit(clean(match.groupValues[2]), url, snippets.getOrElse(index) { "" })
        }.toList()
    }

    internal fun parseLite(body: String): List<SearchHit> {
        val links = Regex("""<a[^>]*href=["']([^"']+)["'][^>]*class=['"]result-link['"][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippets = Regex("""<td[^>]*class=['"]result-snippet['"][^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body).map { clean(it.groupValues[1]) }.toList()
        return links.findAll(body).mapIndexedNotNull { index, match ->
            val url = realUrl(match.groupValues[1]) ?: return@mapIndexedNotNull null
            SearchHit(clean(match.groupValues[2]), url, snippets.getOrElse(index) { "" })
        }.toList()
    }

    /** DuckDuckGo wraps result links as /l/?uddg=<real address>; ads use y.js and are dropped. */
    private fun realUrl(raw: String): String? {
        val href = raw.replace("&amp;", "&")
        if (href.contains("duckduckgo.com/y.js") || href.contains("ad_provider")) return null
        val wrapped = Regex("""[?&]uddg=([^&]+)""").find(href)?.groupValues?.get(1)
        val url = if (wrapped != null) URLDecoder.decode(wrapped, "UTF-8") else href
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun clean(html: String): String =
        Html.fromHtml(html.replace(Regex("(?s)<[^>]+>"), ""), Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()

    private fun instantAnswer(query: String): List<SearchHit> {
        val url = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val body = http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute()
            .use { it.body?.string().orEmpty() }
        val json = JSONObject(body)
        val hits = ArrayList<SearchHit>()
        val abstract = json.optString("AbstractText")
        if (abstract.isNotBlank()) hits += SearchHit(json.optString("Heading"), json.optString("AbstractURL"), abstract)
        val topics = json.optJSONArray("RelatedTopics")
        if (topics != null) {
            for (i in 0 until topics.length()) {
                val topic = topics.optJSONObject(i) ?: continue
                val text = topic.optString("Text")
                if (text.isNotBlank()) hits += SearchHit(text.substringBefore(" - "), topic.optString("FirstURL"), text)
            }
        }
        return hits
    }
}

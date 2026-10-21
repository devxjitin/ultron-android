package com.assistant.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.util.LruCache
import com.assistant.app.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Card data for a link in a chat reply: a thumbnail, title and short description. YouTube links use the public
 * thumbnail address and oEmbed; other pages are read for their Open Graph tags. This runs on the phone, so the sites
 * see the phone's address like any browser, and addresses on the phone's own network are never fetched.
 */
object LinkPreviews {
    data class Preview(
        val url: String,
        val title: String,
        val description: String,
        val site: String,
        val imageUrl: String?,
        val video: Boolean,
        /** The site's small icon, shown when there is no large image. */
        val iconUrl: String? = null
    )

    private const val MAX_LINKS = 10
    private const val MAX_PAGE_BYTES = 300_000
    private const val MAX_IMAGE_BYTES = 2_000_000
    private const val MAX_IMAGE_WIDTH = 800
    private const val BROWSER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    // Balanced brackets stay in the address (Wikipedia style), the closing bracket of a Markdown link does not.
    private val linkPattern = Regex("https?://(?:[^\\s<>()\\[\\]]|\\([^\\s()]*\\))+")
    private val metaPattern = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val attrPattern = Regex("([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val linkTagPattern = Regex("<link\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val titlePattern = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val client = HttpClients.shared.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
        .build()

    // A failed page is remembered too, so scrolling a chat does not fetch it again.
    private val previews = HashMap<String, Preview?>()
    private val images = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The distinct web addresses in a reply, in order, one per YouTube video. */
    fun extractLinks(text: String): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (match in linkPattern.findAll(text)) {
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', '\'', '"', '*', '_')
            val key = youtubeId(url)?.let { "yt:$it" } ?: url.trimEnd('/')
            if (seen.add(key)) out.add(url)
            if (out.size >= MAX_LINKS) break
        }
        return out
    }

    fun youtubeId(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments
        val id = when {
            host == "youtu.be" -> segments.firstOrNull()
            host == "youtube.com" || host.endsWith(".youtube.com") -> when (segments.firstOrNull()) {
                "shorts", "embed", "live" -> segments.getOrNull(1)
                else -> uri.getQueryParameter("v")
            }
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    suspend fun preview(url: String): Preview? = withContext(Dispatchers.IO) {
        synchronized(previews) { if (previews.containsKey(url)) return@withContext previews[url] }
        // A page that cannot be read (blocked, not HTML, no title) still gets a plain card with its site name.
        // YouTube pages that are not a video (channel, playlist, search) are read with the extractor, because the web page
        // itself only returns a generic landing text.
        val result = runCatching {
            youtubeId(url)?.let { youtube(url, it) } ?: if (isYoutube(url)) youtubeOther(url) else page(url)
        }.getOrNull() ?: fallback(url)
        synchronized(previews) { previews[url] = result }
        result
    }

    private fun isYoutube(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private fun youtubeOther(url: String): Preview? {
        val uri = Uri.parse(url)
        val first = uri.pathSegments.firstOrNull().orEmpty()
        val canonical = "https://www.youtube.com" + (uri.encodedPath ?: "") + (uri.encodedQuery?.let { "?$it" } ?: "")
        YouTubeDownloader.ensureInit()
        return when {
            uri.getQueryParameter("list") != null -> {
                val info = PlaylistInfo.getInfo("https://www.youtube.com/playlist?list=${uri.getQueryParameter("list")}")
                Preview(
                    url, info.name.orEmpty().ifBlank { "YouTube playlist" }, "${info.streamCount} videos",
                    info.uploaderName.orEmpty().ifBlank { "YouTube" }, info.thumbnails.maxByOrNull { it.height }?.url, video = true
                )
            }
            first.startsWith("@") || first == "channel" || first == "c" || first == "user" -> {
                val info = ChannelInfo.getInfo(canonical)
                Preview(
                    url, info.name.orEmpty().ifBlank { "YouTube channel" }, info.description.orEmpty().take(160),
                    "YouTube channel", null, video = false, iconUrl = info.avatars.maxByOrNull { it.height }?.url
                )
            }
            first == "results" -> Preview(
                url, "YouTube search: " + uri.getQueryParameter("search_query").orEmpty(), "", "youtube.com", null,
                video = false, iconUrl = "https://www.youtube.com/favicon.ico"
            )
            else -> null
        }
    }

    suspend fun image(url: String): Bitmap? = withContext(Dispatchers.IO) {
        images.get(url)?.let { return@withContext it }
        val bytes = runCatching { get(url, "image/*") { readBytes(it, MAX_IMAGE_BYTES) } }.getOrNull() ?: return@withContext null
        val bitmap = decode(bytes) ?: return@withContext null
        images.put(url, bitmap)
        bitmap
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_WIDTH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** What can be shown at once, before anything is fetched: the video thumbnail, or the site name. */
    fun quick(url: String): Preview? = youtubeId(url)?.let {
        Preview(url, "YouTube video", "", "YouTube", "https://i.ytimg.com/vi/$it/hqdefault.jpg", video = true)
    } ?: fallback(url)

    private fun fallback(url: String): Preview? {
        val http = url.toHttpUrlOrNull() ?: return null
        val path = http.encodedPath.trim('/').let { Uri.decode(it) }.replace("/", " / ").replace('-', ' ').replace('_', ' ')
        return Preview(
            url = url,
            title = path.ifBlank { http.host.removePrefix("www.") }.take(120),
            description = "",
            site = http.host.removePrefix("www."),
            imageUrl = null,
            video = false,
            iconUrl = "https://${http.host}/favicon.ico"
        )
    }

    private fun youtube(url: String, id: String): Preview {
        val thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        val info = runCatching {
            val endpoint = "https://www.youtube.com/oembed?format=json&url=" + Uri.encode("https://www.youtube.com/watch?v=$id")
            get(endpoint, "application/json") { JSONObject(String(readBytes(it, 50_000), Charsets.UTF_8)) }
        }.getOrNull()
        return Preview(
            url = url,
            title = info?.optString("title").orEmpty().ifBlank { "YouTube video" },
            description = "",
            site = info?.optString("author_name").orEmpty().ifBlank { "YouTube" },
            imageUrl = thumbnail,
            video = true
        )
    }

    private fun page(url: String): Preview? {
        val (finalUrl, html) = getWithUrl(url, "text/html,application/xhtml+xml") { response, finalUrl ->
            val type = response.header("Content-Type").orEmpty().lowercase()
            if (!type.contains("html")) return@getWithUrl null
            val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val text = String(readBytes(response, MAX_PAGE_BYTES), charset)
            finalUrl to text.substringBefore("</head>", text)
        } ?: return null
        val tags = HashMap<String, String>()
        for (tag in metaPattern.findAll(html)) {
            val attrs = attrPattern.findAll(tag.value).associate {
                it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3] })
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
            val content = attrs["content"] ?: continue
            tags.putIfAbsent(key, content)
        }
        val icon = linkTagPattern.findAll(html).map { tag ->
            attrPattern.findAll(tag.value).associate { it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        }.firstOrNull { (it["rel"]?.lowercase()?.split(' ')?.any { r -> r == "icon" || r == "apple-touch-icon" } == true) && it["href"] != null }
            ?.get("href")
        val title = clean(tags["og:title"] ?: tags["twitter:title"] ?: titlePattern.find(html)?.groupValues?.get(1).orEmpty())
        if (title.isBlank()) return null
        val base = finalUrl.toHttpUrlOrNull()
        val image = (tags["og:image"] ?: tags["twitter:image"] ?: tags["og:image:url"])?.let { clean(it) }
            ?.let { base?.resolve(it)?.toString() ?: it }?.takeIf { it.startsWith("http") }
        return Preview(
            url = url,
            title = title,
            description = clean(tags["og:description"] ?: tags["twitter:description"] ?: tags["description"].orEmpty()).take(240),
            site = clean(tags["og:site_name"].orEmpty()).ifBlank { base?.host?.removePrefix("www.").orEmpty() },
            imageUrl = image,
            video = false,
            iconUrl = (icon?.let { base?.resolve(clean(it))?.toString() } ?: base?.let { "https://${it.host}/favicon.ico" })
                ?.takeIf { it.startsWith("http") }
        )
    }

    private fun clean(raw: String): String =
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun readBytes(response: Response, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        response.body?.byteStream()?.use { stream ->
            while (out.size() < max) {
                val n = stream.read(buffer, 0, minOf(buffer.size, max - out.size()))
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun <T> get(url: String, accept: String, read: (Response) -> T?): T? =
        getWithUrl(url, accept) { response, _ -> read(response) }

    /** Fetches [url], following up to four redirects, and refuses every hop that is not a public web address. */
    private fun <T> getWithUrl(url: String, accept: String, read: (Response, String) -> T?): T? {
        var current = url
        repeat(5) {
            val http = current.toHttpUrlOrNull() ?: return null
            if (!isPublic(http)) return null
            val request = Request.Builder().url(http)
                .header("User-Agent", BROWSER_AGENT).header("Accept", accept).header("Accept-Language", "en").build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    current = http.resolve(response.header("Location") ?: return null)?.toString() ?: return null
                    return@use
                }
                if (!response.isSuccessful) return null
                return read(response, http.toString())
            }
        }
        return null
    }

    private fun isPublic(url: HttpUrl): Boolean {
        if (url.port != 80 && url.port != 443) return false
        val host = url.host.lowercase()
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
        return runCatching { InetAddress.getAllByName(host).all(::isPublicAddress) }.getOrDefault(false)
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val b = address.address
        if (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC) return false // IPv6 unique local
        if (b.size == 4 && (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64) return false // carrier-grade NAT
        return true
    }
}

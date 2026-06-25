package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.aggregatorx.app.engine.util.EngineUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * HeadlessBrowserHelper — Unified Native Android scraping stack & WebView helper.
 *
 * Resides in com.aggregatorx.app.engine.scraper package.
 */
object HeadlessBrowserHelper {

    private const val TAG = "HeadlessBrowserHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cookieJar = InMemoryCookieJar()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", EngineUtils.DEFAULT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Ch-Ua-Mobile", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    // ── Main-Thread WebView Lifecycles ────────────────────────────────────────

    suspend fun createWebView(context: Context, userAgent: String? = null): WebView = withContext(Dispatchers.Main) {
        val wv = WebView(context.applicationContext)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        if (userAgent != null) {
            settings.userAgentString = userAgent
        }
        wv
    }

    suspend fun destroyWebView(webView: WebView) = withContext(Dispatchers.Main) {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        } catch (_: Throwable) {
        }
    }

    suspend fun runOnMain(block: suspend () -> Unit) = withContext(Dispatchers.Main) {
        block()
    }

    // ── Native Jsoup / OkHttp Scraping ────────────────────────────────────────

    suspend fun fetchPageContentWithShadowAndAdSkip(
        url: String,
        waitSelector: String? = null,
        timeout: Int = 30000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timeoutClient = client.newBuilder()
                .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Referer", extractHost(url) + "/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            timeoutClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPageContentWithShadowAndAdSkip error: ${e.message}")
            null
        }
    }

    suspend fun fetchPageContent(
        url: String,
        waitSelector: String? = null,
        timeout: Int = 30000
    ): String? = fetchPageContentWithShadowAndAdSkip(url, waitSelector, timeout)

    suspend fun extractVideoUrls(url: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val html = fetchRaw(url) ?: return@withContext emptyList()
            val found = mutableListOf<String>()

            val doc = Jsoup.parse(html, url)
            doc.select("video source, video[src]").forEach { el ->
                val src = el.absUrl("src").ifEmpty { el.absUrl("data-src") }
                if (src.isNotEmpty()) found.add(src)
            }

            val videoPatterns = listOf(
                Regex("""['"]?(https?://[^'">\s]+\.(?:mp4|m3u8|mpd|webm|ts|mkv)[^'">\s]*)['"]?""", RegexOption.IGNORE_CASE),
                Regex("""file\s*:\s*['"]([^'"]+\.(?:mp4|m3u8|mpd|webm)[^'"]*)['"]""", RegexOption.IGNORE_CASE),
                Regex("""src\s*:\s*['"]([^'"]+\.(?:mp4|m3u8|mpd|webm)[^'"]*)['"]""", RegexOption.IGNORE_CASE),
                Regex("""videoUrl\s*[=:]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""streamUrl\s*[=:]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
                Regex("""['"]?(https?://[^'">\s]*(?:videoplayback|manifest|playlist)[^'">\s]*)['"]?""", RegexOption.IGNORE_CASE)
            )
            videoPatterns.forEach { pattern ->
                pattern.findAll(html).forEach { match ->
                    val candidate = match.groupValues[1].ifEmpty { match.groupValues[0] }.trim('\'', '"')
                    if (candidate.startsWith("http") && candidate.length > 10) found.add(candidate)
                }
            }

            found.distinct().sortedByDescending { u ->
                when {
                    u.contains(".m3u8") -> 100
                    u.contains(".mpd")  -> 90
                    u.contains("1080")  -> 80
                    u.contains("720")   -> 70
                    u.contains(".mp4")  -> 60
                    else                -> 50
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractVideoUrls error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchContentByClickingTabs(
        baseUrl: String,
        query: String,
        timeout: Int = 30000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val html = fetchPageContentWithShadowAndAdSkip(baseUrl, timeout = timeout) ?: return@withContext null
            val doc = Jsoup.parse(html, baseUrl)
            val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }

            val navLinks = doc.select("nav a, .menu a, .tabs a, .categories a, ul.nav a")
                .map { it.absUrl("href") to it.text().lowercase() }
                .filter { (url, text) ->
                    url.isNotEmpty() && url.startsWith("http") &&
                    queryWords.any { word -> text.contains(word) || url.contains(word) }
                }
                .take(3)

            for ((tabUrl, _) in navLinks) {
                val tabHtml = fetchPageContentWithShadowAndAdSkip(tabUrl, timeout = timeout)
                if (!tabHtml.isNullOrEmpty()) return@withContext tabHtml
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "fetchContentByClickingTabs error: ${e.message}")
            null
        }
    }

    fun deobfuscateJs(js: String): String {
        var result = js
        var iterations = 0
        while (iterations++ < 5) {
            val packed = Regex(
                """eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[dr]\s*\)\s*\{.+?\}\s*\(\s*'([\s\S]+?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([\s\S]+?)'\.split\s*\('\|'\)""",
                RegexOption.DOT_MATCHES_ALL
            ).find(result) ?: break
            try {
                val p = packed.groupValues[1]
                val a = packed.groupValues[2].toIntOrNull() ?: 36
                val c = packed.groupValues[3].toIntOrNull() ?: 0
                val k = packed.groupValues[4].split("|")
                val unpacked = unpackPacked(p, a, c, k)
                if (unpacked.length > 50) result = result.replace(packed.value, unpacked) else break
            } catch (_: Exception) {
                break
            }
        }
        return result
    }

    private fun unpackPacked(p: String, a: Int, c: Int, k: List<String>): String {
        var result = p
        var i = c - 1
        while (i >= 0) {
            val word = k.getOrNull(i)
            if (!word.isNullOrEmpty()) {
                result = result.replace(Regex("\\b${toBase(i, a)}\\b"), word)
            }
            i--
        }
        return result
    }

    private fun toBase(num: Int, base: Int): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
        if (num == 0) return "0"
        var n = num
        val sb = StringBuilder()
        while (n > 0) {
            sb.insert(0, chars[n % base])
            n /= base
        }
        return sb.toString()
    }

    // ── Native Page Stub (Playwright Compatibility) ──────────────────────────

    class NativePage(val pageUrl: String = "") {
        private var _html: String = ""
        fun html(): String = _html
        internal fun setHtml(h: String) { _html = h }

        suspend fun navigate(url: String): NativePage {
            val html = fetchRaw(url) ?: return this
            setHtml(html)
            return this
        }

        fun content(): String = _html
        fun close() {}
        fun waitForLoadState() {}

        fun evaluate(jsCode: String): Any? {
            if (_html.isEmpty()) return null
            val videoPattern = Regex(
                "['\"]?(https?://[^'\">\\s]+\\.(?:mp4|m3u8|mpd|webm)[^'\">\\s]*)['\"]?",
                RegexOption.IGNORE_CASE
            )
            val urls = videoPattern.findAll(_html)
                .map { it.groupValues[1].ifEmpty { it.value }.trim('\'', '"') }
                .filter { it.startsWith("http") }
                .distinct()
                .toList()
            return if (urls.isNotEmpty()) urls else null
        }
    }

    fun createAntiDetectionPage(): NativePage = NativePage()
    
    fun close() {
        cookieJar.clear()
    }

    suspend fun fetchRaw(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("Referer", extractHost(url) + "/")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch error: ${e.message}")
            null
        }
    }

    private suspend fun fetchNativePage(url: String): NativePage? {
        val html = fetchRaw(url) ?: return null
        return NativePage(url).also { it.setHtml(html) }
    }

    suspend fun searchViaHeadlessForm(baseUrl: String, query: String): String? {
        val html = fetchRaw(baseUrl) ?: return null
        val doc = Jsoup.parse(html, baseUrl)
        val form = doc.select("form").firstOrNull { f ->
            f.select("input[type=text], input[type=search], input[name*=q]").isNotEmpty()
        } ?: return html

        val action = form.absUrl("action").ifEmpty { baseUrl }
        val method = form.attr("method").lowercase().ifEmpty { "get" }
        val fields = mutableMapOf<String, String>()
        
        form.select("input, select, textarea").forEach { input ->
            val name = input.attr("name")
            if (name.isNotEmpty()) {
                val type = input.attr("type").lowercase()
                if (type != "submit") {
                    fields[name] = if (name.contains("q") || name.contains("query") || type == "search") query else input.attr("value")
                }
            }
        }

        return try {
            if (method == "post") {
                val body = FormBody.Builder().apply { fields.forEach { add(it.key, it.value) } }.build()
                val req = Request.Builder()
                    .url(action)
                    .post(body)
                    .header("Referer", baseUrl)
                    .build()
                client.newCall(req).execute().use { it.body?.string() }
            } else {
                val qs = fields.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
                fetchRaw(if (action.contains("?")) "$action&$qs" else "$action?$qs")
            }
        } catch (e: Exception) {
            html
        }
    }

    private fun extractHost(url: String): String = try {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}"
    } catch (_: Exception) {
        url
    }

    private class InMemoryCookieJar : okhttp3.CookieJar {
        private val store = mutableMapOf<String, MutableList<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            store.getOrPut(url.host) { mutableListOf() }.addAll(cookies)
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = store[url.host] ?: emptyList()
        fun clear() = store.clear()
    }
}

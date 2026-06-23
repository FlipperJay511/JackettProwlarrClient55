package com.aggregatorx.app.engine.scraper

import android.content.Context
import com.aggregatorx.app.engine.network.PersistentCookieJar
import com.aggregatorx.app.engine.webview.WebViewFetcher as WVF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Basic scraping engine that uses OkHttp for direct requests and falls back to
 * WebViewFetcher when responses indicate JavaScript challenges or bot
 * verification pages. It also respects cookie persistence via PersistentCookieJar.
 */
class ScrapingEngine(
    private val context: Context,
    private val client: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val webViewFetcher: WebViewFetcher,
    private val userAgent: String
) {

    suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        // First attempt: direct HTTP request
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .build()

        try {
            val resp = client.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            val code = resp.code

            if (isChallengeResponse(code, body)) {
                // Fallback to WebView
                val w = webViewFetcher.fetch(url, userAgent)
                return@withContext when (w) {
                    is WebViewFetcher.FetchResult.Success -> w.html
                    is WebViewFetcher.FetchResult.Error -> throw Exception(w.message)
                    else -> ""
                }
            }

            return@withContext body
        } catch (t: Throwable) {
            // On network failure, try with WebView
            val w = webViewFetcher.fetch(url, userAgent)
            return@withContext when (w) {
                is WebViewFetcher.FetchResult.Success -> w.html
                is WebViewFetcher.FetchResult.Error -> throw t
                else -> ""
            }
        }
    }

    private fun isChallengeResponse(code: Int, body: String): Boolean {
        if (code == 403 || code == 429) return true
        val lowered = body.toLowerCase()
        if (lowered.contains("verify you") || lowered.contains("are you human") || lowered.contains("bot")) return true
        return false
    }

}

package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.github.franmontiel.persistentcookiejar.PersistentCookieJar
import okhttp3.HttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Wildcard imports to resolve headless browser and custom extension helpers
import com.aggregatorx.app.engine.*
import com.aggregatorx.app.engine.browser.*
import com.aggregatorx.app.util.*
import com.aggregatorx.app.ext.*

class WebViewFetcher(
    private val context: Context,
    private val cookieJar: PersistentCookieJar
) {

    private val TAG = "WebViewFetcher"

    sealed interface FetchResult {
        data class Success(val html: String, val cookies: String) : FetchResult
        data class Error(val message: String) : FetchResult
    }

    suspend fun fetch(url: String, userAgent: String): FetchResult = withContext(Dispatchers.Main) {
        val webView = HeadlessBrowserHelper.createWebView(context, userAgent)
        try {
            HeadlessBrowserHelper.loadUrlAndWait(webView, url)

            Log.d(TAG, "WebView loading completed for $url")
            // Extract HTML (evaluateJavascript returns a JSON-encoded string)
            val rawResult = try {
                JavaScriptWebViewEngine.eval(webView, "(function(){return document.documentElement.outerHTML;})()")
            } catch (t: Throwable) {
                null
            }

            Log.d(TAG, "evaluateJavascript returned, length=${rawResult?.length ?: 0}")

            // Parse out the JSON-encoded string formatting if present
            val rawHtml = if (rawResult != null) {
                if (rawResult.startsWith("\"") && rawResult.endsWith("\"") && rawResult.length >= 2) {
                    rawResult.substring(1, rawResult.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                } else {
                    rawResult
                }
            } else {
                null
            }

            // Extract cookies from CookieManager
            val cookieManager = CookieManager.getInstance()
            val cookieHeader = cookieManager.getCookie(url)
            Log.d(TAG, "CookieManager.getCookie returned: ${cookieHeader?.substring(0, Math.min(200, cookieHeader.length))}")

            if (!cookieHeader.isNullOrEmpty()) {
                try {
                    val parsedUrl = HttpUrl.get(url)
                    cookieJar.importCookiesFromHeader(parsedUrl, cookieHeader)
                    Log.d(TAG, "Imported cookies into PersistentCookieJar for ${parsedUrl.host}")
                } catch (t: Throwable) {
                    // ignore
                }
            }

            val result = FetchResult.Success(rawHtml ?: "", cookieHeader ?: "")
            return@withContext result
        } catch (e: Throwable) {
            return@withContext FetchResult.Error(e.message ?: "Unknown WebView fetch error")
        } finally {
            try {
                HeadlessBrowserHelper.destroyWebView(webView)
            } catch (_: Throwable) {
            }
        }
    }
}

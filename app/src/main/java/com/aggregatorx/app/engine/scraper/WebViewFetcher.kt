package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aggregatorx.app.engine.network.PersistentCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        var webView: WebView? = null
        try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = userAgent
                settings.domStorageEnabled = true
            }

            val html = suspendCancellableCoroutine<String?> { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
                            continuation.resume(result)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
                webView.loadUrl(url)
            }

            if (html == null) {
                return@withContext FetchResult.Error("Failed to load page in WebView")
            }

            // Parse out the JSON-encoded string formatting if present
            val rawHtml = if (html.startsWith("\"") && html.endsWith("\"") && html.length >= 2) {
                html.substring(1, html.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
            } else {
                html
            }

            // Extract cookies from CookieManager
            val cookieManager = CookieManager.getInstance()
            val cookieHeader = cookieManager.getCookie(url)
            Log.d(TAG, "CookieManager.getCookie returned: ${cookieHeader?.substring(0, Math.min(200, cookieHeader.length))}")

            if (!cookieHeader.isNullOrEmpty()) {
                try {
                    val parsedUrl = url.toHttpUrl()
                    val cookiesList = mutableListOf<Cookie>()
                    cookieHeader.split(";").forEach { cookieStr ->
                        Cookie.parse(parsedUrl, cookieStr)?.let { cookiesList.add(it) }
                    }
                    cookieJar.saveFromResponse(parsedUrl, cookiesList)
                    Log.d(TAG, "Imported cookies into PersistentCookieJar for ${parsedUrl.host}")
                } catch (t: Throwable) {
                    // ignore
                }
            }

            FetchResult.Success(rawHtml, cookieHeader ?: "")
        } catch (e: Throwable) {
            FetchResult.Error(e.message ?: "Unknown WebView fetch error")
        } finally {
            webView?.let {
                try {
                    HeadlessBrowserHelper.destroyWebView(it)
                } catch (_: Throwable) {
                }
            }
        }
    }
}

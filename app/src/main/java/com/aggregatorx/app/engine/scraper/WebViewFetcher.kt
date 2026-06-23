package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import com.aggregatorx.app.engine.network.PersistentCookieJar
import com.aggregatorx.app.engine.webview.HeadlessBrowserHelper
import com.aggregatorx.app.engine.webview.JavaScriptWebViewEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

/**
 * WebView-based fetcher that loads a page in a headless WebView, extracts final
 * HTML and cookies, and returns them to the caller. This is used as a fallback
 * when direct HTTP requests receive anti-bot challenges or require JS.
 */
class WebViewFetcher(
    private val context: Context,
    private val cookieJar: PersistentCookieJar
) {

    suspend fun fetch(url: String, desiredUserAgent: String): FetchResult = withContext(Dispatchers.IO) {
        val webView = HeadlessBrowserHelper.createWebView(context)
        try {
            // Ensure user agent matches HTTP client
            withContext(Dispatchers.Main) {
                webView.settings.userAgentString = desiredUserAgent
            }

            val semaphore = java.util.concurrent.CountDownLatch(1)

            withContext(Dispatchers.Main) {
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, urlStr: String?) {
                        try {
                            semaphore.countDown()
                        } catch (_: Throwable) {
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        semaphore.countDown()
                    }
                }

                webView.loadUrl(url)
            }

            // Wait for page finished or timeout
            semaphore.await(45, TimeUnit.SECONDS)

            // Extract HTML (evaluateJavascript returns a JSON-encoded string)
            val rawResult = try {
                JavaScriptWebViewEngine.eval(webView, "(function(){return document.documentElement.outerHTML;})()")
            } catch (t: Throwable) {
                null
            }

            val rawHtml = try {
                if (rawResult == null) "" else {
                    // Unescape JSON string result
                    val parsed = JSONTokener(rawResult).nextValue()
                    if (parsed is String) parsed else rawResult
                }
            } catch (t: Throwable) {
                // fallback to rawResult
                rawResult ?: ""
            }

            // Extract cookies from CookieManager
            val cookieManager = CookieManager.getInstance()
            val cookieHeader = cookieManager.getCookie(url)

            // Persist cookies into the persistent jar
            if (!cookieHeader.isNullOrEmpty()) {
                try {
                    val parsedUrl = HttpUrl.get(url)
                    cookieJar.importCookiesFromHeader(parsedUrl, cookieHeader)
                } catch (t: Throwable) {
                    // ignore
                }
            }

            val result = FetchResult.Success(rawHtml ?: "", cookieHeader ?: "")
            return@withContext result
        } finally {
            try {
                HeadlessBrowserHelper.destroyWebView(webView)
            } catch (_: Throwable) {
            }
        }
    }

    sealed class FetchResult {
        class Loading : FetchResult()
        data class Success(val html: String, val cookies: String) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }
}

package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.util.Log

/**
 * Utility to execute JavaScript in a WebView and return the result as a String.
 */
class JavaScriptWebViewEngine(private val context: Context) {

    private val TAG = "JavaScriptWebViewEngine"
    private var webView: WebView? = null

    private suspend fun getOrCreateWebView(): WebView = withContext(Dispatchers.Main) {
        if (webView == null) {
            webView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36"
                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "Page loaded: $url")
                    }
                }
            }
        }
        webView!!
    }

    suspend fun eval(webView: WebView, script: String): String = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String>()
        try {
            webView.evaluateJavascript(script) { result ->
                if (result == null) d.complete("") else d.complete(result)
            }
        } catch (t: Throwable) {
            d.completeExceptionally(t)
        }
        d.await()
    }

    suspend fun eval(script: String): String = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        eval(wv, script)
    }

    /**
     * Load a URL, execute JavaScript if query is provided, and return the outer HTML of the document.
     */
    suspend fun loadUrlWithJavaScript(url: String, query: String = "", timeoutMs: Long = 12000L): String = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        val loadDeferred = CompletableDeferred<Boolean>()

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!loadDeferred.isCompleted) {
                    loadDeferred.complete(true)
                }
            }
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (!loadDeferred.isCompleted) {
                    loadDeferred.complete(false)
                }
            }
        }

        wv.loadUrl(url)

        val startTime = System.currentTimeMillis()
        while (!loadDeferred.isCompleted) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                break
            }
            delay(100)
        }

        if (query.isNotEmpty()) {
            delay(1000)
        }

        getOuterHtml()
    }

    /**
     * Scroll to bottom several times to load lazy content.
     */
    suspend fun scrollToBottom(scrollIterations: Int) = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        repeat(scrollIterations) {
            eval(wv, "window.scrollTo(0, document.body.scrollHeight);")
            delay(800)
        }
    }

    /**
     * Injects query into input field, submits search, and waits for results.
     */
    suspend fun injectSearchAndWait(
        url: String,
        searchSelector: String,
        submitSelector: String,
        query: String,
        resultSelector: String,
        timeoutMs: Long = 18000L
    ): String = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        
        loadUrlWithJavaScript(url, "", timeoutMs / 2)
        
        val searchScript = """
            (function() {
                var input = document.querySelector('$searchSelector');
                if (input) {
                    input.value = '$query';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    var btn = document.querySelector('$submitSelector');
                    if (btn) {
                        btn.click();
                        return "clicked_button";
                    } else {
                        var form = input.form;
                        if (form) {
                            form.submit();
                            return "submitted_form";
                        }
                    }
                }
                return "input_not_found";
            })();
        """.trimIndent()

        val submitResult = eval(wv, searchScript)
        Log.d(TAG, "Search injection submit result: $submitResult")

        val checkInterval = 200L
        val limit = timeoutMs / 2
        var elapsed = 0L
        var found = false

        while (elapsed < limit) {
            delay(checkInterval)
            elapsed += checkInterval
            val checkScript = "document.querySelector('$resultSelector') !== null"
            val exists = eval(wv, checkScript)
            if (exists == "true") {
                found = true
                break
            }
        }
        Log.d(TAG, "Result selector '$resultSelector' found: $found")

        delay(1000)

        getOuterHtml()
    }

    suspend fun getCurrentHtml(): String = withContext(Dispatchers.Main) {
        getOuterHtml()
    }

    private suspend fun getOuterHtml(): String {
        val wv = getOrCreateWebView()
        val raw = eval(wv, "document.documentElement.outerHTML")
        return cleanJsonString(raw)
    }

    private fun cleanJsonString(html: String): String {
        if (html.startsWith("\"") && html.endsWith("\"") && html.length >= 2) {
            return html.substring(1, html.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }
        return html
    }

    fun destroy() {
        webView?.let { wv ->
            try {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.removeAllViews()
                wv.destroy()
            } catch (_: Throwable) {
            }
        }
        webView = null
    }
}

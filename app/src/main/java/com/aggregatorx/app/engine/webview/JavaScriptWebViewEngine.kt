package com.aggregatorx.app.engine.webview

import android.webkit.ValueCallback
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to execute JavaScript in a WebView and return the result as a String.
 */
object JavaScriptWebViewEngine {

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
}

package com.aggregatorx.app.engine.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Headless WebView helper that executes WebView operations on the main thread
 * and exposes suspendable helpers. WebView must be created and used on the UI
 * thread; this helper marshals calls appropriately.
 */
object HeadlessBrowserHelper {

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun createWebView(context: Context): WebView = withContext(Dispatchers.Main) {
        val wv = WebView(context.applicationContext)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
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

    suspend fun runOnMain(block: suspend () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            val d = CompletableDeferred<Unit>()
            mainHandler.post {
                try {
                    block()
                    d.complete(Unit)
                } catch (t: Throwable) {
                    d.completeExceptionally(t)
                }
            }
            d.await()
        }
    }
}

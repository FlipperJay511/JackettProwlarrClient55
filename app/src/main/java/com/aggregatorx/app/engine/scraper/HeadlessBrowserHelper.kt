package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.webkit.WebView

/**
 * Transparent proxy to prevent duplicate class definition conflicts
 * while preserving packages.
 */
object HeadlessBrowserHelper {

    suspend fun createWebView(context: Context, userAgent: String? = null): WebView {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.createWebView(context, userAgent)
    }

    suspend fun destroyWebView(webView: WebView) {
        com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.destroyWebView(webView)
    }

    suspend fun runOnMain(block: suspend () -> Unit) {
        com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.runOnMain(block)
    }

    suspend fun fetchPageContentWithShadowAndAdSkip(
        url: String,
        waitSelector: String? = null,
        timeout: Int = 30000
    ): String? {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.fetchPageContentWithShadowAndAdSkip(url, waitSelector, timeout)
    }

    suspend fun fetchPageContent(
        url: String,
        waitSelector: String? = null,
        timeout: Int = 30000
    ): String? {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.fetchPageContent(url, waitSelector, timeout)
    }

    suspend fun extractVideoUrls(url: String): List<String> {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.extractVideoUrls(url)
    }

    suspend fun fetchContentByClickingTabs(
        baseUrl: String,
        query: String,
        timeout: Int = 30000
    ): String? {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.fetchContentByClickingTabs(baseUrl, query, timeout)
    }

    fun deobfuscateJs(js: String): String {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.deobfuscateJs(js)
    }

    // Direct type reference for type safety 
    typealias NativePage = com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.NativePage

    fun createAntiDetectionPage(): NativePage {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.createAntiDetectionPage()
    }

    fun close() {
        com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.close()
    }

    suspend fun fetchRaw(url: String): String? {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.fetchRaw(url)
    }

    suspend fun searchViaHeadlessForm(baseUrl: String, query: String): String? {
        return com.aggregatorx.app.engine.webview.HeadlessBrowserHelper.searchViaHeadlessForm(baseUrl, query)
    }
}

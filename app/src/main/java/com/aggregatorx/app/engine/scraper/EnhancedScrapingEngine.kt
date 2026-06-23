package com.aggregatorx.app.engine.scraper

import android.content.Context
import com.aggregatorx.app.engine.network.ProxyVPNEngine
import com.aggregatorx.app.engine.network.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.random.Random

/**
 * Enhanced scraping engine that adds jitter, randomized delays, proxy rotation
 * and graceful fallback logic on top of the base ScrapingEngine.
 */
class EnhancedScrapingEngine(
    private val context: Context,
    private val client: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val proxyEngine: ProxyVPNEngine,
    private val webViewFetcher: WebViewFetcher,
    private val userAgent: String
) {

    private val baseEngine by lazy { ScrapingEngine(context, client, cookieJar, webViewFetcher, userAgent) }

    suspend fun fetchWithPoliteness(url: String): String = withContext(Dispatchers.IO) {
        // Randomized delay to emulate human browsing
        val jitter = Random.nextLong(300, 1200)
        delay(jitter)

        // Attempt fetch with base engine
        try {
            return@withContext baseEngine.fetchHtml(url)
        } catch (t: Throwable) {
            // If direct fetch failed, try applying a proxy and retry a couple times
            val attempts = 2
            var lastEx: Throwable? = null
            for (i in 0 until attempts) {
                try {
                    // Build a transient client with proxy applied
                    val transientBuilder = client.newBuilder()
                    proxyEngine.applyProxyIfNeeded(transientBuilder)
                    val transientClient = transientBuilder.build()
                    val engine = ScrapingEngine(context, transientClient, cookieJar, webViewFetcher, userAgent)
                    val html = engine.fetchHtml(url)
                    return@withContext html
                } catch (e: Throwable) {
                    lastEx = e
                    delay(500L * (i + 1))
                }
            }
            throw lastEx ?: Exception("Unknown scraping error")
        }
    }
}

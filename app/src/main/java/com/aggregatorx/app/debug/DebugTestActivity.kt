package com.aggregatorx.app.debug

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.aggregatorx.app.engine.network.PersistentCookieJar
import com.aggregatorx.app.engine.network.ProxyVPNEngine
import com.aggregatorx.app.engine.network.NetworkOptimizer
import com.aggregatorx.app.engine.scraper.EnhancedScrapingEngine
import com.aggregatorx.app.engine.scraper.WebViewFetcher
import android.webkit.WebSettings
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simple debug activity that runs a test fetch and logs UA/cookie round-trip and result sizes.
 * Launch with Intent extra "test_url" to specify a URL to fetch. Defaults to https://example.com
 */
class DebugTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DebugTestActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val testUrl = intent?.getStringExtra("test_url") ?: "https://example.com/"

        // Build local components (without relying on DI), mirroring NetworkModule
        val userAgent = try {
            WebSettings.getDefaultUserAgent(this)
        } catch (t: Throwable) {
            "Mozilla/5.0 (Android 13; Mobile; rv:115.0) Gecko/20100101 Firefox/115.0"
        }

        Log.d(TAG, "Using User-Agent: $userAgent")

        val cookieJar = PersistentCookieJar(applicationContext)
        val proxyEngine = ProxyVPNEngine(applicationContext)

        val clientBuilder = OkHttpClient.Builder()
        clientBuilder.addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val req = original.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .build()
            chain.proceed(req)
        })
        val logging = HttpLoggingInterceptor { msg -> Log.d(TAG, msg) }
        logging.level = HttpLoggingInterceptor.Level.BASIC
        clientBuilder.addInterceptor(logging)
        clientBuilder.cookieJar(cookieJar)
        NetworkOptimizer.tune(clientBuilder)
        proxyEngine.applyProxyIfNeeded(clientBuilder)
        val client = clientBuilder.build()

        val webViewFetcher = WebViewFetcher(applicationContext, cookieJar)
        val engine = EnhancedScrapingEngine(applicationContext, client, cookieJar, proxyEngine, webViewFetcher, userAgent)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting fetch for $testUrl")
                val html = engine.fetchWithPoliteness(testUrl)
                Log.d(TAG, "Fetched HTML length=${html.length}")

                // Inspect saved cookies for the host
                try {
                    val httpUrl = okhttp3.HttpUrl.get(testUrl)
                    val cookies = cookieJar.loadForRequest(httpUrl)
                    Log.d(TAG, "Cookies for host ${httpUrl.host}: count=${cookies.size}")
                    for (c in cookies) {
                        Log.d(TAG, "Cookie: ${c.name}=${c.value}; domain=${c.domain}; path=${c.path}")
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "Failed to list cookies: ${t.message}")
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Fetch failed: ${t.message}", t)
            }
        }
    }
}

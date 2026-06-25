package com.aggregatorx.app.engine.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple proxy rotation engine. Configure a list of proxies (host:port) via
 * constructor or persistence. This class applies a Proxy to OkHttpClient.Builder
 * when requested. For more advanced VPN routing integration, platform-specific
 * VPN APIs should be used.
 */
class ProxyVPNEngine(private val context: Context) {

    // Example list - replace with your real proxies or load from secure storage/config
    private val proxies = mutableListOf(
        // Format: host:port
        "185.162.229.213:80", // Netherlands/Europe proxy fallback
        "45.138.16.24:80"
    )

    private val counter = AtomicInteger(0)

    fun initialize() {
        // Prepare proxy rotation if needed on initialization
    }

    fun setProxies(list: List<String>) {
        proxies.clear()
        proxies.addAll(list)
    }

    fun rotateProxy() {
        counter.incrementAndGet()
    }

    fun getCurrentProxy(): Proxy? {
        val available = proxies.filter { it.trim().isNotEmpty() }
        if (available.isEmpty()) return null

        val index = Math.abs(counter.get() % available.size)
        val selected = available[index]
        val parts = selected.split(':')
        if (parts.size >= 2) {
            try {
                val host = parts[0]
                val port = parts[1].toInt()
                return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
            } catch (t: Throwable) {
                // ignore invalid proxy
            }
        }
        return null
    }

    fun applyProxyIfNeeded(builder: OkHttpClient.Builder) {
        val activeProxy = getCurrentProxy()
        if (activeProxy != null) {
            builder.proxy(activeProxy)
        }
    }

    suspend fun fetchDocumentWithProxy(url: String): Document? = withContext(Dispatchers.IO) {
        try {
            val activeProxy = getCurrentProxy()
            val connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(30000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
            
            if (activeProxy != null) {
                connection.proxy(activeProxy)
            }
            connection.get()
        } catch (e: Exception) {
            null
        }
    }

    fun createProxyClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        applyProxyIfNeeded(builder)
        return builder.build()
    }
}

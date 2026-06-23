package com.aggregatorx.app.engine.network

import android.content.Context
import okhttp3.OkHttpClient
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
        "",
    )

    private val counter = AtomicInteger(0)

    fun setProxies(list: List<String>) {
        proxies.clear()
        proxies.addAll(list)
    }

    fun applyProxyIfNeeded(builder: OkHttpClient.Builder) {
        // If no proxies configured, do nothing
        val available = proxies.filter { it.trim().isNotEmpty() }
        if (available.isEmpty()) return

        val index = Math.abs(counter.getAndIncrement() % available.size)
        val selected = available[index]
        val parts = selected.split(':')
        if (parts.size >= 2) {
            try {
                val host = parts[0]
                val port = parts[1].toInt()
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
                builder.proxy(proxy)
            } catch (t: Throwable) {
                // ignore invalid proxy
            }
        }
    }
}

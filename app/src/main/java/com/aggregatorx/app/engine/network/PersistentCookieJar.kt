package com.aggregatorx.app.engine.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A small persistent CookieJar implementation using SharedPreferences.
 * It serializes cookies as the "Set-Cookie" style string and re-parses them
 * when loading. This keeps cookies shared between WebView and OkHttp.
 */
class PersistentCookieJar(private val context: Context) : CookieJar {

    companion object {
        private const val PREFS = "persistent_cookie_jar"
        private const val KEY_COOKIES = "cookies"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cache: ConcurrentHashMap<String, MutableList<String>> = ConcurrentHashMap()

    init {
        loadFromPrefs()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = cache.getOrPut(host) { mutableListOf() }
        var changed = false
        for (c in cookies) {
            val cookieString = c.toString() // OkHttp cookie string can be parsed later with Cookie.parse
            if (!list.contains(cookieString)) {
                list.add(cookieString)
                changed = true
            }
        }
        if (changed) {
            persistToPrefs()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val list = cache[host] ?: return emptyList()
        val out = mutableListOf<Cookie>()
        val iterator = list.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val raw = iterator.next()
            val cookie = Cookie.parse(url, raw)
            if (cookie == null || cookie.expiresAt < System.currentTimeMillis()) {
                iterator.remove()
                changed = true
            } else {
                out.add(cookie)
            }
        }
        if (changed) persistToPrefs()
        return out
    }

    private fun persistToPrefs() {
        val root = JSONObject()
        for ((host, list) in cache) {
            val arr = JSONArray()
            for (s in list) arr.put(s)
            root.put(host, arr)
        }
        prefs.edit().putString(KEY_COOKIES, root.toString()).apply()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val host = keys.next()
                val arr = root.getJSONArray(host)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                cache[host] = list
            }
        } catch (t: Throwable) {
            // ignore and start fresh
            prefs.edit().remove(KEY_COOKIES).apply()
            cache.clear()
        }
    }

    /**
     * Allows external callers (e.g., WebView fetcher) to import cookies for a URL
     * given a "cookie" header string like "a=1; b=2" or a full set-cookie string.
     */
    fun importCookiesFromHeader(url: HttpUrl, cookieHeader: String) {
        val host = url.host
        val parts = cookieHeader.splitToSequence(';')
        val list = cache.getOrPut(host) { mutableListOf() }
        for (p in parts) {
            val trimmed = p.trim()
            if (trimmed.isEmpty()) continue
            // Try to parse as a cookie string
            val c = Cookie.parse(url, trimmed)
            if (c != null) {
                val raw = c.toString()
                if (!list.contains(raw)) list.add(raw)
            }
        }
        persistToPrefs()
    }

}

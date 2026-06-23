--- a/app/src/main/java/com/aggregatorx/app/engine/scraper/EnhancedScrapingEngine.kt
+++ b/app/src/main/java/com/aggregatorx/app/engine/scraper/EnhancedScrapingEngine.kt
@@
-import kotlinx.coroutines.Dispatchers
-import kotlinx.coroutines.delay
-import kotlinx.coroutines.withContext
+import kotlinx.coroutines.Dispatchers
+import kotlinx.coroutines.delay
+import kotlinx.coroutines.withContext
+import android.util.Log
@@
 class EnhancedScrapingEngine(
     private val context: Context,
     private val client: OkHttpClient,
     private val cookieJar: PersistentCookieJar,
     private val proxyEngine: ProxyVPNEngine,
     private val webViewFetcher: WebViewFetcher,
     private val userAgent: String
 ) {
+
+    private val TAG = "EnhancedScrapingEngine"
@@
     suspend fun fetchWithPoliteness(url: String): String = withContext(Dispatchers.IO) {
         // Randomized delay to emulate human browsing
         val jitter = Random.nextLong(300, 1200)
+        Log.d(TAG, "Applying jitter=$jitter ms before fetching $url")
         delay(jitter)

         // Attempt fetch with base engine
         try {
             return@withContext baseEngine.fetchHtml(url)
         } catch (t: Throwable) {
+            Log.w(TAG, "Base fetch failed: ${t.message}; attempting proxy-backed retries")
             // If direct fetch failed, try applying a proxy and retry a couple times
             val attempts = 2
             var lastEx: Throwable? = null
             for (i in 0 until attempts) {
                 try {
                     // Build a transient client with proxy applied
                     val transientBuilder = client.newBuilder()
                     proxyEngine.applyProxyIfNeeded(transientBuilder)
+                    Log.d(TAG, "Applied proxy for retry #${i+1}")
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

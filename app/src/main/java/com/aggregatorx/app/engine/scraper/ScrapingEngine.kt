--- a/app/src/main/java/com/aggregatorx/app/engine/scraper/ScrapingEngine.kt
+++ b/app/src/main/java/com/aggregatorx/app/engine/scraper/ScrapingEngine.kt
@@
-import kotlinx.coroutines.Dispatchers
-import kotlinx.coroutines.withContext
+import kotlinx.coroutines.Dispatchers
+import kotlinx.coroutines.withContext
+import android.util.Log
@@
 class ScrapingEngine(
     private val context: Context,
     private val client: OkHttpClient,
     private val cookieJar: PersistentCookieJar,
     private val webViewFetcher: WebViewFetcher,
     private val userAgent: String
 ) {
+
+    private val TAG = "ScrapingEngine"
@@
         try {
+            Log.d(TAG, "Attempting direct HTTP fetch for $url with UA=$userAgent")
             val resp = client.newCall(request).execute()
             val body = resp.body?.string() ?: ""
             val code = resp.code

             if (isChallengeResponse(code, body)) {
+                Log.d(TAG, "Detected challenge response: code=$code; falling back to WebView")
                 // Fallback to WebView
                 val w = webViewFetcher.fetch(url, userAgent)
                 return@withContext when (w) {
                     is WebViewFetcher.FetchResult.Success -> w.html
                     is WebViewFetcher.FetchResult.Error -> throw Exception(w.message)
                     else -> ""
                 }
             }

+            Log.d(TAG, "Direct HTTP fetch successful: code=$code; bodyLength=${body.length}")
             return@withContext body
         } catch (t: Throwable) {
+            Log.w(TAG, "HTTP fetch failed, attempting WebView fallback: ${t.message}")
             // On network failure, try with WebView
             val w = webViewFetcher.fetch(url, userAgent)
             return@withContext when (w) {
                 is WebViewFetcher.FetchResult.Success -> w.html
                 is WebViewFetcher.FetchResult.Error -> throw t
                 else -> ""
             }
         }
     }
@@
     private fun isChallengeResponse(code: Int, body: String): Boolean {
         if (code == 403 || code == 429) return true
         val lowered = body.toLowerCase()
         if (lowered.contains("verify you") || lowered.contains("are you human") || lowered.contains("bot")) return true
         return false
     }

 }

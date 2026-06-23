--- a/app/src/main/java/com/aggregatorx/app/engine/scraper/WebViewFetcher.kt
+++ b/app/src/main/java/com/aggregatorx/app/engine/scraper/WebViewFetcher.kt
@@
-import kotlinx.coroutines.Dispatchers
-import kotlinx.coroutines.withContext
+import kotlinx.coroutines.Dispatchers
+import kotlinx.coroutines.withContext
+import android.util.Log
@@
 class WebViewFetcher(
     private val context: Context,
     private val cookieJar: PersistentCookieJar
 ) {
+
+    private val TAG = "WebViewFetcher"
@@
-            // Extract HTML (evaluateJavascript returns a JSON-encoded string)
+            Log.d(TAG, "WebView loading completed for $url")
+            // Extract HTML (evaluateJavascript returns a JSON-encoded string)
             val rawResult = try {
                 JavaScriptWebViewEngine.eval(webView, "(function(){return document.documentElement.outerHTML;})()")
             } catch (t: Throwable) {
                 null
             }
@@
-            // Extract cookies from CookieManager
+            Log.d(TAG, "evaluateJavascript returned, length=${rawResult?.length ?: 0}")
+            // Extract cookies from CookieManager
             val cookieManager = CookieManager.getInstance()
             val cookieHeader = cookieManager.getCookie(url)
+            Log.d(TAG, "CookieManager.getCookie returned: ${cookieHeader?.substring(0, Math.min(200, cookieHeader.length))}")
@@
-            if (!cookieHeader.isNullOrEmpty()) {
+            if (!cookieHeader.isNullOrEmpty()) {
                 try {
                     val parsedUrl = HttpUrl.get(url)
                     cookieJar.importCookiesFromHeader(parsedUrl, cookieHeader)
+                    Log.d(TAG, "Imported cookies into PersistentCookieJar for ${parsedUrl.host}")
                 } catch (t: Throwable) {
                     // ignore
                 }
             }
@@
-            val result = FetchResult.Success(rawHtml ?: "", cookieHeader ?: "")
+            val result = FetchResult.Success(rawHtml ?: "", cookieHeader ?: "")
             return@withContext result
         } finally {
             try {
                 HeadlessBrowserHelper.destroyWebView(webView)
             } catch (_: Throwable) {
             }
         }
     }

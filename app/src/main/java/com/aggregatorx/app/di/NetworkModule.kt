package com.aggregatorx.app.di

import android.content.Context
import android.webkit.WebSettings
import com.aggregatorx.app.engine.network.NetworkOptimizer
import com.aggregatorx.app.engine.network.PersistentCookieJar
import com.aggregatorx.app.engine.network.ProxyVPNEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val FALLBACK_USER_AGENT = "Mozilla/5.0 (Android 13; Mobile; rv:115.0) Gecko/20100101 Firefox/115.0"
    private const val BASE_URL = "https://example.invalid/" // Will be overridden by specific API modules

    @Provides
    @Singleton
    fun provideDefaultUserAgent(@ApplicationContext context: Context): String {
        return try {
            WebSettings.getDefaultUserAgent(context)
        } catch (t: Throwable) {
            FALLBACK_USER_AGENT
        }
    }

    @Provides
    @Singleton
    fun providePersistentCookieJar(@ApplicationContext context: Context): PersistentCookieJar {
        return PersistentCookieJar(context.applicationContext)
    }

    @Provides
    @Singleton
    fun provideProxyVPNEngine(@ApplicationContext context: Context): ProxyVPNEngine = ProxyVPNEngine(context.applicationContext)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        userAgent: String,
        cookieJar: PersistentCookieJar,
        proxyEngine: ProxyVPNEngine
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // Add header interceptor to mimic browser footprint consistently
        builder.addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val reqBuilder: Request.Builder = original.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                // Client Hints (example subset)
                .header("Sec-CH-UA", "\"Chromium\";v=115, \"Not A(Brand)\";v=24, \"Google Chrome\";v=115")
                .header("Sec-CH-UA-Mobile", "?1")
                .header("Sec-CH-UA-Platform", "\"Android\"")

            val req = reqBuilder.build()
            chain.proceed(req)
        })

        // Logging for debugging builds only (kept moderate)
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BASIC
        builder.addInterceptor(logging)

        // Cookie management
        builder.cookieJar(cookieJar)

        // Apply network tuning
        NetworkOptimizer.tune(builder)

        // Apply proxy if configured for the base client (rotation may be applied per-request elsewhere)
        proxyEngine.applyProxyIfNeeded(builder)

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

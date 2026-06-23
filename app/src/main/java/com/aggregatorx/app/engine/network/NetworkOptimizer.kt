package com.aggregatorx.app.engine.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized network tuning for OkHttp clients. Keeps timeouts and retry
 * behavior consistent across the app.
 */
object NetworkOptimizer {

    // Tune the OkHttpClient.Builder with sensible defaults for scraping-heavy workloads
    fun tune(builder: OkHttpClient.Builder) {
        // Timeouts: larger than typical mobile apps to allow heavy pages to load
        builder.connectTimeout(30, TimeUnit.SECONDS)
        builder.readTimeout(45, TimeUnit.SECONDS)
        builder.writeTimeout(30, TimeUnit.SECONDS)
        builder.callTimeout(2, TimeUnit.MINUTES)

        // Retry on connection failure but not on response codes
        builder.retryOnConnectionFailure(true)

        // Additional interceptors such as exponential backoff or circuit breakers
        // can be added here for future improvements.
    }
}

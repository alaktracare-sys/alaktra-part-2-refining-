package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import com.example.data.remote.cache.ApiCacheConfig
import com.example.data.remote.cache.OfflineCacheInterceptor
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Centralized OkHttp client factory sharing base security/dispatcher configuration while
 * isolating bulky media streams/preloads/downloads into a dedicated ConnectionPool so
 * large audio transfers never head-of-line-block fast API JSON or thumbnail requests.
 */
object HttpClients {
    private val apiDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 20
    }

    private val apiConnectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    private val mediaDispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 12
    }

    private val mediaConnectionPool = ConnectionPool(4, 2, TimeUnit.MINUTES)

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(apiDispatcher)
            .connectionPool(apiConnectionPool)
            .addInterceptor(AppSecurityInterceptor())
            .build()
    }

    @Volatile
    private var apiClientInstance: OkHttpClient? = null

    @Volatile
    private var imageClientInstance: OkHttpClient? = null

    @Volatile
    private var mediaClientInstance: OkHttpClient? = null

    @Volatile
    private var mediaPreloadClientInstance: OkHttpClient? = null

    @Volatile
    private var downloadClientInstance: OkHttpClient? = null

    fun api(context: Context): OkHttpClient {
        return apiClientInstance ?: synchronized(this) {
            apiClientInstance ?: run {
                val appContext = context.applicationContext
                val logging = HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                }
                val httpCacheDir = File(appContext.cacheDir, ApiCacheConfig.HTTP_CACHE_DIR)
                val httpCache = Cache(httpCacheDir, ApiCacheConfig.HTTP_CACHE_MAX_BYTES)
                baseClient.newBuilder()
                    .cache(httpCache)
                    .addInterceptor(logging)
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build()
                    .also { apiClientInstance = it }
            }
        }
    }

    fun image(context: Context): OkHttpClient {
        return imageClientInstance ?: synchronized(this) {
            imageClientInstance ?: run {
                val appContext = context.applicationContext
                baseClient.newBuilder()
                    .cache(Cache(appContext.cacheDir.resolve("okhttp_image_cache"), 50L * 1024 * 1024))
                    .addInterceptor(OfflineCacheInterceptor(appContext))
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                    .also { imageClientInstance = it }
            }
        }
    }

    fun media(): OkHttpClient {
        return mediaClientInstance ?: synchronized(this) {
            mediaClientInstance ?: baseClient.newBuilder()
                .cache(null)
                .dispatcher(mediaDispatcher)
                .connectionPool(mediaConnectionPool)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
                .also { mediaClientInstance = it }
        }
    }

    fun mediaPreload(): OkHttpClient {
        return mediaPreloadClientInstance ?: synchronized(this) {
            mediaPreloadClientInstance ?: media().newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                .also { mediaPreloadClientInstance = it }
        }
    }

    fun download(): OkHttpClient {
        return downloadClientInstance ?: synchronized(this) {
            downloadClientInstance ?: media().newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                .also { downloadClientInstance = it }
        }
    }
}

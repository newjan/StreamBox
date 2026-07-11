package com.streambox.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@HiltAndroidApp
class StreamBoxApplication : Application(), ImageLoaderFactory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    /**
     * Logos are tiny; keep Coil's memory cache small (10% instead of the
     * default ~25%) so 12k-channel browsing stays comfortable on low-RAM
     * phones and TV boxes. Disk cache is capped at 64 MB. Shares the app's
     * OkHttp client so logos get the same CA-bundle fallback as playlists.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                EntryPointAccessors
                    .fromApplication(this, ImageLoaderEntryPoint::class.java)
                    .okHttpClient()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
}

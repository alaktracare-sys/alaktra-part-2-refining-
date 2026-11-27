package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.launch

class AlreadyApp : Application(), ImageLoaderFactory {
    lateinit var repository: MusicRepository
        private set
    lateinit var audioController: AudioController
        private set

    override fun onCreate() {
        super.onCreate()
        com.example.ui.screens.player.ShapeAtlas.preloadAsync(this)
        repository = MusicRepository.getInstance(this)
        audioController = AudioController.getInstance(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::repository.isInitialized && level >= TRIM_MEMORY_UI_HIDDEN) {
            repository.soundCapsuleRepository.trimMemory()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(com.example.data.remote.HttpClients.image(this))
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache for artwork
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}

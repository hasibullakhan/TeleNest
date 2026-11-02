package com.awd.teledrive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@HiltAndroidApp
class TeleDriveApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("telenest_image_cache"))
                    .maxSizeBytes(250L * 1024L * 1024L) // 250 MB disk cache
                    .build()
            }
            .decoderDispatcher(decodeDispatcher)
            .fetcherDispatcher(Dispatchers.IO)
            .allowRgb565(true)
            .crossfade(120)
            .respectCacheHeaders(false)
            .build()
    }
}


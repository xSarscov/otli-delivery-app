package com.otli.app.catalog.adapters.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.catalog.application.PhotoSource
import com.otli.app.core.result.suspendRunCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns stored JPEG bytes into a bitmap; returns null when the bytes are not a readable image. */
fun interface PhotoDecoder {
    fun decode(bytes: ByteArray): ImageBitmap?
}

/**
 * Loads photos for the UI: fetches the bytes, decodes them off the main thread and keeps the
 * results in a small least-recently-used memory cache bounded by [maxCacheBytes]. The cache key is
 * the whole [PhotoKey], so a new `photoVersion` is a cache miss and the old picture ages out.
 * Failures (missing photo, offline, corrupt bytes) come back as null and are never cached.
 */
@Singleton
class PhotoLoader(
    private val source: PhotoSource,
    private val decoder: PhotoDecoder,
    private val decodeDispatcher: CoroutineDispatcher,
    private val maxCacheBytes: Long,
) {
    @Inject
    constructor(source: PhotoSource, decoder: PhotoDecoder) :
        this(source, decoder, Dispatchers.Default, DEFAULT_CACHE_BYTES)

    private val cache = LinkedHashMap<PhotoKey, ImageBitmap>(16, 0.75f, true)
    private var cachedBytes = 0L

    /** The picture if it is already in memory; never does I/O. */
    fun peek(key: PhotoKey): ImageBitmap? = synchronized(cache) { cache[key] }

    suspend fun load(key: PhotoKey): ImageBitmap? {
        if (key.version <= 0) return null
        peek(key)?.let { return it }
        val bytes = suspendRunCatching { source.fetch(key.merchantId, key.photoId) }.getOrNull() ?: return null
        val image = withContext(decodeDispatcher) { decoder.decode(bytes) } ?: return null
        remember(key, image)
        return image
    }

    private fun remember(key: PhotoKey, image: ImageBitmap) = synchronized(cache) {
        cache.put(key, image)?.let { cachedBytes -= it.byteSize() }
        cachedBytes += image.byteSize()
        val eldest = cache.entries.iterator()
        while (cachedBytes > maxCacheBytes && cache.size > 1 && eldest.hasNext()) {
            val entry = eldest.next()
            if (entry.key == key) continue
            cachedBytes -= entry.value.byteSize()
            eldest.remove()
        }
    }

    private fun ImageBitmap.byteSize(): Long = width.toLong() * height * BYTES_PER_PIXEL

    private companion object {
        const val BYTES_PER_PIXEL = 4
        const val DEFAULT_CACHE_BYTES = 12L * 1024 * 1024
    }
}

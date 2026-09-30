package com.otli.app.catalog.adapters.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Turns a picked photo into the small JPEG stored in Firestore (ADR-11): longest edge at most
 * [MAX_EDGE] px, quality [QUALITIES] first entry, stepped down until it fits the 300 KB rules cap.
 */
class ImageCompressor(private val maxBytes: Int) {
    @Inject
    constructor() : this(MAX_BYTES)


    fun compress(source: ByteArray): Result<ByteArray> = try {
        Result.success(encode(scale(source)))
    } catch (failure: IllegalArgumentException) {
        Result.failure(failure)
    } catch (failure: IllegalStateException) {
        Result.failure(failure)
    }

    private fun scale(source: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The picked file is not a readable image" }

        val (width, height) = ImageScaling.fit(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        // Decode at a power-of-two reduction first so a large camera photo never sits in memory at full size.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(source, 0, source.size, options)) {
            "The picked file is not a readable image"
        }
        return if (decoded.width == width && decoded.height == height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true)
        }
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        for (quality in QUALITIES) {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "JPEG encoding failed" }
            if (output.size() <= maxBytes) return output.toByteArray()
        }
        error("The photo cannot be compressed under $maxBytes bytes")
    }

    companion object {
        const val MAX_EDGE = 640

        /** Matches the `productPhotos` rule cap of 300 KB. */
        const val MAX_BYTES = 300 * 1024
        private val QUALITIES = listOf(70, 60, 50, 40, 30)
    }
}

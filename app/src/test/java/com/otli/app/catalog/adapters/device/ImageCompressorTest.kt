package com.otli.app.catalog.adapters.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Native graphics mode so the JPEG encoder and decoder are real, not shadows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageCompressorTest {
    private val compressor = ImageCompressor()

    private fun gradientPng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) for (y in 0 until height) {
            bitmap.setPixel(x, y, Color.rgb(x * 255 / width, y * 255 / height, 128))
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun noisePng(size: Int): ByteArray {
        val random = Random(42)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)))
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun decode(bytes: ByteArray) = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    @Test
    fun aLargeLandscapeImageIsScaledToAMaxEdgeOf640AndEncodedAsJpeg() {
        val output = compressor.compress(gradientPng(1600, 1200)).getOrThrow()

        assertThat(output.take(2)).containsExactly(0xFF.toByte(), 0xD8.toByte()).inOrder()
        val decoded = decode(output)
        assertThat(decoded.width).isEqualTo(640)
        assertThat(decoded.height).isEqualTo(480)
        assertThat(output.size).isAtMost(ImageCompressor.MAX_BYTES)
    }

    @Test
    fun aLargePortraitImageKeepsItsAspectRatio() {
        val decoded = decode(compressor.compress(gradientPng(900, 1800)).getOrThrow())

        assertThat(decoded.width).isEqualTo(320)
        assertThat(decoded.height).isEqualTo(640)
    }

    @Test
    fun aSmallImageIsNeverUpscaled() {
        val decoded = decode(compressor.compress(gradientPng(300, 200)).getOrThrow())

        assertThat(decoded.width).isEqualTo(300)
        assertThat(decoded.height).isEqualTo(200)
    }

    @Test
    fun aTighterCapLowersTheQualityStepByStepUntilTheOutputFits() {
        val noise = noisePng(1000)
        val atDefaultQuality = compressor.compress(noise).getOrThrow()

        val capped = ImageCompressor(maxBytes = atDefaultQuality.size / 2).compress(noise).getOrThrow()

        assertThat(capped.size).isAtMost(atDefaultQuality.size / 2)
        assertThat(capped.size).isLessThan(atDefaultQuality.size)
        assertThat(decode(capped).width).isEqualTo(640)
    }

    @Test
    fun aPhotoThatCannotFitEvenAtTheLowestQualityFails() {
        val result = ImageCompressor(maxBytes = 500).compress(noisePng(1000))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun aNoisyImageFitsTheRealRulesCap() {
        assertThat(compressor.compress(noisePng(1000)).getOrThrow().size).isAtMost(ImageCompressor.MAX_BYTES)
    }

    @Test
    fun bytesThatAreNotAnImageFail() {
        val result = compressor.compress(byteArrayOf(1, 2, 3, 4, 5))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun theSizeCapMatchesTheFirestoreRulesLimitOf300Kb() {
        assertThat(ImageCompressor.MAX_BYTES).isEqualTo(307_200)
    }
}

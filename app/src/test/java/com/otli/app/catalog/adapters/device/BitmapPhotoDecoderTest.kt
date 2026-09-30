package com.otli.app.catalog.adapters.device

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapPhotoDecoderTest {
    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(200, 80, 20))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    @Test
    fun decodesJpegBytesIntoAnImageOfTheSameSize() {
        val image = BitmapPhotoDecoder().decode(jpeg(64, 32))

        assertThat(image).isNotNull()
        assertThat(image!!.width).isEqualTo(64)
        assertThat(image.height).isEqualTo(32)
    }

    @Test
    fun anotherSizeDecodesToThatSizeNotAConstant() {
        val image = BitmapPhotoDecoder().decode(jpeg(10, 40))

        assertThat(image!!.width).isEqualTo(10)
        assertThat(image.height).isEqualTo(40)
    }

    @Test
    fun bytesThatAreNotAnImageDecodeToNull() {
        assertThat(BitmapPhotoDecoder().decode(byteArrayOf(1, 2, 3, 4))).isNull()
    }

    @Test
    fun emptyBytesDecodeToNull() {
        assertThat(BitmapPhotoDecoder().decode(ByteArray(0))).isNull()
    }
}

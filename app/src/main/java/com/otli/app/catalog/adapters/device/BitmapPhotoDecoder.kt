package com.otli.app.catalog.adapters.device

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.otli.app.catalog.adapters.ui.PhotoDecoder
import javax.inject.Inject

/** Decodes the small stored JPEGs (at most 640 px, ADR-11) straight into a Compose bitmap. */
class BitmapPhotoDecoder @Inject constructor() : PhotoDecoder {
    override fun decode(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}

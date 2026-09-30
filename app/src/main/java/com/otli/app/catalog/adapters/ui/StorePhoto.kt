package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.core.theme.OtliTheme

/** Provided once at the app root; absent in previews and tests that do not care about photos. */
val LocalPhotoLoader = staticCompositionLocalOf<PhotoLoader?> { null }

object PhotoTags {
    const val IMAGE = "photo-image"
    const val PLACEHOLDER = "photo-placeholder"
}

/**
 * A stored merchant or product photo. Shows a neutral placeholder while loading and whenever there
 * is nothing to show (no [key], version 0, no loader, offline, unreadable bytes).
 */
@Composable
fun StorePhoto(
    key: PhotoKey?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val loader = LocalPhotoLoader.current
    val image by produceState<ImageBitmap?>(initialValue = null, key, loader) {
        value = key?.let { loader?.peek(it) }
        if (value == null && key != null) value = loader?.load(key)
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.testTag(PhotoTags.IMAGE),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).testTag(PhotoTags.PLACEHOLDER))
    }
}

@Preview(showBackground = true)
@Composable
private fun StorePhotoPlaceholderPreview() {
    OtliTheme { StorePhoto(key = null, modifier = Modifier.size(96.dp)) }
}

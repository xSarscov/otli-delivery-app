package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.catalog.application.PhotoSource
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorePhotoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fetched = mutableListOf<String>()
    private var sourceOutcome: (String) -> ByteArray? = { byteArrayOf(1) }

    private val loader = PhotoLoader(
        source = object : PhotoSource {
            override suspend fun fetch(merchantId: String, photoId: String): ByteArray? {
                fetched += photoId
                return sourceOutcome(photoId)
            }
        },
        decoder = { ImageBitmap(4, 4) },
        decodeDispatcher = Dispatchers.Unconfined,
        maxCacheBytes = 1_000_000L,
    )

    private var key by mutableStateOf<PhotoKey?>(PhotoKey.product("m1", "p1", 1))

    private fun show(withLoader: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalPhotoLoader provides if (withLoader) loader else null) {
                StorePhoto(key = key, modifier = Modifier.size(48.dp), contentDescription = "Photo")
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun showsTheDecodedPictureOnceItHasLoaded() {
        show()

        compose.onNodeWithTag(PhotoTags.IMAGE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Photo").assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertDoesNotExist()
    }

    @Test
    fun showsAPlaceholderWhenThereIsNoKey() {
        key = null
        show()

        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.IMAGE).assertDoesNotExist()
        assertThat(fetched).isEmpty()
    }

    @Test
    fun showsAPlaceholderWhenThePhotoVersionIsZero() {
        key = PhotoKey.product("m1", "p1", 0)
        show()

        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertIsDisplayed()
        assertThat(fetched).isEmpty()
    }

    @Test
    fun showsAPlaceholderWhenNoLoaderIsProvided() {
        show(withLoader = false)

        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.IMAGE).assertDoesNotExist()
    }

    @Test
    fun keepsThePlaceholderWhenThePhotoCannotBeLoaded() {
        sourceOutcome = { null }
        show()

        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertIsDisplayed()
        assertThat(fetched).containsExactly("p1")
    }

    @Test
    fun switchingTheKeyLoadsTheNewPhotoAndDropsTheOldOne() {
        show()
        compose.onNodeWithTag(PhotoTags.IMAGE).assertIsDisplayed()
        sourceOutcome = { null }

        key = PhotoKey.product("m1", "p2", 1)
        compose.waitForIdle()

        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertIsDisplayed()
        assertThat(fetched).containsExactly("p1", "p2").inOrder()
    }
}

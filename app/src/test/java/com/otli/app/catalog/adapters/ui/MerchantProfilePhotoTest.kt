package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.application.PhotoSource
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The merchant sees their own stored photo on the profile, not only a status line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1600dp")
class MerchantProfilePhotoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fetched = mutableListOf<Pair<String, String>>()
    private val loader = PhotoLoader(
        source = PhotoSource { merchantId, photoId ->
            fetched += merchantId to photoId
            byteArrayOf(1)
        },
        decoder = { ImageBitmap(4, 4) },
        decodeDispatcher = Dispatchers.Unconfined,
        maxCacheBytes = 1_000_000L,
    )

    private fun show(state: MerchantProfileUiState) {
        compose.setContent {
            CompositionLocalProvider(LocalPhotoLoader provides loader) {
                MerchantProfileContent(
                    state = state,
                    onNameChange = {},
                    onDescriptionChange = {},
                    onPhoneChange = {},
                    onOpenChange = {},
                    onPickPhoto = {},
                    onSave = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private val base = MerchantProfileUiState(isLoading = false, merchantId = "m1", name = "Comedor Marta")

    @Test
    fun showsTheStoredProfilePhoto() {
        show(base.copy(photoVersion = 2))

        compose.onNodeWithTag(PhotoTags.IMAGE).assertIsDisplayed()
        assertThat(fetched).containsExactly("m1" to "profile")
    }

    @Test
    fun aMerchantWithoutAPhotoSeesAPlaceholderAndNothingIsFetched() {
        show(base.copy(photoVersion = 0))

        compose.onNodeWithTag(PhotoTags.PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithTag(PhotoTags.IMAGE).assertDoesNotExist()
        assertThat(fetched).isEmpty()
    }
}

package com.otli.app.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.SessionState
import com.otli.app.core.theme.OtliTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The root host owns the two things every screen used to lack (F.1, F.2): system-bar insets and
 * an opaque themed surface. Screens are placed by the root, so none of them pads for bars itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RootSurfaceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val session = MutableStateFlow<SessionState>(SessionState.Loading)
    private var background = Color.Unspecified

    private fun launch(darkTheme: Boolean, insets: WindowInsets) {
        compose.setContent {
            OtliTheme(darkTheme = darkTheme) {
                background = MaterialTheme.colorScheme.background
                RootNavHost(session = session, windowInsets = insets)
            }
        }
        compose.waitForIdle()
    }

    private fun rootPixel(x: Int, y: Int): Int = compose.onRoot().captureToImage().let { image ->
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels, 0, 0, image.width, image.height)
        pixels[y * image.width + x]
    }

    @Test
    fun screensAreKeptBelowTheStatusBarAndAboveTheNavigationBar() {
        launch(darkTheme = false, insets = WindowInsets(top = 48.dp, bottom = 32.dp))

        val screen = compose.onNodeWithTag(RootTags.LOADING).getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()

        assertThat(screen.top).isEqualTo(48.dp)
        assertThat(root.bottom - screen.bottom).isEqualTo(32.dp)
    }

    @Test
    fun screensGetNoExtraPaddingWhenThereAreNoInsets() {
        launch(darkTheme = false, insets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))

        val screen = compose.onNodeWithTag(RootTags.LOADING).getUnclippedBoundsInRoot()

        assertThat(screen.top).isEqualTo(0.dp)
    }

    @Test
    fun theThemeBackgroundFillsTheWholeWindowIncludingUnderTheBars() {
        launch(darkTheme = true, insets = WindowInsets(top = 48.dp))

        assertThat(rootPixel(2, 2)).isEqualTo(background.toArgb())
        assertThat(background).isNotEqualTo(Color.White)
    }

    @Test
    fun theLightThemePaintsItsOwnBackgroundToo() {
        launch(darkTheme = false, insets = WindowInsets(top = 48.dp))

        assertThat(rootPixel(2, 2)).isEqualTo(background.toArgb())
    }
}

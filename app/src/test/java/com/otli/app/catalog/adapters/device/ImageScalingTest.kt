package com.otli.app.catalog.adapters.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageScalingTest {
    @Test
    fun aLandscapeImageScalesTheWidthToTheMaxEdge() {
        assertThat(ImageScaling.fit(1600, 1200, maxEdge = 640)).isEqualTo(640 to 480)
    }

    @Test
    fun aPortraitImageScalesTheHeightToTheMaxEdge() {
        assertThat(ImageScaling.fit(900, 1800, maxEdge = 640)).isEqualTo(320 to 640)
    }

    @Test
    fun anImageWithinTheMaxEdgeIsLeftUntouched() {
        assertThat(ImageScaling.fit(640, 480, maxEdge = 640)).isEqualTo(640 to 480)
        assertThat(ImageScaling.fit(100, 50, maxEdge = 640)).isEqualTo(100 to 50)
    }

    @Test
    fun aVeryThinImageNeverCollapsesBelowOnePixel() {
        assertThat(ImageScaling.fit(6400, 2, maxEdge = 640)).isEqualTo(640 to 1)
    }
}

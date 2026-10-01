package com.otli.app.core.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun itAsksOnAndroid13AndLaterWhenNotGrantedYet() {
        assertThat(NotificationPermission.shouldRequest(sdkInt = 33, granted = false)).isTrue()
        assertThat(NotificationPermission.shouldRequest(sdkInt = 36, granted = false)).isTrue()
    }

    @Test
    fun itNeverAsksBeforeAndroid13BecauseThereIsNoRuntimePermission() {
        assertThat(NotificationPermission.shouldRequest(sdkInt = 32, granted = false)).isFalse()
        assertThat(NotificationPermission.shouldRequest(sdkInt = 26, granted = false)).isFalse()
    }

    @Test
    fun itDoesNotAskAgainOnceGranted() {
        assertThat(NotificationPermission.shouldRequest(sdkInt = 34, granted = true)).isFalse()
    }
}

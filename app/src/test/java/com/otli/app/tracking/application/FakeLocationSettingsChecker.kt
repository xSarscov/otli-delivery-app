package com.otli.app.tracking.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** The location switch under the test's control; [failure] makes the flow fail like a broken system service would. */
class FakeLocationSettingsChecker(initiallyOn: Boolean = true) : LocationSettingsChecker {
    val enabled = MutableStateFlow(initiallyOn)
    var failure: Throwable? = null

    override fun servicesEnabled(): Flow<Boolean> {
        val broken = failure
        return if (broken != null) flow { throw broken } else enabled
    }
}
